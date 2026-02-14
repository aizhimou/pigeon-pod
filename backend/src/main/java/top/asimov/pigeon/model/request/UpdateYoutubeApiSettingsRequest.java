package top.asimov.pigeon.model.request;

import lombok.Data;

@Data
public class UpdateYoutubeApiSettingsRequest {

  private String id;
  private String youtubeApiKey;
  private Integer youtubeDailyLimitUnits;
}
