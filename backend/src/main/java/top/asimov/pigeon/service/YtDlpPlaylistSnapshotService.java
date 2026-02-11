package top.asimov.pigeon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.constant.Youtube;
import top.asimov.pigeon.model.dto.PlaylistSnapshotEntry;

@Log4j2
@Service
public class YtDlpPlaylistSnapshotService {

  private static final DateTimeFormatter UPLOAD_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

  private final ObjectMapper objectMapper;
  private final YtDlpRuntimeService ytDlpRuntimeService;

  @Value("${pigeon.yt-dlp.snapshot-timeout-seconds:180}")
  private int snapshotTimeoutSeconds;

  public YtDlpPlaylistSnapshotService(ObjectMapper objectMapper,
      YtDlpRuntimeService ytDlpRuntimeService) {
    this.objectMapper = objectMapper;
    this.ytDlpRuntimeService = ytDlpRuntimeService;
  }

  public List<PlaylistSnapshotEntry> fetchPlaylistSnapshot(String playlistId) {
    if (!StringUtils.hasText(playlistId)) {
      return List.of();
    }

    String playlistUrl = Youtube.PLAYLIST_URL + playlistId;
    YtDlpRuntimeService.YtDlpResolvedRuntime resolvedRuntime =
        ytDlpRuntimeService.resolveExecutionRuntime();
    YtDlpRuntimeService.YtDlpExecutionContext executionContext = resolvedRuntime.executionContext();

    List<String> command = new ArrayList<>(executionContext.command());
    command.add("--flat-playlist");
    command.add("-J");
    command.add("--quiet");
    command.add("--no-warnings");
    command.add("--ignore-errors");
    command.add("--extractor-args");
    command.add("youtubetab:approximate_date");
    command.add("--remote-components");
    command.add("ejs:npm");
    command.add(playlistUrl);

    log.info(
        "[yt-dlp snapshot] start playlistId={}, runtimeMode={}, runtimeVersion={}, command={}",
        playlistId, resolvedRuntime.mode(), resolvedRuntime.version(), String.join(" ", command));

    long startedAt = System.currentTimeMillis();
    Path outputLog = null;
    try {
      outputLog = Files.createTempFile(".yt-dlp-playlist-snapshot-", ".log");

      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.redirectErrorStream(true);
      processBuilder.redirectOutput(outputLog.toFile());
      processBuilder.environment().putAll(executionContext.environment());

      Process process = processBuilder.start();
      boolean finished = process.waitFor(Math.max(1, snapshotTimeoutSeconds), TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new BusinessException("yt-dlp playlist snapshot timeout");
      }

      String output = Files.readString(outputLog, StandardCharsets.UTF_8);
      if (process.exitValue() != 0) {
        throw new BusinessException("yt-dlp playlist snapshot failed: " + abbreviate(output));
      }

      List<PlaylistSnapshotEntry> snapshotEntries = parseSnapshotEntries(output);
      log.info("[yt-dlp snapshot] done playlistId={}, entries={}, elapsedMs={}",
          playlistId, snapshotEntries.size(), System.currentTimeMillis() - startedAt);
      return snapshotEntries;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException("yt-dlp playlist snapshot interrupted");
    } catch (IOException e) {
      throw new BusinessException("yt-dlp playlist snapshot failed: " + e.getMessage());
    } finally {
      if (outputLog != null) {
        try {
          Files.deleteIfExists(outputLog);
        } catch (IOException ignored) {
          // no-op
        }
      }
    }
  }

  private List<PlaylistSnapshotEntry> parseSnapshotEntries(String output) throws IOException {
    if (!StringUtils.hasText(output)) {
      return List.of();
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(output);
    } catch (Exception e) {
      int start = output.indexOf('{');
      int end = output.lastIndexOf('}');
      if (start < 0 || end <= start) {
        throw e;
      }
      root = objectMapper.readTree(output.substring(start, end + 1));
    }

    JsonNode entries = root.path("entries");
    if (!entries.isArray() || entries.isEmpty()) {
      return List.of();
    }

    List<PlaylistSnapshotEntry> result = new ArrayList<>();
    int index = 0;
    for (JsonNode entry : entries) {
      String videoId = resolveVideoId(entry);
      if (!StringUtils.hasText(videoId)) {
        index++;
        continue;
      }

      Long position = resolvePosition(entry, index);
      String title = normalizeText(entry.path("title").asText(null));
      LocalDateTime approximatePublishedAt = resolveApproximatePublishedAt(entry);
      result.add(new PlaylistSnapshotEntry(videoId, position, title, approximatePublishedAt));
      index++;
    }

    result.sort(Comparator.comparingLong(item -> item.position() == null ? Long.MAX_VALUE : item.position()));
    return result;
  }

  private Long resolvePosition(JsonNode entry, int fallbackIndex) {
    JsonNode playlistIndex = entry.get("playlist_index");
    if (playlistIndex != null && playlistIndex.canConvertToLong()) {
      long value = playlistIndex.asLong();
      if (value > 0) {
        return value - 1;
      }
      return value;
    }
    return (long) fallbackIndex;
  }

  private LocalDateTime resolveApproximatePublishedAt(JsonNode entry) {
    JsonNode timestamp = entry.get("timestamp");
    if (timestamp != null && timestamp.canConvertToLong()) {
      long seconds = timestamp.asLong();
      if (seconds > 0) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault());
      }
    }

    String uploadDate = normalizeText(entry.path("upload_date").asText(null));
    if (StringUtils.hasText(uploadDate) && uploadDate.length() == 8) {
      try {
        LocalDate date = LocalDate.parse(uploadDate, UPLOAD_DATE_FORMAT);
        return date.atStartOfDay();
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  private String resolveVideoId(JsonNode entry) {
    String id = normalizeText(entry.path("id").asText(null));
    if (StringUtils.hasText(id)) {
      return id;
    }
    String url = normalizeText(entry.path("url").asText(null));
    return extractVideoIdFromUrl(url).orElse(null);
  }

  private Optional<String> extractVideoIdFromUrl(String url) {
    if (!StringUtils.hasText(url)) {
      return Optional.empty();
    }
    String normalized = url.trim();

    if (!normalized.contains("/") && !normalized.contains("?") && !normalized.contains("=")) {
      return Optional.of(normalized);
    }

    int watchIndex = normalized.indexOf("v=");
    if (watchIndex >= 0) {
      String candidate = normalized.substring(watchIndex + 2);
      int ampIndex = candidate.indexOf('&');
      if (ampIndex >= 0) {
        candidate = candidate.substring(0, ampIndex);
      }
      if (StringUtils.hasText(candidate)) {
        return Optional.of(candidate);
      }
    }

    String marker = "youtu.be/";
    int shortIndex = normalized.indexOf(marker);
    if (shortIndex >= 0) {
      String candidate = normalized.substring(shortIndex + marker.length());
      int slashIndex = candidate.indexOf('/');
      if (slashIndex >= 0) {
        candidate = candidate.substring(0, slashIndex);
      }
      int queryIndex = candidate.indexOf('?');
      if (queryIndex >= 0) {
        candidate = candidate.substring(0, queryIndex);
      }
      if (StringUtils.hasText(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  private String normalizeText(String text) {
    if (!StringUtils.hasText(text)) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String abbreviate(String text) {
    if (!StringUtils.hasText(text)) {
      return "";
    }
    String trimmed = text.trim();
    if (trimmed.length() <= 500) {
      return trimmed;
    }
    return trimmed.substring(trimmed.length() - 500);
  }
}
