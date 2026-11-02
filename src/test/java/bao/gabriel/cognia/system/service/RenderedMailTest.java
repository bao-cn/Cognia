package bao.gabriel.cognia.system.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderedMailTest {

    @Test
    void replacesKnownPlaceholders() {
        RenderedMail mail = RenderedMail.render(
                "REGISTER_CODE",
                "【{{ siteName }}】验证码 {{code}}",
                "您好，{{email}} 的验证码是 {{code}}，{{ttlMinutes}} 分钟内有效。",
                Map.of(
                        "siteName", "Cognia",
                        "code", "123456",
                        "email", "user@example.com",
                        "ttlMinutes", "5"
                )
        );

        assertEquals("【Cognia】验证码 123456", mail.subject());
        assertEquals("您好，user@example.com 的验证码是 123456，5 分钟内有效。", mail.content());
    }

    @Test
    void stripsNewlinesFromTheRenderedSubject() {
        RenderedMail mail = RenderedMail.render(
                "REGISTER_CODE",
                "【{{siteName}}】验证码",
                "content",
                Map.of("siteName", "Cognia\r\nBcc: evil@example.com")
        );

        assertEquals("【Cognia Bcc: evil@example.com】验证码", mail.subject());
    }

    @Test
    void replacesMissingVariablesWithEmptyStrings() {
        RenderedMail mail = RenderedMail.render(
                "SMTP_TEST",
                "{{siteName}}",
                "{{missing}}",
                Map.of("siteName", "Cognia")
        );

        assertEquals("Cognia", mail.subject());
        assertEquals("", mail.content());
    }
}
