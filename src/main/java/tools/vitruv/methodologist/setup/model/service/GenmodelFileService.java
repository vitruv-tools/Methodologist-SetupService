package tools.vitruv.methodologist.setup.model.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.setup.exception.GenmodelException;
import tools.vitruv.methodologist.setup.messages.ErrorMessages;

/**
 * Service for handling GenModel file operations including upload, processing, and download.
 */
@Slf4j
@Service
public class GenmodelFileService {

  /**
   * Saves an uploaded multipart file to a temporary file.
   *
   * @param file the multipart file to save
   * @return the created temporary file
   * @throws GenmodelException if file is empty or cannot be saved
   */
  public File saveTempFile(MultipartFile file) {
    if (file.isEmpty()) {
      log.error("Uploaded file is empty");
      throw new GenmodelException("EMPTY_FILE", ErrorMessages.MULTIPART_FILE_EMPTY_ERROR);
    }

    try {
      File tempFile = File.createTempFile("genmodel_", ".genmodel");
      log.debug("Saving uploaded file to temp location: {}", tempFile.getAbsolutePath());
      file.transferTo(tempFile);
      return tempFile;
    } catch (IOException e) {
      log.error("Failed to save uploaded file", e);
      throw new GenmodelException(
          "FILE_UPLOAD_ERROR", ErrorMessages.MULTIPART_FILE_UPLOAD_ERROR, e);
    }
  }

  /**
   * Deletes a temporary file.
   *
   * @param file the file to delete
   */
  public void deleteTempFile(File file) {
    if (file != null && file.exists()) {
      if (file.delete()) {
        log.debug("Temp file deleted: {}", file.getAbsolutePath());
      } else {
        log.warn("Failed to delete temp file: {}", file.getAbsolutePath());
      }
    }
  }

  /**
   * Converts a multipart file to byte array.
   *
   * @param file the multipart file to convert
   * @return the file content as byte array
   * @throws GenmodelException if file is empty or cannot be read
   */
  public byte[] multipartToBytes(MultipartFile file) {
    if (file.isEmpty()) {
      log.error("Uploaded file is empty");
      throw new GenmodelException("EMPTY_FILE", ErrorMessages.MULTIPART_FILE_EMPTY_ERROR);
    }

    try {
      log.debug("Converting multipart file to bytes: {}", file.getOriginalFilename());
      return file.getBytes();
    } catch (IOException e) {
      log.error("Failed to read multipart file bytes", e);
      throw new GenmodelException(
          "FILE_READ_ERROR", ErrorMessages.MULTIPART_FILE_UPLOAD_ERROR, e);
    }
  }

  /**
   * Reads bytes from a file.
   *
   * @param file the file to read
   * @return the file content as byte array
   * @throws GenmodelException if file cannot be read
   */
  public byte[] readFileBytes(File file) {
    try {
      log.debug("Reading file bytes: {}", file.getAbsolutePath());
      return Files.readAllBytes(file.toPath());
    } catch (IOException e) {
      log.error("Failed to read file bytes: {}", file.getAbsolutePath(), e);
      throw new GenmodelException(
          "FILE_READ_ERROR",
          String.format(ErrorMessages.GENMODEL_FILE_READ_ERROR, file.getAbsolutePath()),
          e);
    }
  }

  /**
   * Writes byte array content to a file.
   *
   * @param file the file to write to
   * @param content the byte array content
   * @throws GenmodelException if file cannot be written
   */
  public void writeFileBytes(File file, byte[] content) {
    try {
      log.debug("Writing bytes to file: {}", file.getAbsolutePath());
      Files.write(file.toPath(), content);
    } catch (IOException e) {
      log.error("Failed to write file bytes: {}", file.getAbsolutePath(), e);
      throw new GenmodelException(
          "FILE_WRITE_ERROR",
          String.format(ErrorMessages.GENMODEL_FILE_READ_ERROR, file.getAbsolutePath()),
          e);
    }
  }

  /**
   * Converts file bytes to string with UTF-8 encoding.
   *
   * @param bytes the byte array to convert
   * @return the string representation
   */
  public String bytesToString(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /**
   * Converts string to bytes with UTF-8 encoding.
   *
   * @param content the string to convert
   * @return the byte array
   */
  public byte[] stringToBytes(String content) {
    return content.getBytes(StandardCharsets.UTF_8);
  }
}

