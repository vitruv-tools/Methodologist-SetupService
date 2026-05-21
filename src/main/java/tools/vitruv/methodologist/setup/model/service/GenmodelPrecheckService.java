package tools.vitruv.methodologist.setup.model.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
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
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.exception.GenmodelException;
import tools.vitruv.methodologist.setup.messages.ErrorMessages;
import tools.vitruv.methodologist.setup.messages.InfoMessages;

/** Service for validating and standardizing GenModel files for MWE2 workflow compatibility. */
@Slf4j
@Service
public class GenmodelPrecheckService {

  private static final Set<String> ATTRS_TO_REMOVE =
      Set.of(
          "complianceLevel",
          "compliance",
          "editDirectory",
          "editorDirectory",
          "testsDirectory",
          "editPluginID",
          "editorPluginID",
          "testsPluginID");

  /**
   * Inspects a GenModel file and reports the changes that would be applied without modifying it.
   *
   * @param genmodelFile the GenModel file to inspect
   * @return the list of detected issues and planned changes
   */
  public List<GenmodelIssue> inspect(File genmodelFile) {
    return analyze(genmodelFile, false);
  }

  /**
   * Processes a GenModel file and applies the required changes.
   *
   * @param genmodelFile the GenModel file to process
   * @return the list of detected issues and applied changes
   */
  public List<GenmodelIssue> process(File genmodelFile) {
    return analyze(genmodelFile, true);
  }

  /**
   * Inspects GenModel file content from bytes without applying changes.
   *
   * @param fileBytes the GenModel file content as bytes
   * @param filename the original filename
   * @return the list of detected issues and planned changes
   * @throws GenmodelException if file cannot be processed
   */
  public List<GenmodelIssue> inspectFileBytes(byte[] fileBytes, String filename) {
    File tempFile = null;
    try {
      tempFile = File.createTempFile("genmodel_", ".genmodel");
      Files.write(tempFile.toPath(), fileBytes);
      log.debug("Inspecting file bytes for: {}", filename);
      return inspect(tempFile);
    } catch (IOException e) {
      log.error("Failed to create temp file for inspection", e);
      throw new GenmodelException("FILE_PROCESS_ERROR", ErrorMessages.GENMODEL_FILE_READ_ERROR, e);
    } finally {
      if (tempFile != null && tempFile.exists()) {
        tempFile.delete();
      }
    }
  }

  /**
   * Processes GenModel file content from bytes and returns the modified content.
   *
   * @param fileBytes the GenModel file content as bytes
   * @param filename the original filename
   * @return the process result containing issues and processed content
   * @throws GenmodelException if file cannot be processed
   */
  public ProcessResult processFileBytes(byte[] fileBytes, String filename) {
    File tempFile = null;
    try {
      tempFile = File.createTempFile("genmodel_", ".genmodel");
      Files.write(tempFile.toPath(), fileBytes);
      log.debug("Processing file bytes for: {}", filename);

      List<GenmodelIssue> issues = process(tempFile);

      byte[] processedContent = Files.readAllBytes(tempFile.toPath());
      log.info("Successfully processed file: {}", filename);

      return new ProcessResult(issues, processedContent);
    } catch (IOException e) {
      log.error("Failed to process file bytes", e);
      throw new GenmodelException("FILE_PROCESS_ERROR", ErrorMessages.GENMODEL_FILE_READ_ERROR, e);
    } finally {
      if (tempFile != null && tempFile.exists()) {
        tempFile.delete();
      }
    }
  }

  /**
   * Analyzes a GenModel file and optionally applies changes.
   *
   * @param genmodelFile the GenModel file to analyze
   * @param applyChanges whether the detected changes should be written back to disk
   * @return the list of detected issues and applied or planned changes
   */
  public List<GenmodelIssue> analyze(File genmodelFile, boolean applyChanges) {
    validateGenmodelFile(genmodelFile);

    String originalXml = readGenmodelXml(genmodelFile);
    String strippedXml = stripGenmodelXml(genmodelFile, originalXml);

    List<GenmodelIssue> issues = new ArrayList<>();
    handleRemovedAttributes(genmodelFile, originalXml, strippedXml, issues, applyChanges);

    ResourceSet resourceSet = createResourceSet();
    URI uri = URI.createFileURI(genmodelFile.getAbsolutePath());
    Resource resource =
        loadAnalyzedResource(resourceSet, uri, genmodelFile, strippedXml, applyChanges);

    GenModel genModel = extractGenModel(resource, genmodelFile);
    String modelPluginId = requireModelPluginId(genModel, genmodelFile);

    applyGenmodelRules(genmodelFile, genModel, modelPluginId, issues, applyChanges);
    saveResourceIfNeeded(resource, genmodelFile, applyChanges);

    return issues;
  }

