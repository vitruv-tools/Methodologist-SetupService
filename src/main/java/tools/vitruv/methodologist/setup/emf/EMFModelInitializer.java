package tools.vitruv.methodologist.setup.emf;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.emf.ecore.EPackage;

/**
 * Utility class for initializing EMF packages before Xtext validation.
 *
 * <p>This class loads metamodel classes from a generated model JAR and registers them with EMF's
 * package registry to ensure Xtext can resolve nsURIs during validation.
 */
public class EMFModelInitializer {
  private static final Logger logger = LogManager.getLogger(EMFModelInitializer.class);

  /**
   * Initializes EMF packages from a model JAR file.
   *
   * @param modelJarPath path to the generated model JAR
   * @return map of nsURI to EPackage for all registered packages
   */
  public static Map<String, EPackage> initializeFromJar(String modelJarPath) {
    Map<String, EPackage> packages = new HashMap<>();

    File jarFile = new File(modelJarPath);
    if (!jarFile.exists()) {
      logger.warn("Model JAR not found: {}", modelJarPath);
      return packages;
    }

    try (JarFile jar = new JarFile(jarFile)) {
      Collection<JarEntry> entries = jar.stream().toList();

      // Look for all ecore files
      entries.stream()
          .filter(entry -> entry.getName().endsWith(".ecore"))
          .forEach(
              entry -> {
                try {
                  loadEcorePackage(jar.getInputStream(entry), packages);
                } catch (Exception e) {
                  logger.warn("Failed to load ecore from {}: {}", entry.getName(), e.getMessage());
                }
              });

      // Look for FactoryImpl classes that indicate metamodel packages
      entries.stream()
          .filter(entry -> entry.getName().endsWith("FactoryImpl.class"))
          .map(entry -> entry.getName().replace('/', '.').replace(".class", ""))
          .forEach(
              className -> {
                try {
                  loadMetamodelPackage(className, packages);
                } catch (Exception e) {
                  logger.debug("Could not load package from {}: {}", className, e.getMessage());
                }
              });

    } catch (Exception e) {
      logger.error("Error initializing EMF packages from JAR: {}", modelJarPath, e);
    }

    logger.info("Initialized {} EMF packages from model JAR", packages.size());
    return packages;
  }

  /**
   * Initializes EMF packages from a classes directory.
   *
   * @param classesPath path to the generated classes directory
   * @param classLoader classloader to use for loading classes
   * @return map of nsURI to EPackage for all registered packages
   */
  public static Map<String, EPackage> initializeFromClasses(
      String classesPath, ClassLoader classLoader) {
    Map<String, EPackage> packages = new HashMap<>();

    try {
      Path classesDir = Path.of(classesPath);
      if (!Files.exists(classesDir)) {
        logger.warn("Classes directory not found: {}", classesPath);
        return packages;
      }

      // Find all FactoryImpl classes
      Files.walk(classesDir)
          .filter(p -> Files.isRegularFile(p))
          .filter(p -> p.toString().endsWith("FactoryImpl.class"))
          .forEach(
              classPath -> {
                try {
                  String className =
                      classesDir
                          .relativize(classPath)
                          .toString()
                          .replace(File.separator, ".")
                          .replace(".class", "");
                  loadMetamodelPackage(className, packages);
                } catch (Exception e) {
                  logger.debug("Could not load package from {}: {}", classPath, e.getMessage());
                }
              });

    } catch (Exception e) {
      logger.error("Error initializing EMF packages from classes directory: {}", classesPath, e);
    }

    logger.info("Initialized {} EMF packages from classes directory", packages.size());
    return packages;
  }

  /**
   * Registers an EPackage with EMF's package registry.
   *
   * @param nsUri the nsURI for the package
   * @param ePackage the EPackage instance
   */
  public static void registerPackage(String nsUri, EPackage ePackage) {
    if (nsUri != null && ePackage != null) {
      EPackage.Registry.INSTANCE.put(nsUri, ePackage);
      logger.debug("Registered EPackage: {} -> {}", nsUri, ePackage.getName());
    }
  }

  /**
   * Loads an ecore model and registers its packages.
   *
   * @param ecoreStream input stream containing an EMF ecore file
   * @param packages map to collect registered packages
   */
  private static void loadEcorePackage(
      java.io.InputStream ecoreStream, Map<String, EPackage> packages) {
    // This would load and parse the ecore file
    // For now, this is a placeholder that could be extended with actual implementation
  }

  /**
   * Loads a metamodel package class and registers it.
   *
   * @param className fully qualified name of a FactoryImpl class
   * @param packages map to collect registered packages
   */
  private static void loadMetamodelPackage(String className, Map<String, EPackage> packages) {
    try {
      // Load the FactoryImpl class
      Class<?> factoryClass = Class.forName(className);

      // Get the eINSTANCE field
      java.lang.reflect.Field instanceField = factoryClass.getField("eINSTANCE");
      Object factoryInstance = instanceField.get(null);

      // Get the eClass field from the package
      String packageClassName =
          className.substring(0, className.lastIndexOf("FactoryImpl")) + "Package";
      Class<?> packageClass = Class.forName(packageClassName);
      java.lang.reflect.Field eINSTANCE = packageClass.getField("eINSTANCE");
      EPackage ePackage = (EPackage) eINSTANCE.get(null);

      if (ePackage != null) {
        String nsUri = ePackage.getNsURI();
        packages.put(nsUri, ePackage);
        registerPackage(nsUri, ePackage);
      }
    } catch (ClassNotFoundException e) {
      logger.debug("Metamodel class not found on this classpath: {}", className);
    } catch (Exception e) {
      logger.trace("Error loading metamodel package {}: {}", className, e.getMessage());
    }
  }
}
