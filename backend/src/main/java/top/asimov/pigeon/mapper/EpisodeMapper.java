package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.asimov.pigeon.model.entity.Episode;

public interface EpisodeMapper extends BaseMapper<Episode> {

  @Delete("delete from episode "
      + "where id in (select m.id"
      + "             from (select id,"
      + "                          channel_id,"
      + "                          row_number() over (partition by channel_id order by published_at) as rn"
      + "                   from episode where download_status = 'COMPLETED') m"
      + "                      join(select a.channel_id,"
      + "                                  a.channel_cnt,"
      + "                                  b.maximum_episodes,"
      + "                                  (a.channel_cnt - b.maximum_episodes) as minus_num"
      + "                           from (select channel_id, count(0) as channel_cnt"
      + "                                 from episode"
      + "                                 group by channel_id) a"
      + "                                    join channel b on a.channel_id = b.id"
      + "                           where a.channel_cnt > b.maximum_episodes) n"
      + "                          on m.channel_id = n.channel_id"
      + "             where m.rn <= n.minus_num)")
  void deleteEpisodesOverChannelMaximum();

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
