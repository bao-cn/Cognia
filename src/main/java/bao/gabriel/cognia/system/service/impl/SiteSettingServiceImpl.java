package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.dto.settings.AdminSettingsResponse;
import bao.gabriel.cognia.system.dto.settings.PublicSettingsResponse;
import bao.gabriel.cognia.system.dto.settings.SmtpConfig;
import bao.gabriel.cognia.system.dto.settings.TurnstileConfig;
import bao.gabriel.cognia.system.dto.settings.UpdateSettingsRequest;
import bao.gabriel.cognia.system.entity.SiteSetting;
import bao.gabriel.cognia.system.mapper.SiteSettingMapper;
import bao.gabriel.cognia.system.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Database-backed site configuration. A short in-memory cache avoids a SELECT
 * for every date formatting / mail send; writes refresh it immediately.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteSettingServiceImpl implements SiteSettingService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);
    private static final Pattern UPLOAD_TYPE_PATTERN = Pattern.compile("^[a-z0-9]{1,16}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    private final SiteSettingMapper settingMapper;
    private final SecurityCryptoService cryptoService;

    private volatile SiteSetting cachedSetting;
    private volatile long cachedAt;

    @Override
    public SiteSetting getSetting() {
        SiteSetting cached = cachedSetting;
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL.toMillis()) {
            return cached;
        }
        SiteSetting setting = settingMapper.selectById(SiteSetting.SINGLETON_ID);
        if (setting == null) {
            setting = createDefaultSetting();
        }
        cachedSetting = setting;
        cachedAt = System.currentTimeMillis();
        return setting;
    }

    @Override
    public PublicSettingsResponse getPublicSettings() {
        SiteSetting setting = getSetting();
        return new PublicSettingsResponse(
                setting.getSiteName(),
                setting.getLogoUrl(),
                setting.getSiteDescription(),
                splitCsv(setting.getSiteKeywords()),
                setting.getRouteSuffix(),
                setting.getDefaultLanguage(),
                setting.getTimezone(),
                splitCsv(setting.getAllowedUploadTypes()),
                Boolean.TRUE.equals(setting.getRegistrationEnabled()),
                Boolean.TRUE.equals(setting.getRegistrationEmailVerification()),
                Boolean.TRUE.equals(setting.getRegistrationEmailWhitelistEnabled()),
                Boolean.TRUE.equals(setting.getRegisterEmailCaptchaEnabled()),
                Boolean.TRUE.equals(setting.getLoginCaptchaEnabled()),
                Boolean.TRUE.equals(setting.getAppleLoginEnabled()),
                Boolean.TRUE.equals(setting.getGoogleLoginEnabled()),
                isTurnstileConfigured(setting),
                setting.getTurnstileSiteKey(),
                setting.teamCreationLimit(),
                setting.teamJoinLimit()
        );
    }

    @Override
    public AdminSettingsResponse getAdminSettings() {
        return toAdminResponse(getSetting());
    }

    @Override
    @Transactional
    public AdminSettingsResponse updateSettings(UpdateSettingsRequest request, Long operatorId) {
        SiteSetting setting = getSetting();

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(request.timezone().trim());
        } catch (Exception ex) {
            throw BusinessException.badRequest("时区不合法: " + request.timezone());
        }

        String timezone = zoneId.getId();
        String language = request.defaultLanguage().trim();
        if (!"zhCn".equals(language) && !"enUs".equals(language)) {
            throw BusinessException.badRequest("默认语言只能为 zhCn 或 enUs");
        }

        List<String> keywords = normalizeKeywords(request.keywords());
        List<String> uploadTypes = normalizeUploadTypes(request.allowedUploadTypes());
        if (request.allowedUploadTypes() != null && uploadTypes.isEmpty()) {
            throw BusinessException.badRequest("请至少保留一种允许上传的文档类型");
        }

        // PUT still preserves omitted values so that small API clients cannot
        // accidentally wipe unrelated configuration. Empty strings / empty
        // lists are explicit clears.
        if (request.logoUrl() != null) {
            setting.setLogoUrl(trimToNull(request.logoUrl()));
        }
        setting.setSiteName(request.siteName().trim());
        if (request.siteDescription() != null) {
            setting.setSiteDescription(trimToNull(request.siteDescription()));
        }
        if (request.keywords() != null) {
            setting.setSiteKeywords(String.join(",", keywords));
        }
        if (request.routeSuffix() != null) {
            setting.setRouteSuffix(request.routeSuffix());
        }
        setting.setDefaultLanguage(language);
        setting.setTimezone(timezone);
        if (request.allowedUploadTypes() != null) {
            setting.setAllowedUploadTypes(String.join(",", uploadTypes));
        }

        setting.setRegistrationEnabled(valueOr(request.registrationEnabled(), setting.getRegistrationEnabled()));
        setting.setRegistrationEmailVerification(valueOr(request.registrationEmailVerification(),
                setting.getRegistrationEmailVerification()));
        setting.setRegisterEmailCaptchaEnabled(valueOr(request.registerEmailCaptchaEnabled(),
                setting.getRegisterEmailCaptchaEnabled()));
        setting.setLoginCaptchaEnabled(valueOr(request.loginCaptchaEnabled(), setting.getLoginCaptchaEnabled()));
        setting.setRegistrationEmailWhitelistEnabled(valueOr(request.registrationEmailWhitelistEnabled(),
                setting.getRegistrationEmailWhitelistEnabled()));
        if (request.registrationEmailWhitelist() != null) {
            List<String> whitelist = normalizeWhitelist(request.registrationEmailWhitelist());
            setting.setRegistrationEmailWhitelist(whitelist.isEmpty() ? null : String.join(",", whitelist));
        }
        setting.setAppleLoginEnabled(valueOr(request.appleLoginEnabled(), setting.getAppleLoginEnabled()));
        setting.setGoogleLoginEnabled(valueOr(request.googleLoginEnabled(), setting.getGoogleLoginEnabled()));

        if (request.turnstileSiteKey() != null) {
            setting.setTurnstileSiteKey(trimToNull(request.turnstileSiteKey()));
        }
        if (Boolean.TRUE.equals(request.clearTurnstileSecretKey())) {
            setting.setTurnstileSecretKey(null);
        } else if (StringUtils.hasText(request.turnstileSecretKey())) {
            setting.setTurnstileSecretKey(cryptoService.encrypt(request.turnstileSecretKey()));
        }
        boolean hasSiteKey = StringUtils.hasText(setting.getTurnstileSiteKey());
        boolean hasSecretKey = StringUtils.hasText(setting.getTurnstileSecretKey());
        if (hasSiteKey != hasSecretKey) {
            throw BusinessException.badRequest("Turnstile 站点密钥和密钥需要同时配置");
        }

        setting.setSmtpEnabled(valueOr(request.smtpEnabled(), setting.getSmtpEnabled()));
        if (request.smtpHost() != null) {
            setting.setSmtpHost(trimToNull(request.smtpHost()));
        }
        if (request.smtpPort() != null) {
            setting.setSmtpPort(request.smtpPort());
        }
        if (request.smtpUsername() != null) {
            setting.setSmtpUsername(trimToNull(request.smtpUsername()));
        }
        if (request.smtpFrom() != null) {
            setting.setSmtpFrom(trimToNull(request.smtpFrom()));
        }
        setting.setSmtpSsl(valueOr(request.smtpSsl(), setting.getSmtpSsl()));
        setting.setSmtpStarttls(valueOr(request.smtpStarttls(), setting.getSmtpStarttls()));
        setting.setSmtpAuth(valueOr(request.smtpAuth(), setting.getSmtpAuth()));
        if (request.teamCreationLimit() != null) {
            setting.setTeamCreationLimit(request.teamCreationLimit());
        }
        if (request.teamJoinLimit() != null) {
            setting.setTeamJoinLimit(request.teamJoinLimit());
        }
        if (request.maxFolderDepth() != null) {
            setting.setMaxFolderDepth(request.maxFolderDepth());
        }
        if (request.recycleRetentionDays() != null) {
            setting.setRecycleRetentionDays(request.recycleRetentionDays());
        }

        if (Boolean.TRUE.equals(request.clearSmtpPassword())) {
            setting.setSmtpPassword(null);
        } else if (StringUtils.hasText(request.smtpPassword())) {
            setting.setSmtpPassword(cryptoService.encrypt(request.smtpPassword()));
        }

        if (Boolean.TRUE.equals(setting.getSmtpEnabled()) && !StringUtils.hasText(setting.getSmtpHost())) {
            throw BusinessException.badRequest("启用 SMTP 时必须填写 SMTP 主机");
        }

        boolean smtpUsable = Boolean.TRUE.equals(setting.getSmtpEnabled())
                && StringUtils.hasText(setting.getSmtpHost());
        if (Boolean.TRUE.equals(setting.getRegistrationEmailVerification()) && !smtpUsable) {
            throw BusinessException.badRequest("开启注册邮箱验证前，请先启用并配置发信服务");
        }

        boolean turnstileConfigured = isTurnstileConfigured(setting);
        if ((Boolean.TRUE.equals(setting.getRegisterEmailCaptchaEnabled())
                || Boolean.TRUE.equals(setting.getLoginCaptchaEnabled()))
                && !turnstileConfigured) {
            throw BusinessException.badRequest("开启注册/登录人机验证前，请先配置 Turnstile 站点密钥和密钥");
        }

        if (setting.emailWhitelistEnabled()
                && (setting.getRegistrationEmailWhitelist() == null
                || setting.getRegistrationEmailWhitelist().isBlank())) {
            throw BusinessException.badRequest("启用注册邮箱白名单时，至少配置一个邮箱或域名");
        }

        setting.setUpdatedBy(operatorId);
        setting.setUpdatedAt(LocalDateTime.now());
        if (setting.getCreateTime() == null) {
            setting.setCreateTime(LocalDateTime.now());
        }

        if (settingMapper.selectById(SiteSetting.SINGLETON_ID) == null) {
            settingMapper.insert(setting);
        } else {
            settingMapper.updateById(setting);
        }
        cachedSetting = setting;
        cachedAt = System.currentTimeMillis();
        log.info("Site settings updated by user {}", operatorId);
        return toAdminResponse(setting);
    }

    @Override
    public SmtpConfig getSmtpConfig() {
        SiteSetting setting = getSetting();
        String password = cryptoService.decrypt(setting.getSmtpPassword());
        String username = trimToNull(setting.getSmtpUsername());
        String from = trimToNull(setting.getSmtpFrom());
        if (from == null) {
            from = username;
        }
        int port = setting.getSmtpPort() == null ? 587 : setting.getSmtpPort();
        return new SmtpConfig(
                Boolean.TRUE.equals(setting.getSmtpEnabled()),
                trimToNull(setting.getSmtpHost()),
                port,
                username,
                password,
                from,
                Boolean.TRUE.equals(setting.getSmtpSsl()),
                Boolean.TRUE.equals(setting.getSmtpStarttls()),
                Boolean.TRUE.equals(setting.getSmtpAuth())
        );
    }

    @Override
    public TurnstileConfig getTurnstileConfig() {
        SiteSetting setting = getSetting();
        return new TurnstileConfig(
                isTurnstileConfigured(setting),
                setting.getTurnstileSiteKey(),
                cryptoService.decrypt(setting.getTurnstileSecretKey())
        );
    }

    private boolean isTurnstileConfigured(SiteSetting setting) {
        return StringUtils.hasText(setting.getTurnstileSiteKey())
                && StringUtils.hasText(setting.getTurnstileSecretKey());
    }

    @Override
    public boolean isRegistrationEnabled() {
        return Boolean.TRUE.equals(getSetting().getRegistrationEnabled());
    }

    @Override
    public boolean isEmailVerificationRequired() {
        return Boolean.TRUE.equals(getSetting().getRegistrationEmailVerification());
    }

    @Override
    public boolean isRegisterEmailCaptchaEnabled() {
        return Boolean.TRUE.equals(getSetting().getRegisterEmailCaptchaEnabled());
    }

    @Override
    public boolean isLoginCaptchaEnabled() {
        return Boolean.TRUE.equals(getSetting().getLoginCaptchaEnabled());
    }

    @Override
    public boolean isTurnstileConfigured() {
        return isTurnstileConfigured(getSetting());
    }

    @Override
    public boolean isRegistrationEmailAllowed(String email) {
        SiteSetting setting = getSetting();
        if (!setting.emailWhitelistEnabled()) {
            return true;
        }
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int at = normalizedEmail.lastIndexOf('@');
        if (at <= 0 || at == normalizedEmail.length() - 1) {
            return false;
        }
        String domain = normalizedEmail.substring(at + 1);
        for (String entry : splitCsv(setting.getRegistrationEmailWhitelist())) {
            String value = entry.toLowerCase(Locale.ROOT);
            if (value.startsWith("@")) {
                String allowedDomain = value.substring(1);
                if (domain.equals(allowedDomain) || domain.endsWith("." + allowedDomain)) {
                    return true;
                }
            } else if (value.contains("@")) {
                if (normalizedEmail.equals(value)) {
                    return true;
                }
            } else if (domain.equals(value) || domain.endsWith("." + value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ZoneId getZoneId() {
        try {
            return ZoneId.of(getSetting().getTimezone());
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    @Override
    public String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return ZonedDateTime.of(value, ZoneId.systemDefault())
                .withZoneSameInstant(getZoneId())
                .format(DATE_TIME_FORMATTER);
    }

    @Override
    public List<String> getAllowedUploadTypes() {
        return splitCsv(getSetting().getAllowedUploadTypes());
    }

    private AdminSettingsResponse toAdminResponse(SiteSetting setting) {
        return new AdminSettingsResponse(
                setting.getLogoUrl(),
                setting.getSiteName(),
                setting.getSiteDescription(),
                splitCsv(setting.getSiteKeywords()),
                setting.getRouteSuffix(),
                setting.getDefaultLanguage(),
                setting.getTimezone(),
                splitCsv(setting.getAllowedUploadTypes()),
                Boolean.TRUE.equals(setting.getRegistrationEnabled()),
                Boolean.TRUE.equals(setting.getRegistrationEmailVerification()),
                Boolean.TRUE.equals(setting.getRegisterEmailCaptchaEnabled()),
                Boolean.TRUE.equals(setting.getLoginCaptchaEnabled()),
                Boolean.TRUE.equals(setting.getRegistrationEmailWhitelistEnabled()),
                splitCsv(setting.getRegistrationEmailWhitelist()),
                Boolean.TRUE.equals(setting.getAppleLoginEnabled()),
                Boolean.TRUE.equals(setting.getGoogleLoginEnabled()),
                setting.getTurnstileSiteKey(),
                StringUtils.hasText(setting.getTurnstileSecretKey()),
                Boolean.TRUE.equals(setting.getSmtpEnabled()),
                setting.getSmtpHost(),
                setting.getSmtpPort(),
                setting.getSmtpUsername(),
                StringUtils.hasText(setting.getSmtpPassword()),
                setting.getSmtpFrom(),
                Boolean.TRUE.equals(setting.getSmtpSsl()),
                Boolean.TRUE.equals(setting.getSmtpStarttls()),
                Boolean.TRUE.equals(setting.getSmtpAuth()),
                setting.teamCreationLimit(),
                setting.teamJoinLimit(),
                setting.maxFolderDepth(),
                setting.recycleRetentionDays(),
                formatDateTime(setting.getUpdatedAt())
        );
    }

    private SiteSetting createDefaultSetting() {
        SiteSetting setting = new SiteSetting();
        setting.setId(SiteSetting.SINGLETON_ID);
        setting.setLogoUrl("/logo.png");
        setting.setSiteName("Cognia");
        setting.setSiteDescription("Cognia 开源的企业知识库平台");
        setting.setSiteKeywords("Cognia,企业知识库,AI 搜索,知识管理");
        setting.setRouteSuffix(" - Cognia 开源的企业知识库平台");
        setting.setDefaultLanguage("zhCn");
        setting.setTimezone("Asia/Shanghai");
        setting.setAllowedUploadTypes("pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,html,csv");
        setting.setRegistrationEnabled(true);
        setting.setRegistrationEmailVerification(false);
        setting.setRegisterEmailCaptchaEnabled(true);
        setting.setLoginCaptchaEnabled(false);
        setting.setRegistrationEmailWhitelistEnabled(false);
        setting.setRegistrationEmailWhitelist(null);
        setting.setAppleLoginEnabled(false);
        setting.setGoogleLoginEnabled(false);
        setting.setSmtpEnabled(false);
        setting.setSmtpPort(587);
        setting.setSmtpSsl(false);
        setting.setSmtpStarttls(true);
        setting.setSmtpAuth(true);
        setting.setTeamCreationLimit(5);
        setting.setTeamJoinLimit(10);
        setting.setMaxFolderDepth(5);
        setting.setRecycleRetentionDays(30);
        setting.setCreateTime(LocalDateTime.now());
        setting.setUpdatedAt(LocalDateTime.now());
        settingMapper.insert(setting);
        log.info("Default site settings created");
        return setting;
    }

    private List<String> splitCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> normalizeWhitelist(List<String> entries) {
        if (entries == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String value = entry.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                continue;
            }
            if (value.length() > 128) {
                throw BusinessException.badRequest("白名单条目过长: " + entry);
            }
            if (value.startsWith("@") || value.startsWith("*.")) {
                String domain = value.startsWith("@") ? value.substring(1) : value.substring(2);
                if (!DOMAIN_PATTERN.matcher(domain).matches()) {
                    throw BusinessException.badRequest("白名单域名不合法: " + entry);
                }
                result.add("@" + domain);
            } else if (value.contains("@")) {
                if (!EMAIL_PATTERN.matcher(value).matches()) {
                    throw BusinessException.badRequest("白名单邮箱不合法: " + entry);
                }
                result.add(value);
            } else {
                if (!DOMAIN_PATTERN.matcher(value).matches()) {
                    throw BusinessException.badRequest("白名单域名不合法: " + entry);
                }
                result.add("@" + value);
            }
            if (result.size() >= 50) {
                break;
            }
        }
        if (String.join(",", result).length() > 2000) {
            throw BusinessException.badRequest("注册邮箱白名单配置过长");
        }
        return new ArrayList<>(result);
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
            if (result.size() >= 20) {
                break;
            }
        }
        return new ArrayList<>(result);
    }

    private List<String> normalizeUploadTypes(List<String> types) {
        if (types == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String type : types) {
            if (type == null) {
                continue;
            }
            String normalized = type.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
            if (normalized.isEmpty()) {
                continue;
            }
            if (!UPLOAD_TYPE_PATTERN.matcher(normalized).matches()) {
                throw BusinessException.badRequest("上传类型不合法: " + type);
            }
            result.add(normalized);
            if (result.size() >= 30) {
                break;
            }
        }
        return new ArrayList<>(result);
    }

    private Boolean valueOr(Boolean value, Boolean fallback) {
        return value == null ? fallback : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
