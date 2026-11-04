package tools.vitruv.methodologist.setup.vsum.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.MetamodelProcessor;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;
import tools.vitruv.methodologist.setup.vsum.service.reactions.ReactionsProcessor;

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

  /**
   * Checks the project. Like the build, the checks stop at the first stage with problems: reactions
   * are only checked once the metamodels are valid.
   *
   * @param request the project, as for a build
   * @return the problems the build would report before running Maven
   */
  public VsumValidationResult validate(VsumBuildRequest request) {
    try {
      List<GeneratedMetamodel> metamodels = metamodelProcessor.check(request.metamodels());
      reactionsProcessor.check(request.reactions(), metamodels);
      return VsumValidationResult.of(List.of());
    } catch (VsumBuildException e) {
      log.debug("VSUM validation found problems: {}", e.getMessage());
      return VsumValidationResult.of(e.getProblems());
    }
  }
}
