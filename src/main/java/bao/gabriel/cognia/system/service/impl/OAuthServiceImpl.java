package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.LoginUser;
import bao.gabriel.cognia.security.TokenService;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.dto.auth.LoginResponse;
import bao.gabriel.cognia.system.dto.oauth.AdminOAuthProviderResponse;
import bao.gabriel.cognia.system.dto.oauth.OAuthCallbackResponse;
import bao.gabriel.cognia.system.dto.oauth.UserOAuthAccountResponse;
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

import java.net.URI;
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
    private static final String LINK_PREFIX = "oauth:link:";
    private static final String REGISTER_PREFIX = "oauth:register:";
    private static final Duration LINK_TTL = Duration.ofMinutes(10);
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
        if (request.redirectUri() != null) provider.setRedirectUri(normalizeRedirectUri(request.redirectUri()));
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
        return authorizeUrl(code, redirectUri, null);
    }

    @Override
    public String authorizeUrl(String code, String redirectUri, String intent) {
        OAuthProvider provider = require(code);
        return buildAuthorizeUrl(provider, redirectUri, null, intent);
    }

    @Override
    public String userAuthorizeUrl(Long userId, String code, String redirectUri) {
        OAuthProvider provider = require(code);
        if (!provider.enabled()) throw BusinessException.badRequest("该登录方式未启用");
        return buildAuthorizeUrl(provider, redirectUri, userId, null);
    }

    private String buildAuthorizeUrl(OAuthProvider provider, String redirectUri, Long bindUserId, String intent) {
        if (!provider.enabled()) throw BusinessException.badRequest("该登录方式未启用");
        resolveEndpoints(provider);
        if (!StringUtils.hasText(provider.getAuthorizationUri()) || !StringUtils.hasText(provider.getClientId())) {
            throw BusinessException.badRequest("该登录方式尚未配置完整");
        }
        String effectiveRedirect = StringUtils.hasText(provider.getRedirectUri())
                ? provider.getRedirectUri().trim() : redirectUri;
        if (!isAbsoluteHttpUrl(effectiveRedirect)) {
            throw BusinessException.badRequest("回调地址无效，请检查登录方式配置");
        }
        String state = UUID.randomUUID().toString().replace("-", "");
        String payload = provider.getCode() + "|" + effectiveRedirect + "|"
                + (bindUserId == null ? "" : bindUserId) + "|" + (intent == null ? "" : intent);
        redis.opsForValue().set(STATE_PREFIX + state, payload, Duration.ofMinutes(10));
        return UriComponentsBuilder.fromUriString(provider.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", provider.getClientId())
                .queryParam("redirect_uri", effectiveRedirect)
                .queryParam("scope", provider.getScopes())
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    @Override
    public List<UserOAuthAccountResponse> userAccounts(Long userId) {
        List<OAuthProvider> providers = providerMapper.selectList(new LambdaQueryWrapper<OAuthProvider>()
                .orderByAsc(OAuthProvider::getId));
        return providers.stream().map(provider -> {
            UserOAuthIdentity identity = identityMapper.selectOne(new LambdaQueryWrapper<UserOAuthIdentity>()
                    .eq(UserOAuthIdentity::getUserId, userId)
                    .eq(UserOAuthIdentity::getProvider, provider.getCode())
                    .last("LIMIT 1"));
            String linkedAt = identity == null || identity.getCreateTime() == null ? null
                    : identity.getCreateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return new UserOAuthAccountResponse(provider.getCode(), provider.getName(), provider.getProviderType(),
                    provider.enabled(), identity != null, identity == null ? null : identity.getEmail(), linkedAt);
        }).toList();
    }

    @Override
    public void unlinkAccount(Long userId, String code) {
        String providerCode = code.toUpperCase();
        UserOAuthIdentity identity = identityMapper.selectOne(new LambdaQueryWrapper<UserOAuthIdentity>()
                .eq(UserOAuthIdentity::getUserId, userId)
                .eq(UserOAuthIdentity::getProvider, providerCode)
                .last("LIMIT 1"));
        if (identity == null) throw BusinessException.notFound("尚未关联该第三方账户");
        User user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("账户不存在");
        long linkedCount = identityMapper.selectCount(new LambdaQueryWrapper<UserOAuthIdentity>()
                .eq(UserOAuthIdentity::getUserId, userId));
        if (linkedCount <= 1 && !StringUtils.hasText(user.getPassword())) {
            throw BusinessException.badRequest("请先设置登录密码，再解除最后一个第三方账号");
        }
        identityMapper.deleteById(identity.getId());
    }

    @Override
    public OAuthCallbackResponse callback(String code, String state) {
        if (!StringUtils.hasText(state)) throw BusinessException.badRequest("登录状态无效");
        String raw = redis.opsForValue().get(STATE_PREFIX + state);
        if (raw == null) throw BusinessException.badRequest("登录状态已过期，请重试");
        redis.delete(STATE_PREFIX + state);
        String[] parts = raw.split("\\|", -1);
        String providerCode = parts[0];
        String redirectUri = parts.length > 1 ? parts[1] : "";
        Long bindUserId = parts.length > 2 && StringUtils.hasText(parts[2]) ? Long.valueOf(parts[2]) : null;
        String intent = parts.length > 3 ? parts[3] : "";
        OAuthProvider provider = require(providerCode);
        resolveEndpoints(provider);
        String accessToken = exchangeToken(provider, code, redirectUri);
        Map<String, Object> claims = fetchClaims(provider, accessToken, code);
        String subject = first(claims, "sub", "id", "login");
        if (!StringUtils.hasText(subject)) throw BusinessException.badRequest("登录服务未返回用户标识");
        String email = first(claims, provider.getEmailClaim());
        String name = first(claims, provider.getNameClaim());
        String username = first(claims, provider.getUsernameClaim());

        if (bindUserId != null) {
            User target = userMapper.selectById(bindUserId);
            if (target == null || !target.isEnabled()) throw BusinessException.badRequest("账户不可用");
            UserOAuthIdentity bound = identityMapper.selectOne(new LambdaQueryWrapper<UserOAuthIdentity>()
                    .eq(UserOAuthIdentity::getProvider, provider.getCode())
                    .eq(UserOAuthIdentity::getSubject, subject));
            if (bound != null) {
                if (!bound.getUserId().equals(bindUserId)) throw BusinessException.conflict("该第三方账户已关联其他用户");
                return OAuthCallbackResponse.linked(email, provider.getName());
            }
            linkIdentity(provider, target, subject, email);
            return OAuthCallbackResponse.linked(email, provider.getName());
        }

        UserOAuthIdentity identity = identityMapper.selectOne(new LambdaQueryWrapper<UserOAuthIdentity>()
                .eq(UserOAuthIdentity::getProvider, provider.getCode())
                .eq(UserOAuthIdentity::getSubject, subject));
        if (identity != null) {
            User linked = userMapper.selectById(identity.getUserId());
            if (linked == null) throw BusinessException.badRequest("关联账户不存在");
            return OAuthCallbackResponse.loggedIn(login(linked, provider));
        }

        if (StringUtils.hasText(email)) {
            User existing = findByEmail(email);
            if (existing != null) {
                if (!emailTrusted(claims)) {
                    throw BusinessException.badRequest("第三方账户邮箱尚未验证，无法关联现有账户");
                }
                if (!existing.isEnabled()) throw BusinessException.badRequest("关联账户不可用");
                String token = UUID.randomUUID().toString().replace("-", "");
                Map<String, String> ticket = new LinkedHashMap<>();
                ticket.put("provider", provider.getCode());
                ticket.put("subject", subject);
                ticket.put("email", email);
                ticket.put("userId", String.valueOf(existing.getId()));
                redis.opsForValue().set(LINK_PREFIX + token, JSON.toJSONString(ticket), LINK_TTL);
                return OAuthCallbackResponse.linkRequired(token, email, provider.getName());
            }
        }

        boolean registration = "register".equals(intent);
        if (!provider.autoCreate() && !registration) throw BusinessException.badRequest("该账户尚未注册");
        String base = oauthUsernameBase(provider, email, username, subject);
        if (!userService.isUsernameAvailable(base)) {
            String token = UUID.randomUUID().toString().replace("-", "");
            Map<String, String> ticket = new LinkedHashMap<>();
            ticket.put("provider", provider.getCode());
            ticket.put("subject", subject);
            ticket.put("email", email == null ? "" : email);
            ticket.put("name", name == null ? "" : name);
            redis.opsForValue().set(REGISTER_PREFIX + token, JSON.toJSONString(ticket), LINK_TTL);
            return OAuthCallbackResponse.registrationRequired(token, email, provider.getName(),
                    userService.suggestUsernames(base));
        }
        User user = createOAuthUser(provider, subject, email, name, base);
        linkIdentity(provider, user, subject, email);
        return OAuthCallbackResponse.loggedIn(login(user, provider));
    }

    @Override
    public OAuthCallbackResponse completeRegistration(String registrationToken, String username) {
        if (!StringUtils.hasText(registrationToken)) throw BusinessException.badRequest("注册请求无效");
        String raw = redis.opsForValue().get(REGISTER_PREFIX + registrationToken);
        if (raw == null) throw BusinessException.badRequest("注册请求已过期，请重新使用第三方账号注册");
        Map<String, String> ticket = JSON.parseObject(raw, new com.alibaba.fastjson2.TypeReference<Map<String, String>>() {});
        String providerCode = ticket.get("provider");
        String subject = ticket.get("subject");
        String email = ticket.get("email");
        String name = ticket.get("name");
        OAuthProvider provider = require(providerCode);
        String candidate = username == null ? "" : username.trim();
        if (!candidate.matches("^[a-zA-Z][a-zA-Z0-9_]{2,31}$")) {
            throw BusinessException.badRequest("唯一标识需以字母开头，只能包含字母、数字、下划线，长度 3-32");
        }
        userService.assertUsernameAvailable(candidate, null);
        if (StringUtils.hasText(email) && !userService.isEmailAvailable(email)) {
            throw BusinessException.conflict("该邮箱已被注册，请直接登录");
        }
        UserOAuthIdentity existing = identityMapper.selectOne(new LambdaQueryWrapper<UserOAuthIdentity>()
                .eq(UserOAuthIdentity::getProvider, provider.getCode())
                .eq(UserOAuthIdentity::getSubject, subject));
        if (existing != null) throw BusinessException.conflict("该第三方账户已注册，请直接登录");
        String mail = StringUtils.hasText(email) ? email : candidate + "@oauth.local";
        String nickname = StringUtils.hasText(name) ? name : candidate;
        var created = userService.createUser(new UserCreateRequest(candidate, nickname, mail, null, null, 1, null));
        User user = userMapper.selectById(Long.valueOf(created.id()));
        linkIdentity(provider, user, subject, email);
        redis.delete(REGISTER_PREFIX + registrationToken);
        return OAuthCallbackResponse.loggedIn(login(user, provider));
    }

    @Override
    public OAuthCallbackResponse link(String linkToken) {
        if (!StringUtils.hasText(linkToken)) throw BusinessException.badRequest("关联请求无效");
        String raw = redis.opsForValue().getAndDelete(LINK_PREFIX + linkToken);
        if (raw == null) throw BusinessException.badRequest("关联请求已过期，请重新登录");
        Map<String, String> ticket = JSON.parseObject(raw, new com.alibaba.fastjson2.TypeReference<Map<String, String>>() {});
        String providerCode = ticket.get("provider");
        String subject = ticket.get("subject");
        String email = ticket.get("email");
        Long userId = Long.valueOf(ticket.get("userId"));
        OAuthProvider provider = require(providerCode);
        User user = userMapper.selectById(userId);
        if (user == null || !user.isEnabled()) throw BusinessException.badRequest("关联账户不可用");

        UserOAuthIdentity existing = identityMapper.selectOne(new LambdaQueryWrapper<UserOAuthIdentity>()
                .eq(UserOAuthIdentity::getProvider, provider.getCode())
                .eq(UserOAuthIdentity::getSubject, subject));
        if (existing != null) {
            if (!existing.getUserId().equals(userId)) throw BusinessException.conflict("该第三方账户已关联其他用户");
            return OAuthCallbackResponse.loggedIn(login(user, provider));
        }
        linkIdentity(provider, user, subject, email);
        return OAuthCallbackResponse.loggedIn(login(user, provider));
    }

    private LoginResponse login(User user, OAuthProvider provider) {
        User fresh = userMapper.selectById(user.getId());
        if (fresh == null || !fresh.isEnabled()) throw BusinessException.badRequest("账户不可用");
        LoginUser loginUser = userService.buildLoginUser(fresh);
        var tokens = tokenService.issue(loginUser, true, null, "oauth:" + provider.getCode());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(),
                tokens.expiresIn(), tokens.refreshExpiresIn(), userService.getProfile(fresh.getId()));
    }

    private void linkIdentity(OAuthProvider provider, User user, String subject, String email) {
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setUserId(user.getId());
        identity.setProvider(provider.getCode());
        identity.setSubject(subject);
        identity.setEmail(email);
        identity.setCreateTime(java.time.LocalDateTime.now());
        try {
            identityMapper.insert(identity);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw BusinessException.conflict("该第三方账户已关联其他用户");
        }
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email.toLowerCase()).eq(User::getIsDeleted, false).last("LIMIT 1"));
    }

    private boolean emailTrusted(Map<String, Object> claims) {
        Object verified = claims.get("email_verified");
        if (verified == null) return true;
        return Boolean.parseBoolean(String.valueOf(verified));
    }

    private String oauthUsernameBase(OAuthProvider provider, String email, String username, String subject) {
        String base = StringUtils.hasText(email) ? email.split("@")[0]
                : (StringUtils.hasText(username) ? username : provider.getCode().toLowerCase() + "_" + subject);
        base = base.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("^[^a-zA-Z]+", "u");
        if (base.length() < 3) base = "u" + base;
        return base.substring(0, Math.min(base.length(), 24));
    }

    private User createOAuthUser(OAuthProvider provider, String subject, String email, String name, String candidate) {
        String mail = StringUtils.hasText(email) ? email : candidate + "@oauth.local";
        String nickname = StringUtils.hasText(name) ? name : candidate;
        var created = userService.createUser(new UserCreateRequest(candidate, nickname, mail, null, null, 1, null));
        return userMapper.selectById(Long.valueOf(created.id()));
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
                            if (Boolean.TRUE.equals(item.get("primary"))) {
                                claims.put("email", item.get("email"));
                                claims.put("email_verified", item.get("verified"));
                                break;
                            }
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

    private String normalizeRedirectUri(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        if (!isAbsoluteHttpUrl(trimmed)) {
            throw BusinessException.badRequest("回调地址必须是完整的 http(s) 地址");
        }
        return trimmed;
    }

    private boolean isAbsoluteHttpUrl(String value) {
        if (!StringUtils.hasText(value) || value.chars().anyMatch(Character::isWhitespace)) return false;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

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
