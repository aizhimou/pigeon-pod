package top.asimov.pigeon.helper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoLiveStreamingDetails;
import com.google.api.services.youtube.model.VideoSnippet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import top.asimov.pigeon.config.ProxyExecutionScope;

class YoutubeVideoHelperTest {

  private YoutubeVideoHelper youtubeVideoHelper;

  @BeforeEach
  void setUp() {
    youtubeVideoHelper = new YoutubeVideoHelper(
        mock(MessageSource.class),
        mock(YoutubeApiExecutor.class),
        mock(YoutubeServiceFactory.class),
        mock(ProxyExecutionScope.class));
  }

  @Test
  void shouldTreatCompletedLiveBroadcastAsArchivedLiveVod() {
    Video video = new Video()
        .setLiveStreamingDetails(new VideoLiveStreamingDetails()
            .setActualStartTime(new DateTime("2026-03-20T10:00:00Z"))
            .setActualEndTime(new DateTime("2026-03-20T11:00:00Z")));

    assertTrue(youtubeVideoHelper.isArchivedLiveVod(video));
  }

  @Test
  void shouldSkipActiveLiveBroadcasts() {
    Video video = new Video()
        .setId("episode-1")
        .setSnippet(new VideoSnippet()
            .setTitle("Active live")
            .setLiveBroadcastContent("active"));

    assertTrue(youtubeVideoHelper.shouldSkipLiveContent(video));
  }

  @Test
  void shouldNotTreatRegularUploadAsArchivedLiveVod() {
    assertFalse(youtubeVideoHelper.isArchivedLiveVod(new Video()));
  }
}
