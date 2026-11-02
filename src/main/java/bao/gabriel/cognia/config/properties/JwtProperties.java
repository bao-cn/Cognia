package bao.gabriel.cognia.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * HS256 secret. Must contain at least 32 bytes.
     */
    private String secret;

    private String issuer = "cognia";

    private Duration accessExpiration = Duration.ofMinutes(30);

    private Duration refreshExpiration = Duration.ofDays(7);

    private Duration rememberMeExpiration = Duration.ofDays(30);
}
