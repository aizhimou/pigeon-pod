package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.FeedDefaultsMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.Channel;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.model.entity.FeedDefaults;
import top.asimov.pigeon.model.entity.Playlist;
import top.asimov.pigeon.model.enums.DownloadType;

@Service
@Transactional
public class FeedDefaultsService {

  public static final int DEFAULT_ROW_ID = 0;
  private static final int BUILTIN_AUTO_DOWNLOAD_LIMIT = 3;
  private static final int BUILTIN_AUTO_DOWNLOAD_DELAY_MINUTES = 0;
  private static final DownloadType BUILTIN_DOWNLOAD_TYPE = DownloadType.AUDIO;
  private static final String BUILTIN_SUBTITLE_LANGUAGES = "zh,en";
  private static final String BUILTIN_SUBTITLE_FORMAT = "vtt";
  private static final String APPLY_MODE_OVERRIDE_ALL = "override_all";
  private static final String APPLY_MODE_FILL_EMPTY = "fill_empty";

  private final FeedDefaultsMapper feedDefaultsMapper;
  private final ChannelMapper channelMapper;
  private final PlaylistMapper playlistMapper;

  public FeedDefaultsService(FeedDefaultsMapper feedDefaultsMapper, ChannelMapper channelMapper,
      PlaylistMapper playlistMapper) {
    this.feedDefaultsMapper = feedDefaultsMapper;
    this.channelMapper = channelMapper;
    this.playlistMapper = playlistMapper;
  }

  public FeedDefaults getFeedDefaults() {
    return getEffectiveFeedDefaults();
  }

  public FeedDefaults getEffectiveFeedDefaults() {
    return mergeWithBuiltIn(ensureDefaultsRow());
  }

  public FeedDefaults updateFeedDefaults(FeedDefaults request) {
    FeedDefaults existing = ensureDefaultsRow();
    FeedDefaults normalized = normalizeForPersistence(request);

    existing.setAutoDownloadLimit(normalized.getAutoDownloadLimit());
    existing.setAutoDownloadDelayMinutes(normalized.getAutoDownloadDelayMinutes());
    existing.setMaximumEpisodes(normalized.getMaximumEpisodes());
    existing.setAudioQuality(normalized.getAudioQuality());
    existing.setDownloadType(normalized.getDownloadType());
    existing.setVideoQuality(normalized.getVideoQuality());
    existing.setVideoEncoding(normalized.getVideoEncoding());
    existing.setSubtitleLanguages(normalized.getSubtitleLanguages());
    existing.setSubtitleFormat(normalized.getSubtitleFormat());
    existing.setUpdatedAt(LocalDateTime.now());

    feedDefaultsMapper.updateById(existing);
    return getEffectiveFeedDefaults();
  }

  public Map<String, Object> applyFeedDefaultsToFeeds(String mode) {
    String normalizedMode = normalizeApplyMode(mode);
    FeedDefaults defaults = getEffectiveFeedDefaults();
    boolean overrideAll = APPLY_MODE_OVERRIDE_ALL.equals(normalizedMode);

    int updatedChannels = applyFeedDefaultsToChannels(defaults, overrideAll);
    int updatedPlaylists = applyFeedDefaultsToPlaylists(defaults, overrideAll);

    Map<String, Object> result = new HashMap<>();
    result.put("mode", normalizedMode);
    result.put("updatedChannels", updatedChannels);
    result.put("updatedPlaylists", updatedPlaylists);
    result.put("updatedFeeds", updatedChannels + updatedPlaylists);
    return result;
  }

  public void applyDefaultsIfMissing(Feed feed) {
    if (feed == null) {
      return;
    }

    FeedDefaults defaults = getEffectiveFeedDefaults();

    if (feed.getAutoDownloadLimit() == null || feed.getAutoDownloadLimit() <= 0) {
      feed.setAutoDownloadLimit(defaults.getAutoDownloadLimit());
    }
    if (feed.getAutoDownloadDelayMinutes() == null || feed.getAutoDownloadDelayMinutes() < 0) {
      feed.setAutoDownloadDelayMinutes(defaults.getAutoDownloadDelayMinutes());
    }
    if (feed.getMaximumEpisodes() == null) {
      feed.setMaximumEpisodes(defaults.getMaximumEpisodes());
    }
    if (feed.getAudioQuality() == null) {
      feed.setAudioQuality(defaults.getAudioQuality());
    }
    if (feed.getDownloadType() == null) {
      feed.setDownloadType(defaults.getDownloadType());
    }
    if (!StringUtils.hasText(feed.getVideoQuality()) && StringUtils.hasText(defaults.getVideoQuality())) {
      feed.setVideoQuality(defaults.getVideoQuality());
    }
    if (!StringUtils.hasText(feed.getVideoEncoding()) && StringUtils.hasText(defaults.getVideoEncoding())) {
      feed.setVideoEncoding(defaults.getVideoEncoding());
    }
    if (!StringUtils.hasText(feed.getSubtitleLanguages())) {
      feed.setSubtitleLanguages(defaults.getSubtitleLanguages());
    }
    if (!StringUtils.hasText(feed.getSubtitleFormat())) {
      feed.setSubtitleFormat(defaults.getSubtitleFormat());
    }
  }

