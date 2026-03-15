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
@TableName("notification_config")
public class NotificationConfig {

  public static final int SINGLETON_ID = 0;

  @TableId
  private Integer id;

  private Boolean emailEnabled;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String emailHost;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Integer emailPort;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String emailUsername;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String emailPassword;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String emailFrom;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String emailTo;
  private Boolean emailStarttlsEnabled;
  private Boolean emailSslEnabled;

  private Boolean webhookEnabled;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String webhookUrl;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String webhookCustomHeaders;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String webhookJsonBody;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @TableField(exist = false)
  private transient Boolean hasEmailPassword;
}
