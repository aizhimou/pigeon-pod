package top.asimov.pigeon.handler;

import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.enums.FeedType;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedSaveResult;
import top.asimov.pigeon.model.response.FeedRefreshResult;
import top.asimov.pigeon.service.ChannelService;
import top.asimov.pigeon.service.FeedFactory;

@Component
public class ChannelFeedHandler extends AbstractFeedHandler<Channel> {

  private final ChannelService channelService;

  public ChannelFeedHandler(ChannelService channelService, FeedFactory feedFactory,
      MessageSource messageSource) {
    super(feedFactory, messageSource);
    this.channelService = channelService;
  }

  @Override
  public FeedType getType() {
    return FeedType.CHANNEL;
  }

  @Override
  public List<Channel> list() {
    return channelService.selectChannelList();
  }

  @Override
  public Channel detail(String id) {
    return channelService.channelDetail(id);
  }

  @Override
  public String getSubscribeUrl(String id) {
    return channelService.getChannelRssFeedUrl(id);
  }

  @Override
  public FeedConfigUpdateResult updateConfig(String id, Map<String, Object> payload) {
    return channelService.updateChannelConfig(id, buildFeed(payload));
  }

  @Override
  public FeedPack<Channel> fetch(Map<String, ?> payload) {
    return channelService.fetchChannel(resolveSourceUrl(payload, "channelUrl"));
  }

  @Override
  public List<Episode> fetchHistory(String id) {
    return channelService.fetchChannelHistory(id);
  }

  @Override
  public FeedPack<Channel> preview(Map<String, Object> payload) {
    return channelService.previewChannel(buildFeed(payload));
  }

  @Override
  public FeedSaveResult<Channel> add(Map<String, Object> payload) {
    return channelService.saveChannel(buildFeed(payload));
  }

  @Override
  public void delete(String id) {
    channelService.deleteChannel(id);
  }

  @Override
  public FeedRefreshResult refresh(String id) {
    return channelService.refreshChannelById(id);
  }

  @Override
  protected Class<Channel> getFeedClass() {
    return Channel.class;
  }
}
