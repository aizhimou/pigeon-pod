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

  private String feedMode;
  private String ownerId;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String syncError;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime syncErrorAt;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime lastFullScanAt;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Integer lastFullScanSize;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Integer lastFullScanPages;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime bootstrapCompletedAt;
  private Integer lastSyncInsertedItemCount;
  private Integer lastSyncRemovedItemCount;
  private Integer lastSyncMovedItemCount;
  private Integer lastSyncMaterializedCount;
  private Integer lastSyncDispatchedItemCount;
  private Integer syncIntervalHours;

  @Override
  public FeedType getType() {
    return FeedType.PLAYLIST;
  }
}
