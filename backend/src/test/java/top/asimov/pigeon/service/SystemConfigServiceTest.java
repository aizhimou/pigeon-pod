package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import top.asimov.pigeon.mapper.SystemConfigMapper;
import top.asimov.pigeon.model.entity.SystemConfig;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

  @Mock
  private SystemConfigMapper systemConfigMapper;

  @Mock
  private MessageSource messageSource;

  @InjectMocks
  private SystemConfigService systemConfigService;

  @BeforeEach
  void setUp() {
  }

  @Test
  void testSslConfigMerge() {
    SystemConfig existing = new SystemConfig();
    existing.setId(SystemConfig.SINGLETON_ID);
    existing.setSslEnabled(false);
    existing.setSslPort(8080);
    existing.setHttpsOnly(false);

    SystemConfig incoming = new SystemConfig();
    incoming.setSslEnabled(true);
    incoming.setSslPort(8443);
    incoming.setHttpsOnly(true);

    when(systemConfigMapper.selectById(SystemConfig.SINGLETON_ID)).thenReturn(existing);

    SystemConfig result = systemConfigService.buildCandidate(incoming);

    assertTrue(result.getSslEnabled());
    assertEquals(8443, result.getSslPort());
    assertTrue(result.getHttpsOnly());
  }

  @Test
  void testSslConfigDefaults() {
    SystemConfig config = new SystemConfig();
    systemConfigService.normalizeDefaults(config);

    assertFalse(config.getSslEnabled());
    assertEquals(8443, config.getSslPort());
    assertFalse(config.getHttpsOnly());
  }
}
