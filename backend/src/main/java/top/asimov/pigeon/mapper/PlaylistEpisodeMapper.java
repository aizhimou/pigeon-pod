package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.web.bind.annotation.PathVariable;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.PlaylistEpisode;

public interface PlaylistEpisodeMapper extends BaseMapper<PlaylistEpisode> {

  @Select("SELECT COUNT(1) FROM playlist_episode WHERE playlist_id = #{playlistId}")
  long countByPlaylistId(String playlistId);

  @Select("SELECT e.* FROM playlist_episode pe "
      + "JOIN episode e ON pe.episode_id = e.id "
      + "WHERE pe.playlist_id = #{playlistId} "
      + "ORDER BY pe.position, pe.published_at DESC "
      + "LIMIT #{offset}, #{pageSize}")
  List<Episode> selectEpisodePageByPlaylistId(@Param("playlistId") String playlistId,
      @Param("offset") long offset, @Param("pageSize") long pageSize);

  @Select("SELECT * FROM playlist_episode WHERE episode_id = #{episodeId} "
      + "ORDER BY published_at DESC LIMIT 1")
  PlaylistEpisode selectLatestByEpisodeId(String episodeId);

  @Select("select count(1) "
      + "from episode "
      + "where id = #{episodeId} "
      + "and channel_id not in (select id from channel) "
      + "and id not in (select episode_id from playlist_episode)")
  long isOrhanEpisode(@Param("episodeId") String episodeId);

  @Delete("DELETE FROM playlist_episode WHERE playlist_id = #{playlistId}")
  int deleteByPlaylistId(String playlistId);

  @Select(
      "SELECT * FROM playlist_episode WHERE playlist_id = #{playlistId} ORDER BY published_at ASC LIMIT 1")
  PlaylistEpisode selectEarliestByPlaylistId(String playlistId);

  @Select("SELECT COUNT(1) FROM playlist_episode WHERE playlist_id = #{playlistId} AND episode_id = #{episodeId}")
  int countByPlaylistAndEpisode(@Param("playlistId") String playlistId,
      @Param("episodeId") String episodeId);

  @Insert("INSERT INTO playlist_episode (playlist_id, episode_id, position, published_at) "
      + "VALUES (#{playlistId}, #{episodeId}, #{position}, #{publishedAt})")
  int insertMapping(@Param("playlistId") String playlistId, @Param("episodeId") String episodeId,
      @PathVariable("position") Long position, @Param("publishedAt") LocalDateTime publishedAt);

  @Update("UPDATE playlist_episode SET published_at = #{publishedAt}, position = #{position} "
      + "WHERE playlist_id = #{playlistId} AND episode_id = #{episodeId}")
  int updateMapping(@Param("playlistId") String playlistId, @Param("episodeId") String episodeId,
      @PathVariable("position") Long position, @Param("publishedAt") LocalDateTime publishedAt);
}
