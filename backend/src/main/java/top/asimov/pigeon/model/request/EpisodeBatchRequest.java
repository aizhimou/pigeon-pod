package top.asimov.pigeon.model.request;

import java.util.List;
import lombok.Data;
import top.asimov.pigeon.model.enums.EpisodeBatchAction;
import top.asimov.pigeon.model.enums.EpisodeStatus;

@Data
public class EpisodeBatchRequest {

  private EpisodeBatchAction action;

  private EpisodeStatus status;

  private List<String> episodeIds;
}

