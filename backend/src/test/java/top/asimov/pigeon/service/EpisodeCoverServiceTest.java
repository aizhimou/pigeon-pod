package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import top.asimov.pigeon.config.MediaPathProperties;
import top.asimov.pigeon.config.OutboundProxyHolder;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.dto.EpisodeCoverStyle;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.enums.FeedType;

@ExtendWith(MockitoExtension.class)
class EpisodeCoverServiceTest {

  private static final String PAD_STYLE = "{\"mode\":\"PAD\",\"fill\":\"BLUR\",\"fade\":40}";

  @Mock
  private ChannelMapper channelMapper;

  @Mock
  private PlaylistMapper playlistMapper;

  @Mock
  private EpisodeMapper episodeMapper;

  @Mock
  private MediaService mediaService;

  @TempDir
  Path tempDir;

  private EpisodeCoverService episodeCoverService;
  private Channel channel;
  private Episode episode;
  private File thumbnail;

  @BeforeEach
  void setUp() throws Exception {
    MediaPathProperties mediaPathProperties = new MediaPathProperties();
    mediaPathProperties.setCoverFilePath(tempDir.resolve("cover").toString());
    episodeCoverService = new EpisodeCoverService(channelMapper, playlistMapper, episodeMapper,
        mediaService, mediaPathProperties, new OutboundProxyHolder());

    channel = new Channel();
    channel.setId("UCchannel");
    channel.setEpisodeCoverStyle(PAD_STYLE);
    episode = Episode.builder()
        .id("video_001")
        .channelId("UCchannel")
        .mediaFilePath(tempDir.resolve("audio/video_001.m4a").toString())
        .build();

    thumbnail = tempDir.resolve("audio/video_001.jpg").toFile();
    Files.createDirectories(thumbnail.toPath().getParent());
    BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.ORANGE);
    graphics.fillRect(0, 0, 320, 180);
    graphics.dispose();
    ImageIO.write(image, "jpg", thumbnail);

    lenient().when(channelMapper.selectById("UCchannel")).thenReturn(channel);
    lenient().when(episodeMapper.selectById("video_001")).thenReturn(episode);
    lenient().when(mediaService.isS3ModeEnabled()).thenReturn(false);
    lenient().when(mediaService.resolveLocalCoverFile(episode)).thenReturn(thumbnail);
    lenient().when(mediaService.isFilePathAllowed(any())).thenReturn(true);
  }

  private List<Path> cachedFiles() throws Exception {
    Path directory = tempDir.resolve("cover/episode-styles/UCchannel");
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files.toList();
    }
  }

  @Test
  void rendersSquareCoverAndCachesIt() throws Exception {
    ResponseEntity<?> response = episodeCoverService.buildCoverResponse("channel", "UCchannel",
        "video_001");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
    FileSystemResource body = assertInstanceOf(FileSystemResource.class, response.getBody());
    BufferedImage rendered = ImageIO.read(body.getFile());
    assertEquals(320, rendered.getWidth());
    assertEquals(320, rendered.getHeight());

    String fingerprint = EpisodeCoverStyle.parse(PAD_STYLE).fingerprint();
    assertEquals(List.of("video_001-" + fingerprint + ".jpg"),
        cachedFiles().stream().map(path -> path.getFileName().toString()).toList());

    ResponseEntity<?> cachedResponse = episodeCoverService.buildCoverResponse("channel",
        "UCchannel", "video_001");
    FileSystemResource cachedBody = (FileSystemResource) cachedResponse.getBody();
    assertEquals(body.getFile(), cachedBody.getFile());
  }

  @Test
  void smallCoversAreCachedSeparatelyAndSurvivePruning() throws Exception {
    BufferedImage large = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
    ImageIO.write(large, "jpg", thumbnail);

    ResponseEntity<?> small = episodeCoverService.buildCoverResponse("channel", "UCchannel",
        "video_001", true);
    episodeCoverService.buildCoverResponse("channel", "UCchannel", "video_001", false);

    BufferedImage smallImage = ImageIO.read(((FileSystemResource) small.getBody()).getFile());
    assertEquals(EpisodeCoverService.SMALL_COVER_SIDE, smallImage.getWidth());
    String fingerprint = EpisodeCoverStyle.parse(PAD_STYLE).fingerprint();
    assertEquals(List.of("video_001-" + fingerprint + "-s.jpg", "video_001-" + fingerprint + ".jpg"),
        cachedFiles().stream().map(path -> path.getFileName().toString()).sorted().toList());

    episodeCoverService.pruneCache(FeedType.CHANNEL, "UCchannel");
    assertEquals(2, cachedFiles().size());

    episodeCoverService.evictEpisode("video_001");
    assertTrue(cachedFiles().isEmpty());
  }

  @Test
  void originalStyleServesUntouchedThumbnail() {
    channel.setEpisodeCoverStyle(null);
    ResponseEntity<?> original = ResponseEntity.ok().build();
    when(mediaService.buildEpisodeCoverResponse("video_001")).thenAnswer(invocation -> original);

    ResponseEntity<?> response = episodeCoverService.buildCoverResponse("channel", "UCchannel",
        "video_001");

    assertSame(original, response);
  }

  @Test
  void missingSourceFallsBackToOriginalResponse() throws Exception {
    when(mediaService.resolveLocalCoverFile(episode)).thenReturn(null);
    ResponseEntity<?> original = ResponseEntity.notFound().build();
    when(mediaService.buildEpisodeCoverResponse("video_001")).thenAnswer(invocation -> original);

    ResponseEntity<?> response = episodeCoverService.buildCoverResponse("channel", "UCchannel",
        "video_001");

    assertSame(original, response);
    assertTrue(cachedFiles().isEmpty());
  }

  @Test
  void unknownFeedTypeIsNotFound() {
    ResponseEntity<?> response = episodeCoverService.buildCoverResponse("podcast", "UCchannel",
        "video_001");

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  void pruneCacheRemovesCoversFromPreviousStyles() throws Exception {
    episodeCoverService.buildCoverResponse("channel", "UCchannel", "video_001");
    assertEquals(1, cachedFiles().size());

    channel.setEpisodeCoverStyle("{\"mode\":\"CROP\",\"position\":20}");
    episodeCoverService.pruneCache(FeedType.CHANNEL, "UCchannel");
    assertTrue(cachedFiles().isEmpty());

    episodeCoverService.buildCoverResponse("channel", "UCchannel", "video_001");
    channel.setEpisodeCoverStyle(null);
    episodeCoverService.pruneCache(FeedType.CHANNEL, "UCchannel");
    assertFalse(Files.exists(tempDir.resolve("cover/episode-styles/UCchannel")));
  }

  @Test
  void previewRendersRequestedStyleWithoutTouchingTheCache() throws Exception {
    byte[] preview = episodeCoverService.renderPreview(FeedType.CHANNEL, "UCchannel", "video_001",
        "{\"mode\":\"CROP\",\"position\":50}");

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(preview));
    assertNotNull(image);
    assertEquals(180, image.getWidth());
    assertEquals(180, image.getHeight());
    assertTrue(cachedFiles().isEmpty());
  }

  @Test
  void previewRejectsUnknownEpisode() {
    when(episodeMapper.selectById("missing")).thenReturn(null);

    assertThrows(BusinessException.class, () -> episodeCoverService.renderPreview(
        FeedType.CHANNEL, "UCchannel", "missing", PAD_STYLE));
  }
}
