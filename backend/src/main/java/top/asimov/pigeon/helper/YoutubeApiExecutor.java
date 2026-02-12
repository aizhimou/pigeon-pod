package top.asimov.pigeon.helper;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.exception.YoutubeAutoSyncBlockedException;
import top.asimov.pigeon.model.enums.YoutubeApiCallContext;
import top.asimov.pigeon.model.enums.YoutubeApiMethod;
import top.asimov.pigeon.service.YoutubeQuotaService;

@Log4j2
@Component
public class YoutubeApiExecutor {

  private final YoutubeQuotaService youtubeQuotaService;

  public YoutubeApiExecutor(YoutubeQuotaService youtubeQuotaService) {
    this.youtubeQuotaService = youtubeQuotaService;
  }

  public <T> T execute(YoutubeApiMethod method, YoutubeExecutable<T> executable) throws IOException {
    YoutubeApiCallContext callContext = YoutubeQuotaContextHolder.get();
    boolean reserved = youtubeQuotaService.reserveAndRecord(method, callContext);
    if (!reserved && callContext == YoutubeApiCallContext.AUTO_SYNC) {
      throw new YoutubeAutoSyncBlockedException(
          "YouTube daily quota reached; auto sync is blocked until the next Pacific day");
    }

    try {
      return executable.execute();
    } catch (GoogleJsonResponseException e) {
      if (youtubeQuotaService.isQuotaExceededError(e)) {
        youtubeQuotaService.markAutoSyncBlockedByRemoteQuota();
        log.warn("YouTube API 返回配额超限错误，自动同步已阻断。status={}, message={}",
            e.getStatusCode(), e.getDetails() == null ? e.getMessage() : e.getDetails().getMessage());
      }
      throw e;
    }
  }

  @FunctionalInterface
  public interface YoutubeExecutable<T> {

    T execute() throws IOException;
  }
}
