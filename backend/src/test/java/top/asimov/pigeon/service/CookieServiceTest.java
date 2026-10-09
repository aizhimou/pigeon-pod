package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.asimov.pigeon.config.StorageProperties;
import top.asimov.pigeon.mapper.CookieConfigMapper;
import top.asimov.pigeon.model.entity.CookieConfig;
import top.asimov.pigeon.model.enums.CookiePlatform;

class CookieServiceTest {

  private CookieConfigMapper cookieConfigMapper;
  private StorageProperties storageProperties;
  private CookieService cookieService;

  @BeforeEach
  void setUp() {
    cookieConfigMapper = mock(CookieConfigMapper.class);
    storageProperties = new StorageProperties();
    cookieService = new CookieService(cookieConfigMapper, storageProperties);
  }

  @Test
  void getCookieHeader_returnsNullWhenNoConfigOrDisabled() {
    when(cookieConfigMapper.selectOne(any())).thenReturn(null);
    assertNull(cookieService.getCookieHeader(CookiePlatform.BILIBILI));

    CookieConfig disabled = CookieConfig.builder()
        .platform("BILIBILI")
        .enabled(false)
        .cookiesContent("# Netscape HTTP Cookie File\n.bilibili.com\tTRUE\t/\tFALSE\t0\tSESSDATA\t123")
        .build();
    when(cookieConfigMapper.selectOne(any())).thenReturn(disabled);
    assertNull(cookieService.getCookieHeader(CookiePlatform.BILIBILI));
  }

  @Test
  void getCookieHeader_formatsNetscapeCookiesCorrectly() {
    String netscapeContent = "# Netscape HTTP Cookie File\n"
        + "# This is a generated file!  Do not edit.\n\n"
        + ".bilibili.com\tTRUE\t/\tTRUE\t1800000000\tSESSDATA\txyz123\n"
        + ".bilibili.com\tTRUE\t/\tTRUE\t1800000000\tbili_jct\ttoken456\n"
        + "space.bilibili.com\tFALSE\t/\tTRUE\t1800000000\tSESSDATA\txyz123\n";

    CookieConfig config = CookieConfig.builder()
        .platform("BILIBILI")
        .enabled(true)
        .cookiesContent(netscapeContent)
        .build();
    when(cookieConfigMapper.selectOne(any())).thenReturn(config);

    String header = cookieService.getCookieHeader(CookiePlatform.BILIBILI);
    assertEquals("SESSDATA=xyz123; bili_jct=token456", header);
  }
}
