package tools.vitruv.methodologist.setup.vsum.service.metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.fixture;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

class MetamodelProcessorTest {

  private final MetamodelProcessor processor = new MetamodelProcessor();

  @TempDir Path projectRoot;

  @Test
  void generatesGenmodelsMatchingTheProjectLayout() throws IOException {
    List<GeneratedMetamodel> result =
        processor.process(List.of(fixture("model.ecore"), fixture("model2.ecore")), projectRoot);

    assertEquals(2, result.size());
    GeneratedMetamodel model = result.get(0);
    assertEquals("model", model.name());
    assertEquals("http://vitruv.tools/methodologisttemplate/model", model.nsUri());
    assertEquals("model.ecore", model.ecoreFileName());
    assertEquals("model.genmodel", model.genmodelFileName());
    assertEquals(
        List.of(
            new GeneratedMetamodel.GeneratedPackage(
                "http://vitruv.tools/methodologisttemplate/model",
                "tools.vitruv.methodologisttemplate.model.model.ModelPackage")),
        model.packages());

    GenModel genModel =
        loadGenModel(ProjectLayout.ecoreDirectory(projectRoot).resolve("model.genmodel"));
    assertEquals(ProjectLayout.MODEL_ARTIFACT_ID, genModel.getModelPluginID());
    assertEquals(
        "/tools.vitruv.methodologisttemplate.model/target/generated-sources/ecore",
        genModel.getModelDirectory());
    assertEquals(
        ProjectLayout.MODEL_BASE_PACKAGE, genModel.getGenPackages().get(0).getBasePackage());
    assertTrue(genModel.isOperationReflection());
    assertEquals(List.of("model.ecore"), genModel.getForeignModel());
  }

  @Test
  void resolvesWorkspaceSpecificReferencesBetweenUploadedMetamodels() throws IOException {
    List<GeneratedMetamodel> result =
        processor.process(List.of(fixture("base.ecore"), fixture("extension.ecore")), projectRoot);

    assertEquals(2, result.size());
    String extension =
        Files.readString(ProjectLayout.ecoreDirectory(projectRoot).resolve("extension.ecore"));
    assertFalse(extension.contains("platform:/resource"), extension);
    assertTrue(extension.contains("base.ecore#//Named"), extension);

    GenModel genModel =
        loadGenModel(ProjectLayout.ecoreDirectory(projectRoot).resolve("extension.genmodel"));
    assertEquals(1, genModel.getUsedGenPackages().size());
    assertEquals("base", genModel.getUsedGenPackages().get(0).getEcorePackage().getName());
  }

  @Test
  void namesFilesAfterThePackageRegardlessOfTheUploadedName() throws IOException {
    UploadedFile model = new UploadedFile("My_Model__v2_.ecore", fixture("model.ecore").content());
    List<GeneratedMetamodel> result = processor.process(List.of(model), projectRoot);

    assertEquals("model.ecore", result.get(0).ecoreFileName());
    assertTrue(
        Files.isRegularFile(ProjectLayout.ecoreDirectory(projectRoot).resolve("model.genmodel")));
  }

  @Test
  void rejectsReferencesToMetamodelsThatWereNotUploaded() {
    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> processor.process(List.of(fixture("extension.ecore")), projectRoot));

    assertEquals(VsumErrorCodes.METAMODEL_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("extension.ecore"), exception.getMessage());
    assertTrue(exception.getMessage().contains("base.ecore"), exception.getMessage());
  }

  @Test
  void rejectsFilesThatAreNotEcore() {
    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> processor.process(List.of(text("broken.ecore", "<ecore:EPackage")), projectRoot));

    assertEquals(VsumErrorCodes.METAMODEL_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("broken.ecore"), exception.getMessage());
  }

  @Test
  void rejectsDuplicateNsUris() {
    UploadedFile copy =
        text(
            "copy.ecore", fixture("model.ecore").text().replace("name=\"model\"", "name=\"copy\""));

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> processor.process(List.of(fixture("model.ecore"), copy), projectRoot));

    assertTrue(exception.getMessage().contains("nsURI"), exception.getMessage());
  }

  @Test
  void reportsEcoreValidationErrors() {
    UploadedFile invalid =
        text(
            "invalid.ecore",
            fixture("model.ecore")
                .text()
                .replace(
                    "name=\"Protocol\">",
                    "name=\"Protocol\" abstract=\"true\" interface=\"true\" eSuperTypes=\"#//Protocol\">"));

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> processor.process(List.of(invalid), projectRoot));

    assertEquals(VsumErrorCodes.METAMODEL_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("invalid.ecore: "), exception.getMessage());
    assertFalse(exception.getMessage().contains("not a valid Ecore file"), exception.getMessage());
  }

  private static GenModel loadGenModel(Path genmodel) {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("genmodel", new XMIResourceFactoryImpl());
    return (GenModel)
        resourceSet.getResource(URI.createFileURI(genmodel.toString()), true).getContents().get(0);
  }
}
