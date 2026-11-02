package bao.gabriel.cognia.system.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps an editable HTML fragment in a table-based, inline-styled email shell
 * that renders consistently in mainstream clients (Gmail, Outlook, Apple Mail,
 * Thunderbird). Also derives a plain-text alternative for multipart emails.
 */
@Component
public class EmailHtmlRenderer {

    private static final Pattern LINK = Pattern.compile(
            "(?is)<a\\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern BLOCK_END = Pattern.compile(
            "(?i)</(p|div|h[1-6]|tr|li|table|section|article|blockquote)>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern RAW_ELEMENT = Pattern.compile(
            "(?is)<(script|style)[^>]*>.*?</\\1>");

    private static final String SHELL = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <meta name="x-apple-disable-message-reformatting">
              <meta name="color-scheme" content="light">
              <title>__SUBJECT__</title>
            </head>
            <body style="margin:0;padding:0;background-color:#f3f4f6;-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;">
              <div style="display:none;font-size:1px;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;color:#f3f4f6;">__SUBJECT__</div>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f3f4f6;border-collapse:collapse;">
                <tr>
                  <td align="center" style="padding:24px 12px;">
                    <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:600px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:8px;border-collapse:separate;">
                      <tr>
                        <td style="padding:24px 32px 8px 32px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:16px;font-weight:600;line-height:1.4;color:#111827;">__SITE__</td>
                      </tr>
                      <tr>
                        <td style="padding:8px 32px 32px 32px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.7;color:#374151;word-break:break-word;">__CONTENT__</td>
                      </tr>
                      <tr>
                        <td style="padding:16px 32px 24px 32px;border-top:1px solid #eef0f3;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:12px;line-height:1.6;color:#9ca3af;">此邮件由 __SITE__ 自动发送，请勿直接回复。</td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

    public String wrap(String siteName, String subject, String content) {
        String site = StringUtils.hasText(siteName) ? siteName : "Cognia";
        return SHELL
                .replace("__SUBJECT__", HtmlUtils.htmlEscape(subject == null ? "" : subject))
                .replace("__SITE__", HtmlUtils.htmlEscape(site))
                .replace("__CONTENT__", content == null ? "" : content);
    }

    public String plainText(String siteName, String htmlFragment) {
        String site = StringUtils.hasText(siteName) ? siteName : "Cognia";
        String text = toPlainText(htmlFragment);
        return (text.isEmpty() ? "" : text + "\n\n")
                + "此邮件由 " + site + " 自动发送，请勿直接回复。";
    }

    public String toPlainText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        Matcher link = LINK.matcher(html);
        StringBuilder linked = new StringBuilder();
        while (link.find()) {
            String label = TAG.matcher(link.group(2)).replaceAll("").trim();
            link.appendReplacement(linked, Matcher.quoteReplacement(label + " (" + link.group(1) + ")"));
        }
        link.appendTail(linked);

        String text = RAW_ELEMENT.matcher(linked.toString()).replaceAll("");
        text = LINE_BREAK.matcher(text).replaceAll("\n");
        text = BLOCK_END.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        text = HtmlUtils.htmlUnescape(text);
        text = text.replace("\r", "")
                .replaceAll("(?m)^[ \\t]+", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return text;
    }
}
