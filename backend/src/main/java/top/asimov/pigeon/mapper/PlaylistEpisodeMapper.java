package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.entity.PlaylistEpisode;

public interface PlaylistEpisodeMapper extends BaseMapper<PlaylistEpisode> {

  @Select("SELECT COUNT(1) FROM playlist_episode WHERE playlist_id = #{playlistId}")
  long countByPlaylistId(String playlistId);

  @Select("""
          <script>
          SELECT COUNT(1) FROM playlist_episode pe
          JOIN episode e ON pe.episode_id = e.id
          WHERE pe.playlist_id = #{playlistId}
          <if test='search != null and search != ""'>
          AND e.title LIKE CONCAT('%', #{search}, '%')
          </if>
          <if test='statusFilter != null and statusFilter != ""'>
          AND e.download_status = #{statusFilter}
          </if>
          </script>
      """)
  long countByPlaylistIdWithFilters(@Param("playlistId") String playlistId,
      @Param("search") String search, @Param("statusFilter") String statusFilter);

  @Select("""
      <script>
      SELECT e.*,
             pe.source_channel_id AS source_channel_id,
             pe.source_channel_name AS source_channel_name,
             pe.source_channel_url AS source_channel_url
      FROM playlist_episode pe
      JOIN episode e ON pe.episode_id = e.id
      WHERE pe.playlist_id = #{playlistId}
      <if test='search != null and search != ""'>
      AND e.title LIKE CONCAT('%', #{search}, '%')
      </if>
      <if test='statusFilter != null and statusFilter != ""'>
      AND e.download_status = #{statusFilter}
      </if>
      ORDER BY
      <choose>
      <when test='sortOrder == "oldest"'>
      pe.published_at ASC, pe.id ASC
      </when>
      <when test='sortOrder == "newest"'>
      pe.published_at DESC, pe.id ASC
      </when>
      <otherwise>
      pe.position, pe.id DESC
      </otherwise>
      </choose>
      LIMIT #{offset}, #{pageSize}
      </script>
      """
  )
  List<Episode> selectEpisodePageByPlaylistIdWithFilters(@Param("playlistId") String playlistId,
      @Param("offset") long offset, @Param("pageSize") long pageSize,
      @Param("search") String search, @Param("statusFilter") String statusFilter,
      @Param("sortOrder") String sortOrder);

  @Select("SELECT * FROM playlist_episode WHERE episode_id = #{episodeId} "
      + "ORDER BY published_at DESC LIMIT 1")
  PlaylistEpisode selectLatestByEpisodeId(String episodeId);

  @Select("select count(1) "
      + "from episode "
      + "where id = #{episodeId} "
      + "and (channel_id is null or channel_id not in (select id from channel)) "
      + "and id not in (select episode_id from playlist_episode)")
  long isOrhanEpisode(@Param("episodeId") String episodeId);

  @Select("SELECT * FROM playlist_episode WHERE playlist_id = #{playlistId}")
  List<PlaylistEpisode> selectMappingsByPlaylistId(@Param("playlistId") String playlistId);

  @Select(
      "SELECT * FROM playlist_episode WHERE playlist_id = #{playlistId} ORDER BY published_at ASC LIMIT 1")
  PlaylistEpisode selectEarliestByPlaylistId(String playlistId);

  @Select("SELECT COUNT(1) FROM playlist_episode WHERE playlist_id = #{playlistId} AND episode_id = #{episodeId}")
  int countByPlaylistAndEpisode(@Param("playlistId") String playlistId,
      @Param("episodeId") String episodeId);

  @Insert("INSERT INTO playlist_episode (playlist_id, episode_id, position, published_at, "
      + "source_channel_id, source_channel_name, source_channel_url) "
      + "VALUES (#{playlistId}, #{episodeId}, #{position}, #{publishedAt}, #{sourceChannelId}, "
      + "#{sourceChannelName}, #{sourceChannelUrl})")
  int insertMapping(@Param("playlistId") String playlistId, @Param("episodeId") String episodeId,
      @Param("position") Long position, @Param("publishedAt") LocalDateTime publishedAt,
      @Param("sourceChannelId") String sourceChannelId,
      @Param("sourceChannelName") String sourceChannelName,
      @Param("sourceChannelUrl") String sourceChannelUrl);

  @Update("UPDATE playlist_episode SET published_at = #{publishedAt}, position = #{position}, "
      + "source_channel_id = #{sourceChannelId}, source_channel_name = #{sourceChannelName}, "
      + "source_channel_url = #{sourceChannelUrl} "
      + "WHERE playlist_id = #{playlistId} AND episode_id = #{episodeId}")
  int updateMapping(@Param("playlistId") String playlistId, @Param("episodeId") String episodeId,
      @Param("position") Long position, @Param("publishedAt") LocalDateTime publishedAt,
      @Param("sourceChannelId") String sourceChannelId,
      @Param("sourceChannelName") String sourceChannelName,
      @Param("sourceChannelUrl") String sourceChannelUrl);
}
