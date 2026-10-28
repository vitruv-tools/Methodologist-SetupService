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

    <artifactId>[=vsumArtifactId]</artifactId>

    <name>Vsum</name>
    <description />

    <build>
        <plugins>
            <!-- executable jar that hosts the VSUM in a Vitruvius server -->
            <plugin>
                <artifactId>maven-assembly-plugin</artifactId>
                <version>[=assemblyPluginVersion]</version>
                <configuration>
                    <descriptorRefs>
                        <descriptorRef>jar-with-dependencies</descriptorRef>
                    </descriptorRefs>
                    <archive>
                        <manifest>
                            <mainClass>[=vsumPackage].[=vsumMainClass]</mainClass>
                        </manifest>
                    </archive>
                </configuration>
                <executions>
                    <execution>
                        <id>server-jar</id>
                        <phase>package</phase>
                        <goals>
                            <goal>single</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <!-- project dependencies -->
        <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>[=modelArtifactId]</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>[=consistencyArtifactId]</artifactId>
            <version>${project.version}</version>
        </dependency>
<#if constraintResources?has_content>

        <!-- VitruvOCL: cross-model constraint language and evaluator -->
        <dependency>
            <groupId>tools.vitruv.dsls</groupId>
            <artifactId>vitruvocl-language</artifactId>
        </dependency>
</#if>

        <!-- Vitruvius dependencies -->
        <dependency>
            <groupId>tools.vitruv</groupId>
            <artifactId>tools.vitruv.change.interaction</artifactId>
        </dependency>
        <dependency>
            <groupId>tools.vitruv</groupId>
            <artifactId>tools.vitruv.change.propagation</artifactId>
        </dependency>
        <dependency>
            <groupId>tools.vitruv</groupId>
            <artifactId>tools.vitruv.change.testutils.integration</artifactId>
        </dependency>
        <dependency>
            <groupId>tools.vitruv</groupId>
            <artifactId>tools.vitruv.framework.views</artifactId>
        </dependency>
        <dependency>
            <groupId>tools.vitruv</groupId>
            <artifactId>tools.vitruv.framework.vsum</artifactId>
        </dependency>
        <dependency>
            <groupId>tools.vitruv</groupId>
            <artifactId>tools.vitruv.server.remote</artifactId>
        </dependency>

        <!-- external dependencies -->
        <dependency>
            <groupId>org.eclipse.emf</groupId>
            <artifactId>org.eclipse.emf.ecore</artifactId>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
