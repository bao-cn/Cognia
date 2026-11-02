package bao.gabriel.cognia.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.settings")
public class SettingsProperties {

    /**
     * Secret used to encrypt SMTP credentials and the Turnstile secret at rest
     * (AES-GCM). Override in
     * production through SETTINGS_SECRET. Must contain at least 32 characters.
     */
    private String secret;
}
