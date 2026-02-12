package top.asimov.pigeon.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
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
  private LocalDateTime lastSnapshotAt;
  private Integer lastSnapshotSize;
  private Integer lastSyncAddedCount;
  private Integer lastSyncRemovedCount;
  private Integer lastSyncMovedCount;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String syncError;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime syncErrorAt;

  @Override
  public FeedType getType() {
    return FeedType.PLAYLIST;
  }
}
