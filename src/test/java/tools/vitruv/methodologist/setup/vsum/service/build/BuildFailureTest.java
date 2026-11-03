package tools.vitruv.methodologist.setup.vsum.service.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BuildFailureTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[ERROR] Failed to execute goal on project tools.vitruv.methodologisttemplate.vsum:"
            + " Could not resolve dependencies for project"
            + " tools.vitruv:tools.vitruv.methodologisttemplate.vsum"
            + ":jar:0.1.0-SNAPSHOT",
        "[ERROR] Failed to execute goal on project x: Could not transfer artifact"
            + " tools.vitruv:tools.vitruv.change.atomic:jar:4.0.1 from/to central"
            + " (https://repo.maven.apache.org/maven2): Connection reset",
        "[ERROR] Non-resolvable parent POM for tools.vitruv:tools.vitruv.methodologisttemplate:"
            + "0.1.0-SNAPSHOT: The following artifacts could not be resolved:"
            + " tools.vitruv:parent:pom:4.0.0",
        "[ERROR] Plugin org.eclipse.xtext:xtext-maven-plugin:2.43.0 or one of its dependencies"
            + " could not be resolved",
        "[ERROR] Failed to read artifact descriptor for org.eclipse.emf:org.eclipse.emf.ecore:jar:"
            + "2.43.0",
        "[ERROR] java.net.UnknownHostException: repo.maven.apache.org",
        "[ERROR] Could not acquire write lock for 'artifact:tools.vitruv:parent:4.0.0'",
        "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space",
        "java.io.IOException: No space left on device"
      })
  void environmentProblemsAreInfrastructureFailures(String line) {
    BuildFailure failure = BuildFailure.classify(List.of("[INFO] Scanning for projects...", line));

    assertEquals(BuildFailure.INFRASTRUCTURE, failure);
    assertTrue(failure.isInfrastructure());
  }

  @Test
  void dependenciesMissingFromAnOfflineRepositoryCanBeFetchedOnline() {
    BuildFailure failure =
        BuildFailure.classify(
            List.of(
                "[ERROR] Failed to execute goal on project x: Could not resolve dependencies for"
                    + " project x: Cannot access central (https://repo.maven.apache.org/maven2) in"
                    + " offline mode and the artifact tools.vitruv:tools.vitruv.change.atomic:jar:"
                    + "4.0.2 has not been downloaded from it before."));

    assertEquals(BuildFailure.MISSING_OFFLINE, failure);
    assertTrue(failure.isInfrastructure());
  }

  @Test
  void compilationFailuresOfTheVsumModuleAreInternal() {
    assertEquals(
        BuildFailure.INTERNAL,
        BuildFailure.classify(
            List.of(
                "[ERROR] /tmp/vsum-build-1/project/vsum/src/main/java/tools/vitruv/"
                    + "methodologisttemplate/vsum/VSUMExample.java:[12,8] cannot find symbol",
                "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:"
                    + "3.14.0:compile (default-compile) on project"
                    + " tools.vitruv.methodologisttemplate.vsum: Compilation failure")));
  }

  @Test
  void incompatibleLibraryVersionsAreInternal() {
    // recorded with tools.vitruv.change 4.0.0 next to tools.vitruv.framework 4.0.1
    assertEquals(
        BuildFailure.INTERNAL,
        BuildFailure.classify(
            List.of(
                "[ERROR] tools.vitruv.methodologisttemplate.vsum.VSUMExampleTest"
                    + ".virtualModelCanBeCreatedAndReloaded(Path) -- Time elapsed: 0.511 s <<<"
                    + " ERROR!",
                "java.lang.NoSuchMethodError: 'void tools.vitruv.change.propagation"
                    + ".ChangePropagationSpecificationRepository.<init>(java.lang.Iterable,"
                    + " java.util.Map)'",
                "\tat tools.vitruv.framework.vsum.VirtualModelBuilder.buildAndInitialize"
                    + "(VirtualModelBuilder.java:247)",
                "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:"
                    + "3.5.6:test (default-test) on project tools.vitruv.methodologisttemplate"
                    + ".vsum:")));
  }

  @Test
  void exceptionsOfTheSmokeTestAreInputFailures() {
    // e.g. a reaction that throws while the VSUM is created
    assertEquals(
        BuildFailure.INPUT,
        BuildFailure.classify(
            List.of(
                "[ERROR] VSUMExampleTest.virtualModelCanBeCreatedAndReloaded:16 » IllegalState",
                "java.lang.IllegalStateException: no correspondence for Root")));
  }

  @Test
  void brokenGeneratedPomsAreInternal() {
    assertEquals(
        BuildFailure.INTERNAL,
        BuildFailure.classify(
            List.of(
                "[ERROR] Non-parseable POM /tmp/vsum-build-1/project/vsum/pom.xml: end tag name"
                    + " </dependency> must match start tag name <version>")));
  }

  @Test
  void reactionsAndMetamodelErrorsAreInputFailures() {
    BuildFailure failure =
        BuildFailure.classify(
            List.of(
                "[ERROR] ERROR:mismatched input 'java' expecting RULE_STRING (file:/tmp/vsum-build-"
                    + "1/project/consistency/src/main/reactions/a.reactions line : 4 column : 8)",
                "[ERROR] Failed to execute goal org.eclipse.xtext:xtext-maven-plugin:2.43.0:"
                    + "generate (default) on project"
                    + " tools.vitruv.methodologisttemplate.consistency:"
                    + " Execution failed due to a severe validation error. -> [Help 1]"));

    assertEquals(BuildFailure.INPUT, failure);
    assertFalse(failure.isInfrastructure());
  }

  @Test
  void compilationFailuresOfTheGeneratedModelCodeAreInputFailures() {
    assertEquals(
        BuildFailure.INPUT,
        BuildFailure.classify(
            List.of(
                "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:"
                    + "3.14.0:compile (default-compile) on project"
                    + " tools.vitruv.methodologisttemplate.model: Compilation failure")));
  }

  @Test
  void warningsAboutRecoveredProblemsAreIgnored() {
    assertEquals(
        BuildFailure.INPUT,
        BuildFailure.classify(
            List.of(
                "[WARNING] Could not transfer metadata tools.vitruv.dsls:vitruvocl-language:"
                    + "0.1.0-SNAPSHOT/maven-metadata.xml from/to central-portal-snapshots:"
                    + " Connection reset",
                "[ERROR] ERROR:cad cannot be resolved. (file:/a.reactions line : 18 column :"
                    + " 31)")));
  }
}
