package tools.vitruv.methodologist.setup.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
