package tools.vitruv.methodologist.setup.vsum.service.views;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl;
import org.eclipse.xtext.common.types.TypesPackage;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.xbase.XbasePackage;
import org.eclipse.xtext.xtype.XtypePackage;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.Excerpts;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;
import tools.vitruv.neojoin.NeoJoinStandaloneSetup;
import tools.vitruv.neojoin.Parser;
import tools.vitruv.neojoin.ast.AstPackage;

/**
 * Checks NeoJoin view definitions before the build: parses and validates each file with NeoJoin
 * against the uploaded metamodels, and rejects export names and view-type nsURIs declared in more
 * than one file.
 */
@Service
public class NeoJoinChecker {

  private static final Pattern EXPORT_PATTERN = Pattern.compile("(?m)^[ \\t]*(export)[ \\t]+");

  /**
   * Xtext registers NeoJoin's validator globally and keeps the first one, which is bound to the
   * metamodels of the first check. Each check replaces it, and checks run one at a time.
   */
  private static final Object XTEXT_VALIDATOR_LOCK = new Object();

  /** The packages NeoJoin's validator is registered for. */
  private static final List<EPackage> VALIDATED_PACKAGES =
      List.of(
          AstPackage.eINSTANCE,
          XbasePackage.eINSTANCE,
          XtypePackage.eINSTANCE,
          TypesPackage.eINSTANCE);

  /**
   * Checks the view definitions against the metamodels. Syntax errors, unresolved imports, Xbase
   * type errors, and duplicate export names or view-type nsURIs are reported together.
   *
   * @param viewDefinitions uploaded NeoJoin view definitions
   * @param metamodels the processed metamodels the views may import
   * @throws VsumBuildException when a view definition is invalid
   * @throws IOException when a view definition cannot be written to a temporary file for parsing
   */
  public void check(List<UploadedFile> viewDefinitions, List<GeneratedMetamodel> metamodels)
      throws IOException {
    synchronized (XTEXT_VALIDATOR_LOCK) {
      checkExclusively(viewDefinitions, metamodels);
    }
  }

  private void checkExclusively(
      List<UploadedFile> viewDefinitions, List<GeneratedMetamodel> metamodels) throws IOException {

    // 1. Register available metamodels
    EPackage.Registry registry = new EPackageRegistryImpl();
    register(EcorePackage.eINSTANCE, registry);

    for (GeneratedMetamodel model : metamodels) {
      if (model.ePackage() != null) {
        register(model.ePackage(), registry);
      }
    }

    // 2. Initialize NeoJoin parser
    VALIDATED_PACKAGES.forEach(EValidator.Registry.INSTANCE::remove);
    NeoJoinStandaloneSetup setup = new NeoJoinStandaloneSetup(registry);

    List<BuildProblem> problems = new ArrayList<>();

    Map<String, String> exportOwners = new HashMap<>();
    Map<String, String> nsUriOwners = new HashMap<>();

    // 3. Parse and validate each uploaded view
    for (UploadedFile file : viewDefinitions) {
      Parser parser = setup.getParser();
      Path path = Files.createTempFile("neojoin-", ".nj");

      try {
        Files.write(path, file.content());

        Parser.Result result = parser.parse(URI.createFileURI(path.toString()));

        // 4. Collect NeoJoin validation errors
        for (var issue : result.issues()) {
          if (issue.getSeverity() == Severity.ERROR) {
            problems.add(
                BuildProblem.at(
                    Source.VIEWS,
                    file.fileName(),
                    file.text(),
                    issue.getLineNumber(),
                    issue.getColumn(),
                    issue.getMessage()));
          }
        }

        // Handle a failed parse without diagnostics
        if (result instanceof Parser.Result.Failure && result.issues().isEmpty()) {
          problems.add(
              BuildProblem.of(
                  Source.VIEWS, file.fileName(), "Unable to parse NeoJoin view definition"));
        }

        // 5. Check duplicates across files
        if (result instanceof Parser.Result.Success success) {

          String exportName = success.aqr().export().name();

          String nsUri = success.aqr().export().uri().toString();

          String previousExport = exportOwners.putIfAbsent(exportName, file.fileName());

          if (previousExport != null) {
            problems.add(
                duplicateProblem(
                    file,
                    "Duplicate export name '"
                        + exportName
                        + "' already declared in "
                        + previousExport));
          }

          String previousNsUri = nsUriOwners.putIfAbsent(nsUri, file.fileName());

          if (previousNsUri != null) {
            problems.add(
                duplicateProblem(
                    file,
                    "Duplicate view-type nsURI '"
                        + nsUri
                        + "' already declared in "
                        + previousNsUri));
          }
        }

      } finally {
        Files.deleteIfExists(path);
      }
    }

    // 6. Reject invalid view definitions
    if (!problems.isEmpty()) {
      throw new VsumBuildException(
          VsumErrorCodes.INPUT_ERROR, "Invalid NeoJoin view definitions:", problems);
    }
  }

  private static void register(EPackage ePackage, EPackage.Registry registry) {

    if (ePackage.getNsURI() != null) {
      registry.put(ePackage.getNsURI(), ePackage);
    }

    for (EPackage subpackage : ePackage.getESubpackages()) {
      register(subpackage, registry);
    }
  }

  private static BuildProblem duplicateProblem(UploadedFile file, String message) {

    String text = file.text();
    Matcher matcher = EXPORT_PATTERN.matcher(text);

    if (!matcher.find()) {
      return BuildProblem.of(Source.VIEWS, file.fileName(), message);
    }

    int offset = matcher.start(1);

    return BuildProblem.at(
        Source.VIEWS,
        file.fileName(),
        text,
        Excerpts.lineOf(text, offset),
        Excerpts.columnOf(text, offset),
        message);
  }
}
