package top.asimov.pigeon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.response.YtDlpRuntimeInfoResponse;
import top.asimov.pigeon.model.response.YtDlpUpdateStatusResponse;

@Log4j2
@Service
public class YtDlpRuntimeService {

  private static final Set<String> SUPPORTED_CHANNELS = Set.of("stable", "nightly");
  private static final DateTimeFormatter VERSION_DIR_FORMATTER = DateTimeFormatter.ofPattern(
      "yyyyMMddHHmmss");
  private static final int VERSION_CHECK_TIMEOUT_SECONDS = 20;
  private static final int LOG_TAIL_MAX_LENGTH = 8000;

  private final ObjectMapper objectMapper;
  private final Executor taskExecutor;
  private final AtomicBoolean updateRunning = new AtomicBoolean(false);

  @Value("${pigeon.yt-dlp.managed-root:/data/tools/yt-dlp}")
  private String managedRoot;

  @Value("${pigeon.yt-dlp.keep-versions:3}")
  private Integer keepVersions;

  @Value("${pigeon.yt-dlp.update-timeout-seconds:300}")
  private Long updateTimeoutSeconds;

  private volatile YtDlpUpdateStatusResponse status;

  public YtDlpRuntimeService(ObjectMapper objectMapper,
      @Qualifier("channelSyncTaskExecutor") Executor taskExecutor) {
    this.objectMapper = objectMapper;
    this.taskExecutor = taskExecutor;
    this.status = defaultStatus();
  }

  @PostConstruct
  private void init() {
    if (!ensureManagedDirectories()) {
      return;
    }
    loadPersistedStatus();
    if ("RUNNING".equalsIgnoreCase(status.getState())) {
      YtDlpUpdateStatusResponse failed = copyStatus(status);
      failed.setState("FAILED");
      failed.setError("yt-dlp update task interrupted after service restart");
      failed.setFinishedAt(LocalDateTime.now());
      persistStatus(failed);
      status = failed;
    }
  }

  public YtDlpRuntimeInfoResponse getRuntimeInfo() {
    boolean ready = ensureManagedDirectories();
    String version = resolveActiveVersion();
    YtDlpUpdateStatusResponse current = copyStatus(status);
    return YtDlpRuntimeInfoResponse.builder()
        .managedRoot(managedRootPath().toString())
        .managedReady(ready)
        .version(version)
        .channel(current != null && StringUtils.hasText(current.getChannel()) ? current.getChannel()
            : "stable")
        .updating(updateRunning.get())
        .status(current)
        .build();
  }

  public YtDlpUpdateStatusResponse getUpdateStatus() {
    return copyStatus(status);
  }

  public YtDlpUpdateStatusResponse submitUpdate(String rawChannel) {
    String channel = normalizeChannel(rawChannel);

    if (!ensureManagedDirectories()) {
      throw new BusinessException("yt-dlp managed directory is not writable: " + managedRoot);
    }

    if (!updateRunning.compareAndSet(false, true)) {
      throw new BusinessException("yt-dlp update task is already running");
    }

    String beforeVersion = resolveActiveVersion();
    log.info("Submitting yt-dlp update task, channel={}, beforeVersion={}",
        channel, StringUtils.hasText(beforeVersion) ? beforeVersion : "unknown");
    YtDlpUpdateStatusResponse runningStatus = YtDlpUpdateStatusResponse.builder()
        .state("RUNNING")
        .channel(channel)
        .beforeVersion(beforeVersion)
        .afterVersion(null)
        .error(null)
        .startedAt(LocalDateTime.now())
        .finishedAt(null)
        .build();
    persistStatus(runningStatus);
    status = runningStatus;

    try {
      taskExecutor.execute(() -> runUpdateTask(channel, beforeVersion));
    } catch (Exception e) {
      updateRunning.set(false);
      YtDlpUpdateStatusResponse failed = YtDlpUpdateStatusResponse.builder()
          .state("FAILED")
          .channel(channel)
          .beforeVersion(beforeVersion)
          .afterVersion(null)
          .error("failed to submit yt-dlp update task")
          .startedAt(runningStatus.getStartedAt())
          .finishedAt(LocalDateTime.now())
          .build();
      persistStatus(failed);
      status = failed;
      throw new BusinessException("failed to submit yt-dlp update task");
    }

    return copyStatus(runningStatus);
  }

