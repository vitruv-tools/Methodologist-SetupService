package tools.vitruv.methodologist.setup.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;

/** Error response DTO for API error responses. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDTO {
  private String errorCode;
  private String message;
  private String path;
  private Integer status;
  private Long timestamp;

  /** The problems of a failed VSUM build, root causes first; absent for other errors. */
  private List<BuildProblem> problems;
}
