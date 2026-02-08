package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import top.asimov.pigeon.model.entity.Playlist;

public interface PlaylistMapper extends BaseMapper<Playlist> {

  @Select("SELECT p.id, p.owner_id, p.custom_title, p.title, p.custom_cover_ext, p.cover_url, p.description, p.source, p.audio_quality, p.last_updated_at, p.auto_download_enabled, p.auto_download_delay_minutes, "
      + "MAX(pe.published_at) AS last_published_at "
      + "FROM playlist p "
      + "LEFT JOIN playlist_episode pe ON p.id = pe.playlist_id "
      + "GROUP BY "
      + "p.id, p.owner_id, p.custom_title, p.title, p.custom_cover_ext, p.cover_url, p.description, p.source, p.audio_quality, p.last_updated_at, p.auto_download_enabled, p.auto_download_delay_minutes "
      + "ORDER BY CASE WHEN last_published_at IS NULL THEN '9999' ELSE last_published_at END DESC")
  List<Playlist> selectPlaylistsByLastPublishedAt();

  @Select("SELECT p.* FROM playlist p "
      + "INNER JOIN playlist_episode pe ON p.id = pe.playlist_id "
      + "WHERE pe.episode_id = #{episodeId} "
      + "ORDER BY pe.published_at DESC, pe.id DESC LIMIT 1")
  Playlist selectLatestByEpisodeId(String episodeId);

  /**
   * 查询已完成下载数量超过 maximumEpisodes 的播放列表统计信息。
   * 仅统计 download_status = 'COMPLETED' 的节目数量。
   *
   * 返回字段：
   * - playlist_id
   * - playlist_title
   * - completed_count
   * - maximum_episodes
   */
  @Select("SELECT p.id AS playlist_id, p.title AS playlist_title, " +
      "COUNT(e.id) AS completed_count, p.maximum_episodes AS maximum_episodes " +
      "FROM playlist p " +
      "JOIN playlist_episode pe ON pe.playlist_id = p.id " +
      "JOIN episode e ON e.id = pe.episode_id AND e.download_status = 'COMPLETED' " +
      "WHERE p.maximum_episodes IS NOT NULL AND p.maximum_episodes > 0 " +
      "GROUP BY p.id, p.title, p.maximum_episodes " +
      "HAVING COUNT(e.id) > p.maximum_episodes")
  java.util.List<java.util.Map<String, Object>> selectPlaylistCompletedOverLimit();
}
