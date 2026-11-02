package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.config.properties.JwtProperties;
import com.alibaba.fastjson2.JSON;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and validates JWT based sessions.
 *
 * <p>Redis keys:
 * <ul>
 *   <li>{@code auth:session:{accessJti}} &rarr; access session JSON (TTL = access token TTL)</li>
 *   <li>{@code auth:refresh:{refreshJti}} &rarr; user id (TTL = refresh token TTL)</li>
 *   <li>{@code auth:user:tokens:{userId}} &rarr; set of all jtis of that user</li>
 * </ul>
 * Keeping a server-side allow-list makes logout and permission changes take
 * effect immediately, which plain JWT cannot do.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String SESSION_PREFIX = "auth:session:";
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String USER_TOKENS_PREFIX = "auth:user:tokens:";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final JwtProperties properties;

    public TokenPair issue(LoginUser user, boolean rememberMe, String ip, String userAgent) {
        Duration accessTtl = properties.getAccessExpiration();
        Duration refreshTtl = rememberMe ? properties.getRememberMeExpiration() : properties.getRefreshExpiration();

        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String accessToken = jwtUtil.createToken(user.getUserId(), user.getUsername(),
                JwtUtil.TOKEN_TYPE_ACCESS, accessJti, accessTtl);
        String refreshToken = jwtUtil.createToken(user.getUserId(), user.getUsername(),
                JwtUtil.TOKEN_TYPE_REFRESH, refreshJti, refreshTtl);

        AccessSession session = AccessSession.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .jti(accessJti)
                .refreshJti(refreshJti)
                .roles(user.getRoles())
                .permissions(user.getPermissions())
                .loginTime(System.currentTimeMillis())
                .ip(ip)
                .userAgent(userAgent)
                .build();

        redis.opsForValue().set(SESSION_PREFIX + accessJti, JSON.toJSONString(session), accessTtl);
        redis.opsForValue().set(REFRESH_PREFIX + refreshJti, String.valueOf(user.getUserId()), refreshTtl);

        String tokenSetKey = USER_TOKENS_PREFIX + user.getUserId();
        redis.opsForSet().add(tokenSetKey, accessJti, refreshJti);
        redis.expire(tokenSetKey, refreshTtl);

        return new TokenPair(accessToken, refreshToken, "Bearer",
                accessTtl.toSeconds(), refreshTtl.toSeconds());
    }

    /**
     * Validates an access token and rebuilds the principal from the Redis
     * session snapshot.
     */
    public LoginUser authenticate(String token) {
        Claims claims = parse(token, JwtUtil.TOKEN_TYPE_ACCESS);
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        String json = redis.opsForValue().get(SESSION_PREFIX + jti);
        if (json == null) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED, "登录状态已失效，请重新登录");
        }

        AccessSession session;
        try {
            session = JSON.parseObject(json, AccessSession.class);
        } catch (Exception ex) {
            log.warn("Corrupted access session for jti {}", jti, ex);
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        if (session == null || session.getUserId() == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        return LoginUser.builder()
                .userId(session.getUserId())
                .username(session.getUsername())
                .nickname(session.getNickname())
                .email(session.getEmail())
                .avatar(session.getAvatar())
                .jti(session.getJti() == null ? jti : session.getJti())
                .roles(session.getRoles())
                .permissions(session.getPermissions())
                .build();
    }

    /**
     * Consumes a refresh token (single use) and returns the owning user id.
     */
    public Long consumeRefreshToken(String refreshToken) {
        Claims claims = parse(refreshToken, JwtUtil.TOKEN_TYPE_REFRESH);
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        String userId = redis.opsForValue().getAndDelete(REFRESH_PREFIX + jti);
        if (userId == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "刷新凭证已失效，请重新登录");
        }

        Long parsedUserId;
        try {
            parsedUserId = Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        redis.opsForSet().remove(USER_TOKENS_PREFIX + parsedUserId, jti);
        return parsedUserId;
    }

    /**
     * Revokes the current access token and its paired refresh token.
     */
    public void logout(LoginUser user) {
        if (user == null || user.getUserId() == null) {
            return;
        }
        String jti = user.getJti();
        String setKey = USER_TOKENS_PREFIX + user.getUserId();
        if (jti != null && !jti.isBlank()) {
            String sessionKey = SESSION_PREFIX + jti;
            String sessionJson = redis.opsForValue().get(sessionKey);
            redis.delete(sessionKey);
            redis.opsForSet().remove(setKey, jti);

            if (sessionJson != null) {
                try {
                    AccessSession session = JSON.parseObject(sessionJson, AccessSession.class);
                    if (session != null && session.getRefreshJti() != null) {
                        redis.delete(REFRESH_PREFIX + session.getRefreshJti());
                        redis.opsForSet().remove(setKey, session.getRefreshJti());
                    }
                } catch (Exception ex) {
                    log.warn("Could not read session while logging out jti {}", jti, ex);
                }
            }
        }
    }

    /**
     * Revokes every session of a user. Called after a password, status or
     * role/permission change so the new rules take effect immediately.
     */
    public void revokeAll(Long userId) {
        if (userId == null) {
            return;
        }
        String setKey = USER_TOKENS_PREFIX + userId;
        Set<String> jtis = redis.opsForSet().members(setKey);
        if (jtis != null) {
            for (String jti : jtis) {
                redis.delete(SESSION_PREFIX + jti);
                redis.delete(REFRESH_PREFIX + jti);
            }
        }
        redis.delete(setKey);
    }

    private Claims parse(String token, String expectedType) {
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        if (!expectedType.equals(jwtUtil.tokenType(claims))) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        return claims;
    }
}
