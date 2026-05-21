package tools.vitruv.methodologist.setup;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Generic response template DTO for all API responses.
 *
 * @param <T> the type of data in the response
 */
@Setter
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseTemplateDto<T> {
  private T data;
  private String message;
  private String errorCode;
  private Long timestamp;

  /**
   * Constructs a ResponseTemplateDto without timestamp.
   *
   * @param data the response data
   * @param message the response message
   */
  public ResponseTemplateDto(T data, String message) {
    this.data = data;
    this.message = message;
    this.timestamp = System.currentTimeMillis();
  }
}

