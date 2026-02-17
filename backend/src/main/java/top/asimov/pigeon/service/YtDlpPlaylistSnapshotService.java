package top.asimov.pigeon.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
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
    command.add("--compat-options");
    command.add("no-youtube-unavailable-videos");
    command.add("--extractor-args");
    command.add("youtubetab:approximate_date");
    command.add("--remote-components");
    command.add("ejs:npm");
    command.add(playlistUrl);

    log.info(
        "[yt-dlp snapshot] start playlistId={}, runtimeMode={}, runtimeVersion={}, filterUnavailable=true, command={}",
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

      if (process.exitValue() != 0) {
        throw new BusinessException(
            "yt-dlp playlist snapshot failed: " + readOutputTail(outputLog, 2000));
      }

      List<PlaylistSnapshotEntry> snapshotEntries = parseSnapshotEntries(outputLog);
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

  private List<PlaylistSnapshotEntry> parseSnapshotEntries(Path outputLog) throws IOException {
    if (outputLog == null || !Files.exists(outputLog)) {
      return List.of();
    }

    List<PlaylistSnapshotEntry> result = new ArrayList<>();
    try (InputStream inputStream = Files.newInputStream(outputLog);
        JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
      JsonToken token = parser.nextToken();
      while (token != null && token != JsonToken.START_OBJECT) {
        token = parser.nextToken();
      }
      if (token == null) {
        return List.of();
      }

      int index = 0;
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        String fieldName = parser.currentName();
        if (fieldName == null) {
          parser.skipChildren();
          continue;
        }
        JsonToken valueToken = parser.nextToken();
        if (!"entries".equals(fieldName) || valueToken != JsonToken.START_ARRAY) {
          parser.skipChildren();
          continue;
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
          JsonToken entryToken = parser.currentToken();
          if (entryToken == JsonToken.START_OBJECT) {
            PlaylistSnapshotEntry snapshotEntry = parseSnapshotEntry(parser, index);
            if (snapshotEntry != null) {
              result.add(snapshotEntry);
            }
          } else {
            parser.skipChildren();
          }
          index++;
        }
      }
    }
    result.sort(Comparator.comparingLong(item -> item.position() == null ? Long.MAX_VALUE : item.position()));
    return result;
  }

  private PlaylistSnapshotEntry parseSnapshotEntry(JsonParser parser, int fallbackIndex)
      throws IOException {
    String id = null;
    String url = null;
    String title = null;
    String sourceChannelId = null;
    String sourceChannelName = null;
    String sourceChannelUrl = null;
    Long playlistIndex = null;
    Long timestamp = null;
    String uploadDate = null;

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      String fieldName = parser.currentName();
      if (fieldName == null) {
        parser.skipChildren();
        continue;
      }
      JsonToken valueToken = parser.nextToken();
      switch (fieldName) {
        case "id" -> id = normalizeText(parser.getValueAsString());
        case "url" -> url = normalizeText(parser.getValueAsString());
        case "title" -> title = normalizeText(parser.getValueAsString());
        case "channel_id" -> sourceChannelId = normalizeText(parser.getValueAsString());
        case "channel" -> sourceChannelName = normalizeText(parser.getValueAsString());
        case "channel_url" -> sourceChannelUrl = normalizeText(parser.getValueAsString());
        case "uploader" -> {
          if (!StringUtils.hasText(sourceChannelName)) {
            sourceChannelName = normalizeText(parser.getValueAsString());
          }
        }
        case "uploader_url" -> {
          if (!StringUtils.hasText(sourceChannelUrl)) {
            sourceChannelUrl = normalizeText(parser.getValueAsString());
          }
        }
        case "playlist_index" -> playlistIndex = parseLongToken(parser, valueToken);
        case "timestamp" -> timestamp = parseLongToken(parser, valueToken);
        case "upload_date" -> uploadDate = normalizeText(parser.getValueAsString());
        default -> parser.skipChildren();
      }
    }

    String videoId = resolveVideoId(id, url);
    if (!StringUtils.hasText(videoId)) {
      return null;
    }
    Long position = resolvePosition(playlistIndex, fallbackIndex);
    LocalDateTime approximatePublishedAt = resolveApproximatePublishedAt(timestamp, uploadDate);
    return new PlaylistSnapshotEntry(videoId, position, title, approximatePublishedAt,
        sourceChannelId, sourceChannelName, sourceChannelUrl);
  }

  private Long parseLongToken(JsonParser parser, JsonToken token) throws IOException {
    if (token == null) {
      return null;
    }
    if (token.isNumeric()) {
      return parser.getLongValue();
    }
    String raw = normalizeText(parser.getValueAsString());
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Long resolvePosition(Long playlistIndex, int fallbackIndex) {
    if (playlistIndex != null) {
      long value = playlistIndex;
      if (value > 0) {
        return value - 1;
      }
      return value;
    }
    return (long) fallbackIndex;
  }

  private LocalDateTime resolveApproximatePublishedAt(Long timestamp, String uploadDate) {
    if (timestamp != null) {
      long seconds = timestamp;
      if (seconds > 0) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault());
      }
    }

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

  private String resolveVideoId(String id, String url) {
    if (StringUtils.hasText(id)) {
      return id;
    }
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

  private String readOutputTail(Path outputLog, int maxChars) {
    if (outputLog == null || !Files.exists(outputLog) || maxChars <= 0) {
      return "";
    }
    try {
      long fileSize = Files.size(outputLog);
      int maxBytes = Math.max(1024, maxChars * 4);
      long start = Math.max(0, fileSize - maxBytes);
      byte[] bytes;
      try (InputStream inputStream = Files.newInputStream(outputLog)) {
        skipFully(inputStream, start);
        bytes = inputStream.readNBytes(maxBytes);
      }
      return abbreviate(new String(bytes, StandardCharsets.UTF_8));
    } catch (IOException e) {
      return "unable to read yt-dlp output tail: " + e.getMessage();
    }
  }

  private void skipFully(InputStream inputStream, long bytes) throws IOException {
    long remaining = bytes;
    while (remaining > 0) {
      long skipped = inputStream.skip(remaining);
      if (skipped > 0) {
        remaining -= skipped;
        continue;
      }
      if (inputStream.read() == -1) {
        break;
      }
      remaining--;
    }
  }
}
