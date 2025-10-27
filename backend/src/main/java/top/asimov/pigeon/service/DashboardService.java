package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.response.EpisodeStatisticsResponse;

@Log4j2
@Service
public class DashboardService {

  private final EpisodeService episodeService;

  public DashboardService(EpisodeService episodeService) {
    this.episodeService = episodeService;
  }

  /**
   * 获取各状态的Episode统计数量
   */
  public EpisodeStatisticsResponse getStatistics() {
    return episodeService.getStatistics();
  }

  /**
   * 分页查询指定状态的Episode列表
   */
  public Page<Episode> getEpisodesByStatus(EpisodeStatus status, Page<Episode> page) {
    return episodeService.getEpisodesByStatus(status, page);
  }
}

