package top.asimov.pigeon.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

  @TableId
  private String id;
  private String username;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String password;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String salt;

  private String apiKey;

  private String dateFormat;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private transient String newPassword; // For password update

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private transient String token; // For token management

  private transient boolean hasCookie;

}
