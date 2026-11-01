<?xml version="1.0" encoding="UTF-8"?>

<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <!-- Build Parent -->
  <parent>
    <groupId>tools.vitruv</groupId>
    <artifactId>parent</artifactId>
    <version>${versions.parent}</version>
  </parent>

  <!-- Project Information -->
  <artifactId>${packageName}</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <name>Methodologist Template</name>
  <description />

  <!-- Modules -->
  <modules>
    <!-- <module>viewtype</module> -->
    <!-- <module>vsum</module> -->
    <module>model</module>
    <module>consistency</module>
    <module>vsum</module>
    <module>p2wrappers</module>
  </modules>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <vitruv-change.version>${versions.change}</vitruv-change.version>
    <vitruv.version>${versions.framework}</vitruv.version>
    <vitruv-dsls.version>${versions.dsls}</vitruv-dsls.version>
    <vitruv-server.version>${versions.server}</vitruv-server.version>
    <vitruvocl.version>${versions.vitruvocl}</vitruvocl.version>
  </properties>
  <#noparse>

  <!-- snapshot versions of Vitruvius artifacts are published to the Maven Central snapshots -->
  <repositories>
    <repository>
      <id>central-snapshots</id>
      <url>https://central.sonatype.com/repository/maven-snapshots/</url>
      <releases>
        <enabled>false</enabled>
      </releases>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>
  </repositories>

  <dependencyManagement>
    <dependencies>
      <!-- Vitruvius dependencies -->
      <dependency>
        <groupId>tools.vitruv</groupId>
        <artifactId>tools.vitruv.change.atomic</artifactId>
        <version>${vitruv-change.version}</version>
      </dependency>
      <dependency>
        <groupId>tools.vitruv</groupId>
        <artifactId>tools.vitruv.change.composite</artifactId>
        <version>${vitruv-change.version}</version>
      </dependency>
      <dependency>
        <groupId>tools.vitruv</groupId>
        <artifactId>tools.vitruv.change.interaction</artifactId>
        <version>${vitruv-change.version}</version>
      </dependency>
      <dependency>
        <groupId>tools.vitruv</groupId>
        <artifactId>tools.vitruv.change.propagation</artifactId>
        <version>${vitruv-change.version}</version>
      </dependency>
      <dependency>
        <groupId>tools.vitruv</groupId>
        <artifactId>tools.vitruv.change.testutils.integration</artifactId>
        <version>${vitruv-change.version}</version>
      </dependency>
      <dependency>
        <groupId>tools.vitruv</groupId>
        <artifactId>tools.vitruv.dsls.reactions.runtime</artifactId>
        <version>${vitruv-dsls.version}</version>
      </dependency>
      <dependency>
        <groupId>tools.vitruv</groupId>
        <artifactId>tools.vitruv.framework.views</artifactId>
        <version>${vitruv.version}</version>
      </dependency>
      <dependency>
        <groupId>tools.vitruv</groupId>
        <artifactId>tools.vitruv.framework.vsum</artifactId>
        <version>${vitruv.version}</version>
      </dependency>
      <dependency>
        <groupId>tools.vitruv</groupId>
        <artifactId>tools.vitruv.server.remote</artifactId>
        <version>${vitruv-server.version}</version>
      </dependency>
      <dependency>
        <groupId>tools.vitruv</groupId>
        <artifactId>tools.vitruv.vitruvocl.language</artifactId>
        <version>${vitruvocl.version}</version>
      </dependency>

      <!-- external dependencies-->
      <dependency>
        <groupId>org.eclipse.emf</groupId>
        <artifactId>org.eclipse.emf.common</artifactId>
        <version>2.42.0</version>
      </dependency>
      <dependency>
        <groupId>org.eclipse.emf</groupId>
        <artifactId>org.eclipse.emf.ecore</artifactId>
        <version>2.39.0</version>
      </dependency>
      <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
        <version>5.12.0</version>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
</#noparse>
