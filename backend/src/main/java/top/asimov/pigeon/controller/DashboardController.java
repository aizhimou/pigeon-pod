package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.model.response.EpisodeStatisticsResponse;
import top.asimov.pigeon.service.DashboardService;

@SaCheckLogin
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  /**
   * 获取各状态的Episode统计数量
   */
  @GetMapping("/statistics")
  public SaResult getStatistics() {
    EpisodeStatisticsResponse statistics = dashboardService.getStatistics();
    return SaResult.data(statistics);
  }

  /**
   * 分页查询指定状态的Episode列表
   *
   * @param status 任务状态
   * @param page 当前页码
   * @param size 每页大小
   */
  @GetMapping("/episodes")
  public SaResult getEpisodes(
      @RequestParam EpisodeStatus status,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "10") Integer size) {
    Page<Episode> episodePage = dashboardService.getEpisodesByStatus(status,
        new Page<>(page, size));
    return SaResult.data(episodePage);
  }
}

