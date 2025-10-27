package top.asimov.pigeon.handler;

import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.model.enums.FeedType;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedSaveResult;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.service.FeedFactory;
import top.asimov.pigeon.service.PlaylistService;

@Component
public class PlaylistFeedHandler extends AbstractFeedHandler<Playlist> {

  private final PlaylistService playlistService;

  public PlaylistFeedHandler(PlaylistService playlistService, FeedFactory feedFactory,
      MessageSource messageSource) {
    super(feedFactory, messageSource);
    this.playlistService = playlistService;
  }

  @Override
  public FeedType getType() {
    return FeedType.PLAYLIST;
  }

  @Override
  public List<Playlist> list() {
    return playlistService.selectPlaylistList();
  }

  @Override
  public Playlist detail(String id) {
    return playlistService.playlistDetail(id);
  }

  @Override
  public String getSubscribeUrl(String id) {
    return playlistService.getPlaylistRssFeedUrl(id);
  }

  @Override
  public FeedConfigUpdateResult updateConfig(String id, Map<String, Object> payload) {
    return playlistService.updatePlaylistConfig(id, buildFeed(payload));
  }

  @Override
  public FeedPack<Playlist> fetch(Map<String, ?> payload) {
    return playlistService.fetchPlaylist(resolveSourceUrl(payload, "playlistUrl"));
  }

  @Override
  public FeedPack<Playlist> preview(Map<String, Object> payload) {
    return playlistService.previewPlaylist(buildFeed(payload));
  }

  @Override
  public FeedSaveResult<Playlist> add(Map<String, Object> payload) {
    return playlistService.savePlaylist(buildFeed(payload));
  }

  @Override
  public void delete(String id) {
    playlistService.deletePlaylist(id);
  }

  @Override
  protected Class<Playlist> getFeedClass() {
    return Playlist.class;
  }
}
