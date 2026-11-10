package tools.vitruv.methodologist.setup.vsum.service;

import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.MetamodelProcessor;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;
import tools.vitruv.methodologist.setup.vsum.service.reactions.ReactionsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.views.NeoJoinChecker;

/**
 * Checks a VSUM project without building it, so that problems can be shown while it is edited.
 *
 * <p>Runs the checks {@link VsumProjectBuildService} runs before Maven, through the same code, but
 * writes no project and does not run Maven. Validations do not use the build slots ({@code
 * setup.vsum.max-concurrent-builds}), so they never wait behind running builds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VsumValidationService {

  private final MetamodelProcessor metamodelProcessor;
  private final ReactionsProcessor reactionsProcessor;
  private final NeoJoinChecker neoJoinChecker;

  /**
   * Checks the project. Like the build, the checks stop at the first stage with problems: reactions
   * and view definitions are only checked once the metamodels are valid, and view definitions once
   * the reactions are.
   *
   * @param request the project, as for a build
   * @return the problems the build would report before running Maven
   */
  public VsumValidationResult validate(VsumBuildRequest request) {
    try {
      List<GeneratedMetamodel> metamodels = metamodelProcessor.check(request.metamodels());
      reactionsProcessor.check(request.reactions(), metamodels);
      neoJoinChecker.check(request.viewDefinitions(), metamodels);
      return VsumValidationResult.of(List.of());
    } catch (VsumBuildException e) {
      log.debug("VSUM validation found problems: {}", e.getMessage());
      return VsumValidationResult.of(e.getProblems());
    } catch (IOException e) {
      // the views are checked from temporary files; failing to write them is not a problem of the
      // uploads
      throw new MethodologistSetupException(
          VsumErrorCodes.BUILD_ERROR,
          "Failed to validate the VSUM project: " + e.getMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR,
          e);
    }
  }
}
