package bao.gabriel.cognia.security;

import bao.gabriel.cognia.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Thin wrapper around JJWT. Only signing/parsing is handled here; session
 * validation and revocation live in {@link TokenService}.
 */
@Component
public class JwtUtil {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("jwt.secret must contain at least 32 bytes for HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret);
    }

    public String createToken(Long userId, String username, String type, String jti, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(username)
                .id(jti)
                .claim("uid", String.valueOf(userId))
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.ExpiredJwtException when the token is expired
     * @throws io.jsonwebtoken.JwtException          when signature/format is invalid
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long userId(Claims claims) {
        String uid = claims.get("uid", String.class);
        return uid == null ? null : Long.valueOf(uid);
    }

    public String tokenType(Claims claims) {
        return claims.get("typ", String.class);
    }
}
