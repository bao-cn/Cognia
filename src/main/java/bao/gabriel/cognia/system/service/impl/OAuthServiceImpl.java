package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.LoginUser;
import bao.gabriel.cognia.security.TokenPair;
import bao.gabriel.cognia.security.TokenService;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.dto.oauth.AdminOAuthProviderResponse;
import bao.gabriel.cognia.system.dto.oauth.PublicOAuthProvider;
import bao.gabriel.cognia.system.dto.oauth.UpdateOAuthProviderRequest;
import bao.gabriel.cognia.system.dto.user.UserCreateRequest;
import bao.gabriel.cognia.system.entity.OAuthProvider;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.entity.UserOAuthIdentity;
import bao.gabriel.cognia.system.mapper.OAuthProviderMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.mapper.UserOAuthIdentityMapper;
import bao.gabriel.cognia.system.service.OAuthService;
import bao.gabriel.cognia.system.service.UserService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthServiceImpl implements OAuthService {

    private static final String STATE_PREFIX = "oauth:state:";
    private final OAuthProviderMapper providerMapper;
    private final UserOAuthIdentityMapper identityMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final TokenService tokenService;
    private final SecurityCryptoService cryptoService;
    private final StringRedisTemplate redis;
    private final RestClient http = RestClient.create();

    @Override
    public List<AdminOAuthProviderResponse> adminProviders() {
        return providerMapper.selectList(new LambdaQueryWrapper<OAuthProvider>().orderByAsc(OAuthProvider::getId))
                .stream().map(this::toAdmin).toList();
    }

    @Override
    public AdminOAuthProviderResponse updateProvider(String code, UpdateOAuthProviderRequest request) {
        OAuthProvider provider = require(code);
        if (request.enabled() != null) provider.setEnabled(request.enabled());
        if (request.clientId() != null) provider.setClientId(blankToNull(request.clientId()));
        if (Boolean.TRUE.equals(request.clearClientSecret())) provider.setClientSecret(null);
        else if (StringUtils.hasText(request.clientSecret())) provider.setClientSecret(cryptoService.encrypt(request.clientSecret().trim()));
        if (request.issuer() != null) provider.setIssuer(blankToNull(request.issuer()));
        if (request.authorizationUri() != null) provider.setAuthorizationUri(blankToNull(request.authorizationUri()));
        if (request.tokenUri() != null) provider.setTokenUri(blankToNull(request.tokenUri()));
        if (request.userinfoUri() != null) provider.setUserinfoUri(blankToNull(request.userinfoUri()));
        if (request.scopes() != null) provider.setScopes(request.scopes().trim());
        if (request.redirectUri() != null) provider.setRedirectUri(blankToNull(request.redirectUri()));
        if (request.usernameClaim() != null) provider.setUsernameClaim(request.usernameClaim().trim());
        if (request.emailClaim() != null) provider.setEmailClaim(request.emailClaim().trim());
        if (request.nameClaim() != null) provider.setNameClaim(request.nameClaim().trim());
        if (request.autoCreate() != null) provider.setAutoCreate(request.autoCreate());
        provider.setUpdateTime(java.time.LocalDateTime.now());
        providerMapper.updateById(provider);
        return toAdmin(provider);
    }

    @Override
    public List<PublicOAuthProvider> publicProviders() {
        return providerMapper.selectList(new LambdaQueryWrapper<OAuthProvider>()
                        .eq(OAuthProvider::getEnabled, true).orderByAsc(OAuthProvider::getId))
                .stream().map(p -> new PublicOAuthProvider(p.getCode(), p.getName(), p.getProviderType())).toList();
    }

    @Override
    public String authorizeUrl(String code, String redirectUri) {
        OAuthProvider provider = require(code);
        if (!provider.enabled()) throw BusinessException.badRequest("该登录方式未启用");
        resolveEndpoints(provider);
        if (!StringUtils.hasText(provider.getAuthorizationUri()) || !StringUtils.hasText(provider.getClientId())) {
            throw BusinessException.badRequest("该登录方式尚未配置完整");
        }
        String state = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(STATE_PREFIX + state, code + "|" + redirectUri, Duration.ofMinutes(10));
        return UriComponentsBuilder.fromUriString(provider.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", provider.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", provider.getScopes())
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    @Override
    public TokenPair callback(String code, String state) {
        if (!StringUtils.hasText(state)) throw BusinessException.badRequest("登录状态无效");
        String raw = redis.opsForValue().get(STATE_PREFIX + state);
        if (raw == null) throw BusinessException.badRequest("登录状态已过期，请重试");
        redis.delete(STATE_PREFIX + state);
        String providerCode = raw.substring(0, raw.indexOf('|'));
        String redirectUri = raw.substring(raw.indexOf('|') + 1);
        OAuthProvider provider = require(providerCode);
        resolveEndpoints(provider);
        String accessToken = exchangeToken(provider, code, redirectUri);
        Map<String, Object> claims = fetchClaims(provider, accessToken, code);
        String subject = first(claims, "sub", "id", "login");
        if (!StringUtils.hasText(subject)) throw BusinessException.badRequest("登录服务未返回用户标识");
        String email = first(claims, provider.getEmailClaim());
        String name = first(claims, provider.getNameClaim());
        String username = first(claims, provider.getUsernameClaim());
        UserOAuthIdentity identity = identityMapper.selectOne(new LambdaQueryWrapper<UserOAuthIdentity>()
                .eq(UserOAuthIdentity::getProvider, provider.getCode())
                .eq(UserOAuthIdentity::getSubject, subject));
        User user;
        if (identity != null) {
            user = userMapper.selectById(identity.getUserId());
            if (user == null) throw BusinessException.badRequest("关联账户不存在");
        } else {
            if (!provider.autoCreate()) throw BusinessException.badRequest("该账户尚未注册");
            user = findOrCreateUser(provider, subject, email, name, username);
            identity = new UserOAuthIdentity();
            identity.setUserId(user.getId());
            identity.setProvider(provider.getCode());
            identity.setSubject(subject);
            identity.setEmail(email);
            identity.setCreateTime(java.time.LocalDateTime.now());
            identityMapper.insert(identity);
        }
        User fresh = userMapper.selectById(user.getId());
        if (fresh == null || !fresh.isEnabled()) throw BusinessException.badRequest("账户不可用");
        LoginUser loginUser = userService.buildLoginUser(fresh);
        return tokenService.issue(loginUser, true, null, "oauth:" + provider.getCode());
    }

    private User findOrCreateUser(OAuthProvider provider, String subject, String email, String name, String username) {
        if (StringUtils.hasText(email)) {
            User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, email.toLowerCase()).eq(User::getIsDeleted, false).last("LIMIT 1"));
            if (existing != null) return existing;
        }
        String base = StringUtils.hasText(email) ? email.split("@")[0]
                : (StringUtils.hasText(username) ? username : provider.getCode().toLowerCase() + "_" + subject);
        base = base.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("^[^a-zA-Z]+", "u");
        if (base.length() < 3) base = "u" + base;
        base = base.substring(0, Math.min(base.length(), 24));
        String candidate = base;
        int suffix = 1;
        while (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, candidate)) > 0) {
            candidate = base + "_" + suffix++;
        }
        String mail = StringUtils.hasText(email) ? email : candidate + "@oauth.local";
        String nickname = StringUtils.hasText(name) ? name : candidate;
        var created = userService.createUser(new UserCreateRequest(candidate, nickname, mail, null, null, 1, null));
        return userMapper.selectById(created.id());
    }

    private String exchangeToken(OAuthProvider provider, String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", provider.getClientId());
        form.add("client_secret", cryptoService.decrypt(provider.getClientSecret()));
        form.add("redirect_uri", redirectUri);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = http.post().uri(provider.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("User-Agent", "Cognia")
                    .body(form).retrieve().body(Map.class);
            if (response == null) throw BusinessException.badRequest("登录服务未返回令牌");
            String access = stringValue(response.get("access_token"));
            String idToken = stringValue(response.get("id_token"));
            if (!StringUtils.hasText(access) && StringUtils.hasText(idToken)) {
                Map<String, Object> claims = decodeJwtPayload(idToken);
                redis.opsForValue().set("oauth:idtoken:" + provider.getCode() + ":" + code, JSON.toJSONString(claims), Duration.ofMinutes(5));
                return "id_token";
            }
            if (!StringUtils.hasText(access)) throw BusinessException.badRequest("登录失败：未获取访问令牌");
            if (StringUtils.hasText(idToken)) {
                redis.opsForValue().set("oauth:idtoken:" + provider.getCode() + ":" + code, JSON.toJSONString(decodeJwtPayload(idToken)), Duration.ofMinutes(5));
            }
            return access;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("OAuth token exchange failed for {}: {}", provider.getCode(), ex.getMessage());
            throw BusinessException.badRequest("登录服务连接失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchClaims(OAuthProvider provider, String accessToken, String code) {
        Map<String, Object> claims = new LinkedHashMap<>();
        String idTokenKey = "oauth:idtoken:" + provider.getCode() + ":" + code;
        String cached = redis.opsForValue().get(idTokenKey);
        if (cached != null) {
            redis.delete(idTokenKey);
            claims.putAll(JSON.parseObject(cached));
        }
        if ("id_token".equals(accessToken)) return claims;
        if (StringUtils.hasText(provider.getUserinfoUri())) {
            try {
                Map<String, Object> info = http.get().uri(provider.getUserinfoUri())
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Cognia")
                        .retrieve().body(Map.class);
                if (info != null) claims.putAll(info);
            } catch (Exception ex) {
                log.warn("OAuth userinfo failed for {}: {}", provider.getCode(), ex.getMessage());
                throw BusinessException.badRequest("获取登录用户信息失败");
            }
        }
        if ("GITHUB".equals(provider.getCode())) {
            Object email = claims.get("email");
            if (email == null) {
                try {
                    List<Map<String, Object>> emails = http.get().uri("https://api.github.com/user/emails")
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Accept", "application/vnd.github+json")
                            .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {});
                    if (emails != null) {
                        for (Map<String, Object> item : emails) {
                            if (Boolean.TRUE.equals(item.get("primary"))) { claims.put("email", item.get("email")); break; }
                        }
                    }
                } catch (Exception ignored) { }
            }
        }
        return claims;
    }

    private void resolveEndpoints(OAuthProvider provider) {
        if (!"OIDC".equalsIgnoreCase(provider.getProviderType())
                || StringUtils.hasText(provider.getAuthorizationUri())
                && StringUtils.hasText(provider.getTokenUri())) return;
        if (!StringUtils.hasText(provider.getIssuer())) return;
        try {
            String base = provider.getIssuer().replaceAll("/$", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> doc = http.get().uri(base + "/.well-known/openid-configuration")
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(Map.class);
            if (doc != null) {
                if (!StringUtils.hasText(provider.getAuthorizationUri())) provider.setAuthorizationUri(stringValue(doc.get("authorization_endpoint")));
                if (!StringUtils.hasText(provider.getTokenUri())) provider.setTokenUri(stringValue(doc.get("token_endpoint")));
                if (!StringUtils.hasText(provider.getUserinfoUri())) provider.setUserinfoUri(stringValue(doc.get("userinfo_endpoint")));
            }
        } catch (Exception ex) {
            log.warn("OIDC discovery failed for {}: {}", provider.getCode(), ex.getMessage());
        }
    }

    private OAuthProvider require(String code) {
        OAuthProvider provider = providerMapper.selectOne(new LambdaQueryWrapper<OAuthProvider>()
                .eq(OAuthProvider::getCode, code.toUpperCase()));
        if (provider == null) throw BusinessException.notFound("登录方式不存在");
        return provider;
    }

    private AdminOAuthProviderResponse toAdmin(OAuthProvider p) {
        return new AdminOAuthProviderResponse(p.getId(), p.getCode(), p.getName(), p.getProviderType(), p.enabled(),
                p.getClientId(), StringUtils.hasText(p.getClientSecret()), p.getIssuer(),
                p.getAuthorizationUri(), p.getTokenUri(), p.getUserinfoUri(), p.getScopes(),
                p.getRedirectUri(), p.getUsernameClaim(), p.getEmailClaim(), p.getNameClaim(), p.autoCreate());
    }

    private String first(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (StringUtils.hasText(key)) {
                Object value = map.get(key);
                if (value != null && StringUtils.hasText(String.valueOf(value))) return String.valueOf(value);
            }
        }
        return null;
    }

    private String stringValue(Object value) { return value == null ? null : String.valueOf(value); }

    private String blankToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private Map<String, Object> decodeJwtPayload(String token) {
        try {
            String payload = token.split("\\.")[1];
            byte[] bytes = Base64.getUrlDecoder().decode(payload);
            return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw BusinessException.badRequest("登录令牌解析失败");
        }
    }
}
