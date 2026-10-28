package tools.vitruv.methodologist.setup.vsum.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.fixture;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.text;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.vsum.service.build.MavenBuildRunner;
import tools.vitruv.methodologist.setup.vsum.service.constraints.ConstraintsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.MetamodelProcessor;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectGenerator;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;
import tools.vitruv.methodologist.setup.vsum.service.reactions.ReactionsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.views.NeoJoinViewDefinitionProcessor;

class VsumProjectBuildServiceTest {

  private static final VsumBuildRequest TEMPLATE_REQUEST =
      new VsumBuildRequest(
          List.of(fixture("model.ecore"), fixture("model2.ecore")),
          List.of(fixture("templateReactions.reactions")),
          List.of(fixture("constraints.ocl")),
          List.of(text("components.nj", "view stub")));

  private final VsumBuildProperties properties = new VsumBuildProperties();
  private final VsumProjectBuildService service =
      new VsumProjectBuildService(
          new MetamodelProcessor(),
          new ReactionsProcessor(),
          new ConstraintsProcessor(),
          new NeoJoinViewDefinitionProcessor(),
          new ProjectGenerator(properties),
          new MavenBuildRunner(properties),
          properties);

  @TempDir Path projectRoot;

  @Test
  void generatesAProjectFollowingTheMethodologistTemplate() throws IOException {
    service.generateProject(TEMPLATE_REQUEST, projectRoot);

    for (String file :
        List.of(
            "pom.xml",
            "model/pom.xml",
            "model/.project",
            "model/plugin.xml",
            "model/META-INF/MANIFEST.MF",
            "model/workflow/generate.mwe2",
            "model/src/main/ecore/model.ecore",
            "model/src/main/ecore/model.genmodel",
            "model/src/main/ecore/model2.genmodel",
            "consistency/pom.xml",
            "consistency/src/main/reactions/tools/vitruv/methodologisttemplate/consistency/templateReactions.reactions",
            "consistency/src/main/constraints/tools/vitruv/methodologisttemplate/consistency/constraints.ocl",
            "views/src/main/neojoin/components.nj",
            "vsum/pom.xml",
            "vsum/src/main/java/tools/vitruv/methodologisttemplate/vsum/VSUMExample.java",
            "vsum/src/test/java/tools/vitruv/methodologisttemplate/vsum/VSUMExampleTest.java")) {
      assertTrue(Files.isRegularFile(projectRoot.resolve(file)), file);
    }

    String rootPom = Files.readString(projectRoot.resolve("pom.xml"));
    assertTrue(rootPom.contains("<version>${vitruv.version}</version>"), rootPom);
    assertTrue(rootPom.contains("<vitruv.version>4.0.0</vitruv.version>"), rootPom);

    String workflow = Files.readString(projectRoot.resolve("model/workflow/generate.mwe2"));
    assertTrue(
        workflow.contains(
            "platform:/resource/tools.vitruv.methodologisttemplate.model/src/main/ecore/model2.genmodel"),
        workflow);

    String main =
        Files.readString(
            projectRoot.resolve(
                "vsum/src/main/java/tools/vitruv/methodologisttemplate/vsum/VSUMExample.java"));
    assertTrue(
        main.contains(
            "new mir.reactions.model2Model2.Model2Model2ChangePropagationSpecification()"),
        main);
    assertTrue(
        main.contains("tools.vitruv.methodologisttemplate.model.model2.Model2Package.eINSTANCE"),
        main);
    assertTrue(
        main.contains("\"tools/vitruv/methodologisttemplate/consistency/constraints.ocl\""), main);

    assertTrue(
        Files.readString(projectRoot.resolve("vsum/pom.xml")).contains("vitruvocl-language"));
  }

  @Test
  void leavesOutVitruvOclWithoutConstraints() throws IOException {
    VsumBuildRequest request =
        new VsumBuildRequest(List.of(fixture("model.ecore")), List.of(), List.of(), List.of());

    service.generateProject(request, projectRoot);

    assertFalse(
        Files.readString(projectRoot.resolve("vsum/pom.xml")).contains("vitruvocl-language"));
    String main =
        Files.readString(
            projectRoot.resolve(
                "vsum/src/main/java/tools/vitruv/methodologisttemplate/vsum/VSUMExample.java"));
    assertFalse(main.contains("import tools.vitruv.dsls.vitruvocl"), main);
  }

  /**
   * Runs the real Maven build. Needs Maven and access to the Vitruvius artifacts, so it only runs
   * with {@code -Dvsum.integration=true}.
   */
  @Test
  @EnabledIfSystemProperty(named = "vsum.integration", matches = "true")
  void buildsAnExecutableVsumJar() throws IOException {
    byte[] jar = service.buildProjectJar(TEMPLATE_REQUEST);

    Set<String> entries = new HashSet<>();
    try (JarInputStream input = new JarInputStream(new ByteArrayInputStream(jar))) {
      assertTrue(
          input
              .getManifest()
              .getMainAttributes()
              .getValue("Main-Class")
              .equals(ProjectLayout.VSUM_PACKAGE + "." + ProjectLayout.VSUM_MAIN_CLASS));
      for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
        entries.add(entry.getName());
      }
    }
    assertTrue(
        entries.contains(
            "mir/reactions/model2Model2/Model2Model2ChangePropagationSpecification.class"));
    assertTrue(
        entries.contains("tools/vitruv/methodologisttemplate/model/model/ModelPackage.class"));
    assertTrue(entries.contains("tools/vitruv/methodologisttemplate/consistency/constraints.ocl"));
  }
}
