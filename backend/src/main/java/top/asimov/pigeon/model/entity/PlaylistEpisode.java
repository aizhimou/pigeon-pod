package top.asimov.pigeon.model.entity;

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
@TableName("playlist_episode")
public class PlaylistEpisode {

  @TableId
  private String id;
  private String playlistId;
  private String episodeId;
  private String coverUrl;
  private LocalDateTime publishedAt;
}
