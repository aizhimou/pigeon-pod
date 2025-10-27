package top.asimov.pigeon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.model.enums.FeedType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.Feed;

@Component
public class FeedFactory {

  private final ObjectMapper objectMapper;
  private final MessageSource messageSource;
  private final Map<FeedType, Class<? extends Feed>> registry;

  public FeedFactory(ObjectMapper objectMapper, MessageSource messageSource) {
    this.objectMapper = objectMapper;
    this.messageSource = messageSource;
    this.registry = new ConcurrentHashMap<>();
  }

  public <T extends Feed> void register(FeedType type, Class<T> feedClass) {
    registry.put(type, feedClass);
  }

  @SuppressWarnings("unchecked")
  public <T extends Feed> T create(FeedType type, Map<String, Object> payload) {
    Class<? extends Feed> feedClass = registry.get(type);
    if (feedClass == null) {
      throw new BusinessException(messageSource
          .getMessage("feed.type.invalid", new Object[]{type.name()},
              LocaleContextHolder.getLocale()));
    }
    return (T) objectMapper.convertValue(payload, feedClass);
  }
}
