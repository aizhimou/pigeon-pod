package top.asimov.pigeon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.model.enums.DownloadType;

@Log4j2
@Service
public class MediaIntegrityValidator {

  private static final int MAX_OUTPUT_LENGTH = 500;

  @Value("${pigeon.ffmpeg-location:}")
  private String ffmpegLocation;

  private final ObjectMapper objectMapper;

  public MediaIntegrityValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ValidationResult validate(Path mediaFilePath, DownloadType downloadType) {
    if (mediaFilePath == null) {
      return ValidationResult.failure("media validation failed: media file path is missing");
    }
    if (downloadType == null) {
      return ValidationResult.failure("media validation failed: download type is missing");
    }
    if (!Files.exists(mediaFilePath) || !Files.isRegularFile(mediaFilePath)) {
      return ValidationResult.failure("media validation failed: media file does not exist");
    }

    try {
      long fileSize = Files.size(mediaFilePath);
      if (fileSize <= 0) {
        return ValidationResult.failure("media validation failed: media file is empty");
      }
    } catch (IOException e) {
      return ValidationResult.failure("media validation failed: unable to read media file size");
    }

    CommandResult probeResult;
    try {
      probeResult = runFfprobe(mediaFilePath);
    } catch (IOException e) {
      log.warn("Failed to execute ffprobe for {}", mediaFilePath, e);
      return ValidationResult.failure("media validation failed: unable to execute ffprobe");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ValidationResult.failure("media validation failed: ffprobe was interrupted");
    }

    if (probeResult.exitCode() != 0) {
      return ValidationResult.failure("media validation failed: ffprobe exited with code "
          + probeResult.exitCode() + formatCommandDetails(probeResult.stderr()));
    }

    try {
      JsonNode root = objectMapper.readTree(probeResult.stdout());
      JsonNode formatNode = root.path("format");
      if (formatNode.isMissingNode() || formatNode.isNull()
          || !StringUtils.hasText(formatNode.path("format_name").asText(null))) {
        return ValidationResult.failure(
            "media validation failed: ffprobe could not identify the media container");
      }

      StreamSummary streamSummary = readStreamSummary(root.path("streams"));
      if (downloadType == DownloadType.AUDIO && !streamSummary.hasAudio()) {
        return ValidationResult.failure(
            "media validation failed: audio download has no audio stream");
      }
      if (downloadType == DownloadType.VIDEO && !streamSummary.hasVideo()) {
        return ValidationResult.failure(
            "media validation failed: video download has no video stream");
      }

      Double durationSeconds = parsePositiveDuration(formatNode.path("duration"));
      if (durationSeconds != null && durationSeconds <= 0D) {
        return ValidationResult.failure(
            "media validation failed: media duration is not positive");
      }
      return ValidationResult.success();
    } catch (Exception e) {
      log.warn("Failed to parse ffprobe output for {}", mediaFilePath, e);
      return ValidationResult.failure("media validation failed: unable to parse ffprobe output");
    }
  }

  private CommandResult runFfprobe(Path mediaFilePath) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add(resolveFfprobeBinary());
    command.add("-v");
    command.add("error");
    command.add("-show_format");
    command.add("-show_streams");
    command.add("-of");
    command.add("json");
    command.add(mediaFilePath.toAbsolutePath().toString());

    Process process = new ProcessBuilder(command).start();
    int exitCode = process.waitFor();
    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return new CommandResult(exitCode, stdout, stderr);
  }

  private String resolveFfprobeBinary() {
    if (!StringUtils.hasText(ffmpegLocation)) {
      return "ffprobe";
    }

    Path configuredPath = Path.of(ffmpegLocation.trim());
    if (Files.isDirectory(configuredPath)) {
      return configuredPath.resolve(resolveBinaryName("ffprobe")).toString();
    }

    String configuredName = configuredPath.getFileName() == null
        ? configuredPath.toString()
        : configuredPath.getFileName().toString();
    String normalizedName = configuredName.toLowerCase(Locale.ROOT);

    if (normalizedName.startsWith("ffprobe")) {
      return configuredPath.toString();
    }
    if (!normalizedName.startsWith("ffmpeg")) {
      return configuredPath.toString();
    }

    String suffix = "";
    int extensionIndex = configuredName.lastIndexOf('.');
    if (extensionIndex >= 0) {
      suffix = configuredName.substring(extensionIndex);
    }

    Path parent = configuredPath.getParent();
    String probeName = "ffprobe" + suffix;
    return parent == null ? probeName : parent.resolve(probeName).toString();
  }

  private String resolveBinaryName(String baseName) {
    return isWindowsPathConfigured() ? baseName + ".exe" : baseName;
  }

  private boolean isWindowsPathConfigured() {
    return StringUtils.hasText(ffmpegLocation)
        && ffmpegLocation.trim().toLowerCase(Locale.ROOT).endsWith(".exe");
  }

  private StreamSummary readStreamSummary(JsonNode streamsNode) {
    boolean hasAudio = false;
    boolean hasVideo = false;
    if (streamsNode == null || !streamsNode.isArray()) {
      return new StreamSummary(false, false);
    }

    for (JsonNode streamNode : streamsNode) {
      String codecType = streamNode.path("codec_type").asText("");
      if ("audio".equalsIgnoreCase(codecType)) {
        hasAudio = true;
      } else if ("video".equalsIgnoreCase(codecType)) {
        hasVideo = true;
      }
    }
    return new StreamSummary(hasAudio, hasVideo);
  }

  private Double parsePositiveDuration(JsonNode durationNode) {
    if (durationNode == null || durationNode.isNull()) {
      return null;
    }
    String rawValue = durationNode.asText(null);
    if (!StringUtils.hasText(rawValue) || "N/A".equalsIgnoreCase(rawValue)) {
      return null;
    }
    try {
      return Double.parseDouble(rawValue);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private String formatCommandDetails(String stderr) {
    if (!StringUtils.hasText(stderr)) {
      return "";
    }
    String normalized = stderr.replaceAll("\\s+", " ").trim();
    if (normalized.isEmpty()) {
      return "";
    }
    if (normalized.length() > MAX_OUTPUT_LENGTH) {
      normalized = normalized.substring(0, MAX_OUTPUT_LENGTH) + "...";
    }
    return ": " + normalized;
  }

  public record ValidationResult(boolean valid, String message) {

    public static ValidationResult success() {
      return new ValidationResult(true, null);
    }

    public static ValidationResult failure(String message) {
      return new ValidationResult(false, message);
    }
  }

  private record CommandResult(int exitCode, String stdout, String stderr) {
  }

  private record StreamSummary(boolean hasAudio, boolean hasVideo) {
  }
}
