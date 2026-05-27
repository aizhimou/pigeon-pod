package top.asimov.pigeon.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "downloadTaskExecutor")
  public ThreadPoolTaskExecutor downloadTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    // 启用 SQLite WAL 模式后，可以支持适度的并发
    // 设置为 3 个核心线程，平衡并发性能和资源使用
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(3);  // 最大 3 个线程
    // 无队列直交付模式：提交成功即执行，避免“排队”中间状态
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("PP-Downloader-");
    executor.setKeepAliveSeconds(60); // 空闲线程保持时间
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); // 拒绝策略
    executor.initialize();

    log.info("[config] executor configured: name=downloadTaskExecutor corePoolSize={} maxPoolSize={} queueCapacity={}",
        executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

    return executor;
  }

  @Bean(name = "channelSyncTaskExecutor")
  public Executor channelSyncTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(3);
    executor.setThreadNamePrefix("PP-FeedAsync-");
    executor.setKeepAliveSeconds(60);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();

    log.info("[config] executor configured: name=channelSyncTaskExecutor corePoolSize={} maxPoolSize={} queueCapacity={}",
        executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

    return executor;
  }
}
