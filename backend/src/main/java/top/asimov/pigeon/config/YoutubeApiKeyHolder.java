package top.asimov.pigeon.config;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.service.SystemConfigService;

/**
 * Holds the YouTube API key so that it can be accessed globally without repeatedly querying the database. The key is
 * loaded once at startup and kept in an {@link AtomicReference}, with helper methods to update or refresh the cached
 * value when it changes.
 */
@Slf4j
@Component
public class YoutubeApiKeyHolder {

  private static final AtomicReference<String> YOUTUBE_API_KEY = new AtomicReference<>();

  private final SystemConfigService systemConfigService;

  public YoutubeApiKeyHolder(SystemConfigService systemConfigService) {
    this.systemConfigService = systemConfigService;
  }

  @PostConstruct
  public void init() {
    refreshYoutubeApiKey();
  }

  /**
   * Reload the latest YouTube API key from the database and cache it in memory.
   */
  public void refreshYoutubeApiKey() {
    String youtubeApiKey = systemConfigService.getYoutubeApiKey();
    if (!StringUtils.hasText(youtubeApiKey)) {
      log.info("[youtube-api] api key cache refreshed: status=empty");
      YOUTUBE_API_KEY.set(null);
      return;
    }
    YOUTUBE_API_KEY.set(youtubeApiKey);
    log.info("[youtube-api] api key cache refreshed: status=loaded");
  }

  /**
   * Get the cached YouTube API key.
   *
   * @return the cached API key
   */
  public static String getYoutubeApiKey() {
    String youtubeApiKey = YOUTUBE_API_KEY.get();
    if (!StringUtils.hasText(youtubeApiKey)) {
      log.debug("[youtube-api] api key cache requested: status=empty");
    }
    return youtubeApiKey;
  }

  /**
   * Update the cached YouTube API key with a new value.
   *
   * @param youtubeApiKey the new API key value
   */
  public static void updateYoutubeApiKey(String youtubeApiKey) {
    YOUTUBE_API_KEY.set(youtubeApiKey);
    if (!StringUtils.hasText(youtubeApiKey)) {
      log.info("[youtube-api] api key cache updated: status=empty");
    } else {
      log.info("[youtube-api] api key cache updated: status=loaded");
    }
  }

  /**
   * Retrieve the YouTube API key and throw a localized BusinessException if it is missing.
   *
   * @param messageSource message source for localization (optional)
   * @return non-empty YouTube API key
   */
  public static String requireYoutubeApiKey(MessageSource messageSource) {
    String youtubeApiKey = YoutubeApiKeyHolder.getYoutubeApiKey();
    if (!StringUtils.hasText(youtubeApiKey)) {
      String message = messageSource != null
          ? messageSource.getMessage("youtube.api.key.not.set", null,
          LocaleContextHolder.getLocale())
          : "YouTube API key is not set";
      throw new BusinessException(message);
    }
    return youtubeApiKey;
  }
}
