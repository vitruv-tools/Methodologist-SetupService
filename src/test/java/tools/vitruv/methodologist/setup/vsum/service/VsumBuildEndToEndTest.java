package tools.vitruv.methodologist.setup.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import tools.vitruv.methodologist.setup.model.service.EcoreToGenmodelService;

/**
 * Builds a real VSUM project with Maven, starts the resulting jar and queries the constraints of
 * the running Vitruv server. Requires Maven and the configured Vitruvius versions; enable with
 * {@code -Dvsum.e2e=true}.
 */
@EnabledIfSystemProperty(named = "vsum.e2e", matches = "true")
class VsumBuildEndToEndTest {
  private static final String SERVER_URL = "http://localhost:8080";

  @TempDir Path runDirectory;

  @Test
  void builtVsumServesTheUploadedConstraints() throws Exception {
    VsumProjectBuildService buildService =
        new VsumProjectBuildService(
            new VsumService(new GenerateFromTemplate(), new EcoreToGenmodelService()));

    byte[] jar =
        buildService.buildProjectJar(
            List.of(file("metamodelFiles", "model.ecore"), file("metamodelFiles", "model2.ecore")),
            List.of(
                file("genmodelFiles", "model.genmodel"), file("genmodelFiles", "model2.genmodel")),
            List.of(file("reactionFiles", "templateReactions.reactions")),
            List.of(file("constraintFiles", "Größen & Preise (v1).ocl")));

    Path jarFile = Files.write(runDirectory.resolve("vsum.jar"), jar);
    Process server =
        new ProcessBuilder("java", "-jar", jarFile.toString())
            .directory(runDirectory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(runDirectory.resolve("server.log").toFile())
            .start();
    try {
      HttpClient http = HttpClient.newHttpClient();
      awaitHealthy(http, server);
      // otherwise another process answered on the port
      assertThat(server.isAlive()).as("the built VSUM server is running").isTrue();

      String constraints = get(http, "/vsum/constraints");
      assertThat(constraints).contains("model::Component::componentHasName");

      String validation = post(http, "/vsum/constraints/validate", "{}");
      assertThat(validation).contains("\"status\" : \"SATISFIED\"");
    } finally {
      stop(server);
    }
  }

  @Test
  void builtVsumWithoutConstraintsStarts() throws Exception {
    VsumProjectBuildService buildService =
        new VsumProjectBuildService(
            new VsumService(new GenerateFromTemplate(), new EcoreToGenmodelService()));

    byte[] jar =
        buildService.buildProjectJar(
            List.of(file("metamodelFiles", "model.ecore"), file("metamodelFiles", "model2.ecore")),
            List.of(
                file("genmodelFiles", "model.genmodel"), file("genmodelFiles", "model2.genmodel")),
            List.of(file("reactionFiles", "templateReactions.reactions")));

    Path jarFile = Files.write(runDirectory.resolve("vsum-plain.jar"), jar);
    Process server =
        new ProcessBuilder("java", "-jar", jarFile.toString())
            .directory(runDirectory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(runDirectory.resolve("server-plain.log").toFile())
            .start();
    try {
      HttpClient http = HttpClient.newHttpClient();
      awaitHealthy(http, server);
      assertThat(server.isAlive()).as("the built VSUM server is running").isTrue();

      assertThat(get(http, "/vsum/constraints")).isEqualTo("[ ]");
    } finally {
      stop(server);
    }
    assertThat(server.exitValue()).as("quit stops the server normally").isZero();
  }

  @Test
  void builtVsumKeepsRunningWithoutInput() throws Exception {
    VsumProjectBuildService buildService =
        new VsumProjectBuildService(
            new VsumService(new GenerateFromTemplate(), new EcoreToGenmodelService()));

    byte[] jar =
        buildService.buildProjectJar(
            List.of(file("metamodelFiles", "model.ecore"), file("metamodelFiles", "model2.ecore")),
            List.of(
                file("genmodelFiles", "model.genmodel"), file("genmodelFiles", "model2.genmodel")),
            List.of(file("reactionFiles", "templateReactions.reactions")));

    Path jarFile = Files.write(runDirectory.resolve("vsum-detached.jar"), jar);
    // like in a container or as a service: the input ends immediately
    Path noInput = Files.createFile(runDirectory.resolve("no-input"));
    Process server =
        new ProcessBuilder("java", "-jar", jarFile.toString())
            .directory(runDirectory.toFile())
            .redirectInput(noInput.toFile())
            .redirectErrorStream(true)
            .redirectOutput(runDirectory.resolve("server-detached.log").toFile())
            .start();
    try {
      HttpClient http = HttpClient.newHttpClient();
      awaitHealthy(http, server);
      Thread.sleep(2000);
      assertThat(server.isAlive()).as("the VSUM server keeps running without input").isTrue();
      assertThat(get(http, "/vsum/constraints")).isEqualTo("[ ]");
      assertThat(runDirectory.resolve("server-detached.log"))
          .as("the end of the input is no error")
          .content()
          .doesNotContain("Exception in thread");
    } finally {
      server.destroy();
      if (!server.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
        server.destroyForcibly();
      }
    }
  }

  private static void awaitHealthy(HttpClient http, Process server) throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
    while (Instant.now().isBefore(deadline)) {
      if (!server.isAlive()) {
        throw new IllegalStateException("VSUM server terminated with " + server.exitValue());
      }
      try {
        if (http.send(request("/health").GET().build(), HttpResponse.BodyHandlers.ofString())
                .statusCode()
            == 200) {
          return;
        }
      } catch (IOException notYetStarted) {
        // the server is still starting
      }
      Thread.sleep(500);
    }
    throw new IllegalStateException("VSUM server did not start");
  }

  private static String get(HttpClient http, String path) throws Exception {
    return http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
  }

  private static String post(HttpClient http, String path, String body) throws Exception {
    return http.send(
            request(path).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString())
        .body();
  }

  private static HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create(SERVER_URL + path)).timeout(Duration.ofSeconds(10));
  }

  private static void stop(Process server) throws InterruptedException {
    try (OutputStream input = server.getOutputStream()) {
      input.write("quit\n".getBytes());
    } catch (IOException alreadyStopped) {
      // nothing to do
    }
    if (!server.waitFor(
        Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
      server.destroyForcibly();
    }
  }

  private static MockMultipartFile file(String part, String name) throws IOException {
    try (InputStream content = VsumBuildEndToEndTest.class.getResourceAsStream("/e2e/" + name)) {
      return new MockMultipartFile(part, name, "application/octet-stream", content);
    }
  }
}
