package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import top.asimov.pigeon.model.entity.Channel;

public interface ChannelMapper extends BaseMapper<Channel> {

  @Select("SELECT c.id, c.handler, c.custom_title, c.title, c.cover_url, c.custom_cover_ext, c.description, c.source, c.audio_quality, c.last_updated_at, c.sync_state, " +
      "max(e.published_at) as last_published_at " +
      "FROM channel c LEFT JOIN episode e ON c.id = e.channel_id " +
      "GROUP BY c.id, c.handler, c.custom_title, c.title, c.cover_url, c.description, c.source, c.audio_quality, c.last_updated_at, c.sync_state " +
      "ORDER BY (CASE WHEN last_published_at IS NULL THEN '9999' ELSE last_published_at END) DESC")
  List<Channel> selectChannelsByLastUploadedAt();
}
