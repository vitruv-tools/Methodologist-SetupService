package tools.vitruv.methodologist.setup.emf;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.emf.ecore.EPackage;
import tools.vitruv.methodologist.setup.messages.ErrorMessages;
import tools.vitruv.methodologist.setup.messages.InfoMessages;

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
        logger.warn(ErrorMessages.EMF_CLASSES_DIR_NOT_FOUND, classesPath);
        return packages;
      }

      try (Stream<Path> paths = Files.walk(classesDir)) {
        paths
            .filter(Files::isRegularFile)
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
                    logger.debug(
                        ErrorMessages.EMF_CLASSES_LOAD_PACKAGE_ERROR, classPath, e.getMessage());
                  }
                });
      }

    } catch (Exception e) {
      logger.error(ErrorMessages.EMF_CLASSES_INIT_ERROR, classesPath, e);
    }

    logger.info(InfoMessages.EMF_CLASSES_INIT_SUCCESS, packages.size());
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
      java.io.InputStream ecoreStream, Map<String, EPackage> packages) {}

  /**
   * Loads a metamodel package class and registers it.
   *
   * @param className fully qualified name of a FactoryImpl class
   * @param packages map to collect registered packages
   */
  private static void loadMetamodelPackage(String className, Map<String, EPackage> packages) {
    try {
      Class<?> factoryClass = Class.forName(className);

      Field instanceField = factoryClass.getField("eINSTANCE");
      Object factoryInstance = instanceField.get(null);

      String packageClassName =
          className.substring(0, className.lastIndexOf("FactoryImpl")) + "Package";
      Class<?> packageClass = Class.forName(packageClassName);
      Field instance = packageClass.getField("eINSTANCE");
      EPackage ePackage = (EPackage) instance.get(null);

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
