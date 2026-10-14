package tools.vitruv.methodologist.setup.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.model.service.EcoreToGenmodelService;

@DisplayName("VitruvConfiguration Tests")
class VitruvConfigurationTest {

  @TempDir Path tempDir;

  private static String ecore(String packageName, String nsUri) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<ecore:EPackage xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\"\n"
        + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
        + "    xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\" name=\""
        + packageName
        + "\" nsURI=\""
        + nsUri
        + "\" nsPrefix=\""
        + packageName
        + "\">\n"
        + "  <eClassifiers xsi:type=\"ecore:EClass\" name=\"Root\"/>\n"
        + "</ecore:EPackage>\n";
  }

  private Path write(String fileName, String content) throws IOException {
    Path file = tempDir.resolve(fileName);
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  private Path writeGenerated(String ecoreFileName, String ecoreContent) throws IOException {
    byte[] genmodel =
        new EcoreToGenmodelService()
            .generateGenmodelFromEcore(
                ecoreContent.getBytes(StandardCharsets.UTF_8), ecoreFileName);
    Path file = tempDir.resolve(ecoreFileName.replace(".ecore", ".genmodel"));
    Files.write(file, genmodel);
    return file;
  }

  private static String pair(Path ecore, Path genmodel) {
    return ecore.toAbsolutePath() + "," + genmodel.toAbsolutePath();
  }

  @Test
  @DisplayName("Should use the nsURI and generated package class of a template genmodel")
  void testTemplateGenmodel() throws IOException {
    Path ecore = write("model2.ecore", ecore("model2", "http://vitruv.tools/template/model2"));
    Path genmodel =
        write(
            "model2.genmodel",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<genmodel:GenModel xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\"\n"
                + "    xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\"\n"
                + "    xmlns:genmodel=\"http://www.eclipse.org/emf/2002/GenModel\"\n"
                + "    modelDirectory=\"/tools.vitruv.template.model"
                + "/target/generated-sources/ecore\"\n"
                + "    modelPluginID=\"tools.vitruv.template.model\" modelName=\"Model2\">\n"
                + "  <foreignModel>model2.ecore</foreignModel>\n"
                + "  <genPackages prefix=\"Model2\" basePackage=\"tools.vitruv.template.model\""
                + " ecorePackage=\"model2.ecore#/\">\n"
                + "    <genClasses ecoreClass=\"model2.ecore#//Root\"/>\n"
                + "  </genPackages>\n"
                + "</genmodel:GenModel>\n");

    VitruvConfiguration configuration = new VitruvConfiguration();
    configuration.setMetaModelLocations(pair(ecore, genmodel));

    MetamodelLocation location = configuration.getMetaModelLocations().get(0);
    assertEquals("http://vitruv.tools/template/model2", location.nsUri());
    assertEquals("tools.vitruv.template.model.model2.Model2Package", location.packageClassName());
  }

  @Test
  @DisplayName("Should derive the change propagation specifications from the reaction files")
  void testChangePropagationSpecifications() throws IOException {
    Path first =
        write(
            "first.reactions",
            "import \"http://example.com/a\" as a\n"
                + "// reactions: commentedOut\n"
                + "reactions: model2Model2\n"
                + "in reaction to changes in a\n"
                + "execute actions in a\n");
    Path second =
        write(
            "second.reactions",
            "reactions: PersonsToFamilies\nin reaction to changes in a\nexecute actions in a\n");

    VitruvConfiguration configuration = new VitruvConfiguration();
    configuration.setReactionLocations(List.of(first, second));

    assertEquals(
        List.of(
            "mir.reactions.model2Model2.Model2Model2ChangePropagationSpecification",
            "mir.reactions.personsToFamilies.PersonsToFamiliesChangePropagationSpecification"),
        configuration.getChangePropagationSpecifications());
  }

  @Test
  @DisplayName("Should register each generated genmodel under its own nsURI and package class")
  void testGeneratedGenmodels() throws IOException {
    String codexEcore = ecore("codexdemo", "http://vitruv.tools/codexdemo");
    Path codex = write("Codex Demo MetaModel.ecore", codexEcore);
    Path codexGenmodel = writeGenerated("Codex Demo MetaModel.ecore", codexEcore);
    String libraryEcore = ecore("library", "http://example.com/library");
    Path library = write("library.ecore", libraryEcore);
    Path libraryGenmodel = writeGenerated("library.ecore", libraryEcore);

    VitruvConfiguration configuration = new VitruvConfiguration();
    configuration.setMetaModelLocations(
        pair(codex, codexGenmodel) + ";" + pair(library, libraryGenmodel));

    List<MetamodelLocation> locations = configuration.getMetaModelLocations();
    assertEquals(2, locations.size());
    assertEquals("http://vitruv.tools/codexdemo", locations.get(0).nsUri());
    assertEquals(
        "tools.vitruv.methodologisttemplate.model.codexdemo.CodexdemoPackage",
        locations.get(0).packageClassName());
    assertEquals("http://example.com/library", locations.get(1).nsUri());
    assertEquals(
        "tools.vitruv.methodologisttemplate.model.library.LibraryPackage",
        locations.get(1).packageClassName());
  }
}
