package top.asimov.pigeon.model.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicEpisodeShareResponse {

  private String id;
  private String title;
  private String description;
  private String coverUrl;
  private String sourceUrl;
  private String mediaUrl;
  private String mediaType;
  private LocalDateTime publishedAt;
  private String duration;
}
