package tools.vitruv.methodologist.setup.messages;

/** Centralized error messages for the Methodologist Setup Service. */
public final class ErrorMessages {

  public static final String GENMODEL_FILE_NOT_FOUND = "GenModel file not found: %s";
  public static final String GENMODEL_FILE_READ_ERROR = "Could not read genmodel file: %s";
  public static final String GENMODEL_FILE_WRITE_ERROR = "Could not write genmodel file: %s";
  public static final String GENMODEL_INVALID_FORMAT = "Not a valid GenModel: %s";
  public static final String GENMODEL_MISSING_PLUGIN_ID =
      "GenModel has missing/blank modelPluginID: %s";
  public static final String GENMODEL_ATTRIBUTE_STRIP_ERROR =
      "Could not strip attributes from genmodel XML: %s";
  public static final String GENMODEL_LOAD_ERROR = "Could not load genmodel file: %s";
  public static final String GENMODEL_SAVE_ERROR = "Could not save genmodel file: %s";
  public static final String FILE_NULL_ERROR = "File must not be null";
  public static final String MULTIPART_FILE_EMPTY_ERROR = "Uploaded file is empty";
  public static final String MULTIPART_FILE_UPLOAD_ERROR = "Failed to save uploaded file";
  public static final String XML_STREAM_ERROR = "Failed to process XML stream";
  public static final String UNEXPECTED_ERROR = "An unexpected error occurred";

  private ErrorMessages() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }
}
