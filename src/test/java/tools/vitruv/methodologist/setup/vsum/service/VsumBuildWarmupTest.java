package tools.vitruv.methodologist.setup.vsum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;

class VsumBuildWarmupTest {

  private final VsumProjectBuildService buildService = mock(VsumProjectBuildService.class);
  private final VsumBuildProperties properties = new VsumBuildProperties();
  private final VsumBuildWarmup warmup =
      new VsumBuildWarmup(buildService, properties, mock(ConfigurableApplicationContext.class));

  @Test
  void theSampleNeedsEveryKindOfInput() {
    VsumBuildRequest sample = VsumBuildWarmup.sampleRequest();

    assertEquals(
        List.of("model.ecore", "model2.ecore"),
        sample.metamodels().stream().map(UploadedFile::fileName).toList());
    assertEquals(1, sample.reactions().size());
    assertEquals(1, sample.constraints().size(), "constraints pull in VitruvOCL");
    assertTrue(sample.metamodels().stream().allMatch(file -> file.content().length > 0));
  }

  @Test
  void isUpAndDoesNothingWhenDisabled() {
    warmup.run(new DefaultApplicationArguments());

    assertEquals(Status.UP, warmup.health().getStatus());
    verifyNoInteractions(buildService);
  }

  @Test
  void isOutOfServiceUntilTheBackgroundBuildSucceeded() {
    properties.getWarmup().setEnabled(true);
    when(buildService.buildProjectJar(any()))
        .thenAnswer(
            invocation -> {
              assertEquals(Status.OUT_OF_SERVICE, warmup.health().getStatus());
              return new byte[] {1};
            });

    warmup.run(new DefaultApplicationArguments());

    verify(buildService, timeout(5000)).buildProjectJar(any());
    awaitStatus(Status.UP);
    assertEquals("succeeded", warmup.health().getDetails().get("warmup"));
  }

  @Test
  void staysOutOfServiceWithTheErrorWhenTheBuildFails() {
    when(buildService.buildProjectJar(any()))
        .thenThrow(
            new MethodologistSetupException(
                "VSUM_BUILD_UNAVAILABLE",
                "a dependency is missing",
                HttpStatus.SERVICE_UNAVAILABLE,
                null));

    assertFalse(warmup.warmUp());

    assertEquals(Status.OUT_OF_SERVICE, warmup.health().getStatus());
    assertEquals("a dependency is missing", warmup.health().getDetails().get("error"));
  }

  private void awaitStatus(Status expected) {
    long deadline = System.currentTimeMillis() + 5000;
    while (!expected.equals(warmup.health().getStatus()) && System.currentTimeMillis() < deadline) {
      Thread.onSpinWait();
    }
    assertEquals(expected, warmup.health().getStatus());
  }
}
