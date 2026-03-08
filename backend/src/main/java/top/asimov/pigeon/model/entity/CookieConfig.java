package top.asimov.pigeon.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("cookie_config")
public class CookieConfig {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String platform;
  private String cookiesContent;
  private Boolean enabled;
  private String sourceType;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
