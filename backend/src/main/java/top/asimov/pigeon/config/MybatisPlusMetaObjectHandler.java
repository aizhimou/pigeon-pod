package top.asimov.pigeon.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

  @Override
  public void insertFill(MetaObject metaObject) {
    log.debug("[database] meta insert fill started");
    this.strictInsertFill(metaObject, "subscribedAt", LocalDateTime::now, LocalDateTime.class);
    this.strictInsertFill(metaObject, "lastUpdatedAt", LocalDateTime::now, LocalDateTime.class);
  }

  @Override
  public void updateFill(MetaObject metaObject) {
    log.debug("[database] meta update fill started");
    this.setFieldValByName("lastUpdatedAt", LocalDateTime.now(), metaObject);
  }
}
