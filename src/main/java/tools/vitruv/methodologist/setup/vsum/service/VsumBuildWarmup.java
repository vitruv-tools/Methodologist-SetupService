package tools.vitruv.methodologist.setup.vsum.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Builds a bundled sample project once ({@code setup.vsum.warmup.enabled}).
 *
 * <p>While building the Docker image ({@code setup.vsum.warmup.exit}), this downloads every
 * dependency of the generated projects into the image's Maven repository and exits. When the
 * service starts, it verifies in the background that builds work with the configured versions and
 * repository; until it has succeeded, the {@code vsumBuildWarmup} health contributor, which is part
 * of the readiness group, reports the service as out of service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VsumBuildWarmup implements ApplicationRunner, HealthIndicator {

  private static final String SAMPLE_DIRECTORY = "/vsum-sample/";

  private final VsumProjectBuildService buildService;
  private final VsumBuildProperties properties;
  private final ConfigurableApplicationContext context;

  private volatile Health health = Health.up().withDetail("warmup", "disabled").build();

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.getWarmup().isEnabled()) {
      return;
    }
    if (properties.getWarmup().isExit()) {
      boolean succeeded = warmUp();
      System.exit(SpringApplication.exit(context, () -> succeeded ? 0 : 1));
    }
    health = Health.outOfService().withDetail("warmup", "running").build();
    Thread.ofVirtual().name("vsum-build-warmup").start(this::warmUp);
  }

  @Override
  public Health health() {
    return health;
  }

  /**
   * Builds the sample project and records the outcome.
   *
   * @return whether the build succeeded
   */
  boolean warmUp() {
    log.info("VSUM build warm-up started");
    Instant start = Instant.now();
    try {
      buildService.buildProjectJar(sampleRequest());
    } catch (RuntimeException e) {
      log.error(
          "VSUM build warm-up failed; the service reports itself as not ready: {}",
          e.getMessage(),
          e);
      health =
          Health.outOfService()
              .withDetail("warmup", "failed")
              .withDetail("error", String.valueOf(e.getMessage()))
              .build();
      return false;
    }
    Duration duration = Duration.between(start, Instant.now());
    log.info("VSUM build warm-up succeeded in {} s", duration.toSeconds());
    health =
        Health.up()
            .withDetail("warmup", "succeeded")
            .withDetail("durationSeconds", duration.toSeconds())
            .build();
    return true;
  }

  /**
   * The sample project: the two metamodels, reactions, and constraints of the Methodologist
   * Template, which together need every dependency a generated project can have.
   *
   * @return the build request of the sample project
   */
  static VsumBuildRequest sampleRequest() {
    return new VsumBuildRequest(
        List.of(sample("model.ecore"), sample("model2.ecore")),
        List.of(sample("templateReactions.reactions")),
        List.of(sample("constraints.ocl")),
        List.of());
  }

  private static UploadedFile sample(String name) {
    try (InputStream input = VsumBuildWarmup.class.getResourceAsStream(SAMPLE_DIRECTORY + name)) {
      if (input == null) {
        throw new IllegalStateException("Missing sample file " + SAMPLE_DIRECTORY + name);
      }
      return new UploadedFile(name, input.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
