package tools.vitruv.methodologist.setup.vsum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.fixture;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.vsum.service.build.MavenBuildRunner;
import tools.vitruv.methodologist.setup.vsum.service.constraints.ConstraintsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.MetamodelProcessor;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectGenerator;
import tools.vitruv.methodologist.setup.vsum.service.reactions.ReactionsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.views.NeoJoinChecker;
import tools.vitruv.methodologist.setup.vsum.service.views.NeoJoinViewDefinitionProcessor;

/**
 * Builds VSUMs, starts them and checks that their Vitruv server serves the uploaded constraints and
 * views. Runs the real Maven build and needs port 8080, so it only runs with {@code
 * -Dvsum.integration=true}.
 */
@EnabledIfSystemProperty(named = "vsum.integration", matches = "true")
class VsumBuildEndToEndTest {
  private static final String SERVER_URL = "http://localhost:8080";

  private final VsumBuildProperties properties = new VsumBuildProperties();
  private final VsumProjectBuildService service =
      new VsumProjectBuildService(
          new MetamodelProcessor(),
          new ReactionsProcessor(),
          new ConstraintsProcessor(),
          new NeoJoinChecker(),
          new NeoJoinViewDefinitionProcessor(),
          new ProjectGenerator(properties),
          new MavenBuildRunner(properties),
          properties);

  @TempDir Path runDirectory;

  @Test
  void builtVsumServesTheUploadedConstraints() throws Exception {
    byte[] jar =
        service.buildProjectJar(
            new VsumBuildRequest(
                List.of(fixture("model.ecore"), fixture("model2.ecore")),
                List.of(fixture("templateReactions.reactions")),
                List.of(fixture("constraints.ocl")),
                List.of()));

    Process server = start(jar);
    try {
      HttpClient http = HttpClient.newHttpClient();
      awaitHealthy(http, server);

      HttpResponse<String> constraints = send(http, request("/vsum/constraints").GET());
      assertEquals(200, constraints.statusCode());
      assertTrue(
          constraints.body().contains("\"model::Component::ComponentHasCorrespondingEntity\""),
          constraints.body());

      HttpResponse<String> validation =
          send(
              http,
              request("/vsum/constraints/validate")
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{}")));
      assertEquals(200, validation.statusCode());
      assertTrue(validation.body().contains("\"satisfied\" : true"), validation.body());
    } finally {
      stop(server);
    }
  }

  @Test
  void builtVsumServesTheUploadedViews() throws Exception {
    byte[] jar =
        service.buildProjectJar(
            new VsumBuildRequest(
                List.of(fixture("model.ecore"), fixture("model2.ecore")),
                List.of(),
                List.of(),
                List.of(fixture("components.nj"))));

    Process server = start(jar);
    try {
      HttpClient http = HttpClient.newHttpClient();
      awaitHealthy(http, server);

      // the view type generated from components.nj is offered next to the identity view types
      HttpResponse<String> viewTypes = send(http, request("/vsum/view/types").GET());
      assertEquals(200, viewTypes.statusCode());
      assertTrue(viewTypes.body().contains("\"Components\""), viewTypes.body());
      assertTrue(viewTypes.body().contains("\"model\""), viewTypes.body());

      // a view is fetched in two steps: a selector of the view type, then the view of its selection
      HttpResponse<String> selector =
          send(http, request("/vsum/view/selector").header("View-Type", "Components").GET());
      assertEquals(200, selector.statusCode(), selector.body());
      String selectorUuid = selector.headers().firstValue("Selector-UUID").orElseThrow();

      HttpResponse<String> view =
          send(
              http,
              request("/vsum/view")
                  .header("Selector-UUID", selectorUuid)
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("[]")));
      assertEquals(200, view.statusCode(), view.body());
      // the root of the view metamodel exported by components.nj
      assertTrue(view.body().contains("components#//Root"), view.body());
    } finally {
      stop(server);
    }
  }

  private Process start(byte[] jar) throws IOException {
    Path jarFile = Files.write(runDirectory.resolve("vsum.jar"), jar);
    return new ProcessBuilder("java", "-jar", jarFile.toString())
        .directory(runDirectory.toFile())
        .redirectErrorStream(true)
        .redirectOutput(runDirectory.resolve("server.log").toFile())
        .start();
  }

  private static void stop(Process server) throws InterruptedException {
    server.destroy();
    if (!server.waitFor(10, TimeUnit.SECONDS)) {
      server.destroyForcibly();
    }
  }

  private static void awaitHealthy(HttpClient http, Process server) throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
    while (Instant.now().isBefore(deadline)) {
      if (!server.isAlive()) {
        throw new IllegalStateException("VSUM server terminated with " + server.exitValue());
      }
      try {
        if (send(http, request("/health").GET()).statusCode() == 200) {
          return;
        }
      } catch (IOException notYetStarted) {
        // the server is still starting
      }
      Thread.sleep(500);
    }
    throw new IllegalStateException("VSUM server did not start");
  }

  private static HttpResponse<String> send(HttpClient http, HttpRequest.Builder request)
      throws IOException, InterruptedException {
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create(SERVER_URL + path)).timeout(Duration.ofSeconds(10));
  }
}
