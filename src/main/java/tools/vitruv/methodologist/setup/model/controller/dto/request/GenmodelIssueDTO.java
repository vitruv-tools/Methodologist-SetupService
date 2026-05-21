package tools.vitruv.methodologist.setup.model.controller.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Data transfer object representing a GenModel validation issue. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenmodelIssueDTO {
  private String filename;
  private String message;
}
