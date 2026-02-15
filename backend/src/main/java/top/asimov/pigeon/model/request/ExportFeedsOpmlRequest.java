package top.asimov.pigeon.model.request;

import java.util.List;
import lombok.Data;

@Data
public class ExportFeedsOpmlRequest {

  private List<FeedSelection> feeds;

  @Data
  public static class FeedSelection {

    private String id;
    private String type;
  }
}
