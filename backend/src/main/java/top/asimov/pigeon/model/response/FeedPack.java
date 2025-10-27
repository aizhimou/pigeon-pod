package top.asimov.pigeon.model.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Feed;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedPack<T extends Feed> {

  private T feed;
  private List<Episode> episodes;
}
