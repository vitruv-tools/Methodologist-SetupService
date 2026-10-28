package tools.vitruv.methodologist.setup.vsum.service.project;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.vsum.service.VsumBuildProperties;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;

/**
 * Renders the Maven project around the processed metamodels, reactions, and constraints, following
 * the layout of the Methodologist Template.
 */
@Service
@RequiredArgsConstructor
public class ProjectGenerator {

  private static final String TEMPLATE_DIRECTORY = "/templates/vsum";

  private final VsumBuildProperties properties;
  private final Configuration freemarker = createFreemarkerConfiguration();

  /**
   * Writes all build files and sources of the generated project.
   *
   * @param projectRoot root of the generated project
   * @param metamodels processed metamodels
   * @param changePropagationSpecifications fully qualified names of the reactions' generated change
   *     propagation specifications
   * @param constraintResources classpath resources of the constraint files
   * @throws IOException when rendering or writing a file fails
   */
  public void generate(
      Path projectRoot,
      List<GeneratedMetamodel> metamodels,
      List<String> changePropagationSpecifications,
      List<String> constraintResources)
      throws IOException {
    Map<String, Object> model =
        createModel(metamodels, changePropagationSpecifications, constraintResources);

    Path modelModule = ProjectLayout.modelModule(projectRoot);
    Path consistencyModule = ProjectLayout.consistencyModule(projectRoot);
    Path vsumModule = ProjectLayout.vsumModule(projectRoot);
    String vsumSources = ProjectLayout.VSUM_PACKAGE.replace('.', '/') + "/";

    render("rootPom.ftl", model, projectRoot.resolve("pom.xml"));
    render("modelPom.ftl", model, modelModule.resolve("pom.xml"));
    render("modelProject.ftl", model, modelModule.resolve(".project"));
    render("modelManifest.ftl", model, modelModule.resolve("META-INF/MANIFEST.MF"));
    render("modelPluginProperties.ftl", model, modelModule.resolve("plugin.properties"));
    render("modelPluginXml.ftl", model, modelModule.resolve("plugin.xml"));
    render("modelWorkflow.ftl", model, modelModule.resolve("workflow/generate.mwe2"));
    render("consistencyPom.ftl", model, consistencyModule.resolve("pom.xml"));
    render("vsumPom.ftl", model, vsumModule.resolve("pom.xml"));
    render(
        "vsumMain.ftl",
        model,
        vsumModule.resolve(
            "src/main/java/" + vsumSources + ProjectLayout.VSUM_MAIN_CLASS + ".java"));
    render(
        "vsumTest.ftl",
        model,
        vsumModule.resolve(
            "src/test/java/" + vsumSources + ProjectLayout.VSUM_MAIN_CLASS + "Test.java"));

    Files.createDirectories(ProjectLayout.reactionsDirectory(projectRoot));
    Files.createDirectories(ProjectLayout.constraintsDirectory(projectRoot));
  }

  private Map<String, Object> createModel(
      List<GeneratedMetamodel> metamodels,
      List<String> changePropagationSpecifications,
      List<String> constraintResources) {
    Map<String, Object> model = new HashMap<>();
    model.put("groupId", ProjectLayout.GROUP_ID);
    model.put("rootArtifactId", ProjectLayout.ROOT_ARTIFACT_ID);
    model.put("version", ProjectLayout.VERSION);
    model.put("modelArtifactId", ProjectLayout.MODEL_ARTIFACT_ID);
    model.put("consistencyArtifactId", ProjectLayout.CONSISTENCY_ARTIFACT_ID);
    model.put("vsumArtifactId", ProjectLayout.VSUM_ARTIFACT_ID);
    model.put("vsumPackage", ProjectLayout.VSUM_PACKAGE);
    model.put("vsumMainClass", ProjectLayout.VSUM_MAIN_CLASS);
    model.put("ecoreDirectory", ProjectLayout.ECORE_DIRECTORY);
    model.put("generatedSources", ProjectLayout.GENERATED_ECORE_SOURCES);

    model.put("parentVersion", properties.getParentVersion());
    model.put("vitruvVersion", properties.getVitruvVersion());
    model.put("vitruvoclVersion", properties.getVitruvoclVersion());
    model.put("emfCommonVersion", properties.getEmfCommonVersion());
    model.put("emfEcoreVersion", properties.getEmfEcoreVersion());
    model.put("junitVersion", properties.getJunitVersion());
    model.put("assemblyPluginVersion", properties.getAssemblyPluginVersion());

    model.put(
        "metamodels",
        metamodels.stream()
            .map(
                metamodel ->
                    Map.of(
                        "name", metamodel.name(),
                        "genmodelFileName", metamodel.genmodelFileName(),
                        "packages",
                            metamodel.packages().stream()
                                .map(
                                    pkg ->
                                        Map.of(
                                            "nsUri", pkg.nsUri(),
                                            "packageInterface", pkg.packageInterface()))
                                .toList()))
            .toList());

    List<String> packageInterfaces =
        metamodels.stream()
            .flatMap(metamodel -> metamodel.packages().stream())
            .map(GeneratedMetamodel.GeneratedPackage::packageInterface)
            .toList();
    model.put("packageInterfaces", packageInterfaces);

    Set<String> exportedPackages = new LinkedHashSet<>();
    for (String packageInterface : packageInterfaces) {
      String javaPackage = packageInterface.substring(0, packageInterface.lastIndexOf('.'));
      exportedPackages.add(javaPackage);
      exportedPackages.add(javaPackage + ".impl");
      exportedPackages.add(javaPackage + ".util");
    }
    // manifest continuation lines start with a single space
    model.put("exportPackageHeader", String.join(",\n ", exportedPackages));

    model.put("changePropagationSpecifications", changePropagationSpecifications);
    model.put("constraintResources", constraintResources);
    return model;
  }

  private void render(String templateName, Map<String, Object> model, Path target)
      throws IOException {
    Files.createDirectories(target.getParent());
    Template template = freemarker.getTemplate(templateName);
    try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
      template.process(model, writer);
    } catch (TemplateException e) {
      throw new IOException("Failed to render template " + templateName, e);
    }
  }

  private static Configuration createFreemarkerConfiguration() {
    Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
    configuration.setClassForTemplateLoading(ProjectGenerator.class, TEMPLATE_DIRECTORY);
    configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
    // [=name] interpolation keeps Maven's ${...} expressions free of escaping
    configuration.setInterpolationSyntax(Configuration.SQUARE_BRACKET_INTERPOLATION_SYNTAX);
    configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    configuration.setLogTemplateExceptions(false);
    configuration.setWrapUncheckedExceptions(true);
    configuration.setFallbackOnNullLoopVariable(false);
    return configuration;
  }
}
