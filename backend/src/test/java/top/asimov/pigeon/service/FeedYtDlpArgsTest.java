package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import top.asimov.pigeon.config.AppBaseUrlResolver;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.helper.BilibiliChannelHelper;
import top.asimov.pigeon.helper.BilibiliResolverHelper;
import top.asimov.pigeon.helper.YoutubeChannelHelper;
import top.asimov.pigeon.helper.YoutubeHelper;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.model.entity.Channel;

@ExtendWith(MockitoExtension.class)
class FeedYtDlpArgsTest {

  @Mock
  private ChannelMapper channelMapper;

  @Mock
  private EpisodeService episodeService;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private YoutubeHelper youtubeHelper;

  @Mock
  private YoutubeChannelHelper youtubeChannelHelper;

  @Mock
  private BilibiliResolverHelper bilibiliResolverHelper;

  @Mock
  private BilibiliChannelHelper bilibiliChannelHelper;

  @Mock
  private AccountService accountService;

  @Mock
  private MessageSource messageSource;

  @Mock
  private FeedDefaultsService feedDefaultsService;

  @Mock
  private AppBaseUrlResolver appBaseUrlResolver;

  private ChannelService channelService;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    channelService = new ChannelService(
        channelMapper,
        episodeService,
        eventPublisher,
        youtubeHelper,
        youtubeChannelHelper,
        bilibiliResolverHelper,
        bilibiliChannelHelper,
        accountService,
        messageSource,
        feedDefaultsService,
        appBaseUrlResolver,
        objectMapper
    );
  }

  @Test
  void updateChannelConfig_withValidYtDlpArgs_normalizesAndSavesJson() {
    Channel existing = new Channel();
    existing.setId("channel-1");
    when(channelMapper.selectById("channel-1")).thenReturn(existing);
    when(channelMapper.updateById(any(Channel.class))).thenReturn(1);

    Channel incoming = new Channel();
    incoming.setYtDlpArgs("[\"--sponsorblock-remove\", \"sponsor\"]");

    channelService.updateFeedConfig("channel-1", incoming);

    ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
    verify(channelMapper).updateById(captor.capture());
    Channel updated = captor.getValue();

    assertNotNull(updated.getYtDlpArgs());
    assertEquals("[\"--sponsorblock-remove\",\"sponsor\"]", updated.getYtDlpArgs());
  }

  @Test
  void updateChannelConfig_withPlainTextYtDlpArgs_tokenizesAndSavesJson() {
    Channel existing = new Channel();
    existing.setId("channel-1");
    when(channelMapper.selectById("channel-1")).thenReturn(existing);
    when(channelMapper.updateById(any(Channel.class))).thenReturn(1);

    Channel incoming = new Channel();
    incoming.setYtDlpArgs("--sponsorblock-mark all");

    channelService.updateFeedConfig("channel-1", incoming);

    ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
    verify(channelMapper).updateById(captor.capture());
    Channel updated = captor.getValue();

    assertNotNull(updated.getYtDlpArgs());
    assertEquals("[\"--sponsorblock-mark\",\"all\"]", updated.getYtDlpArgs());
  }

  @Test
  void updateChannelConfig_withBlockedFlag_throwsBusinessException() {
    Channel existing = new Channel();
    existing.setId("channel-1");
    when(channelMapper.selectById("channel-1")).thenReturn(existing);

    Channel incoming = new Channel();
    incoming.setYtDlpArgs("--exec \"rm -rf /\"");

    assertThrows(BusinessException.class, () -> channelService.updateFeedConfig("channel-1", incoming));
  }

  @Test
  void updateChannelConfig_withNullOrEmptyYtDlpArgs_resetsToNull() {
    Channel existing = new Channel();
    existing.setId("channel-1");
    existing.setYtDlpArgs("[\"--sponsorblock-remove\",\"sponsor\"]");
    when(channelMapper.selectById("channel-1")).thenReturn(existing);
    when(channelMapper.updateById(any(Channel.class))).thenReturn(1);

    Channel incoming = new Channel();
    incoming.setYtDlpArgs("");

    channelService.updateFeedConfig("channel-1", incoming);

    ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
    verify(channelMapper).updateById(captor.capture());
    Channel updated = captor.getValue();

    assertNull(updated.getYtDlpArgs());
  }
}
