package tools.vitruv.methodologist.setup.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/** The VitruvConfiguration class is used to store the configuration of the Vitruv CLI. */
@Slf4j
public class VitruvConfiguration {

  /** Matches the {@code reactions: <name>} declaration of a reactions segment. */
  private static final Pattern REACTIONS_SEGMENT =
      Pattern.compile("^\\s*reactions:\\s*([A-Za-z_]\\w*)", Pattern.MULTILINE);

  /** -- GETTER -- Returns the model names. */
  @Getter private final List<String> modelNames = new ArrayList<>();

  private final List<MetamodelLocation> metamodelLocations = new ArrayList<>();

  /** -- GETTER -- Returns the reaction file locations used by the CLI. */
  @Getter private final List<Path> reactionLocations = new ArrayList<>();

  /**
   * -- SETTER -- Sets the local path of the configuration.
   *
   * <p>-- GETTER -- Returns the local path of the configuration.
   */
  @Getter @Setter private Path localPath;

  /** -- GETTER -- Returns the package name. */
  @Getter private String packageName;

  /**
   * -- SETTER -- Sets the workflow of the configuration.
   *
   * <p>-- GETTER -- Returns the workflow of the configuration.
   */
  @Getter @Setter private File workflow;

  /**
   * Removes the last segment of a string.
   *
   * @param input The input string.
   * @return The input string without the last segment.
   */
  public static String removeLastSegment(String input) {
    int lastDotIndex = input.lastIndexOf('.');
    if (lastDotIndex == -1) {
      return input;
    }
    return input.substring(0, lastDotIndex);
  }

  /**
   * Adds a metamodel location to the configuration.
   *
   * @param metamodelLocation The metamodel location to add.
   */
  public void addMetamodelLocations(MetamodelLocation metamodelLocation) {
    this.metamodelLocations.add(metamodelLocation);
  }

  /**
   * Sets the reaction file locations used by the CLI.
   *
   * @param reactionLocations list of paths to reaction files.
   */
  public void setReactionLocations(List<Path> reactionLocations) {
    this.reactionLocations.clear();
    if (reactionLocations != null) {
      this.reactionLocations.addAll(reactionLocations);
    }
  }

  /**
   * Returns the qualified names of the change propagation specifications the reactions compiler
   * generates for the reaction files: a file declaring {@code reactions: model2Model2} yields
   * {@code mir.reactions.model2Model2.Model2Model2ChangePropagationSpecification}.
   *
   * @return the change propagation specification class names, in file order
   * @throws IOException if a reaction file cannot be read
   */
  public List<String> getChangePropagationSpecifications() throws IOException {
    List<String> classNames = new ArrayList<>();
    for (Path reactionFile : reactionLocations) {
      Matcher matcher = REACTIONS_SEGMENT.matcher(Files.readString(reactionFile));
      while (matcher.find()) {
        classNames.add(changePropagationSpecification(matcher.group(1)));
      }
    }
    return classNames;
  }

  /**
   * Returns the change propagation specification class name for a reactions segment, following the
   * naming of the Vitruv reactions code generator.
   *
   * @param segmentName the name declared with {@code reactions:}
   * @return the qualified class name
   */
  static String changePropagationSpecification(String segmentName) {
    String lower = Character.toLowerCase(segmentName.charAt(0)) + segmentName.substring(1);
    String upper = Character.toUpperCase(segmentName.charAt(0)) + segmentName.substring(1);
    return "mir.reactions." + lower + "." + upper + "ChangePropagationSpecification";
  }

  /**
   * Returns the metamodel locations.
   *
   * @return The metamodel locations.
   */
  public List<MetamodelLocation> getMetaModelLocations() {
    return this.metamodelLocations;
  }

  /**
   * Sets the metamodel locations using a semicolon-separated list of {@code ecore,genmodel} pairs.
   *
   * @param paths The metamodel argument string.
   */
  public void setMetaModelLocations(String paths) {
    Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
    reg.getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
    reg.getExtensionToFactoryMap().put("genmodel", new XMIResourceFactoryImpl());

    GenModelPackage.eINSTANCE.eClass();

    for (String modelPaths : paths.split(";")) {
      String metamodelPath = modelPaths.split(",")[0];
      String genmodelPath = modelPaths.split(",")[1];

      File metamodel = new File(metamodelPath);
      File genmodel = new File(genmodelPath);

      String localModelDirectory = "";
      String packageClassName = "";

      ResourceSet resourceSet = new ResourceSetImpl();
      URI uri = URI.createFileURI(metamodel.getAbsolutePath().trim());
      Resource resource = resourceSet.getResource(uri, true);
      String nsUri = "";
      if (!resource.getContents().isEmpty()
          && resource.getContents().get(0) instanceof EPackage ePackage) {
        URI genmodelURI = URI.createFileURI(genmodel.getAbsolutePath());
        nsUri = ePackage.getNsURI();
        Resource genmodelResource = resourceSet.getResource(genmodelURI, true);
        modelNames.add(ePackage.getName());
        if (!genmodelResource.getContents().isEmpty()
            && genmodelResource.getContents().get(0) instanceof GenModel genModel) {
          String packageString = removeLastSegment(genModel.getModelPluginID());
          log.info("--------------------->>>>  " + packageString);
          this.setPackageName(packageString);
          localModelDirectory = genModel.getModelDirectory();
          packageClassName = packageInterfaceName(genModel, ePackage);
        }
      }

      this.addMetamodelLocations(
          new MetamodelLocation(metamodel, genmodel, nsUri, localModelDirectory, packageClassName));
    }
  }

  /**
   * Returns the qualified name of the package interface EMF generates for the given package, e.g.
   * {@code tools.vitruv.methodologisttemplate.model.model2.Model2Package}. It is registered in the
   * model project's {@code plugin.xml} so the reactions can resolve the metamodel by its nsURI.
   *
   * @param genModel the genmodel of the metamodel
   * @param ePackage the root package of the metamodel
   * @return the qualified package interface name, or an empty string if the genmodel has no package
   */
  static String packageInterfaceName(GenModel genModel, EPackage ePackage) {
    GenPackage genPackage = genModel.findGenPackage(ePackage);
    if (genPackage == null && !genModel.getGenPackages().isEmpty()) {
      genPackage = genModel.getGenPackages().get(0);
    }
    return genPackage == null ? "" : genPackage.getQualifiedPackageInterfaceName();
  }

  /**
   * Sets the package name.
   *
   * @param packageName The package name.
   */
  public void setPackageName(String packageName) {
    this.packageName = packageName.replace("\\s", "");
  }
}
