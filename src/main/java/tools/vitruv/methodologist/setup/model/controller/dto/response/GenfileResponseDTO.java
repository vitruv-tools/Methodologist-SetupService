package tools.vitruv.methodologist.setup.model.controller.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Data transfer object for generated genfile response. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenfileResponseDTO {
  private String filename;
  private String modelName;
  private String nsURI;
  private String basePackage;
  private String modelDirectory;
}
