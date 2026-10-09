package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import top.asimov.pigeon.config.DownloadProperties;
import top.asimov.pigeon.config.StorageProperties;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.service.storage.S3StorageService;

@ExtendWith(MockitoExtension.class)
class EpisodeServiceDeletionTest {

  @Mock
  private EpisodeMapper episodeMapper;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private MessageSource messageSource;

  @Mock
  private ChannelMapper channelMapper;

  @Mock
  private PlaylistEpisodeMapper playlistEpisodeMapper;

  @Mock
  private PlaylistMapper playlistMapper;

  @Mock
  private StorageProperties storageProperties;

  @Mock
  private S3StorageService s3StorageService;

  @Mock
  private DownloadProperties downloadProperties;

  private EpisodeService episodeService;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    when(storageProperties.isS3Mode()).thenReturn(false);
    episodeService = new EpisodeService(
        episodeMapper,
        eventPublisher,
        messageSource,
        channelMapper,
        playlistEpisodeMapper,
        playlistMapper,
        storageProperties,
        s3StorageService,
        downloadProperties
    );
  }

  @Test
  void testDeleteAssetFiles_WhenParentDirectoryMissing_DoesNotThrow() {
    String missingMediaPath = tempDir.resolve("missing_subdir").resolve("media.mp4").toString();

    assertDoesNotThrow(() -> episodeService.deleteSubtitleFiles(missingMediaPath));
    assertDoesNotThrow(() -> episodeService.deleteThumbnailFiles(missingMediaPath));
    assertDoesNotThrow(() -> episodeService.deleteChaptersFile(missingMediaPath, "ep-1"));
  }

  @Test
  void testDeleteAssetFiles_WhenFilesExist_DeletesSuccessfully() throws IOException {
    Path mediaPath = tempDir.resolve("media.mp4");
    Files.createFile(mediaPath);

    Path subtitleVtt = tempDir.resolve("media.en.vtt");
    Files.createFile(subtitleVtt);

    Path thumbnailJpg = tempDir.resolve("media.jpg");
    Files.createFile(thumbnailJpg);

    Path chaptersJson = tempDir.resolve("media.chapters.json");
    Files.createFile(chaptersJson);

    assertTrue(Files.exists(subtitleVtt));
    assertTrue(Files.exists(thumbnailJpg));
    assertTrue(Files.exists(chaptersJson));

    episodeService.deleteSubtitleFiles(mediaPath.toString());
    assertFalse(Files.exists(subtitleVtt));

    episodeService.deleteThumbnailFiles(mediaPath.toString());
    assertFalse(Files.exists(thumbnailJpg));

    episodeService.deleteChaptersFile(mediaPath.toString(), "ep-1");
    assertFalse(Files.exists(chaptersJson));
  }
}