  public YtDlpExecutionContext resolveExecutionContext() {
    Path currentVersionPath = resolveCurrentVersionPath();
    if (isUsableManagedPath(currentVersionPath)) {
      return new YtDlpExecutionContext(List.of("python3", "-m", "yt_dlp"),
          buildPythonPathEnvironment(currentVersionPath));
    }
    return new YtDlpExecutionContext(List.of("yt-dlp"), Map.of());
  }

  public YtDlpResolvedRuntime resolveExecutionRuntime() {
    YtDlpExecutionContext context = resolveExecutionContext();
    RuntimeIdentity runtimeIdentity = resolveRuntimeIdentity(context);
    String mode = isPythonModuleMode(context.command()) ? "MANAGED_PYTHON_MODULE" : "SYSTEM_BINARY";
    return new YtDlpResolvedRuntime(mode, runtimeIdentity.version(), runtimeIdentity.modulePath(),
        context);
  }

  private void runUpdateTask(String channel, String beforeVersion) {
    Path previousCurrentPath = resolveCurrentVersionPath();
    Path installedPath = null;
    boolean switched = false;
    try {
      log.info("yt-dlp update task started, channel={}, beforeVersion={}",
          channel, StringUtils.hasText(beforeVersion) ? beforeVersion : "unknown");
      installedPath = versionsPath()
          .resolve(VERSION_DIR_FORMATTER.format(LocalDateTime.now()));
      Files.createDirectories(installedPath);
      log.info("Created yt-dlp staging directory: {}", installedPath);

      log.info("Installing yt-dlp runtime, channel={}, target={}", channel, installedPath);
      installManagedYtDlp(channel, installedPath);
      log.info("yt-dlp pip install completed, target={}", installedPath);

      String installedVersion = resolveVersionByManagedPath(installedPath);
      if (!StringUtils.hasText(installedVersion)) {
        throw new RuntimeException("unable to read installed yt-dlp version");
      }
      log.info("Resolved installed yt-dlp version: {}", installedVersion);

      log.info("Switching current yt-dlp runtime to: {}", installedPath);
      replaceCurrentLink(installedPath);
      switched = true;
      log.info("Current yt-dlp runtime switch completed");

      String activeVersion = resolveVersionByManagedPath(installedPath);
      if (!StringUtils.hasText(activeVersion)) {
        throw new RuntimeException("unable to verify activated yt-dlp version");
      }
      log.info("Activated yt-dlp runtime verified, version={}", activeVersion);

      pruneOldVersions();
      YtDlpUpdateStatusResponse success = YtDlpUpdateStatusResponse.builder()
          .state("SUCCESS")
          .channel(channel)
          .beforeVersion(beforeVersion)
          .afterVersion(activeVersion)
          .error(null)
          .startedAt(status.getStartedAt())
          .finishedAt(LocalDateTime.now())
          .build();
      persistStatus(success);
      status = success;
      log.info("yt-dlp update task completed successfully, channel={}, beforeVersion={}, afterVersion={}",
          channel,
          StringUtils.hasText(beforeVersion) ? beforeVersion : "unknown",
          activeVersion);
    } catch (Exception e) {
      log.error("yt-dlp update task failed, channel={}, staging={}, message={}",
          channel, installedPath, e.getMessage(), e);
      if (switched) {
        log.warn("Rolling back yt-dlp runtime link to previous target: {}", previousCurrentPath);
        rollbackCurrent(previousCurrentPath);
      }
      deleteDirectoryQuietly(installedPath);
      YtDlpUpdateStatusResponse failed = YtDlpUpdateStatusResponse.builder()
          .state("FAILED")
          .channel(channel)
          .beforeVersion(beforeVersion)
          .afterVersion(beforeVersion)
          .error(abbreviate(e.getMessage(), 1000))
          .startedAt(status.getStartedAt())
          .finishedAt(LocalDateTime.now())
          .build();
      persistStatus(failed);
      status = failed;
      log.warn("yt-dlp update task marked as FAILED, channel={}, beforeVersion={}", channel,
          StringUtils.hasText(beforeVersion) ? beforeVersion : "unknown");
    } finally {
      updateRunning.set(false);
      log.info("yt-dlp update task finished, channel={}, finalState={}", channel,
          status != null ? status.getState() : "UNKNOWN");
    }
  }

  private void installManagedYtDlp(String channel, Path targetPath) {
    List<String> command = new ArrayList<>();
    command.add("python3");
    command.add("-m");
    command.add("pip");
    command.add("install");
    command.add("--no-cache-dir");
    command.add("-U");
    if ("nightly".equals(channel)) {
      command.add("--pre");
    }
    command.add("yt-dlp[default,curl-cffi]");
    command.add("--target");
    command.add(targetPath.toString());

    CommandResult result = runCommand(command, Map.of(), updateTimeoutSeconds);
    if (result.exitCode() != 0) {
      throw new RuntimeException("pip install failed: " + abbreviate(result.output(),
          LOG_TAIL_MAX_LENGTH));
    }
  }

