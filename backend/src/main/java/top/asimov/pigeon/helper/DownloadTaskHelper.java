package top.asimov.pigeon.helper;

import java.util.concurrent.Semaphore;
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
  private final TaskStatusHelper taskStatusHelper;
  private final DownloadHandler downloadHandler;
  private final Semaphore downloadSlots;

  @Autowired
  public DownloadTaskHelper(ThreadPoolTaskExecutor downloadTaskExecutor,
      @Lazy TaskStatusHelper taskStatusHelper, DownloadHandler downloadHandler) {
    this.downloadTaskExecutor = downloadTaskExecutor;
    this.taskStatusHelper = taskStatusHelper;
    this.downloadHandler = downloadHandler;
    this.downloadSlots = new Semaphore(downloadTaskExecutor.getMaxPoolSize(), true);
  }

  /**
   * 尝试提交单个下载任务
   *
   * @param episodeId 节目ID
   * @return true if successful, false otherwise
   */
  public boolean submitDownloadTask(String episodeId) {
    if (!downloadSlots.tryAcquire()) {
      log.debug("下载槽位已满，任务保持为原状态，等待后续补位: {}", episodeId);
      return false;
    }

    boolean submitted = false;
    try {
      // 提交前将状态标记为 DOWNLOADING（通过代理Bean调用，确保新事务生效）
      boolean updated = taskStatusHelper.tryMarkDownloading(episodeId);
      if (updated) {
        // 状态更新成功后，提交到线程池
        downloadTaskExecutor.execute(() -> {
          try {
            downloadHandler.download(episodeId);
          } finally {
            downloadSlots.release();
          }
        });
        submitted = true;
        log.debug("任务已提交执行: {}", episodeId);
        return true;
      }
      return false;
    } catch (RejectedExecutionException e) {
      // 提交失败，回滚状态到PENDING（通过代理Bean调用）
      taskStatusHelper.rollbackFromDownloadingToPending(episodeId);
      log.warn("线程池不可用，任务被拒绝，状态回滚为 PENDING: {}", episodeId);
      return false;
    } finally {
      if (!submitted) {
        downloadSlots.release();
      }
    }
  }
}
