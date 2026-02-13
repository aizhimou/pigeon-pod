package top.asimov.pigeon.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubtitleInfo {

  private String language;
  private String format;
  private String objectKey;
}
