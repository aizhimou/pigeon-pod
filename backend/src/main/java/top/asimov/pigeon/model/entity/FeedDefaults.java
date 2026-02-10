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
import top.asimov.pigeon.model.enums.DownloadType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("feed_defaults")
public class FeedDefaults {

  @TableId
  private Integer id;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Integer autoDownloadLimit;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Integer autoDownloadDelayMinutes;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Integer maximumEpisodes;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Integer audioQuality;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private DownloadType downloadType;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String videoQuality;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String videoEncoding;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String subtitleLanguages;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String subtitleFormat;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
