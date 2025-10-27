package top.asimov.pigeon.handler;

import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.Feed;
import top.asimov.pigeon.service.FeedFactory;
import top.asimov.pigeon.service.FeedHandler;

public abstract class AbstractFeedHandler<T extends Feed> implements FeedHandler<T> {

  private final MessageSource messageSource;
  private final FeedFactory feedFactory;

  protected AbstractFeedHandler(FeedFactory feedFactory, MessageSource messageSource) {
    this.feedFactory = feedFactory;
    this.messageSource = messageSource;
    this.feedFactory.register(getType(), getFeedClass());
  }

  protected MessageSource getMessageSource() {
    return messageSource;
  }

  protected T buildFeed(Map<String, Object> payload) {
    return feedFactory.create(getType(), payload);
  }

  protected String resolveSourceUrl(Map<String, ?> request, String fallbackKey) {
    String sourceUrl = asText(request.get("originalUrl"));
    if (!StringUtils.hasText(sourceUrl)) {
      sourceUrl = asText(request.get("sourceUrl"));
    }
    if (!StringUtils.hasText(sourceUrl)) {
      sourceUrl = asText(request.get(fallbackKey));
    }
    if (!StringUtils.hasText(sourceUrl)) {
      throw new BusinessException(messageSource
          .getMessage("feed.source.url.missing", null, LocaleContextHolder.getLocale()));
    }
    return sourceUrl;
  }

  private String asText(Object value) {
    return value instanceof String ? (String) value : null;
  }

  protected abstract Class<T> getFeedClass();
}