  public int resolveDefaultAutoDownloadLimit() {
    Integer limit = getEffectiveFeedDefaults().getAutoDownloadLimit();
    return limit != null && limit > 0 ? limit : BUILTIN_AUTO_DOWNLOAD_LIMIT;
  }

  public int resolveDefaultAutoDownloadDelayMinutes() {
    Integer delay = getEffectiveFeedDefaults().getAutoDownloadDelayMinutes();
    return delay != null && delay > 0 ? delay : BUILTIN_AUTO_DOWNLOAD_DELAY_MINUTES;
  }

  private int applyFeedDefaultsToChannels(FeedDefaults defaults, boolean overrideAll) {
    List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<>());
    int updated = 0;
    for (Channel channel : channels) {
      LambdaUpdateWrapper<Channel> wrapper = new LambdaUpdateWrapper<>();
      wrapper.eq(Channel::getId, channel.getId());
      boolean changed = false;

      changed |= applyIntegerField(wrapper, Channel::getAutoDownloadLimit, channel.getAutoDownloadLimit(),
          defaults.getAutoDownloadLimit(), overrideAll, this::isAutoDownloadLimitEmpty);
      changed |= applyIntegerField(wrapper, Channel::getAutoDownloadDelayMinutes, channel.getAutoDownloadDelayMinutes(),
          defaults.getAutoDownloadDelayMinutes(), overrideAll, Objects::isNull);
      changed |= applyIntegerField(wrapper, Channel::getMaximumEpisodes, channel.getMaximumEpisodes(),
          defaults.getMaximumEpisodes(), overrideAll, this::isMaximumEpisodesEmpty);
      changed |= applyIntegerField(wrapper, Channel::getAudioQuality, channel.getAudioQuality(),
          defaults.getAudioQuality(), overrideAll, Objects::isNull);
      changed |= applyEnumField(wrapper, Channel::getDownloadType, channel.getDownloadType(),
          defaults.getDownloadType(), overrideAll);
      changed |= applyTextField(wrapper, Channel::getVideoQuality, channel.getVideoQuality(),
          defaults.getVideoQuality(), overrideAll);
      changed |= applyTextField(wrapper, Channel::getVideoEncoding, channel.getVideoEncoding(),
          defaults.getVideoEncoding(), overrideAll);
      changed |= applyTextField(wrapper, Channel::getSubtitleLanguages, channel.getSubtitleLanguages(),
          defaults.getSubtitleLanguages(), overrideAll);
      changed |= applyTextField(wrapper, Channel::getSubtitleFormat, channel.getSubtitleFormat(),
          defaults.getSubtitleFormat(), overrideAll);

      if (!changed) {
        continue;
      }
      channelMapper.update(null, wrapper);
      updated++;
    }
    return updated;
  }

  private int applyFeedDefaultsToPlaylists(FeedDefaults defaults, boolean overrideAll) {
    List<Playlist> playlists = playlistMapper.selectList(new LambdaQueryWrapper<>());
    int updated = 0;
    for (Playlist playlist : playlists) {
      LambdaUpdateWrapper<Playlist> wrapper = new LambdaUpdateWrapper<>();
      wrapper.eq(Playlist::getId, playlist.getId());
      boolean changed = false;

      changed |= applyIntegerField(wrapper, Playlist::getAutoDownloadLimit, playlist.getAutoDownloadLimit(),
          defaults.getAutoDownloadLimit(), overrideAll, this::isAutoDownloadLimitEmpty);
      changed |= applyIntegerField(wrapper, Playlist::getAutoDownloadDelayMinutes, playlist.getAutoDownloadDelayMinutes(),
          defaults.getAutoDownloadDelayMinutes(), overrideAll, Objects::isNull);
      changed |= applyIntegerField(wrapper, Playlist::getMaximumEpisodes, playlist.getMaximumEpisodes(),
          defaults.getMaximumEpisodes(), overrideAll, this::isMaximumEpisodesEmpty);
      changed |= applyIntegerField(wrapper, Playlist::getAudioQuality, playlist.getAudioQuality(),
          defaults.getAudioQuality(), overrideAll, Objects::isNull);
      changed |= applyEnumField(wrapper, Playlist::getDownloadType, playlist.getDownloadType(),
          defaults.getDownloadType(), overrideAll);
      changed |= applyTextField(wrapper, Playlist::getVideoQuality, playlist.getVideoQuality(),
          defaults.getVideoQuality(), overrideAll);
      changed |= applyTextField(wrapper, Playlist::getVideoEncoding, playlist.getVideoEncoding(),
          defaults.getVideoEncoding(), overrideAll);
      changed |= applyTextField(wrapper, Playlist::getSubtitleLanguages, playlist.getSubtitleLanguages(),
          defaults.getSubtitleLanguages(), overrideAll);
      changed |= applyTextField(wrapper, Playlist::getSubtitleFormat, playlist.getSubtitleFormat(),
          defaults.getSubtitleFormat(), overrideAll);

      if (!changed) {
        continue;
      }
      playlistMapper.update(null, wrapper);
      updated++;
    }
    return updated;
  }

  private <T> boolean applyIntegerField(LambdaUpdateWrapper<T> wrapper,
      com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Integer> field,
      Integer currentValue,
      Integer targetValue,
      boolean overrideAll,
      java.util.function.Predicate<Integer> isEmptyPredicate) {
    if (!overrideAll && !isEmptyPredicate.test(currentValue)) {
      return false;
    }
    if (!overrideAll && targetValue == null) {
      return false;
    }
    if (Objects.equals(currentValue, targetValue)) {
      return false;
    }
    wrapper.set(field, targetValue);
    return true;
  }

  private <T> boolean applyEnumField(LambdaUpdateWrapper<T> wrapper,
      com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, DownloadType> field,
      DownloadType currentValue,
      DownloadType targetValue,
      boolean overrideAll) {
    if (!overrideAll && currentValue != null) {
      return false;
    }
    if (!overrideAll && targetValue == null) {
      return false;
    }
    if (Objects.equals(currentValue, targetValue)) {
      return false;
    }
    wrapper.set(field, targetValue);
    return true;
  }

  private <T> boolean applyTextField(LambdaUpdateWrapper<T> wrapper,
      com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> field,
      String currentValue,
      String targetValue,
      boolean overrideAll) {
    String normalizedCurrent = normalizeNullableText(currentValue);
    String normalizedTarget = normalizeNullableText(targetValue);
    if (!overrideAll && StringUtils.hasText(normalizedCurrent)) {
      return false;
    }
    if (!overrideAll && !StringUtils.hasText(normalizedTarget)) {
      return false;
    }
    if (Objects.equals(normalizedCurrent, normalizedTarget)) {
      return false;
    }
    wrapper.set(field, normalizedTarget);
    return true;
  }

  private String normalizeApplyMode(String mode) {
    String normalized = mode == null ? "" : mode.trim().toLowerCase();
    if (APPLY_MODE_OVERRIDE_ALL.equals(normalized) || APPLY_MODE_FILL_EMPTY.equals(normalized)) {
      return normalized;
    }
    throw new BusinessException("Invalid apply mode");
  }

  private FeedDefaults ensureDefaultsRow() {
    FeedDefaults defaults = feedDefaultsMapper.selectById(DEFAULT_ROW_ID);
    if (defaults != null) {
      return defaults;
    }

    FeedDefaults builtInDefaults = createBuiltInDefaults();
    try {
      feedDefaultsMapper.insert(builtInDefaults);
    } catch (Exception ignore) {
      // concurrent insert, ignore and re-query
    }
    FeedDefaults created = feedDefaultsMapper.selectById(DEFAULT_ROW_ID);
    return created != null ? created : builtInDefaults;
  }

  private FeedDefaults createBuiltInDefaults() {
    LocalDateTime now = LocalDateTime.now();
    return FeedDefaults.builder()
        .id(DEFAULT_ROW_ID)
        .autoDownloadLimit(BUILTIN_AUTO_DOWNLOAD_LIMIT)
        .autoDownloadDelayMinutes(BUILTIN_AUTO_DOWNLOAD_DELAY_MINUTES)
        .maximumEpisodes(null)
        .audioQuality(null)
        .downloadType(BUILTIN_DOWNLOAD_TYPE)
        .videoQuality(null)
        .videoEncoding(null)
        .subtitleLanguages(BUILTIN_SUBTITLE_LANGUAGES)
        .subtitleFormat(BUILTIN_SUBTITLE_FORMAT)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  private FeedDefaults mergeWithBuiltIn(FeedDefaults source) {
    FeedDefaults defaults = source == null ? createBuiltInDefaults() : source;

    if (defaults.getAutoDownloadLimit() == null || defaults.getAutoDownloadLimit() <= 0) {
      defaults.setAutoDownloadLimit(BUILTIN_AUTO_DOWNLOAD_LIMIT);
    }
    if (defaults.getAutoDownloadDelayMinutes() == null || defaults.getAutoDownloadDelayMinutes() < 0) {
      defaults.setAutoDownloadDelayMinutes(BUILTIN_AUTO_DOWNLOAD_DELAY_MINUTES);
    }
    defaults.setMaximumEpisodes(normalizeMaximumEpisodes(defaults.getMaximumEpisodes()));
    defaults.setAudioQuality(normalizeAudioQuality(defaults.getAudioQuality()));
    if (defaults.getDownloadType() == null) {
      defaults.setDownloadType(BUILTIN_DOWNLOAD_TYPE);
    }
    defaults.setVideoQuality(normalizeNullableText(defaults.getVideoQuality()));
    defaults.setVideoEncoding(normalizeNullableText(defaults.getVideoEncoding()));

    defaults.setSubtitleLanguages(normalizeNullableText(defaults.getSubtitleLanguages()));
    defaults.setSubtitleFormat(normalizeNullableText(defaults.getSubtitleFormat()));
    return defaults;
  }

  private FeedDefaults normalizeForPersistence(FeedDefaults request) {
    FeedDefaults source = request == null ? new FeedDefaults() : request;
    return FeedDefaults.builder()
        .id(DEFAULT_ROW_ID)
        .autoDownloadLimit(normalizeAutoDownloadLimit(source.getAutoDownloadLimit()))
        .autoDownloadDelayMinutes(normalizeAutoDownloadDelay(source.getAutoDownloadDelayMinutes()))
        .maximumEpisodes(normalizeMaximumEpisodes(source.getMaximumEpisodes()))
        .audioQuality(normalizeAudioQuality(source.getAudioQuality()))
        .downloadType(source.getDownloadType() == null ? BUILTIN_DOWNLOAD_TYPE : source.getDownloadType())
        .videoQuality(normalizeNullableText(source.getVideoQuality()))
        .videoEncoding(normalizeNullableText(source.getVideoEncoding()))
        .subtitleLanguages(normalizeNullableText(source.getSubtitleLanguages()))
        .subtitleFormat(normalizeNullableText(source.getSubtitleFormat()))
        .build();
  }

  private Integer normalizeAutoDownloadLimit(Integer autoDownloadLimit) {
    if (autoDownloadLimit == null || autoDownloadLimit <= 0) {
      return BUILTIN_AUTO_DOWNLOAD_LIMIT;
    }
    return autoDownloadLimit;
  }

  private Integer normalizeAutoDownloadDelay(Integer autoDownloadDelayMinutes) {
    if (autoDownloadDelayMinutes == null || autoDownloadDelayMinutes < 0) {
      return BUILTIN_AUTO_DOWNLOAD_DELAY_MINUTES;
    }
    return autoDownloadDelayMinutes;
  }

  private Integer normalizeMaximumEpisodes(Integer maximumEpisodes) {
    if (maximumEpisodes == null || maximumEpisodes <= 0) {
      return null;
    }
    return maximumEpisodes;
  }

  private Integer normalizeAudioQuality(Integer audioQuality) {
    if (audioQuality == null) {
      return null;
    }
    return Math.max(0, Math.min(audioQuality, 10));
  }

  private String normalizeNullableText(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private boolean isAutoDownloadLimitEmpty(Integer value) {
    return value == null || value <= 0;
  }

  private boolean isMaximumEpisodesEmpty(Integer value) {
    return value == null || value <= 0;
  }
}