  private String resolveActiveVersion() {
    try {
      YtDlpExecutionContext context = resolveExecutionContext();
      List<String> command = new ArrayList<>(context.command());
      command.add("--version");
      CommandResult result = runCommand(command, context.environment(), VERSION_CHECK_TIMEOUT_SECONDS);
      if (result.exitCode() != 0) {
        log.warn("Failed to resolve yt-dlp version: {}", abbreviate(result.output(),
            LOG_TAIL_MAX_LENGTH));
        return null;
      }
      return extractLastNonEmptyLine(result.output());
    } catch (Exception e) {
      log.warn("Failed to resolve active yt-dlp version", e);
      return null;
    }
  }

  private String resolveVersionByManagedPath(Path managedPath) {
    YtDlpExecutionContext context = new YtDlpExecutionContext(List.of("python3", "-m", "yt_dlp"),
        buildPythonPathEnvironment(managedPath));
    List<String> command = new ArrayList<>(context.command());
    command.add("--version");
    CommandResult result = runCommand(command, context.environment(), VERSION_CHECK_TIMEOUT_SECONDS);
    if (result.exitCode() != 0) {
      throw new RuntimeException(
          "managed yt-dlp version check failed: " + abbreviate(result.output(), LOG_TAIL_MAX_LENGTH));
    }
    String version = extractLastNonEmptyLine(result.output());
    if (!StringUtils.hasText(version)) {
      throw new RuntimeException("managed yt-dlp version output is empty");
    }
    return version;
  }

  private String normalizeChannel(String rawChannel) {
    String channel = StringUtils.hasText(rawChannel) ? rawChannel.trim().toLowerCase(Locale.ROOT)
        : "stable";
    if (!SUPPORTED_CHANNELS.contains(channel)) {
      throw new BusinessException("unsupported yt-dlp channel: " + rawChannel);
    }
    return channel;
  }

  private boolean ensureManagedDirectories() {
    try {
      Files.createDirectories(managedRootPath());
      Files.createDirectories(versionsPath());
      Path probe = Files.createTempFile(managedRootPath(), ".write-check-", ".tmp");
      Files.deleteIfExists(probe);
      return true;
    } catch (Exception e) {
      log.warn("yt-dlp managed directory unavailable: {}", managedRootPath(), e);
      return false;
    }
  }

  private void loadPersistedStatus() {
    Path stateFile = statusFilePath();
    if (!Files.exists(stateFile)) {
      status = defaultStatus();
      return;
    }
    try {
      YtDlpUpdateStatusResponse loaded = objectMapper.readValue(stateFile.toFile(),
          YtDlpUpdateStatusResponse.class);
      status = loaded != null ? loaded : defaultStatus();
    } catch (Exception e) {
      log.warn("Failed to read yt-dlp state file: {}", stateFile, e);
      status = defaultStatus();
    }
  }

