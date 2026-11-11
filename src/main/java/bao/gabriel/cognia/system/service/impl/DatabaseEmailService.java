package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.settings.SmtpConfig;
import bao.gabriel.cognia.system.service.EmailHtmlRenderer;
import bao.gabriel.cognia.system.service.EmailService;
import bao.gabriel.cognia.system.service.MailTemplateService;
import bao.gabriel.cognia.system.service.RenderedMail;
import bao.gabriel.cognia.system.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;

/**
 * SMTP sender backed by {@code sys_site_setting}. When SMTP is disabled or not
 * configured the verification code is written to the application log, keeping
 * local development possible without a mail server.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseEmailService implements EmailService {

    private final SiteSettingService settingService;
    private final MailTemplateService mailTemplateService;
    private final EmailHtmlRenderer emailHtmlRenderer;

    @Override
    public void sendRegisterCode(String email, String code, long ttlMinutes) {
        SmtpConfig config = settingService.getSmtpConfig();
        if (!config.usable()) {
            log.warn("""

                    ==================== DEV MAIL (SMTP disabled) ====================
                    To      : {}
                    Subject : {} 注册验证码
                    Code    : {}
                    Expires : {} minutes
                    ==================================================================
                    """, email, siteName(), code, ttlMinutes);
            return;
        }
        RenderedMail mail = mailTemplateService.render(MailTemplateService.REGISTER_CODE, Map.of(
                "siteName", siteName(),
                "email", email,
                "code", code,
                "ttlMinutes", String.valueOf(ttlMinutes),
                "expiresAt", settingService.formatDateTime(LocalDateTime.now().plusMinutes(ttlMinutes)),
                "year", String.valueOf(LocalDate.now().getYear())
        ));
        send(config, email, mail.subject(), mail.content());
    }

    @Override
    public void sendEmailChangeCode(String email, String code, long ttlMinutes) {
        SmtpConfig config = settingService.getSmtpConfig();
        if (!config.usable()) {
            log.warn("""

                    ==================== DEV MAIL (SMTP disabled) ====================
                    To      : {}
                    Subject : 邮箱变更验证码
                    Code    : {}
                    Expires : {} minutes
                    ==================================================================
                    """, email, code, ttlMinutes);
            return;
        }
        String site = HtmlUtils.htmlEscape(siteName());
        String safeEmail = HtmlUtils.htmlEscape(email);
        String content = """
                <h1 style="margin:0 0 16px;font-size:22px;line-height:1.35;color:#111827;">验证您的新邮箱</h1>
                <p style="margin:0 0 16px;">您好！</p>
                <p style="margin:0 0 16px;">您正在将 %s 账户的邮箱变更为 %s，本次验证码为：</p>
                <div style="margin:24px 0;padding:16px 20px;background-color:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;text-align:center;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;font-size:28px;font-weight:700;letter-spacing:8px;color:#111827;">%s</div>
                <p style="margin:0 0 16px;">验证码 %d 分钟内有效。如非本人操作，请忽略本邮件。</p>
                <p style="margin:0;">—— %s 团队</p>
                """.formatted(site, safeEmail, code, ttlMinutes, site);
        send(config, email, "【" + siteName() + "】邮箱变更验证码", content);
    }

    @Override
    public void sendTestEmail(String recipient) {
        SmtpConfig config = settingService.getSmtpConfig();
        if (!config.usable()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "SMTP 未启用或未配置，请先保存并启用邮件服务");
        }
        RenderedMail mail = mailTemplateService.render(MailTemplateService.SMTP_TEST, Map.of(
                "siteName", siteName(),
                "recipient", recipient,
                "sentAt", settingService.formatDateTime(LocalDateTime.now()),
                "year", String.valueOf(LocalDate.now().getYear())
        ));
        send(config, recipient, mail.subject(), mail.content());
    }

    private String siteName() {
        String name = settingService.getSetting().getSiteName();
        return name == null || name.isBlank() ? "Cognia" : name;
    }

    private void send(SmtpConfig config, String to, String subject, String htmlFragment) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        sender.setDefaultEncoding("UTF-8");
        if (config.username() != null) {
            sender.setUsername(config.username());
        }
        if (config.password() != null) {
            sender.setPassword(config.password());
        }

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", String.valueOf(config.auth()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(config.starttls()));
        properties.put("mail.smtp.ssl.enable", String.valueOf(config.ssl()));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");

        String from = config.from();
        if (from == null || from.isBlank()) {
            from = config.username() == null || config.username().isBlank()
                    ? "no-reply@cognia.local"
                    : config.username();
        }

        String html = emailHtmlRenderer.wrap(siteName(), subject, htmlFragment);
        String text = emailHtmlRenderer.plainText(siteName(), htmlFragment);

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
            log.info("HTML mail sent to {} via {}", to, config.host());
        } catch (MailException | MessagingException ex) {
            log.error("Failed to send mail to {} via {}", to, config.host(), ex);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "邮件发送失败，请检查 SMTP 配置");
        }
    }
}
