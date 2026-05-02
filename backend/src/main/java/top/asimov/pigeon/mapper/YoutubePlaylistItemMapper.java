package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.asimov.pigeon.model.entity.YoutubePlaylistItem;

public interface YoutubePlaylistItemMapper extends BaseMapper<YoutubePlaylistItem> {

  @Select("SELECT * FROM youtube_playlist_item WHERE playlist_id = #{playlistId}")
  List<YoutubePlaylistItem> selectByPlaylistId(@Param("playlistId") String playlistId);

  @Select("SELECT * FROM youtube_playlist_item WHERE playlist_id = #{playlistId} AND presence_status = 'ACTIVE'")
  List<YoutubePlaylistItem> selectActiveByPlaylistId(@Param("playlistId") String playlistId);

  @Select("""
      SELECT * FROM youtube_playlist_item
      WHERE playlist_id = #{playlistId}
      AND presence_status = 'ACTIVE'
      AND materialization_status IN ('PENDING', 'FAILED')
      ORDER BY position ASC, id ASC
      """)
  List<YoutubePlaylistItem> selectPendingMaterialization(@Param("playlistId") String playlistId);

  @Select("""
      SELECT * FROM youtube_playlist_item
      WHERE playlist_id = #{playlistId}
      AND presence_status = 'ACTIVE'
      AND materialization_status = 'LINKED'
      AND episode_id IS NOT NULL
      ORDER BY position ASC, id ASC
      """)
  List<YoutubePlaylistItem> selectActiveLinked(@Param("playlistId") String playlistId);

  @Select("""
      SELECT * FROM youtube_playlist_item
      WHERE playlist_id = #{playlistId}
      AND presence_status = 'ACTIVE'
      AND materialization_status = 'LINKED'
      AND auto_dispatch_status = 'PENDING'
      AND episode_id IS NOT NULL
      ORDER BY position ASC, id ASC
      """)
  List<YoutubePlaylistItem> selectPendingDispatch(@Param("playlistId") String playlistId);

  @Insert("""
      INSERT INTO youtube_playlist_item
      (playlist_id, playlist_item_id, video_id, episode_id, item_added_at, video_published_at, position,
       item_privacy_status, source_channel_id, source_channel_name, source_channel_url,
       presence_status, materialization_status, auto_dispatch_status,
       first_seen_at, last_seen_at, removed_at, last_error, created_at, updated_at)
      VALUES
      (#{playlistId}, #{playlistItemId}, #{videoId}, #{episodeId}, #{itemAddedAt}, #{videoPublishedAt}, #{position},
       #{itemPrivacyStatus}, #{sourceChannelId}, #{sourceChannelName}, #{sourceChannelUrl},
       #{presenceStatus}, #{materializationStatus}, #{autoDispatchStatus},
       #{firstSeenAt}, #{lastSeenAt}, #{removedAt}, #{lastError}, #{createdAt}, #{updatedAt})
      """)
  int insertItem(YoutubePlaylistItem item);

  @Update("""
      UPDATE youtube_playlist_item
      SET video_id = #{videoId},
          item_added_at = #{itemAddedAt},
          video_published_at = #{videoPublishedAt},
          position = #{position},
          item_privacy_status = #{itemPrivacyStatus},
          source_channel_id = #{sourceChannelId},
          source_channel_name = #{sourceChannelName},
          source_channel_url = #{sourceChannelUrl},
          presence_status = 'ACTIVE',
          last_seen_at = #{lastSeenAt},
          removed_at = NULL,
          last_error = NULL,
          updated_at = #{updatedAt}
      WHERE playlist_id = #{playlistId}
      AND playlist_item_id = #{playlistItemId}
      """)
  int markActive(YoutubePlaylistItem item);

  @Update("""
      UPDATE youtube_playlist_item
      SET presence_status = 'REMOVED',
          removed_at = #{removedAt},
          updated_at = #{updatedAt}
      WHERE id = #{id}
      """)
  int markRemoved(@Param("id") Long id, @Param("removedAt") LocalDateTime removedAt,
      @Param("updatedAt") LocalDateTime updatedAt);

  @Update("""
      UPDATE youtube_playlist_item
      SET episode_id = #{episodeId},
          materialization_status = #{materializationStatus},
          last_error = #{lastError},
          updated_at = #{updatedAt}
      WHERE id = #{id}
      """)
  int updateMaterialization(@Param("id") Long id, @Param("episodeId") String episodeId,
      @Param("materializationStatus") String materializationStatus,
      @Param("lastError") String lastError, @Param("updatedAt") LocalDateTime updatedAt);

  @Update("""
      UPDATE youtube_playlist_item
      SET auto_dispatch_status = #{autoDispatchStatus},
          updated_at = #{updatedAt}
      WHERE id = #{id}
      """)
  int updateAutoDispatchStatus(@Param("id") Long id, @Param("autoDispatchStatus") String autoDispatchStatus,
      @Param("updatedAt") LocalDateTime updatedAt);

  @Update("""
      UPDATE youtube_playlist_item
      SET materialization_status = 'PENDING',
          last_error = NULL,
          updated_at = #{updatedAt}
      WHERE playlist_id = #{playlistId}
      AND presence_status = 'ACTIVE'
      AND materialization_status = 'SKIPPED'
      """)
  int resetSkippedMaterialization(@Param("playlistId") String playlistId,
      @Param("updatedAt") LocalDateTime updatedAt);

  @Delete("DELETE FROM youtube_playlist_item WHERE playlist_id = #{playlistId}")
  int deleteByPlaylistId(@Param("playlistId") String playlistId);
}
