package bao.gabriel.cognia.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.verification-code")
public class VerificationCodeProperties {

    private int length = 6;

    private Duration ttl = Duration.ofMinutes(5);

    private Duration resendInterval = Duration.ofSeconds(60);

    private int maxAttempts = 5;

    private int dailyLimit = 10;
}
