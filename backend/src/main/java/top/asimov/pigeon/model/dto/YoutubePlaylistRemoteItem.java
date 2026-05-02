package top.asimov.pigeon.model.dto;

import java.time.LocalDateTime;

public record YoutubePlaylistRemoteItem(
    String playlistItemId,
    String videoId,
    LocalDateTime itemAddedAt,
    LocalDateTime videoPublishedAt,
    Long position,
    String itemPrivacyStatus,
    String sourceChannelId,
    String sourceChannelName,
    String sourceChannelUrl
) {

}
