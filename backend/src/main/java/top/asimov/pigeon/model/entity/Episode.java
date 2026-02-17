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
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("episode")
public class Episode {

  @TableId
  private String id;
  private String channelId;
  private String title;
  private String description;
  private LocalDateTime publishedAt;
  private String defaultCoverUrl;
  private String maxCoverUrl;
  private String duration; // in ISO 8601 format
  private String downloadStatus;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String mediaFilePath;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String mediaType;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long mediaSizeBytes;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String mediaEtag;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String errorLog;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Integer retryNumber;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime autoDownloadAfter;
  private LocalDateTime createdAt;

  @TableField(exist = false)
  private transient Long position;
  @TableField(exist = false)
  private String sourceChannelId;
  @TableField(exist = false)
  private String sourceChannelName;
  @TableField(exist = false)
  private String sourceChannelUrl;

}
