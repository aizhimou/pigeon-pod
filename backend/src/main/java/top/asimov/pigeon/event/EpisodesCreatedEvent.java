package top.asimov.pigeon.event;

import java.util.List;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EpisodesCreatedEvent extends ApplicationEvent {

  private final List<String> episodeIds;
  private final String context;

  public EpisodesCreatedEvent(Object source, List<String> episodeIds) {
    this(source, episodeIds, null);
  }

  public EpisodesCreatedEvent(Object source, List<String> episodeIds, String context) {
    super(source);
    this.episodeIds = episodeIds;
    this.context = context;
  }

}
