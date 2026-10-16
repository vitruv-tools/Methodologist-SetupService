package tools.vitruv.methodologist.setup.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.config.MetamodelLocation;
import tools.vitruv.methodologist.setup.config.VitruvConfiguration;
import tools.vitruv.methodologist.setup.model.service.EcoreToGenmodelService;

class GenmodelProjectAlignerTest {

  @TempDir Path tempDir;

  private File writeEcore(String filename, String packageName) throws IOException {
    File ecore = tempDir.resolve(filename).toFile();
    Files.writeString(
        ecore.toPath(),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <ecore:EPackage xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"
            xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore" name="%s"
            nsURI="http://www.example.org/university" nsPrefix="university">
          <eClassifiers xsi:type="ecore:EClass" name="exam"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
        </ecore:EPackage>
        """
            .formatted(packageName));
    return ecore;
  }

  private File writeGenmodel(String filename, String attributes, String ecoreRef, String extra)
      throws IOException {
    File genmodel = tempDir.resolve(filename).toFile();
    Files.writeString(
        genmodel.toPath(),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <genmodel:GenModel xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"
            xmlns:genmodel="http://www.eclipse.org/emf/2002/GenModel" %s
            importerID="org.eclipse.emf.importer.ecore">
          <foreignModel>%s</foreignModel>
          <genPackages prefix="Newuniversity" %s ecorePackage="%s#/"/>
        </genmodel:GenModel>
        """
            .formatted(attributes, ecoreRef, extra, ecoreRef));
    return genmodel;
  }

  @Test
  void align_rewritesForeignProjectToModelProject() throws IOException {
    File ecore = writeEcore("model2.ecore", "model2");
    File genmodel =
        writeGenmodel(
            "model2.genmodel",
            "modelDirectory=\"/tools.vitruv.cli.model/target/generated-sources/ecore\""
                + " modelPluginID=\"tools.vitruv.framework.cli.model\" modelName=\"Model2\"",
            "model2.ecore",
            "basePackage=\"tools.vitruv.framework.cli.target\"");

    GenmodelProjectAligner.align(genmodel, ecore);

    String aligned = Files.readString(genmodel.toPath());
    assertThat(aligned)
        .contains("modelDirectory=\"" + EcoreToGenmodelService.MODEL_DIRECTORY + "\"")
        .contains("modelPluginID=\"" + EcoreToGenmodelService.MODEL_PLUGIN_ID + "\"")
        .contains("basePackage=\"tools.vitruv.framework.cli.target\"")
        .contains("model2.ecore#/")
        .doesNotContain("tools.vitruv.cli.model");
  }

  @Test
  void align_pointsGenmodelAtRenamedEcoreFile() throws IOException {
    File ecore = writeEcore("KITModel.ecore", "Newuniversity");
    File genmodel =
        writeGenmodel(
            "KITModel.genmodel",
            "modelDirectory=\"/Newuniversity/src-gen\" modelPluginID=\"Newuniversity\"",
            "Newuniversity.ecore",
            "");

    GenmodelProjectAligner.align(genmodel, ecore);

    String aligned = Files.readString(genmodel.toPath());
    assertThat(aligned)
        .contains("KITModel.ecore#/")
        .contains("<foreignModel>KITModel.ecore</foreignModel>")
        .doesNotContain("Newuniversity.ecore");

    VitruvConfiguration configuration = new VitruvConfiguration();
    configuration.setMetaModelLocations(ecore.getAbsolutePath() + "," + genmodel.getAbsolutePath());
    MetamodelLocation location = configuration.getMetaModelLocations().get(0);
    assertThat(location.packageClassName()).isEqualTo("Newuniversity.NewuniversityPackage");
  }

  @Test
  void align_rejectsGenmodelNotMatchingEcore() throws IOException {
    File ecore = writeEcore("KITModel.ecore", "Newuniversity");
    File genmodel = tempDir.resolve("other.genmodel").toFile();
    Files.writeString(
        genmodel.toPath(),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <genmodel:GenModel xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"
            xmlns:genmodel="http://www.eclipse.org/emf/2002/GenModel" modelPluginID="x">
          <genPackages prefix="Sub" ecorePackage="Other.ecore#//missing"/>
        </genmodel:GenModel>
        """);

    assertThatThrownBy(() -> GenmodelProjectAligner.align(genmodel, ecore))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("other.genmodel");
  }

  @Test
  void align_rejectsNonGenmodelFile() throws IOException {
    File ecore = writeEcore("KITModel.ecore", "Newuniversity");
    File notGenmodel = tempDir.resolve("broken.genmodel").toFile();
    Files.writeString(notGenmodel.toPath(), "not xml");

    assertThatThrownBy(() -> GenmodelProjectAligner.align(notGenmodel, ecore))
        .isInstanceOf(IOException.class);
  }
}
