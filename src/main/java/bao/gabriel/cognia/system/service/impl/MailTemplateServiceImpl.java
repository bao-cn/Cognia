package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.settings.MailTemplatePreviewResponse;
import bao.gabriel.cognia.system.dto.settings.MailTemplateResponse;
import bao.gabriel.cognia.system.dto.settings.UpdateMailTemplateRequest;
import bao.gabriel.cognia.system.entity.MailTemplate;
import bao.gabriel.cognia.system.mapper.MailTemplateMapper;
import bao.gabriel.cognia.system.service.EmailHtmlRenderer;
import bao.gabriel.cognia.system.service.MailTemplateService;
import bao.gabriel.cognia.system.service.RenderedMail;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Editable HTML mail templates. Defaults are kept in code so "reset" works
 * even on an older database; startup SQL seeds the same defaults for fresh
 * installations. Content is an HTML fragment; the shared email shell provides
 * an email-client-safe table layout and inline styles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailTemplateServiceImpl implements MailTemplateService {

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private static final List<TemplateDefault> DEFAULTS = List.of(
            new TemplateDefault(
                    REGISTER_CODE,
                    "注册验证码邮件",
                    "注册账户时发送的邮箱验证码（HTML）",
                    "【{{siteName}}】邮箱注册验证码",
                    """
                    <h1 style="margin:0 0 16px;font-size:22px;line-height:1.35;color:#111827;">验证您的邮箱</h1>
                    <p style="margin:0 0 16px;">您好！</p>
                    <p style="margin:0 0 16px;">您正在注册 {{siteName}} 账户，本次验证码为：</p>
                    <div style="margin:24px 0;padding:16px 20px;background-color:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;text-align:center;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;font-size:28px;font-weight:700;letter-spacing:8px;color:#111827;">{{code}}</div>
                    <p style="margin:0 0 16px;">验证码 {{ttlMinutes}} 分钟内有效（{{expiresAt}} 前）。如非本人操作，请忽略本邮件。</p>
                    <p style="margin:0;">—— {{siteName}} 团队</p>
                    """,
                    List.of("siteName", "email", "code", "ttlMinutes", "expiresAt", "year")
            ),
            new TemplateDefault(
                    SMTP_TEST,
                    "SMTP 测试邮件",
                    "控制台发送测试邮件时使用的模板（HTML）",
                    "【{{siteName}}】SMTP 配置测试",
                    """
                    <h1 style="margin:0 0 16px;font-size:22px;line-height:1.35;color:#111827;">SMTP 配置测试</h1>
                    <p style="margin:0 0 16px;">这是一封来自 {{siteName}} 的 SMTP 测试邮件。</p>
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:16px 0;border-collapse:collapse;">
                      <tr>
                        <td style="padding:8px 0;width:96px;color:#6b7280;font-size:13px;">收件人</td>
                        <td style="padding:8px 0;color:#111827;font-size:14px;">{{recipient}}</td>
                      </tr>
                      <tr>
                        <td style="padding:8px 0;color:#6b7280;font-size:13px;">发送时间</td>
                        <td style="padding:8px 0;color:#111827;font-size:14px;">{{sentAt}}</td>
                      </tr>
                    </table>
                    <p style="margin:0 0 16px;">如果您收到此邮件，说明控制台中的邮件服务配置已生效。</p>
                    <p style="margin:0;">—— {{siteName}} 团队</p>
                    """,
                    List.of("siteName", "recipient", "sentAt", "year")
            )
    );

    private static final Map<String, TemplateDefault> DEFAULT_BY_CODE = DEFAULTS.stream()
            .collect(java.util.stream.Collectors.toMap(TemplateDefault::code, item -> item,
                    (left, right) -> left, LinkedHashMap::new));

    private final MailTemplateMapper templateMapper;
    private final SiteSettingService settingService;
    private final EmailHtmlRenderer emailHtmlRenderer;

    @Override
    public List<MailTemplateResponse> listTemplates() {
        DEFAULTS.forEach(defaultTemplate -> ensureTemplate(defaultTemplate.code()));
        return templateMapper.selectList(new LambdaQueryWrapper<MailTemplate>().orderByAsc(MailTemplate::getId))
                .stream()
                .filter(template -> DEFAULT_BY_CODE.containsKey(template.getCode()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public MailTemplateResponse updateTemplate(String code, UpdateMailTemplateRequest request, Long operatorId) {
        String normalized = normalizeCode(code);
        TemplateDefault defaultTemplate = DEFAULT_BY_CODE.get(normalized);
        if (defaultTemplate == null) {
            throw BusinessException.notFound("邮件模板不存在");
        }
        validateRequest(defaultTemplate, request);

        MailTemplate template = ensureTemplate(normalized);
        template.setSubject(request.subject().trim());
        template.setContent(request.content());
        template.setUpdatedBy(operatorId);
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
        log.info("Mail template {} updated by user {}", normalized, operatorId);
        return toResponse(template);
    }

    @Override
    @Transactional
    public MailTemplateResponse resetTemplate(String code, Long operatorId) {
        String normalized = normalizeCode(code);
        TemplateDefault defaultTemplate = DEFAULT_BY_CODE.get(normalized);
        if (defaultTemplate == null) {
            throw BusinessException.notFound("邮件模板不存在");
        }
        MailTemplate template = ensureTemplate(normalized);
        applyDefault(template, defaultTemplate);
        template.setUpdatedBy(operatorId);
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
        log.info("Mail template {} reset by user {}", normalized, operatorId);
        return toResponse(template);
    }

    @Override
    public MailTemplatePreviewResponse previewTemplate(String code, UpdateMailTemplateRequest request) {
        String normalized = normalizeCode(code);
        TemplateDefault defaultTemplate = DEFAULT_BY_CODE.get(normalized);
        if (defaultTemplate == null) {
            throw BusinessException.notFound("邮件模板不存在");
        }
        validateRequest(defaultTemplate, request);
        RenderedMail rendered = RenderedMail.render(normalized, request.subject(), request.content(), sampleVariables());
        String html = emailHtmlRenderer.wrap(siteName(), rendered.subject(), rendered.content());
        return new MailTemplatePreviewResponse(rendered.subject(), html,
                emailHtmlRenderer.plainText(siteName(), rendered.content()));
    }

    @Override
    public RenderedMail render(String code, Map<String, String> variables) {
        String normalized = normalizeCode(code);
        TemplateDefault defaultTemplate = DEFAULT_BY_CODE.get(normalized);
        if (defaultTemplate == null) {
            throw BusinessException.notFound("邮件模板不存在");
        }
        MailTemplate template = ensureTemplate(normalized);
        String subject = StringUtils.hasText(template.getSubject())
                ? template.getSubject() : defaultTemplate.subject();
        String content = StringUtils.hasText(template.getContent())
                ? template.getContent() : defaultTemplate.content();
        return RenderedMail.render(normalized, subject, content, variables);
    }

    private Map<String, String> sampleVariables() {
        LocalDateTime now = LocalDateTime.now();
        return Map.of(
                "siteName", siteName(),
                "email", "user@example.com",
                "code", "123456",
                "ttlMinutes", "5",
                "expiresAt", settingService.formatDateTime(now.plusMinutes(5)),
                "recipient", "user@example.com",
                "sentAt", settingService.formatDateTime(now),
                "year", String.valueOf(LocalDate.now().getYear())
        );
    }

    private String siteName() {
        String name = settingService.getSetting().getSiteName();
        return StringUtils.hasText(name) ? name : "Cognia";
    }

    private void validateRequest(TemplateDefault defaultTemplate, UpdateMailTemplateRequest request) {
        if (request.subject().contains("\r") || request.subject().contains("\n")) {
            throw BusinessException.badRequest("邮件主题不能包含换行");
        }
        validatePlaceholders(defaultTemplate, request.subject() + "\n" + request.content());
        if (!request.content().contains("<") || !request.content().contains(">")) {
            throw BusinessException.badRequest("邮件正文必须是 HTML 片段");
        }
    }

    private MailTemplate ensureTemplate(String code) {
        MailTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<MailTemplate>()
                .eq(MailTemplate::getCode, code)
                .last("LIMIT 1"));
        if (template != null) {
            return template;
        }
        TemplateDefault defaultTemplate = DEFAULT_BY_CODE.get(code);
        if (defaultTemplate == null) {
            throw BusinessException.notFound("邮件模板不存在");
        }
        template = new MailTemplate();
        template.setCode(defaultTemplate.code());
        applyDefault(template, defaultTemplate);
        template.setCreateTime(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(template);
        return template;
    }

    private void applyDefault(MailTemplate template, TemplateDefault defaultTemplate) {
        template.setName(defaultTemplate.name());
        template.setDescription(defaultTemplate.description());
        template.setSubject(defaultTemplate.subject());
        template.setContent(defaultTemplate.content());
    }

    private void validatePlaceholders(TemplateDefault defaultTemplate, String text) {
        Set<String> allowed = new LinkedHashSet<>(defaultTemplate.placeholders());
        Set<String> unknown = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!allowed.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw BusinessException.badRequest("不支持模板变量: " + String.join(", ", unknown)
                    + "；可用变量: " + String.join(", ", allowed));
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            throw BusinessException.badRequest("邮件模板编码不能为空");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private MailTemplateResponse toResponse(MailTemplate template) {
        TemplateDefault defaultTemplate = DEFAULT_BY_CODE.get(template.getCode());
        return new MailTemplateResponse(
                template.getCode(),
                template.getName(),
                template.getDescription(),
                template.getSubject(),
                template.getContent(),
                defaultTemplate == null ? List.of() : defaultTemplate.placeholders(),
                settingService.formatDateTime(template.getUpdatedAt())
        );
    }

    private record TemplateDefault(String code, String name, String description,
                                   String subject, String content, List<String> placeholders) {
    }
}
