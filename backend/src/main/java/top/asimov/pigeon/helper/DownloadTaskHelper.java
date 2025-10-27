package top.asimov.pigeon.helper;

import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import top.asimov.pigeon.handler.DownloadHandler;

@Log4j2
@Service
public class DownloadTaskHelper {

  private final ThreadPoolTaskExecutor downloadTaskExecutor;
  private final TaskStatusHelper taskStatushelper;
  private final DownloadHandler downloadHandler;

  @Autowired
  public DownloadTaskHelper(ThreadPoolTaskExecutor downloadTaskExecutor,
      @Lazy TaskStatusHelper taskStatushelper, DownloadHandler downloadHandler) {
    this.downloadTaskExecutor = downloadTaskExecutor;
    this.taskStatushelper = taskStatushelper;
    this.downloadHandler = downloadHandler;
  }

  /**
   * 尝试提交单个下载任务
   *
   * @param episodeId 节目ID
   * @return true if successful, false otherwise
   */
  public boolean submitDownloadTask(String episodeId) {
    try {
      // 提交前将状态标记为 DOWNLOADING（通过代理Bean调用，确保新事务生效）
      boolean updated = taskStatushelper.tryMarkDownloading(episodeId);
      if (updated) {
        // 状态更新成功后，提交到线程池
        downloadTaskExecutor.execute(() -> downloadHandler.download(episodeId));
        log.debug("任务已提交执行: {}", episodeId);
        return true;
      }
      return false;
    } catch (RejectedExecutionException e) {
      // 提交失败，回滚状态到PENDING（通过代理Bean调用）
      taskStatushelper.rollbackFromDownloadingToPending(episodeId);
      log.warn("线程池不可用，任务被拒绝，状态回滚为 PENDING: {}", episodeId);
      return false;
    }
  }
}
