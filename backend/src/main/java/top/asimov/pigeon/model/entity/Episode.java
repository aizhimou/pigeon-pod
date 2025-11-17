package top.asimov.pigeon.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.beans.Transient;
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
  private String mediaFilePath;
  private String mediaType;
  private String errorLog;
  private Integer retryNumber;
  private LocalDateTime createdAt;

  @TableField(exist = false)
  private transient Long position;

}
