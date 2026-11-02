package bao.gabriel.cognia.security;

import bao.gabriel.cognia.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private JwtUtil jwtUtil() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.setIssuer("cognia-test");
        properties.setAccessExpiration(Duration.ofMinutes(30));
        return new JwtUtil(properties);
    }

    @Test
    void createsAndParsesAccessToken() {
        JwtUtil jwtUtil = jwtUtil();
        String jti = "test-jti";

        String token = jwtUtil.createToken(42L, "admin", JwtUtil.TOKEN_TYPE_ACCESS, jti, Duration.ofMinutes(5));
        Claims claims = jwtUtil.parse(token);

        assertEquals("admin", claims.getSubject());
        assertEquals("42", claims.get("uid", String.class));
        assertEquals(JwtUtil.TOKEN_TYPE_ACCESS, claims.get("typ", String.class));
        assertEquals(jti, claims.getId());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
        assertEquals(42L, jwtUtil.userId(claims));
    }

    @Test
    void rejectsTamperedToken() {
        JwtUtil jwtUtil = jwtUtil();
        String token = jwtUtil.createToken(1L, "user", JwtUtil.TOKEN_TYPE_ACCESS, "jti", Duration.ofMinutes(5));
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(JwtException.class, () -> jwtUtil.parse(tampered));
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");

        assertThrows(IllegalStateException.class, () -> new JwtUtil(properties));
    }
}
