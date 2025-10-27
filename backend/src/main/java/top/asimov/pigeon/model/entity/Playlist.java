package top.asimov.pigeon.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import top.asimov.pigeon.model.enums.FeedType;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("playlist")
public class Playlist extends Feed {

  private String ownerId;
  private Integer episodeSort;

  @Override
  public FeedType getType() {
    return FeedType.PLAYLIST;
  }
}
