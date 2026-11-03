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

    <artifactId>[=consistencyArtifactId]</artifactId>

    <name>Consistency</name>
    <description />

    <dependencies>
        <!-- project dependencies-->
        <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>[=modelArtifactId]</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Vitruvius dependencies -->
        <dependency>
            <groupId>tools.vitruv</groupId>
            <artifactId>tools.vitruv.change.composite</artifactId>
        </dependency>
        <dependency>
            <groupId>tools.vitruv</groupId>
            <artifactId>tools.vitruv.change.propagation</artifactId>
        </dependency>
        <dependency>
            <groupId>tools.vitruv</groupId>
            <artifactId>tools.vitruv.dsls.reactions.runtime</artifactId>
        </dependency>

        <!-- external dependencies -->
        <dependency>
            <groupId>org.eclipse.emf</groupId>
            <artifactId>org.eclipse.emf.ecore</artifactId>
        </dependency>
    </dependencies>

    <build>
        <!-- VitruvOCL constraints are shipped as classpath resources -->
        <resources>
            <resource>
                <directory>src/main/constraints</directory>
            </resource>
        </resources>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>build-helper-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>add-source-reactions</id>
                        <phase>generate-sources</phase>
                        <goals>
                            <goal>add-source</goal>
                        </goals>
                        <configuration>
                            <sources>
                                <source>${project.basedir}/src/main/reactions</source>
                                <source>${project.build.directory}/generated-sources/reactions</source>
                            </sources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.eclipse.xtext</groupId>
                <artifactId>xtext-maven-plugin</artifactId>
                <configuration>
                    <languages>
                        <language>
                            <setup>tools.vitruv.dsls.reactions.ReactionsLanguageStandaloneSetup</setup>
                            <outputConfigurations>
                                <outputConfiguration>
                                    <outputDirectory>${project.build.directory}/generated-sources/reactions</outputDirectory>
                                </outputConfiguration>
                            </outputConfigurations>
                        </language>
                        <language>
                            <setup>org.eclipse.xtend.core.XtendStandaloneSetup</setup>
                            <outputConfigurations>
                                <outputConfiguration>
                                    <outputDirectory>${project.build.directory}/generated-sources/xtend</outputDirectory>
                                </outputConfiguration>
                            </outputConfigurations>
                        </language>
                    </languages>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>tools.vitruv</groupId>
                        <artifactId>tools.vitruv.dsls.reactions.language</artifactId>
                        <version>${vitruv.dsls.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>tools.vitruv</groupId>
                        <artifactId>tools.vitruv.dsls.reactions.runtime</artifactId>
                        <version>${vitruv.dsls.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>tools.vitruv</groupId>
                        <artifactId>tools.vitruv.dsls.common</artifactId>
                        <version>${vitruv.dsls.version}</version>
                    </dependency>
                    <!-- makes the metamodels resolvable by nsURI for the Reactions compiler -->
                    <dependency>
                        <groupId>${project.groupId}</groupId>
                        <artifactId>[=modelArtifactId]</artifactId>
                        <version>${project.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
