package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import top.asimov.pigeon.model.entity.Channel;

public interface ChannelMapper extends BaseMapper<Channel> {

  @Select("SELECT c.id, c.handler, c.custom_title, c.title, c.cover_url, c.custom_cover_ext, c.description, c.source, c.audio_quality, c.last_updated_at, c.auto_download_enabled, c.auto_download_delay_minutes, " +
      "max(e.published_at) as last_published_at " +
      "FROM channel c LEFT JOIN episode e ON c.id = e.channel_id " +
      "GROUP BY c.id, c.handler, c.custom_title, c.title, c.cover_url, c.description, c.source, c.audio_quality, c.last_updated_at, c.auto_download_enabled, c.auto_download_delay_minutes " +
      "ORDER BY (CASE WHEN last_published_at IS NULL THEN '9999' ELSE last_published_at END) DESC")
  List<Channel> selectChannelsByLastUploadedAt();

  /**
   * 查询已完成下载数量超过 maximumEpisodes 的频道统计信息。
   * 仅统计 download_status = 'COMPLETED' 的节目数量。
   *
   * 返回字段：
   * - channel_id
   * - channel_title
   * - completed_count
   * - maximum_episodes
   */
  @Select("SELECT c.id AS channel_id, c.title AS channel_title, " +
      "COUNT(e.id) AS completed_count, c.maximum_episodes AS maximum_episodes " +
      "FROM channel c " +
      "JOIN episode e ON e.channel_id = c.id AND e.download_status = 'COMPLETED' " +
      "WHERE c.maximum_episodes IS NOT NULL AND c.maximum_episodes > 0 " +
      "GROUP BY c.id, c.title, c.maximum_episodes " +
      "HAVING COUNT(e.id) > c.maximum_episodes")
  java.util.List<java.util.Map<String, Object>> selectChannelCompletedOverLimit();
}
