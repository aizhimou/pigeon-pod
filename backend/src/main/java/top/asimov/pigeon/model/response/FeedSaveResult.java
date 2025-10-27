package top.asimov.pigeon.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.asimov.pigeon.model.entity.Feed;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedSaveResult<T extends Feed> {

  private T feed;
  private boolean async;
  private String message;
}
