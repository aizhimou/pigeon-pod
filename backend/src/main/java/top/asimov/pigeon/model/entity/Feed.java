package top.asimov.pigeon.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import top.asimov.pigeon.model.enums.DownloadType;
import top.asimov.pigeon.model.enums.FeedType;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class Feed {

  @TableId
  private String id;

  private String title;
  private String customTitle;
  private String coverUrl;
  private String customCoverExt;
  private String source;
  private String description;
  private String titleContainKeywords;
  private String titleExcludeKeywords;
  private String descriptionContainKeywords;
  private String descriptionExcludeKeywords;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Integer minimumDuration;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Integer maximumDuration;
  private Integer initialEpisodes;
  private Integer maximumEpisodes;
  private Integer audioQuality;
  private DownloadType downloadType;
  private String videoQuality;
  private String videoEncoding;
  private String subtitleLanguages;
  private String subtitleFormat;
  private String lastSyncVideoId;
  private LocalDateTime lastSyncTimestamp;
  @TableField("sync_state")
  @lombok.Builder.Default
  private Boolean syncState = Boolean.TRUE;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime subscribedAt;

  @TableField(fill = FieldFill.UPDATE)
  private LocalDateTime lastUpdatedAt;

  @TableField(exist = false)
  private transient String originalUrl;

  @TableField(exist = false)
  private transient LocalDateTime lastPublishedAt;

  @TableField(exist = false)
  private transient String customCoverUrl;

  public abstract FeedType getType();
}
