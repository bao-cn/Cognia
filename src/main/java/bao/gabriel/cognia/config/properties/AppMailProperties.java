package bao.gabriel.cognia.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {

    /**
     * Development helper: include the verification code in the API response.
     * SMTP parameters are stored in the database, not in this file. Never
     * enable this in production.
     */
    private boolean exposeCode = false;
}
