package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import top.asimov.pigeon.config.MediaPathProperties;
import top.asimov.pigeon.config.StorageProperties;
import top.asimov.pigeon.handler.MediaFileResourceHandler;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.service.storage.S3StorageService;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

  @Mock
  private EpisodeMapper episodeMapper;

  @Mock
  private MessageSource messageSource;

  @Mock
  private StorageProperties storageProperties;

  @Mock
  private S3StorageService s3StorageService;

  @Mock
  private MediaPathProperties mediaPathProperties;

  @Mock
  private MediaFileResourceHandler mediaFileResourceHandler;

  private MediaService mediaService;

  @TempDir
  Path tempDir;

  private Path audioDir;

  @BeforeEach
  void setUp() throws IOException {
    audioDir = tempDir.resolve("audio");
    Files.createDirectories(audioDir);

    mediaService = new MediaService(
        episodeMapper,
        messageSource,
        storageProperties,
        s3StorageService,
        mediaPathProperties,
        mediaFileResourceHandler
    );
  }

  @Test
  void testResolveEpisodeCoverUrlForRssLocalModeWithExistingThumbnail() throws IOException {
    when(storageProperties.isS3Mode()).thenReturn(false);
    when(mediaPathProperties.getAudioFilePath()).thenReturn(audioDir.toString());

    Path channelDir = audioDir.resolve("MyChannel");
    Files.createDirectories(channelDir);
    Path mediaFile = channelDir.resolve("Ep1.m4a");
    Files.writeString(mediaFile, "dummy-media");
    Path coverFile = channelDir.resolve("Ep1.jpg");
    Files.writeString(coverFile, "dummy-cover");

    Episode episode = Episode.builder()
        .id("ep_001")
        .mediaFilePath(mediaFile.toString())
        .maxCoverUrl("https://i.ytimg.com/remote.jpg")
        .build();

    String resolved = mediaService.resolveEpisodeCoverUrlForRss("https://pigeon.example.com", episode);

    assertEquals("https://pigeon.example.com/media/ep_001/cover", resolved);
    assertTrue(mediaService.hasEpisodeCover(episode));
  }

  @Test
  void testResolveEpisodeCoverUrlForRssLocalModeWithBaseUrlWithoutProtocol() throws IOException {
    when(storageProperties.isS3Mode()).thenReturn(false);
    when(mediaPathProperties.getAudioFilePath()).thenReturn(audioDir.toString());

    Path channelDir = audioDir.resolve("MyChannel");
    Files.createDirectories(channelDir);
    Path mediaFile = channelDir.resolve("Ep1.m4a");
    Files.writeString(mediaFile, "dummy-media");
    Path coverFile = channelDir.resolve("Ep1.jpg");
    Files.writeString(coverFile, "dummy-cover");

    Episode episode = Episode.builder()
        .id("ep_001")
        .mediaFilePath(mediaFile.toString())
        .build();

    // Passing "localhost:8080" without http:// should auto-prepend http://
    String resolved = mediaService.resolveEpisodeCoverUrlForRss("localhost:8080", episode);

    assertEquals("http://localhost:8080/media/ep_001/cover", resolved);
  }

  @Test
  void testResolveEpisodeCoverUrlForRssLocalModeFallbackWhenMissing() {
    when(storageProperties.isS3Mode()).thenReturn(false);

    Episode episode = Episode.builder()
        .id("ep_002")
        .mediaFilePath("/non/existent/path/Ep2.m4a")
        .maxCoverUrl("https://i.ytimg.com/remote_max.jpg")
        .defaultCoverUrl("https://i.ytimg.com/remote_default.jpg")
        .build();

    String resolved = mediaService.resolveEpisodeCoverUrlForRss("https://pigeon.example.com", episode);

    assertEquals("https://i.ytimg.com/remote_max.jpg", resolved);
    assertFalse(mediaService.hasEpisodeCover(episode));
  }

  @Test
  void testResolveEpisodeCoverUrlForRssLocalModeFallbackToDefaultWhenMaxMissing() {
    when(storageProperties.isS3Mode()).thenReturn(false);

    Episode episode = Episode.builder()
        .id("ep_003")
        .mediaFilePath(null)
        .maxCoverUrl(null)
        .defaultCoverUrl("https://i.ytimg.com/remote_default.jpg")
        .build();

    String resolved = mediaService.resolveEpisodeCoverUrlForRss("https://pigeon.example.com", episode);

    assertEquals("https://i.ytimg.com/remote_default.jpg", resolved);
  }

  @Test
  void testResolveEpisodeCoverUrlForRssS3ModeWhenThumbnailKeyExists() {
    when(storageProperties.isS3Mode()).thenReturn(true);
    when(s3StorageService.listKeysByPrefix("audio/MyChannel/Ep1.thumbnail."))
        .thenReturn(List.of("audio/MyChannel/Ep1.thumbnail.jpg"));
    when(s3StorageService.generatePresignedGetUrl(eq("audio/MyChannel/Ep1.thumbnail.jpg"), any(), eq(null)))
        .thenReturn("https://s3.example.com/presigned-cover-url");

    Episode episode = Episode.builder()
        .id("ep_s3_001")
        .mediaFilePath("audio/MyChannel/Ep1.m4a")
        .maxCoverUrl("https://i.ytimg.com/remote.jpg")
        .build();

    String resolved = mediaService.resolveEpisodeCoverUrlForRss("https://pigeon.example.com", episode);

    assertEquals("https://s3.example.com/presigned-cover-url", resolved);
    assertTrue(mediaService.hasEpisodeCover(episode));
  }

  @Test
  void testResolveEpisodeCoverUrlForRssS3ModeFallbackWhenThumbnailKeyMissing() {
    when(storageProperties.isS3Mode()).thenReturn(true);
    when(s3StorageService.listKeysByPrefix("audio/MyChannel/Ep2.thumbnail."))
        .thenReturn(Collections.emptyList());

    Episode episode = Episode.builder()
        .id("ep_s3_002")
        .mediaFilePath("audio/MyChannel/Ep2.m4a")
        .maxCoverUrl("https://i.ytimg.com/fallback.jpg")
        .build();

    String resolved = mediaService.resolveEpisodeCoverUrlForRss("https://pigeon.example.com", episode);

    assertEquals("https://i.ytimg.com/fallback.jpg", resolved);
    assertFalse(mediaService.hasEpisodeCover(episode));
  }

  @Test
  void testBuildEpisodeCoverResponseLocalModeSuccess() throws IOException {
    when(storageProperties.isS3Mode()).thenReturn(false);
    when(mediaPathProperties.getAudioFilePath()).thenReturn(audioDir.toString());

    Path channelDir = audioDir.resolve("MyChannel");
    Files.createDirectories(channelDir);
    Path mediaFile = channelDir.resolve("Ep1.m4a");
    Files.writeString(mediaFile, "dummy-media");
    Path coverFile = channelDir.resolve("Ep1.jpg");
    Files.writeString(coverFile, "dummy-cover");

    Episode episode = Episode.builder()
        .id("ep_local_01")
        .mediaFilePath(mediaFile.toString())
        .build();

    when(episodeMapper.selectById("ep_local_01")).thenReturn(episode);

    ResponseEntity<?> response = mediaService.buildEpisodeCoverResponse("ep_local_01");

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
  }

  @Test
  void testBuildEpisodeCoverResponseLocalModeRedirectsToFallback() {
    when(storageProperties.isS3Mode()).thenReturn(false);

    Episode episode = Episode.builder()
        .id("ep_local_02")
        .mediaFilePath(null)
        .maxCoverUrl("https://i.ytimg.com/remote.jpg")
        .build();

    when(episodeMapper.selectById("ep_local_02")).thenReturn(episode);

    ResponseEntity<?> response = mediaService.buildEpisodeCoverResponse("ep_local_02");

    assertNotNull(response);
    assertEquals(HttpStatus.FOUND, response.getStatusCode());
    assertEquals("https://i.ytimg.com/remote.jpg", response.getHeaders().getLocation().toString());
  }

  @Test
  void testBuildEpisodeCoverResponseNotFoundWhenEpisodeDoesNotExist() {
    when(storageProperties.isS3Mode()).thenReturn(false);
    when(episodeMapper.selectById("ep_missing")).thenReturn(null);
    when(messageSource.getMessage(eq("episode.not.found"), any(), any())).thenReturn("Episode not found");

    ResponseEntity<?> response = mediaService.buildEpisodeCoverResponse("ep_missing");

    assertNotNull(response);
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  void testBuildEpisodeCoverResponseNotFoundWhenFileAndRemoteBothMissing() {
    when(storageProperties.isS3Mode()).thenReturn(false);

    Episode episode = Episode.builder()
        .id("ep_local_03")
        .mediaFilePath(null)
        .maxCoverUrl(null)
        .defaultCoverUrl(null)
        .build();

    when(episodeMapper.selectById("ep_local_03")).thenReturn(episode);

    ResponseEntity<?> response = mediaService.buildEpisodeCoverResponse("ep_local_03");

    assertNotNull(response);
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  void testBuildEpisodeCoverResponseS3ModeRedirect() {
    when(storageProperties.isS3Mode()).thenReturn(true);
    when(s3StorageService.listKeysByPrefix("audio/MyChannel/Ep1.thumbnail."))
        .thenReturn(List.of("audio/MyChannel/Ep1.thumbnail.jpg"));
    when(s3StorageService.generatePresignedGetUrl(eq("audio/MyChannel/Ep1.thumbnail.jpg"), any(), eq(null)))
        .thenReturn("https://s3.example.com/cover-url");

    Episode episode = Episode.builder()
        .id("ep_s3_03")
        .mediaFilePath("audio/MyChannel/Ep1.m4a")
        .build();

    when(episodeMapper.selectById("ep_s3_03")).thenReturn(episode);

    ResponseEntity<?> response = mediaService.buildEpisodeCoverResponse("ep_s3_03");

    assertNotNull(response);
    assertEquals(HttpStatus.FOUND, response.getStatusCode());
    assertEquals("https://s3.example.com/cover-url", response.getHeaders().getLocation().toString());
  }
}
