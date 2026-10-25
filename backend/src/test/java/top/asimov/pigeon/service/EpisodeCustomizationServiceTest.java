package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.entity.Episode;

@ExtendWith(MockitoExtension.class)
class EpisodeCustomizationServiceTest {

  @Mock
  private EpisodeMapper episodeMapper;

  @Mock
  private MediaService mediaService;

  @Mock
  private EpisodeCoverService episodeCoverService;

  private EpisodeCustomizationService service;
  private Episode episode;

  @BeforeAll
  static void registerEntityMetadata() {
    // lets LambdaUpdateWrapper resolve Episode columns without a database
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
        Episode.class);
  }

  @BeforeEach
  void setUp() {
    service = new EpisodeCustomizationService(episodeMapper, mediaService, episodeCoverService);
    episode = Episode.builder()
        .id("video_001")
        .title("Original title")
        .description("Original description")
        .build();
    lenient().when(episodeMapper.selectById("video_001")).thenReturn(episode);
  }

  @SuppressWarnings("unchecked")
  private LambdaUpdateWrapper<Episode> capturedUpdate() {
    ArgumentCaptor<LambdaUpdateWrapper<Episode>> captor =
        ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    verify(episodeMapper).update(isNull(), captor.capture());
    return captor.getValue();
  }

  private String updateSql() {
    LambdaUpdateWrapper<Episode> wrapper = capturedUpdate();
    return wrapper.getSqlSet() + " | " + wrapper.getParamNameValuePairs().values();
  }

  @Test
  void storesTrimmedOverrides() {
    service.updateText("video_001", "  New\n title  ", "  New description\r\n");

    String sql = updateSql();
    assertTrue(sql.contains("custom_title"), sql);
    assertTrue(sql.contains("New title"), "title newlines collapse to spaces: " + sql);
    assertTrue(sql.contains("New description"), sql);
  }

  @Test
  void blankOrUnchangedValuesClearTheOverride() {
    service.updateText("video_001", "  ", " Original description ");

    LambdaUpdateWrapper<Episode> wrapper = capturedUpdate();
    String sql = wrapper.getSqlSet();
    assertTrue(sql.contains("custom_title=") && sql.contains("custom_description="), sql);
    long nulls = wrapper.getParamNameValuePairs().values().stream()
        .filter(Objects::isNull).count();
    assertEquals(2, nulls, "both overrides should be cleared: " + sql);
  }

  @Test
  void rejectsOverlongTitles() {
    String title = "x".repeat(EpisodeCustomizationService.MAX_TITLE_LENGTH + 1);

    assertThrows(BusinessException.class, () -> service.updateText("video_001", title, null));
    verify(episodeMapper, never()).update(any(), any());
  }

  @Test
  void uploadReplacesPreviousCoverAndClearsCachedArtwork() throws Exception {
    episode.setCustomCoverExt("png");
    MockMultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg",
        new byte[]{1, 2, 3});
    when(mediaService.saveEpisodeCustomCover("video_001", file)).thenReturn("jpg");

    service.updateCover("video_001", file);

    verify(mediaService).deleteEpisodeCustomCover("video_001", "png");
    verify(episodeCoverService).evictEpisode("video_001");
    assertTrue(updateSql().contains("jpg"));
  }

  @Test
  void clearingWithoutUploadedCoverDoesNothing() throws Exception {
    Episode result = service.clearCover("video_001");

    assertSame(episode, result);
    verify(episodeMapper, never()).update(any(), any());
    verify(episodeCoverService, never()).evictEpisode(any());
  }

  @Test
  void clearingUploadedCoverDeletesIt() throws Exception {
    episode.setCustomCoverExt("webp");

    service.clearCover("video_001");

    verify(mediaService).deleteEpisodeCustomCover("video_001", "webp");
    verify(episodeCoverService).evictEpisode("video_001");
    assertTrue(updateSql().contains("custom_cover_ext"));
  }

  @Test
  void unknownEpisodeIsRejected() {
    when(episodeMapper.selectById("missing")).thenReturn(null);

    BusinessException error = assertThrows(BusinessException.class,
        () -> service.updateText("missing", "t", "d"));
    assertEquals("Episode not found", error.getMessage());
  }
}
