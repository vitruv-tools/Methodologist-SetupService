package tools.vitruv.methodologist.setup.vsum.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.exception.MissingModelException;

/** Coordinates VSUM project build operations from uploaded files. */
@Service
@RequiredArgsConstructor
public class VsumProjectBuildService {

  private static final String VSUM_BUILD_ERROR_CODE = "VSUM_BUILD_ERROR";
  private static final String VSUM_INPUT_ERROR_CODE = "VSUM_INPUT_ERROR";
  private static final Pattern REACTION_IMPORT_PATTERN = Pattern.compile("import\\s+\"([^\"]+)\"");

  private final VsumService vsumService;

  /**
   * Builds a VSUM project from uploaded files and returns a zip archive.
   *
   * @param metamodelFiles metamodel files
   * @param genmodelFiles genmodel files in the same order as metamodel files
   * @param reactionFiles optional reaction files
   * @return built project archive bytes
   */
  public byte[] buildProjectArchive(
      List<MultipartFile> metamodelFiles,
      List<MultipartFile> genmodelFiles,
      List<MultipartFile> reactionFiles) {
    validateInputs(metamodelFiles, genmodelFiles);

    Path uploadWorkspace = null;
    try {
      uploadWorkspace = Files.createTempDirectory("vsum-upload-");
      List<VsumService.ModelFiles> modelPairs =
          toModelPairs(uploadWorkspace, metamodelFiles, genmodelFiles);
      List<File> copiedReactionFiles = toReactionFiles(uploadWorkspace, reactionFiles);
      normalizeReactionImports(modelPairs, copiedReactionFiles);
      Map<String, String> metamodelNamespaceMap = extractMetamodelNamespaceMap(modelPairs);
      return vsumService.generateProjectArchive(
          modelPairs, copiedReactionFiles, metamodelNamespaceMap);
    } catch (IOException | MissingModelException e) {
      throw new MethodologistSetupException(
          VSUM_BUILD_ERROR_CODE, "Failed to build VSUM project archive", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MethodologistSetupException(
          VSUM_BUILD_ERROR_CODE, "VSUM project build was interrupted", e);
    } finally {
      deleteRecursively(uploadWorkspace);
    }
  }

  /**
   * Validates uploaded metamodel/genmodel lists.
   *
   * @param metamodelFiles metamodel files
   * @param genmodelFiles genmodel files
   */
  private void validateInputs(
      List<MultipartFile> metamodelFiles, List<MultipartFile> genmodelFiles) {
    if (metamodelFiles == null
        || genmodelFiles == null
        || metamodelFiles.isEmpty()
        || genmodelFiles.isEmpty()) {
      throw new MethodologistSetupException(
          VSUM_INPUT_ERROR_CODE, "At least one metamodel and one genmodel file are required");
    }

    if (metamodelFiles.size() != genmodelFiles.size()) {
      throw new MethodologistSetupException(
          VSUM_INPUT_ERROR_CODE,
          "Metamodel and genmodel file counts must be identical to build file pairs");
    }

    for (MultipartFile file : metamodelFiles) {
      validateUploadedFile(file, "metamodel file");
    }
    for (MultipartFile file : genmodelFiles) {
      validateUploadedFile(file, "genmodel file");
    }
  }

  /**
   * Converts uploaded metamodel/genmodel files into local file pairs.
   *
   * @param root upload root directory
   * @param metamodelFiles metamodel files
   * @param genmodelFiles genmodel files
   * @return model file pairs
   * @throws IOException when writing files fails
   */
  private List<VsumService.ModelFiles> toModelPairs(
      Path root, List<MultipartFile> metamodelFiles, List<MultipartFile> genmodelFiles)
      throws IOException {
    Path modelUploadPath = Files.createDirectories(root.resolve("models"));
    List<VsumService.ModelFiles> modelPairs = new ArrayList<>();

    for (int index = 0; index < metamodelFiles.size(); index++) {
      File metamodel =
          writeMultipartFile(metamodelFiles.get(index), modelUploadPath, "metamodel", index);
      File genmodel =
          writeMultipartFile(genmodelFiles.get(index), modelUploadPath, "genmodel", index);
      modelPairs.add(new VsumService.ModelFiles(metamodel, genmodel));
    }

    return modelPairs;
  }

  /**
   * Converts uploaded reaction files into local files.
   *
   * @param root upload root directory
   * @param reactionFiles reaction files
   * @return copied reaction files
   * @throws IOException when writing files fails
   */
  private List<File> toReactionFiles(Path root, List<MultipartFile> reactionFiles)
      throws IOException {
    if (reactionFiles == null || reactionFiles.isEmpty()) {
      return List.of();
    }

    Path reactionUploadPath = Files.createDirectories(root.resolve("reactions"));
    List<File> files = new ArrayList<>();
    for (int index = 0; index < reactionFiles.size(); index++) {
      MultipartFile reactionFile = reactionFiles.get(index);
      validateUploadedFile(reactionFile, "reaction file");
      files.add(writeMultipartFile(reactionFile, reactionUploadPath, "reaction", index));
    }
    return files;
  }

  /**
   * Normalizes imported model URIs in reaction files to match uploaded metamodel nsURIs.
   *
   * @param modelPairs metamodel/genmodel file pairs
   * @param reactionFiles copied reaction files
   * @throws IOException when reading or rewriting a reaction file fails
   */
  private void normalizeReactionImports(
      List<VsumService.ModelFiles> modelPairs, List<File> reactionFiles) throws IOException {
    if (reactionFiles == null || reactionFiles.isEmpty()) {
      return;
    }

    Map<String, String> packageNameToNsUri = new HashMap<>();
    Set<String> nsUris = new HashSet<>();
    for (VsumService.ModelFiles pair : modelPairs) {
      MetamodelInfo info = readMetamodelInfo(pair.metamodelFile());
      if (info != null) {
        if (info.packageName != null && !info.packageName.isBlank()) {
          packageNameToNsUri.put(info.packageName, info.nsUri);
        }
        nsUris.add(info.nsUri);
      }
    }

    for (File reactionFile : reactionFiles) {
      String content = Files.readString(reactionFile.toPath(), StandardCharsets.UTF_8);
      String updated = normalizeReactionImports(content, packageNameToNsUri, nsUris);
      if (!updated.equals(content)) {
        Files.writeString(reactionFile.toPath(), updated, StandardCharsets.UTF_8);
      }
    }
  }

  /**
   * Rewrites each {@code import "..."} statement in a single reaction file's content, replacing
   * imports that do not match a known nsURI with the nsURI mapped from the import's last path
   * segment.
   *
   * @param content the reaction file content to rewrite
   * @param packageNameToNsUri map of metamodel package names to their nsURIs
   * @param knownNsUris the set of nsURIs that are already valid and should be left unchanged
   * @return the rewritten content, identical to the input when no import required replacement
   */
  private String normalizeReactionImports(
      String content, Map<String, String> packageNameToNsUri, Set<String> knownNsUris) {
    Matcher matcher = REACTION_IMPORT_PATTERN.matcher(content);
    StringBuffer rewritten = new StringBuffer();
    while (matcher.find()) {
      String importUri = matcher.group(1);
      String replacementUri = importUri;
      if (!knownNsUris.contains(importUri)) {
        String candidatePackageName = extractLastSegment(importUri);
        String mappedUri = packageNameToNsUri.get(candidatePackageName);
        if (mappedUri != null && !mappedUri.isBlank()) {
          replacementUri = mappedUri;
        }
      }

      matcher.appendReplacement(
          rewritten, "import \"" + Matcher.quoteReplacement(replacementUri) + "\"");
    }
    matcher.appendTail(rewritten);
    return rewritten.toString();
  }

  /**
   * Stores metamodel nsURI information for build-time EMF initialization.
   *
   * @param modelPairs metamodel/genmodel file pairs
   * @return map of model names to nsURIs
   */
  Map<String, String> extractMetamodelNamespaceMap(List<VsumService.ModelFiles> modelPairs) {
    Map<String, String> modelNameToNsUri = new HashMap<>();
    for (VsumService.ModelFiles pair : modelPairs) {
      MetamodelInfo info = readMetamodelInfo(pair.metamodelFile());
      if (info != null && info.nsUri != null && !info.nsUri.isBlank()) {
        modelNameToNsUri.put(info.packageName != null ? info.packageName : "model", info.nsUri);
      }
    }
    return modelNameToNsUri;
  }

  /**
   * Extracts the last segment of a URI, i.e. the substring following the final {@code /} or {@code
   * #}.
   *
   * @param uri the URI to inspect
   * @return the substring after the last separator, or the whole URI when no separator is present
   */
  private String extractLastSegment(String uri) {
    int slash = uri.lastIndexOf('/');
    int hash = uri.lastIndexOf('#');
    int separator = Math.max(slash, hash);
    return separator >= 0 ? uri.substring(separator + 1) : uri;
  }

  /**
   * Parses an ecore metamodel file and extracts the root element's {@code name} and {@code nsURI}
   * attributes.
   *
   * @param metamodelFile the metamodel (ecore) file to parse
   * @return the extracted metamodel info, or {@code null} when the file cannot be parsed or its
   *     nsURI is missing or blank
   */
  private MetamodelInfo readMetamodelInfo(File metamodelFile) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      Document document = factory.newDocumentBuilder().parse(metamodelFile);
      Element root = document.getDocumentElement();
      String nsUri = root.getAttribute("nsURI");
      String packageName = root.getAttribute("name");
      if (nsUri == null || nsUri.isBlank()) {
        return null;
      }
      return new MetamodelInfo(packageName, nsUri);
    } catch (Exception exception) {
      return null;
    }
  }

  /**
   * Writes a multipart file to disk.
   *
   * @param file multipart file
   * @param directory target directory
   * @param type logical file type label
   * @param index index for deterministic fallback naming
   * @return copied file
   * @throws IOException when writing file fails
   */
  private File writeMultipartFile(MultipartFile file, Path directory, String type, int index)
      throws IOException {
    String originalName = file.getOriginalFilename();
    String safeFileName =
        originalName == null || originalName.isBlank()
            ? type + "-" + index
            : Path.of(originalName).getFileName().toString();

    Path targetFile = directory.resolve(safeFileName);
    file.transferTo(targetFile);
    return targetFile.toFile();
  }

  /**
   * Ensures an uploaded file is present and non-empty.
   *
   * @param file uploaded file
   * @param label logical file label
   */
  private void validateUploadedFile(MultipartFile file, String label) {
    if (file == null || file.isEmpty()) {
      throw new MethodologistSetupException(
          VSUM_INPUT_ERROR_CODE, "Uploaded " + label + " must not be empty");
    }
  }

  /**
   * Deletes a directory tree if it exists.
   *
   * @param root root directory
   */
  private void deleteRecursively(Path root) {
    if (root == null || !Files.exists(root)) {
      return;
    }

    try (Stream<Path> paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
    } catch (IOException ignored) {
    }
  }

  /**
   * Deletes a single path while ignoring IO errors.
   *
   * @param path file system path
   */
  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
    }
  }

  /** Immutable holder for a metamodel's package name and nsURI. */
  private static class MetamodelInfo {
    private final String packageName;
    private final String nsUri;

    /**
     * Creates a holder for the given metamodel attributes.
     *
     * @param packageName the metamodel's package name (root element {@code name} attribute)
     * @param nsUri the metamodel's namespace URI (root element {@code nsURI} attribute)
     */
    private MetamodelInfo(String packageName, String nsUri) {
      this.packageName = packageName;
      this.nsUri = nsUri;
    }
  }
}