  private void validateGenmodelFile(File genmodelFile) {
    if (genmodelFile == null) {
      throw new GenmodelException("INVALID_FILE", ErrorMessages.FILE_NULL_ERROR);
    }
  }

  private String readGenmodelXml(File genmodelFile) {
    try {
      log.debug("Reading GenModel file: {}", genmodelFile.getAbsolutePath());
      return Files.readString(genmodelFile.toPath(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("Failed to read genmodel file: {}", genmodelFile.getAbsolutePath(), e);
      throw new GenmodelException(
          "FILE_READ_ERROR",
          String.format(ErrorMessages.GENMODEL_FILE_READ_ERROR, genmodelFile.getAbsolutePath()),
          e);
    }
  }

  private String stripGenmodelXml(File genmodelFile, String originalXml) {
    try {
      log.debug("Stripping attributes from GenModel file");
      return stripAttributesWithStax(originalXml, ATTRS_TO_REMOVE);
    } catch (XMLStreamException e) {
      log.error("Failed to strip attributes from genmodel XML", e);
      throw new GenmodelException(
          "ATTRIBUTE_STRIP_ERROR",
          String.format(
              ErrorMessages.GENMODEL_ATTRIBUTE_STRIP_ERROR, genmodelFile.getAbsolutePath()),
          e);
    }
  }

  private void handleRemovedAttributes(
      File genmodelFile,
      String originalXml,
      String strippedXml,
      List<GenmodelIssue> issues,
      boolean applyChanges) {

    if (originalXml.equals(strippedXml)) {
      return;
    }

    List<String> foundAttrs = findPresentAttributes(originalXml);
    if (!foundAttrs.isEmpty()) {
      String message =
          String.format(
              applyChanges ? InfoMessages.ATTRIBUTES_REMOVED : InfoMessages.ATTRIBUTES_WOULD_REMOVE,
              String.join(", ", foundAttrs));
      issues.add(new GenmodelIssue(genmodelFile.getName(), message));
    }

    if (applyChanges) {
      writeGenmodelXml(genmodelFile, strippedXml);
    }
  }

  private List<String> findPresentAttributes(String xml) {
    List<String> foundAttrs = new ArrayList<>();
    for (String attr : ATTRS_TO_REMOVE) {
      if (xml.contains(attr + "=")) {
        foundAttrs.add(attr);
      }
    }
    return foundAttrs;
  }

  private void writeGenmodelXml(File genmodelFile, String xml) {
    try {
      log.debug("Writing GenModel file: {}", genmodelFile.getAbsolutePath());
      Files.writeString(genmodelFile.toPath(), xml, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("Failed to write genmodel file: {}", genmodelFile.getAbsolutePath(), e);
      throw new GenmodelException(
          "FILE_WRITE_ERROR",
          String.format(ErrorMessages.GENMODEL_FILE_WRITE_ERROR, genmodelFile.getAbsolutePath()),
          e);
    }
  }

  private Resource loadAnalyzedResource(
      ResourceSet resourceSet,
      URI uri,
      File genmodelFile,
      String strippedXml,
      boolean applyChanges) {
    return loadResource(resourceSet, uri, applyChanges ? null : strippedXml, genmodelFile);
  }

  private GenModel extractGenModel(Resource resource, File genmodelFile) {
    if (resource.getContents().isEmpty() || !(resource.getContents().get(0) instanceof GenModel)) {
      log.error("Invalid GenModel file: {}", genmodelFile.getAbsolutePath());
      throw new GenmodelException(
          "INVALID_GENMODEL",
          String.format(ErrorMessages.GENMODEL_INVALID_FORMAT, genmodelFile.getAbsolutePath()));
    }
    return (GenModel) resource.getContents().get(0);
  }

  private String requireModelPluginId(GenModel genModel, File genmodelFile) {
    String modelPluginId = safeTrim(genModel.getModelPluginID());
    if (modelPluginId.isEmpty()) {
      log.error("GenModel has missing modelPluginID: {}", genmodelFile.getAbsolutePath());
      throw new GenmodelException(
          "MISSING_PLUGIN_ID",
          String.format(ErrorMessages.GENMODEL_MISSING_PLUGIN_ID, genmodelFile.getAbsolutePath()));
    }
    return modelPluginId;
  }

  private void applyGenmodelRules(
      File genmodelFile,
      GenModel genModel,
      String modelPluginId,
      List<GenmodelIssue> issues,
      boolean applyChanges) {
    enforceBasePackageEqualsModelPluginId(
        genmodelFile, genModel, modelPluginId, issues, applyChanges);
    enforceModelDirectory(genmodelFile, genModel, modelPluginId, issues, applyChanges);
    enforceForeignModel(genmodelFile, genModel, issues, applyChanges);
    enforceCreationIcons(genmodelFile, genModel, issues, applyChanges);
  }

  private void saveResourceIfNeeded(Resource resource, File genmodelFile, boolean applyChanges) {
    if (!applyChanges) {
      return;
    }
    try {
      log.debug("Saving GenModel file: {}", genmodelFile.getAbsolutePath());
      resource.save(null);
    } catch (IOException e) {
      log.error("Failed to save genmodel file: {}", genmodelFile.getAbsolutePath(), e);
      throw new GenmodelException(
          "FILE_SAVE_ERROR",
          String.format(ErrorMessages.GENMODEL_SAVE_ERROR, genmodelFile.getAbsolutePath()),
          e);
    }
  }

  /**
   * Removes the provided attributes from the XML using StAX.
   *
   * @param xml the XML source
   * @param attributeLocalNamesToRemove the local attribute names to remove
   * @return the XML without the specified attributes
   */
  public String stripAttributesWithStax(String xml, Set<String> attributeLocalNamesToRemove)
      throws XMLStreamException {
    XMLInputFactory inFactory = XMLInputFactory.newFactory();
    inFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    if (inFactory.isPropertySupported(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES)) {
      inFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }
    inFactory.setXMLResolver(
        (publicID, systemID, baseURI, namespace) -> {
          throw new XMLStreamException("External entity resolution disabled");
        });
    inFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);

    XMLOutputFactory outFactory = XMLOutputFactory.newFactory();
    XMLEventFactory eventFactory = XMLEventFactory.newFactory();
    XMLEventReader reader = inFactory.createXMLEventReader(new StringReader(xml));
    StringWriter stringWriter = new StringWriter();
    XMLEventWriter writer = outFactory.createXMLEventWriter(stringWriter);
    while (reader.hasNext()) {
      XMLEvent xmlEvent = reader.nextEvent();
      if (xmlEvent.isStartElement()) {
        StartElement startElement = xmlEvent.asStartElement();
        List<Attribute> keptAttrs = new ArrayList<>();
        Iterator<?> attributes = startElement.getAttributes();
        while (attributes.hasNext()) {
          Attribute attribute = (Attribute) attributes.next();
          String localName = attribute.getName().getLocalPart();
          if (!attributeLocalNamesToRemove.contains(localName)) {
            keptAttrs.add(attribute);
          }
        }

        @SuppressWarnings("unchecked")
        Iterator<Namespace> namespaces = startElement.getNamespaces();

        StartElement rebuilt =
            eventFactory.createStartElement(
                startElement.getName(), keptAttrs.iterator(), namespaces);
        writer.add(rebuilt);
      } else {
        writer.add(xmlEvent);
      }
    }
    writer.flush();
    writer.close();
    reader.close();
    return stringWriter.toString();
  }

  /**
   * Ensures creationIcons is false.
   *
   * @param genmodelFile the source file
   * @param genModel the GenModel to inspect or mutate
   * @param issues the issue accumulator
   * @param applyChanges whether the change should be applied
   */
  public void enforceCreationIcons(
      File genmodelFile, GenModel genModel, List<GenmodelIssue> issues, boolean applyChanges) {
    if (genModel.isCreationIcons()) {
      if (applyChanges) {
        genModel.setCreationIcons(false);
        issues.add(new GenmodelIssue(genmodelFile.getName(), InfoMessages.CREATION_ICONS_SET));
      } else {
        issues.add(
            new GenmodelIssue(genmodelFile.getName(), InfoMessages.CREATION_ICONS_WOULD_SET));
      }
    }
  }

  /**
   * Ensures a foreignModel entry exists.
   *
   * @param genmodelFile the source file
   * @param genModel the GenModel to inspect or mutate
   * @param issues the issue accumulator
   * @param applyChanges whether the change should be applied
   */
  public void enforceForeignModel(
      File genmodelFile, GenModel genModel, List<GenmodelIssue> issues, boolean applyChanges) {
    List<String> foreignModels = genModel.getForeignModel();

    if (foreignModels == null || foreignModels.isEmpty()) {
      String defaultModel = genmodelFile.getName().replace(".genmodel", ".ecore");
      if (applyChanges) {
        genModel.getForeignModel().add(defaultModel);
        issues.add(
            new GenmodelIssue(
                genmodelFile.getName(),
                String.format(InfoMessages.FOREIGN_MODEL_ADDED, defaultModel)));
      } else {
        issues.add(
            new GenmodelIssue(
                genmodelFile.getName(),
                String.format(InfoMessages.FOREIGN_MODEL_WOULD_ADD, defaultModel)));
      }
    }
  }

  /**
   * Ensures basePackage equals modelPluginId for all GenPackages.
   *
   * @param genmodelFile the source file
   * @param genModel the GenModel to inspect or mutate
   * @param modelPluginId the expected plugin id
   * @param issues the issue accumulator
   * @param applyChanges whether the change should be applied
   */
  public void enforceBasePackageEqualsModelPluginId(
      File genmodelFile,
      GenModel genModel,
      String modelPluginId,
      List<GenmodelIssue> issues,
      boolean applyChanges) {

    List<GenPackage> genPackages = genModel.getGenPackages();
    for (GenPackage genPackage : genPackages) {
      String before = safeTrim(genPackage.getBasePackage());
      String gpName = safeTrim(genPackage.getPackageName());
      String label = gpName.isEmpty() ? InfoMessages.UNNAMED_PACKAGE : gpName;

      if (!modelPluginId.equals(before)) {
        if (applyChanges) {
          genPackage.setBasePackage(modelPluginId);
          if (before.isEmpty()) {
            issues.add(
                new GenmodelIssue(
                    genmodelFile.getName(),
                    String.format(InfoMessages.BASE_PACKAGE_SET, label, modelPluginId)));
          } else {
            issues.add(
                new GenmodelIssue(
                    genmodelFile.getName(),
                    String.format(
                        InfoMessages.BASE_PACKAGE_CHANGED, label, before, modelPluginId)));
          }
        } else {
          if (before.isEmpty()) {
            issues.add(
                new GenmodelIssue(
                    genmodelFile.getName(),
                    String.format(InfoMessages.BASE_PACKAGE_WOULD_SET, label, modelPluginId)));
          } else {
            issues.add(
                new GenmodelIssue(
                    genmodelFile.getName(),
                    String.format(
                        InfoMessages.BASE_PACKAGE_WOULD_CHANGE, label, before, modelPluginId)));
          }
        }
      }
    }
  }

  /**
   * Ensures modelDirectory follows the required pattern.
   *
   * @param genmodelFile the source file
   * @param genModel the GenModel to inspect or mutate
   * @param modelPluginId the plugin id used to compute the expected directory
   * @param issues the issue accumulator
   * @param applyChanges whether the change should be applied
   */
  public void enforceModelDirectory(
      File genmodelFile,
      GenModel genModel,
      String modelPluginId,
      List<GenmodelIssue> issues,
      boolean applyChanges) {

    String expected = normalize("/" + modelPluginId + "/target/generated-sources/ecore");
    String beforeRaw = genModel.getModelDirectory();
    String before = normalize(safeTrim(beforeRaw));

    if (before.isEmpty()) {
      if (applyChanges) {
        genModel.setModelDirectory(expected);
        issues.add(
            new GenmodelIssue(
                genmodelFile.getName(), String.format(InfoMessages.MODEL_DIRECTORY_SET, expected)));
      } else {
        issues.add(
            new GenmodelIssue(
                genmodelFile.getName(),
                String.format(InfoMessages.MODEL_DIRECTORY_WOULD_SET, expected)));
      }
    } else if (!before.equals(expected)) {
      if (applyChanges) {
        genModel.setModelDirectory(expected);
        issues.add(
            new GenmodelIssue(
                genmodelFile.getName(),
                String.format(InfoMessages.MODEL_DIRECTORY_CHANGED, beforeRaw, expected)));
      } else {
        issues.add(
            new GenmodelIssue(
                genmodelFile.getName(),
                String.format(InfoMessages.MODEL_DIRECTORY_WOULD_CHANGE, beforeRaw, expected)));
      }
    }
  }

  /**
   * Safely trims a string, treating null as empty string.
   *
   * @param s the input string
   * @return the trimmed string or empty string
   */
  public String safeTrim(String s) {
    return s == null ? "" : s.trim();
  }

  /**
   * Normalizes a path by converting separators and collapsing repeated slashes.
   *
   * @param s the input path
   * @return the normalized path
   */
  public String normalize(String s) {
    return s.replace("\\", "/").replaceAll("/+", "/").trim();
  }

  /**
   * Creates the EMF ResourceSet used for loading GenModel resources.
   *
   * @return the configured ResourceSet
   */
  public ResourceSet createResourceSet() {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getPackageRegistry().put(GenModelPackage.eNS_URI, GenModelPackage.eINSTANCE);
    resourceSet
        .getPackageRegistry()
        .put("http://www.eclipse.emf/2002/GenModel", GenModelPackage.eINSTANCE);

    EPackage.Registry.INSTANCE.put(GenModelPackage.eNS_URI, GenModelPackage.eINSTANCE);
    EPackage.Registry.INSTANCE.put(
        "http://www.eclipse.emf/2002/GenModel", GenModelPackage.eINSTANCE);
    EPackage.Registry.INSTANCE.put(
        "http://www.eclipse.org/emf/2002/GenModel", GenModelPackage.eINSTANCE);

    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("genmodel", new XMIResourceFactoryImpl());
    return resourceSet;
  }

  /**
   * Loads a GenModel resource either from disk or from an in-memory XML string.
   *
   * @param resourceSet the ResourceSet to use
   * @param uri the file URI of the GenModel
   * @param xmlOverride optional XML content to load instead of the file on disk
   * @param genmodelFile the source file for error reporting
   * @return the loaded Resource
   */
  public Resource loadResource(
      ResourceSet resourceSet, URI uri, String xmlOverride, File genmodelFile) {
    Resource resource;
    try {
      if (xmlOverride == null) {
        resource = resourceSet.getResource(uri, true);
        resource.load(null);
      } else {
        resource = resourceSet.createResource(uri);
        resource.load(new ByteArrayInputStream(xmlOverride.getBytes(StandardCharsets.UTF_8)), null);
      }
      return resource;
    } catch (IOException e) {
      log.error("Failed to load genmodel file: {}", genmodelFile.getAbsolutePath(), e);
      throw new GenmodelException(
          "FILE_LOAD_ERROR",
          String.format(ErrorMessages.GENMODEL_LOAD_ERROR, genmodelFile.getAbsolutePath()),
          e);
    }
  }

  /** Represents a validation issue found in a GenModel file. */
  public static final class GenmodelIssue {
    public final String filename;
    public final String message;

    /**
     * Creates a new GenModel issue.
     *
     * @param filename the filename of the genmodel file
     * @param message the issue message
     */
    public GenmodelIssue(String filename, String message) {
      this.filename = filename;
      this.message = message;
    }

    @Override
    public String toString() {
      return filename + ": " + message;
    }
  }

  /** Represents the result of GenModel processing including issues and processed content. */
  public static final class ProcessResult {
    public final List<GenmodelIssue> issues;
    public final byte[] processedContent;

    /**
     * Creates a new process result.
     *
     * @param issues the list of detected issues
     * @param processedContent the processed file content as bytes
     */
    public ProcessResult(List<GenmodelIssue> issues, byte[] processedContent) {
      this.issues = issues;
      this.processedContent = processedContent;
    }
  }
}
