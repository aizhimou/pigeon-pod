package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import top.asimov.pigeon.model.response.YtDlpRuntimeInfoResponse;

class YtDlpRuntimeServiceTest {

  @TempDir
  Path tempDir;

  private YtDlpRuntimeService service;
  private Path managedRoot;

  @BeforeEach
  void setUp() {
    service = new YtDlpRuntimeService(new ObjectMapper(), Runnable::run);
    managedRoot = tempDir.resolve("yt-dlp");
    ReflectionTestUtils.setField(service, "managedRoot", managedRoot.toString());
    ReflectionTestUtils.setField(service, "keepVersions", 3);
    ReflectionTestUtils.setField(service, "updateTimeoutSeconds", 30L);
    ReflectionTestUtils.invokeMethod(service, "init");
  }

  @Test
  void shouldSwitchToManagedRuntimeWithoutDeletingOtherVersions() throws IOException {
    Path managedVersion = createManagedRuntime("20260315010101", "2026.03.15");
    createManagedRuntime("20260314010101", "2026.03.14");

    YtDlpRuntimeInfoResponse info =
        service.switchRuntime("managed:" + managedVersion.getFileName());

    Path currentLink = managedRoot.resolve("current");
    assertTrue(Files.exists(currentLink, LinkOption.NOFOLLOW_LINKS));
    assertEquals(managedVersion.normalize(), currentLink.toRealPath());
    assertEquals("MANAGED_PYTHON_MODULE", info.getMode());
    assertEquals("managed:" + managedVersion.getFileName(), info.getActiveRuntimeKey());
    assertNotNull(info.getAvailableRuntimes());
    assertEquals(2, info.getAvailableRuntimes().stream()
        .filter(runtime -> "MANAGED_PYTHON_MODULE".equals(runtime.getMode()))
        .count());
  }

  @Test
  void shouldSwitchBackToSystemWithoutDeletingManagedVersions() throws IOException {
    Path managedVersion = createManagedRuntime("20260315010101", "2026.03.15");
    service.switchRuntime("managed:" + managedVersion.getFileName());

    YtDlpRuntimeInfoResponse info = service.switchRuntime("system");

    Path currentLink = managedRoot.resolve("current");
    assertFalse(Files.exists(currentLink, LinkOption.NOFOLLOW_LINKS));
    assertTrue(Files.exists(managedVersion));
    assertEquals("system", info.getActiveRuntimeKey());
    assertEquals("SYSTEM_BINARY", info.getMode());
  }

  private Path createManagedRuntime(String directoryName, String version) throws IOException {
    Path runtimePath = managedRoot.resolve("versions").resolve(directoryName);
    Path packagePath = runtimePath.resolve("yt_dlp");
    Files.createDirectories(packagePath);
    Files.writeString(packagePath.resolve("__init__.py"), "");
    Files.writeString(packagePath.resolve("version.py"), "__version__ = \"" + version + "\"\n");
    return runtimePath;
  }
}
