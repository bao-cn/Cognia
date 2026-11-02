package bao.gabriel.cognia.system.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailHtmlRendererTest {

    private final EmailHtmlRenderer renderer = new EmailHtmlRenderer();

    @Test
    void wrapsContentInATableBasedEmailShell() {
        String html = renderer.wrap("Cognia & Co", "验证码", "<p>Hello</p>");

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("role=\"presentation\""));
        assertTrue(html.contains("max-width:600px"));
        assertTrue(html.contains("Cognia &amp; Co"));
        assertTrue(html.contains("<p>Hello</p>"));
    }

    @Test
    void derivesReadablePlainTextWithLinkUrls() {
        String html = renderer.wrap("Cognia", "主题",
                "<p>点击 <a href=\"https://example.com/help\">帮助中心</a></p><p>验证码：123456</p>");

        String text = renderer.toPlainText(html);

        assertTrue(text.contains("帮助中心 (https://example.com/help)"));
        assertTrue(text.contains("验证码：123456"));
        assertFalse(text.contains("<p>"));
    }
}
