package top.asimov.pigeon.service;

import java.util.List;
import java.util.Map;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.enums.FeedType;
import top.asimov.pigeon.model.response.FeedConfigUpdateResult;
import top.asimov.pigeon.model.response.FeedPack;
import top.asimov.pigeon.model.response.FeedSaveResult;

public interface FeedHandler<T extends Feed> {

  FeedType getType();

  List<T> list();

  T detail(String id);

  String getSubscribeUrl(String id);

  FeedConfigUpdateResult updateConfig(String id, Map<String, Object> payload);

  FeedPack<T> fetch(Map<String, ?> payload);

  /**
   * 拉取历史节目信息（仅采集与入库，不触发内容下载）。
   */
  List<Episode> fetchHistory(String id);

  FeedPack<T> preview(Map<String, Object> payload);

  FeedSaveResult<T> add(Map<String, Object> payload);

  void delete(String id);

  void refresh(String id);
}
