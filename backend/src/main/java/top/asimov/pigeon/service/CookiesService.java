package top.asimov.pigeon.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import top.asimov.pigeon.config.StorageProperties;

@Log4j2
@Service
public class CookiesService {

  private final StorageProperties storageProperties;
  private final SystemConfigService systemConfigService;
  private final MessageSource messageSource;

  public CookiesService(StorageProperties storageProperties, SystemConfigService systemConfigService,
      MessageSource messageSource) {
    this.storageProperties = storageProperties;
    this.systemConfigService = systemConfigService;
    this.messageSource = messageSource;
  }

  /**
   * 创建临时cookies文件
   *
   * @param userId 用户ID
   * @return 临时cookies文件路径，如果用户没有cookies配置则返回null
   */
  public String createTempCookiesFile(String userId) {
    String cookiesContent = systemConfigService.getCookiesContent();
    if (!org.springframework.util.StringUtils.hasText(cookiesContent)) {
      log.debug("没有存储 cookie 文件，不使用 cookie 下载。");
      return null;
    }

    try {
      // 创建临时目录
      String tempDir = storageProperties.getTempDir() + "/cookies/";
      File dir = new File(tempDir);
      if (!dir.exists() && !dir.mkdirs()) {
        throw new RuntimeException(messageSource.getMessage("system.create.temp.directory.failed",
            new Object[]{tempDir}, LocaleContextHolder.getLocale()));
      }

      // 创建临时cookies文件
      String tempFileName = "cookies_" + userId + "_" + System.currentTimeMillis() + ".txt";
      String tempFilePath = tempDir + tempFileName;

      try (FileWriter writer = new FileWriter(tempFilePath)) {
        writer.write(cookiesContent);
      }

      log.debug("创建临时cookies文件: {}", tempFilePath);
      return tempFilePath;

    } catch (IOException e) {
      log.error("创建临时cookies文件失败", e);
      throw new RuntimeException(messageSource.getMessage("system.create.temp.cookies.failed",
          null, LocaleContextHolder.getLocale()), e);
    }
  }

  /**
   * 删除临时cookies文件
   *
   * @param filePath 临时文件路径
   */
  public void deleteTempCookiesFile(String filePath) {
    if (filePath == null) {
      return;
    }

    try {
      Path path = Paths.get(filePath);
      Files.deleteIfExists(path);
      log.debug("删除临时cookies文件: {}", filePath);
    } catch (IOException e) {
      log.warn("删除临时cookies文件失败: {}", filePath, e);
    }
  }

}