  private void persistStatus(YtDlpUpdateStatusResponse newStatus) {
    Path stateFile = statusFilePath();
    Path tempFile = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
    try {
      objectMapper.writeValue(tempFile.toFile(), newStatus);
      moveWithFallback(tempFile, stateFile);
    } catch (Exception e) {
      log.warn("Failed to persist yt-dlp state: {}", stateFile, e);
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException ignored) {
        // ignore cleanup failure
      }
    }
  }

  private void replaceCurrentLink(Path targetPath) throws IOException {
    Path normalizedTarget = targetPath.toAbsolutePath().normalize();
    if (!Files.exists(normalizedTarget)) {
      throw new IOException("target version path does not exist: " + normalizedTarget);
    }
    Path current = currentLinkPath();
    if (Files.exists(current) && Files.isDirectory(current) && !Files.isSymbolicLink(current)) {
      throw new IOException("current path is an unexpected directory: " + current);
    }

    Path tempLink = managedRootPath().resolve("current_tmp_" + System.currentTimeMillis());
    Path relativeTarget = managedRootPath().relativize(normalizedTarget);
    Files.deleteIfExists(tempLink);
    Files.createSymbolicLink(tempLink, relativeTarget);
    moveWithFallback(tempLink, current);
    log.info("Updated yt-dlp current link: {} -> {}", current, relativeTarget);
  }

  private void rollbackCurrent(Path previousCurrentPath) {
    try {
      if (isUsableManagedPath(previousCurrentPath)) {
        replaceCurrentLink(previousCurrentPath);
        log.info("Rollback completed, current yt-dlp restored to: {}", previousCurrentPath);
        return;
      }
      Files.deleteIfExists(currentLinkPath());
      log.warn("Rollback target unavailable, deleted current link: {}", currentLinkPath());
    } catch (Exception e) {
      log.error("Failed to rollback yt-dlp current link", e);
    }
  }

  private void pruneOldVersions() {
    int maxKeep = Math.max(1, keepVersions != null ? keepVersions : 3);
    Path currentPath = resolveCurrentVersionPath();
    String currentDirName = currentPath != null ? currentPath.getFileName().toString() : null;

    List<Path> versionDirs;
    try (Stream<Path> stream = Files.list(versionsPath())) {
      versionDirs = stream
          .filter(Files::isDirectory)
          .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
          .toList();
    } catch (Exception e) {
      log.warn("Failed to list yt-dlp versions for pruning", e);
      return;
    }

    Set<String> keepNames = new java.util.HashSet<>();
    for (int i = 0; i < Math.min(maxKeep, versionDirs.size()); i++) {
      keepNames.add(versionDirs.get(i).getFileName().toString());
    }
    if (StringUtils.hasText(currentDirName)) {
      keepNames.add(currentDirName);
    }

    for (Path versionDir : versionDirs) {
      String name = versionDir.getFileName().toString();
      if (!keepNames.contains(name)) {
        log.info("Pruning old yt-dlp version directory: {}", versionDir);
        deleteDirectoryQuietly(versionDir);
      }
    }
  }

  private void deleteDirectoryQuietly(Path path) {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder()).forEach(current -> {
        try {
          Files.deleteIfExists(current);
        } catch (IOException e) {
          log.warn("Failed to delete path during cleanup: {}", current);
        }
      });
    } catch (IOException e) {
      log.warn("Failed to clean directory: {}", path, e);
    }
  }

  private CommandResult runCommand(List<String> command, Map<String, String> env,
      long timeoutSeconds) {
    Path outputLog = null;
    try {
      outputLog = Files.createTempFile(managedRootPath(), ".yt-dlp-cmd-", ".log");
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.directory(managedRootPath().toFile());
      processBuilder.redirectErrorStream(true);
      processBuilder.redirectOutput(outputLog.toFile());
      processBuilder.environment().putAll(env);

      Process process = processBuilder.start();
      boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new RuntimeException(
            "command timeout: " + String.join(" ", command));
      }

      int exitCode = process.exitValue();
      String output = readLogTail(outputLog, LOG_TAIL_MAX_LENGTH);
      log.debug("Command finished with exitCode={}, command={}", exitCode,
          String.join(" ", command));
      return new CommandResult(exitCode, output);
    } catch (Exception e) {
      throw new RuntimeException("failed to execute command: " + String.join(" ", command), e);
    } finally {
      if (outputLog != null) {
        try {
          Files.deleteIfExists(outputLog);
        } catch (IOException ignored) {
          // ignore cleanup failure
        }
      }
    }
  }

  private String readLogTail(Path logPath, int maxLength) {
    if (logPath == null || !Files.exists(logPath)) {
      return "";
    }
    try {
      String content = Files.readString(logPath, StandardCharsets.UTF_8);
      if (content.length() <= maxLength) {
        return content;
      }
      return content.substring(content.length() - maxLength);
    } catch (Exception e) {
      return "";
    }
  }

  private Map<String, String> buildPythonPathEnvironment(Path managedPath) {
    Map<String, String> env = new HashMap<>();
    String existing = System.getenv("PYTHONPATH");
    if (StringUtils.hasText(existing)) {
      env.put("PYTHONPATH", managedPath + java.io.File.pathSeparator + existing);
    } else {
      env.put("PYTHONPATH", managedPath.toString());
    }
    return env;
  }

  private String extractLastNonEmptyLine(String output) {
    if (!StringUtils.hasText(output)) {
      return null;
    }
    String[] lines = output.split("\\R");
    for (int i = lines.length - 1; i >= 0; i--) {
      if (StringUtils.hasText(lines[i])) {
        return lines[i].trim();
      }
    }
    return null;
  }

  private RuntimeIdentity resolveRuntimeIdentity(YtDlpExecutionContext context) {
    if (isPythonModuleMode(context.command())) {
      List<String> command = List.of(
          "python3",
          "-c",
          "import yt_dlp, yt_dlp.version; "
              + "print('PP_YTDLP_VERSION=' + str(yt_dlp.version.__version__)); "
              + "print('PP_YTDLP_MODULE=' + str(getattr(yt_dlp, '__file__', '')))"
      );
      CommandResult result = runCommand(command, context.environment(), VERSION_CHECK_TIMEOUT_SECONDS);
      if (result.exitCode() != 0) {
        return new RuntimeIdentity(null, null);
      }
      String version = extractPrefixedLine(result.output(), "PP_YTDLP_VERSION=");
      String modulePath = extractPrefixedLine(result.output(), "PP_YTDLP_MODULE=");
      return new RuntimeIdentity(version, modulePath);
    }

    List<String> versionCommand = new ArrayList<>(context.command());
    versionCommand.add("--version");
    CommandResult versionResult = runCommand(versionCommand, context.environment(),
        VERSION_CHECK_TIMEOUT_SECONDS);
    String version = versionResult.exitCode() == 0 ? extractLastNonEmptyLine(versionResult.output())
        : null;

    CommandResult binaryResult = runCommand(List.of("which", "yt-dlp"), Map.of(), 5);
    String modulePath = binaryResult.exitCode() == 0 ? extractLastNonEmptyLine(binaryResult.output())
        : null;

    return new RuntimeIdentity(version, modulePath);
  }

  private boolean isPythonModuleMode(List<String> command) {
    return command != null
        && command.size() >= 3
        && "python3".equals(command.get(0))
        && "-m".equals(command.get(1))
        && "yt_dlp".equals(command.get(2));
  }

  private String extractPrefixedLine(String output, String prefix) {
    if (!StringUtils.hasText(output) || !StringUtils.hasText(prefix)) {
      return null;
    }
    String[] lines = output.split("\\R");
    for (String line : lines) {
      if (line != null && line.startsWith(prefix)) {
        return line.substring(prefix.length()).trim();
      }
    }
    return null;
  }

  private String abbreviate(String text, int maxLength) {
    if (!StringUtils.hasText(text)) {
      return null;
    }
    String trimmed = text.trim();
    if (trimmed.length() <= maxLength) {
      return trimmed;
    }
    return trimmed.substring(trimmed.length() - maxLength);
  }

  private boolean isUsableManagedPath(Path path) {
    return path != null && Files.exists(path) && Files.exists(path.resolve("yt_dlp"));
  }

  private Path resolveCurrentVersionPath() {
    Path current = currentLinkPath();
    if (!Files.exists(current, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      return null;
    }
    try {
      if (Files.isSymbolicLink(current)) {
        Path linkTarget = Files.readSymbolicLink(current);
        if (!linkTarget.isAbsolute()) {
          return current.getParent().resolve(linkTarget).normalize();
        }
        return linkTarget.normalize();
      }
      if (Files.isDirectory(current)) {
        return current.toAbsolutePath().normalize();
      }
    } catch (Exception e) {
      log.warn("Failed to resolve current yt-dlp link: {}", current, e);
    }
    return null;
  }

  private void moveWithFallback(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private YtDlpUpdateStatusResponse defaultStatus() {
    return YtDlpUpdateStatusResponse.builder()
        .state("IDLE")
        .channel("stable")
        .beforeVersion(null)
        .afterVersion(null)
        .error(null)
        .startedAt(null)
        .finishedAt(null)
        .build();
  }

  private YtDlpUpdateStatusResponse copyStatus(YtDlpUpdateStatusResponse source) {
    if (source == null) {
      return null;
    }
    return YtDlpUpdateStatusResponse.builder()
        .state(source.getState())
        .channel(source.getChannel())
        .beforeVersion(source.getBeforeVersion())
        .afterVersion(source.getAfterVersion())
        .error(source.getError())
        .startedAt(source.getStartedAt())
        .finishedAt(source.getFinishedAt())
        .build();
  }

  private Path managedRootPath() {
    return Paths.get(managedRoot).toAbsolutePath().normalize();
  }

  private Path versionsPath() {
    return managedRootPath().resolve("versions");
  }

  private Path currentLinkPath() {
    return managedRootPath().resolve("current");
  }

  private Path statusFilePath() {
    return managedRootPath().resolve("state.json");
  }

  public record YtDlpExecutionContext(List<String> command, Map<String, String> environment) {

  }

  public record YtDlpResolvedRuntime(String mode, String version, String modulePath,
                                     YtDlpExecutionContext executionContext) {

  }

  private record RuntimeIdentity(String version, String modulePath) {

  }

  private record CommandResult(int exitCode, String output) {

  }
}
