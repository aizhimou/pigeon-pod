package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import top.asimov.pigeon.model.entity.Playlist;

public interface PlaylistMapper extends BaseMapper<Playlist> {

  @Select("SELECT p.id, p.owner_id, p.custom_title, p.title, p.custom_cover_ext, p.cover_url, p.description, p.source, p.audio_quality, p.last_updated_at, p.sync_state, "
      + "MAX(pe.published_at) AS last_published_at "
      + "FROM playlist p "
      + "LEFT JOIN playlist_episode pe ON p.id = pe.playlist_id "
      + "GROUP BY "
      + "p.id, p.owner_id, p.custom_title, p.title, p.custom_cover_ext, p.cover_url, p.description, p.source, p.audio_quality, p.last_updated_at, p.sync_state "
      + "ORDER BY CASE WHEN last_published_at IS NULL THEN '9999' ELSE last_published_at END DESC")
  List<Playlist> selectPlaylistsByLastPublishedAt();

  @Select("SELECT p.* FROM playlist p "
      + "INNER JOIN playlist_episode pe ON p.id = pe.playlist_id "
      + "WHERE pe.episode_id = #{episodeId} "
      + "ORDER BY pe.published_at DESC, pe.id DESC LIMIT 1")
  Playlist selectLatestByEpisodeId(String episodeId);
}
