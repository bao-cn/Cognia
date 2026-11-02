package bao.gabriel.cognia.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.init")
public class InitProperties {

    private boolean enabled = true;

    private String adminUsername = "admin";

    private String adminPassword = "Admin@123";

    private String adminEmail = "admin@cognia.local";
}
