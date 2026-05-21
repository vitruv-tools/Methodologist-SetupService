package tools.vitruv.methodologist.setup.log;

import static net.logstash.logback.marker.Markers.append;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Servlet filter that logs HTTP request and response details, including bodies, for auditing and
 * debugging.
 *
 * <p>Sensitive fields and paths are masked to avoid leaking confidential information. Supports JSON
 * and non-JSON payloads, with recursive masking for JSON. Skips logging for multipart requests and
 * certain sensitive endpoints.
 */
@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {
  private static final String STATUS = "status";

  private static final Set<String> FILE_BODYLESS_PATH_PREFIXES =
      Set.of("/api/files", "/api/upload");

  private static final Set<String> BINARY_BODYLESS_PATH_PREFIXES = Set.of("/api/v1/vsums");

  private static final Set<String> BINARY_CONTENT_TYPES =
      Set.of(
          "application/octet-stream",
          "application/java-archive",
          "application/zip",
          "application/x-zip-compressed");

  private static final String MASK = "***";
  private final ObjectMapper mapper = new ObjectMapper();

  private static boolean isMultipart(String contentType) {
    return contentType != null && contentType.toLowerCase().contains("multipart/");
  }

  private static boolean isJson(String contentType) {
    if (contentType == null) {
      return false;
    }
    String ct = contentType.toLowerCase();
    return ct.contains(MediaType.APPLICATION_JSON_VALUE) || ct.matches(".*\\+json(;.*)?$");
  }

  private static boolean isBinaryContentType(String contentType) {
    if (contentType == null) {
      return false;
    }
    String ct = contentType.toLowerCase();
    return BINARY_CONTENT_TYPES.stream().anyMatch(ct::contains);
  }

  private static String contentTypeOrEmpty(String ct) {
    return ct == null ? "" : ct;
  }

  private static Charset charsetOrDefault(String enc) {
    try {
      return enc == null ? StandardCharsets.UTF_8 : Charset.forName(enc);
    } catch (Exception e) {
      return StandardCharsets.UTF_8;
    }
  }

  private static String safeString(byte[] bytes, Charset charset) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    return new String(bytes, charset);
  }

  private boolean isFileEndpoint(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return FILE_BODYLESS_PATH_PREFIXES.stream().anyMatch(uri::startsWith);
  }

  private boolean isArtifactEndpoint(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (uri == null) {
      return false;
    }
    return uri.matches("^/api/v1/vsums/\\d+/build/(artifact|check)$");
  }

  private boolean isBinaryEndpoint(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (uri == null) {
      return false;
    }
    return isArtifactEndpoint(request)
        || BINARY_BODYLESS_PATH_PREFIXES.stream().anyMatch(uri::startsWith);
  }

  /**
   * Filters each HTTP request/response, logs details, and masks sensitive data.
   *
   * @param request the incoming HTTP request
   * @param response the outgoing HTTP response
   * @param filterChain the filter chain to continue processing
   * @throws jakarta.servlet.ServletException if a servlet error occurs
   * @throws java.io.IOException if an I/O error occurs
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    long startTime = System.currentTimeMillis();

    MDC.put("requestId", UUID.randomUUID().toString());
    MDC.put("api", request.getRequestURI());
    MDC.put("method", request.getMethod());
    MDC.put("ip", request.getRemoteHost());
    MDC.put("type", "SERVED_API");

    ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 100000);
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

    try {
      filterChain.doFilter(requestWrapper, responseWrapper);
    } finally {
      LinkedHashMap<String, Object> logEntry = new LinkedHashMap<>();

      boolean skipBodyLogging =
          request.getRequestURI().contains("swagger")
              || request.getRequestURI().contains("actuator")
              || isMultipart(request.getContentType())
              || isFileEndpoint(request)
              || isBinaryEndpoint(request)
              || isBinaryContentType(responseWrapper.getContentType());

      logEntry.put("request", "");
      logEntry.put("response", "");
      logEntry.put("response_content_type", responseWrapper.getContentType());
      String cl = responseWrapper.getHeader("Content-Length");
      if (cl != null) {
        logEntry.put("response_content_length", cl);
      }

      responseWrapper.copyBodyToResponse();
      long durationMs = System.currentTimeMillis() - startTime;

      if (responseWrapper.getStatus() == 200 || responseWrapper.getStatus() == 201) {
        logger.info(
            append(STATUS, responseWrapper.getStatus())
                .and(append("duration_in_ms", durationMs).and(append("detail", logEntry))));
      } else {
        logger.error(
            append(STATUS, responseWrapper.getStatus())
                .and(append("duration_in_ms", durationMs).and(append("detail", logEntry))));
      }

      MDC.clear();
    }
  }

  /**
   * Attempts to parse the body as JSON if indicated, otherwise returns the raw body.
   *
   * @param body the body string to parse
   * @param shouldParse whether to attempt JSON parsing
   * @return parsed JsonNode or raw string if parsing fails
   */
  private Object tryParseJson(String body, boolean shouldParse) {
    if (!shouldParse) {
      return body;
    }
    try {
      return mapper.readTree(body);
    } catch (Exception e) {
      return body;
    }
  }
}
