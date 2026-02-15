package top.asimov.pigeon.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedRefreshResult {

  /**
   * 本次刷新是否发现了新的节目。
   */
  private boolean hasNewEpisodes;

  /**
   * 本次刷新新增的节目数量。
   */
  private int newEpisodeCount;

  /**
   * 面向用户的国际化提示信息，例如： “本次刷新未发现新节目” 或 “本次新增 3 个节目”。
   */
  private String message;
}
