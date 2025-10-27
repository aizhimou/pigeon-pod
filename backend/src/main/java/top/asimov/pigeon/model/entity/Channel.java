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
@TableName("channel")
public class Channel extends Feed {

  private String handler;

  @Override
  public FeedType getType() {
    return FeedType.CHANNEL;
  }
}
