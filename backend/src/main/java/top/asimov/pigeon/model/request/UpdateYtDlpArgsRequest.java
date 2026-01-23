package top.asimov.pigeon.model.request;

import java.util.List;
import lombok.Data;

@Data
public class UpdateYtDlpArgsRequest {

  private String id;
  private List<String> ytDlpArgs;
}
