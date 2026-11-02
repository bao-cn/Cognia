package bao.gabriel.cognia.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.login")
public class LoginProperties {

    private int maxFailures = 5;

    private Duration lockDuration = Duration.ofMinutes(15);
}
