package tools.vitruv.methodologist.setup.config;

import java.net.URL;
import java.net.URLClassLoader;

/** The CustomClassLoader class is used to load classes from a custom classpath. */
public class CustomClassLoader extends URLClassLoader {
  /**
   * The constructor of the CustomClassLoader class.
   *
   * @param urls The URLs of the classpath.
   * @param parent The parent class loader.
   */
  public CustomClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }

  /**
   * Adds a JAR file to the classpath.
   *
   * @param url The URL of the JAR file.
   */
  public void addJar(URL url) {
    this.addURL(url);
  }
}
