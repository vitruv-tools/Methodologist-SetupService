package tools.vitruv.methodologist.setup.vsum.service.views;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.emf.ecore.EcorePackage;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.Excerpts;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;
import tools.vitruv.methodologist.setup.vsum.service.reactions.ReactionsProcessor;

/**
 * Places NeoJoin view definitions into the view type module of the generated project, where NeoJoin
 * for Vitruvius generates a view type from each of them during the build. The generated VSUM
 * registers these view types next to the identity view types of the metamodels.
 *
 * <p>Before the build, the definitions are checked for what would only fail late or confusingly:
 * the export declaration that names the generated classes, names that clash, and imports of
 * metamodels that were not uploaded. Everything else is left to the NeoJoin compiler, whose errors
 * the build attributes to the definitions.
 */
@Service
public class NeoJoinViewDefinitionProcessor implements ViewDefinitionProcessor {

  private static final Pattern FILE_BASE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
  private static final Pattern EXPORT =
      Pattern.compile("\\bexport\\s+\\^?([A-Za-z_][A-Za-z0-9_]*)\\s+to\\s+\"([^\"]*)\"");
  private static final Pattern IMPORT = Pattern.compile("\\bimport\\s+\"([^\"]+)\"");

  @Override
  public List<GeneratedViewType> process(
      List<UploadedFile> viewDefinitions, List<GeneratedMetamodel> metamodels, Path projectRoot)
      throws IOException {
    List<GeneratedViewType> viewTypes = check(viewDefinitions, metamodels);
    if (viewDefinitions.isEmpty()) {
      return viewTypes;
    }
    Path directory = ProjectLayout.viewsDirectory(projectRoot);
    Files.createDirectories(directory);
    for (UploadedFile file : viewDefinitions) {
      Files.write(directory.resolve(file.fileName()), file.content());
    }
    return viewTypes;
  }

  @Override
  public List<GeneratedViewType> check(
      List<UploadedFile> viewDefinitions, List<GeneratedMetamodel> metamodels) {
    Set<String> metamodelNsUris = new LinkedHashSet<>();
    metamodels.forEach(
        metamodel -> {
          metamodelNsUris.add(metamodel.nsUri());
          metamodel.packages().forEach(pkg -> metamodelNsUris.add(pkg.nsUri()));
        });
    Set<String> identityViewTypes = new LinkedHashSet<>();
    metamodels.forEach(metamodel -> identityViewTypes.add(metamodel.name()));

    List<BuildProblem> problems = new ArrayList<>();
    List<GeneratedViewType> viewTypes = new ArrayList<>();
    Map<String, String> nameOwners = new HashMap<>();
    Map<String, String> exportOwners = new HashMap<>();
    Map<String, String> nsUriOwners = new HashMap<>();

    for (UploadedFile file : viewDefinitions) {
      String text = file.text();
      String source = ReactionsProcessor.stripComments(text);
      problems.addAll(checkImports(file, source, metamodelNsUris));

      String baseName = GeneratedViewType.baseName(file.fileName());
      if (!FILE_BASE_NAME.matcher(baseName).matches()) {
        problems.add(
            BuildProblem.of(
                    Source.VIEWS,
                    file.fileName(),
                    "the file name cannot be the name of a view type")
                .withHint(
                    "NeoJoin names the view type after the file; start the file name with a letter"
                        + " and use only letters, digits, '_', and '-', e.g. 'components.nj'"));
        continue;
      }

      Matcher export = EXPORT.matcher(source);
      if (!export.find()) {
        problems.add(
            BuildProblem.of(Source.VIEWS, file.fileName(), "declares no export")
                .withHint(
                    "Start the view definition with 'export <name> to \"<nsURI>\"', which names"
                        + " the package and nsURI of the view's metamodel"));
        continue;
      }
      GeneratedViewType viewType =
          GeneratedViewType.of(
              file.fileName(),
              export.group(1),
              export.group(2),
              ProjectLayout.GENERATED_NEOJOIN_SOURCES);
      int exportOffset = export.start();
      int count = problems.size();

      if (identityViewTypes.contains(viewType.name())) {
        problems.add(
            problem(
                    file,
                    exportOffset,
                    String.format(
                        "the view type '%s' has the same name as the view type of the metamodel"
                            + " '%s'",
                        viewType.name(), viewType.name()))
                .withHint("Rename the file; NeoJoin names the view type after it"));
      }
      String nameOwner = nameOwners.putIfAbsent(viewType.name(), file.fileName());
      if (nameOwner != null) {
        problems.add(
            problem(
                    file,
                    exportOffset,
                    String.format(
                        "the view type '%s' is already defined by %s", viewType.name(), nameOwner))
                .withHint(
                    "View types are named after the file, ignoring case, '_', '-', and spaces;"
                        + " rename one of the files"));
      }
      String exportOwner =
          exportOwners.putIfAbsent(viewType.exportName().toLowerCase(Locale.ROOT), file.fileName());
      if (exportOwner != null) {
        problems.add(
            problem(
                    file,
                    export.start(1),
                    String.format(
                        "the export '%s' is already declared in %s",
                        viewType.exportName(), exportOwner))
                .withHint("Each view definition needs its own export name"));
      }
      if (metamodelNsUris.contains(viewType.nsUri())) {
        problems.add(
            problem(
                    file,
                    export.start(2) - 1,
                    String.format(
                        "exports to '%s', which is the nsURI of an uploaded metamodel",
                        viewType.nsUri()))
                .withHint("Export the view's metamodel to an nsURI of its own"));
      } else {
        String nsUriOwner = nsUriOwners.putIfAbsent(viewType.nsUri(), file.fileName());
        if (nsUriOwner != null) {
          problems.add(
              problem(
                      file,
                      export.start(2) - 1,
                      String.format(
                          "exports to '%s', which %s exports to as well",
                          viewType.nsUri(), nsUriOwner))
                  .withHint("Each view definition needs its own export nsURI"));
        }
      }
      if (problems.size() == count) {
        viewTypes.add(viewType);
      }
    }

    if (!problems.isEmpty()) {
      throw new VsumBuildException(
          VsumErrorCodes.VIEWS_ERROR, "The uploaded view definitions are invalid:", problems);
    }
    return viewTypes;
  }

  private static List<BuildProblem> checkImports(
      UploadedFile file, String source, Set<String> metamodelNsUris) {
    List<BuildProblem> problems = new ArrayList<>();
    Matcher imports = IMPORT.matcher(source);
    while (imports.find()) {
      String nsUri = imports.group(1);
      if (!metamodelNsUris.contains(nsUri) && !EcorePackage.eNS_URI.equals(nsUri)) {
        problems.add(
            problem(
                    file,
                    imports.start(1) - 1,
                    String.format(
                        "imports '%s', which is not the nsURI of an uploaded metamodel", nsUri))
                .withHint(
                    "Import a metamodel by its nsURI; the uploaded metamodels have: "
                        + String.join(", ", metamodelNsUris)));
      }
    }
    return problems;
  }

  private static BuildProblem problem(UploadedFile file, int offset, String message) {
    String text = file.text();
    return BuildProblem.at(
        Source.VIEWS,
        file.fileName(),
        text,
        Excerpts.lineOf(text, offset),
        Excerpts.columnOf(text, offset),
        message);
  }
}
