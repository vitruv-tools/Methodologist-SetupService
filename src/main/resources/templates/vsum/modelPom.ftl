<?xml version="1.0" encoding="UTF-8"?>

<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>[=groupId]</groupId>
    <artifactId>[=rootArtifactId]</artifactId>
    <version>[=version]</version>
  </parent>

  <artifactId>[=modelArtifactId]</artifactId>

  <name>Model</name>
  <description />

  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>build-helper-maven-plugin</artifactId>
      </plugin>
      <!-- runs workflow/generate.mwe2 to generate the metamodel code -->
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
      </plugin>
      <plugin>
        <artifactId>maven-jar-plugin</artifactId>
        <configuration>
          <archive>
            <manifestFile>${project.basedir}/META-INF/MANIFEST.MF</manifestFile>
          </archive>
        </configuration>
      </plugin>
    </plugins>
  </build>

  <dependencies>
    <dependency>
      <groupId>org.eclipse.emf</groupId>
      <artifactId>org.eclipse.emf.common</artifactId>
    </dependency>
    <dependency>
      <groupId>org.eclipse.emf</groupId>
      <artifactId>org.eclipse.emf.ecore</artifactId>
    </dependency>
  </dependencies>
</project>
