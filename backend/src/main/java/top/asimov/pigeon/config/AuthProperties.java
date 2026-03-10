package top.asimov.pigeon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pigeon.auth")
public class AuthProperties {

  private boolean enabled = true;
}
