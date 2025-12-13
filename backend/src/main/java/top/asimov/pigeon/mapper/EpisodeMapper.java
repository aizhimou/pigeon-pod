package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.asimov.pigeon.model.entity.Episode;

public interface EpisodeMapper extends BaseMapper<Episode> {

  @Update("update episode set download_status = #{downloadStatus} where id = #{id}")
  void updateDownloadStatus(String id, String downloadStatus);

  @Select("SELECT COALESCE(c.title, p.title) FROM episode e "
      + "LEFT JOIN channel c ON c.id = e.channel_id "
      + "LEFT JOIN playlist_episode pe ON pe.episode_id = e.id "
      + "LEFT JOIN playlist p ON p.id = pe.playlist_id "
      + "WHERE e.id = #{episodeId} "
      + "LIMIT 1")
  String getFeedNameByEpisodeId(String episodeId);

  @Select("SELECT e.* FROM playlist_episode pe "
      + "JOIN episode e ON pe.episode_id = e.id "
      + "WHERE pe.playlist_id = #{playlistId} "
      + "ORDER BY pe.published_at DESC")
  java.util.List<Episode> selectEpisodesByPlaylistId(String playlistId);

  /**
   * 获取指定频道中已完成下载的节目列表，按发布时间倒序排序，并支持 offset/limit。
   * 主要用于 EpisodeCleaner 只选出需要清理的旧节目。
   */
  @Select("SELECT e.* FROM episode e "
      + "WHERE e.channel_id = #{channelId} "
      + "AND e.download_status = 'COMPLETED' "
      + "ORDER BY e.published_at DESC "
      + "LIMIT #{offset}, #{limit}")
  java.util.List<Episode> selectCompletedEpisodesByChannelWithOffset(
      @Param("channelId") String channelId,
      @Param("offset") long offset,
      @Param("limit") long limit);

  /**
   * 获取指定播放列表中已完成下载的节目列表，按播放列表内的 published_at 倒序排序，并支持 offset/limit。
   * 主要用于 EpisodeCleaner 只选出需要清理的旧节目。
   */
  @Select("SELECT e.* FROM playlist_episode pe "
      + "JOIN episode e ON pe.episode_id = e.id "
      + "WHERE pe.playlist_id = #{playlistId} "
      + "AND e.download_status = 'COMPLETED' "
      + "ORDER BY pe.published_at DESC "
      + "LIMIT #{offset}, #{limit}")
  java.util.List<Episode> selectCompletedEpisodesByPlaylistWithOffset(
      @Param("playlistId") String playlistId,
      @Param("offset") long offset,
      @Param("limit") long limit);

  /**
   * 按状态分组统计Episode数量（一次查询返回所有状态的统计）
   */
  @Select("SELECT download_status as status, COUNT(*) as count FROM episode GROUP BY download_status")
  java.util.List<java.util.Map<String, Object>> countGroupByStatus();

  /**
   * 分页查询指定状态的Episode（关联Channel和Playlist信息）
   * 注意：由于Episode可能同时属于Channel和Playlist，这里优先返回Channel信息
   */
  @Select("SELECT e.* FROM episode e "
      + "WHERE e.download_status = #{status} "
      + "ORDER BY e.created_at DESC")
  Page<Episode> selectEpisodesByStatusWithFeedInfo(Page<Episode> page, @Param("status") String status);
}
