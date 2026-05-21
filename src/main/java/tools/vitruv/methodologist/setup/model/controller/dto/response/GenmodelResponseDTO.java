package tools.vitruv.methodologist.setup.model.controller.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.vitruv.methodologist.setup.model.controller.dto.request.GenmodelIssueDTO;

/**
 * Data transfer object for GenModel validation response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenmodelResponseDTO {
  private String status;
  private String message;
  private List<GenmodelIssueDTO> issues;
}

