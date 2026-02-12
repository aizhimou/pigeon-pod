package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.asimov.pigeon.model.entity.PlaylistEpisodeDetailRetry;

public interface PlaylistEpisodeDetailRetryMapper extends BaseMapper<PlaylistEpisodeDetailRetry> {

  @Insert("""
      "INSERT INTO playlist_episode_detail_retry 
      (playlist_id, episode_id, position, approximate_published_at, retry_count, next_retry_at, last_error, created_at, updated_at) 
      VALUES (#{playlistId}, #{episodeId}, #{position}, #{approximatePublishedAt}, #{retryCount}, #{nextRetryAt}, #{lastError}, #{createdAt}, #{updatedAt}) 
      ON CONFLICT(playlist_id, episode_id) DO UPDATE SET 
      position = excluded.position, 
      approximate_published_at = excluded.approximate_published_at, 
      retry_count = playlist_episode_detail_retry.retry_count, 
      next_retry_at = excluded.next_retry_at, 
      last_error = excluded.last_error, 
      updated_at = excluded.updated_at
      """
  )
  void upsert(PlaylistEpisodeDetailRetry retry);


  @Select(""" 
      SELECT * FROM playlist_episode_detail_retry 
      WHERE next_retry_at <= #{now} 
      ORDER BY next_retry_at ASC, id ASC 
      LIMIT #{limit}
      """)
  List<PlaylistEpisodeDetailRetry> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);


  @Update(""" 
      UPDATE playlist_episode_detail_retry SET retry_count = #{retryCount}, 
      next_retry_at = #{nextRetryAt}, last_error = #{lastError}, updated_at = #{updatedAt}
      WHERE id = #{id}
      """
  )
  void updateRetryMeta(@Param("id") Long id, @Param("retryCount") Integer retryCount,
      @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("lastError") String lastError,
      @Param("updatedAt") LocalDateTime updatedAt);
}
