package top.asimov.pigeon.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("youtube_playlist_item")
public class YoutubePlaylistItem {

  @TableId
  private Long id;
  private String playlistId;
  private String playlistItemId;
  private String videoId;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String episodeId;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime itemAddedAt;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime videoPublishedAt;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long position;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String itemPrivacyStatus;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String sourceChannelId;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String sourceChannelName;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String sourceChannelUrl;
  private String presenceStatus;
  private String materializationStatus;
  private String autoDispatchStatus;
  private LocalDateTime firstSeenAt;
  private LocalDateTime lastSeenAt;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime removedAt;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String lastError;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
