package tools.vitruv.methodologist.setup.messages;

/**
 * Centralized informational messages for the Methodologist Setup Service.
 */
public final class InfoMessages {

  private InfoMessages() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static final String GENMODEL_INSPECTION_SUCCESS =
      "GenModel inspected successfully, showing planned changes";
  public static final String GENMODEL_PROCESS_SUCCESS =
      "GenModel processed and changes applied successfully";
  public static final String ATTRIBUTES_REMOVED = "Removed attributes: %s";
  public static final String ATTRIBUTES_WOULD_REMOVE = "Would remove attributes: %s";
  public static final String BASE_PACKAGE_SET =
      "Set basePackage for genPackage %s to '%s'.";
  public static final String BASE_PACKAGE_WOULD_SET =
      "Would set basePackage for genPackage %s to '%s'.";
  public static final String BASE_PACKAGE_CHANGED =
      "Changed basePackage for genPackage %s from '%s' to '%s'.";
  public static final String BASE_PACKAGE_WOULD_CHANGE =
      "Would change basePackage for genPackage %s from '%s' to '%s'.";
  public static final String MODEL_DIRECTORY_SET =
      "Set modelDirectory to '%s'.";
  public static final String MODEL_DIRECTORY_WOULD_SET =
      "Would set modelDirectory to '%s'.";
  public static final String MODEL_DIRECTORY_CHANGED =
      "Changed modelDirectory from '%s' to '%s'.";
  public static final String MODEL_DIRECTORY_WOULD_CHANGE =
      "Would change modelDirectory from '%s' to '%s'.";
  public static final String CREATION_ICONS_SET = "Set creationIcons=false.";
  public static final String CREATION_ICONS_WOULD_SET = "Would set creationIcons=false.";
  public static final String FOREIGN_MODEL_ADDED = "Added missing foreignModel entry: %s";
  public static final String FOREIGN_MODEL_WOULD_ADD =
      "Would add missing foreignModel entry: %s";
  public static final String UNNAMED_PACKAGE = "<unnamed GenPackage>";
}

