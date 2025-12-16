package top.asimov.pigeon.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DownloadTaskEvent extends ApplicationEvent {

  private final DownloadTargetType targetType;
  private final DownloadAction action;
  private final String targetId;
  private final Integer downloadNumber;
  private final String titleContainKeywords;
  private final String titleExcludeKeywords;
  private final String descriptionContainKeywords;
  private final String descriptionExcludeKeywords;
  private final Integer minimumDuration;
  private final Integer maximumDuration;

  public DownloadTaskEvent(Object source, DownloadTargetType targetType, DownloadAction action,
      String targetId, Integer downloadNumber, String titleContainKeywords,
      String titleExcludeKeywords, String descriptionContainKeywords,
      String descriptionExcludeKeywords, Integer minimumDuration, Integer maximumDuration) {
    super(source);
    this.targetType = targetType;
    this.action = action;
    this.targetId = targetId;
    this.downloadNumber = downloadNumber;
    this.titleContainKeywords = titleContainKeywords;
    this.titleExcludeKeywords = titleExcludeKeywords;
    this.descriptionContainKeywords = descriptionContainKeywords;
    this.descriptionExcludeKeywords = descriptionExcludeKeywords;
    this.minimumDuration = minimumDuration;
    this.maximumDuration = maximumDuration;
  }

  public enum DownloadTargetType {
    CHANNEL,
    PLAYLIST
  }

  public enum DownloadAction {
    INIT
  }
}
