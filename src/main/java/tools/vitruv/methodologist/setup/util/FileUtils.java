package tools.vitruv.methodologist.setup.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.methodologist.setup.config.CustomClassLoader;

/** The FileUtils class provides utility methods for file operations. */
@Slf4j
public final class FileUtils {

  /**
   * The CLASS_LOADER is used to load classes from JAR files at runtime. It is used to load the
   * classes of the virtual model builder.
   */
  public static final CustomClassLoader CLASS_LOADER =
      new CustomClassLoader(new URL[] {}, ClassLoader.getSystemClassLoader());

  private FileUtils() {}

  /**
   * Copy a file to a new location.
   *
   * @param filePath The path of the file that should be copied.
   * @param folderPath The path of the folder to which the file should be copied.
   * @param relativeSubfolder The relative subfolder in which the file should be copied.
   * @return The target file.
   */
  public static File copyFile(String filePath, Path folderPath, String relativeSubfolder) {
    File source;
    File target;
    if (new File(filePath).isAbsolute()) {
      source = Path.of(filePath).toFile();
    } else {
      source = Path.of(new File("").getAbsolutePath().trim() + "/" + filePath.trim()).toFile();
    }
    if (folderPath.isAbsolute()) {
      target =
          Path.of(folderPath.toString().trim() + "/" + relativeSubfolder + source.getName().trim())
              .toFile();
    } else {

      target =
          Path.of(
                  new File("").getAbsolutePath().trim()
                      + "/"
                      + folderPath.toString().trim()
                      + "/"
                      + relativeSubfolder
                      + source.getName().trim())
              .toFile();
    }
    // Files.copy throws a misleading Exception if the target File and/or the
    // folders of the target file are not existing.
    log.info("Copying file " + source.getAbsolutePath() + " to  " + target.getAbsolutePath());
    target.getParentFile().mkdirs();
    try {
      target.createNewFile();
      Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return target;
  }

  /**
   * Create a new file in the given path.
   *
   * @param filePath The path of the file that should be created.
   */
  public static void createFile(String filePath) {
    File file = new File(filePath);
    try {
      // Ensure the directory exists
      File parentDir = file.getParentFile();
      if (parentDir != null && !parentDir.exists()) {
        parentDir.mkdirs();
      }
      // Create the file
      if (file.createNewFile()) {
        log.info("File created: " + file.getAbsolutePath());
      } else {
        log.info("File already exists: " + file.getAbsolutePath());
      }
    } catch (IOException e) {
      log.error("An error occurred while creating the file: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Create a new folder in the given path.
   *
   * @param path The path of the folder that should be created.
   * @param folder The name of the folder that should be created.
   * @return The created folder.
   */
  public static Path createNewFolder(Path path, String folder) {
    Path folderPath = path.resolve(folder);
    File file = folderPath.toFile();
    if (file.mkdirs()) {
      log.info("Directory created: " + file.getAbsolutePath());
    } else {
      log.info("Directory already exists: " + file.getAbsolutePath());
    }
    return folderPath;
  }

  public static String findOption(File file, String option) {
    try {
      for (String line : Files.readAllLines(file.toPath())) {
        if (line.startsWith(option)) {
          return line.substring(option.length()).trim();
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    throw new IllegalArgumentException("Option: " + option + "not found in given file!");
  }

  /**
   * Adding Jar to a class path.
   *
   * @param jarPath The path of the JAR file that should be added to the class path.
   */
  public static void addJarToClassPath(String jarPath) {
    try {
      URL jarUrl = new URL("file:///" + jarPath);
      CLASS_LOADER.addJar(jarUrl);
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      // Open the JAR file
      JarFile jarFile = new JarFile(new File(jarPath));

      // Get the entries in the JAR file
      Enumeration<JarEntry> entries = jarFile.entries();

      // Iterate through the entries
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();

        // Check if the entry is a class file
        if (entry.getName().endsWith(".class")) {
          // Print the class name
          String className = entry.getName().replace("/", ".").replace(".class", "");
          log.info(className);
        }
      }

      // Close the JAR file
      jarFile.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
