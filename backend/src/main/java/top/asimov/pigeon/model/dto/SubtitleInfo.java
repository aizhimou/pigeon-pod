package top.asimov.pigeon.model.dto;

import java.io.File;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubtitleInfo {

  private String language;
  private String format;
  private File file;
}
