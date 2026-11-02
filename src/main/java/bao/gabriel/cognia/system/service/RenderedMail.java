package bao.gabriel.cognia.system.service;

import org.springframework.web.util.HtmlUtils;

import java.util.Map;

/**
 * Rendered subject/content ready to be sent.
 */
public record RenderedMail(String subject, String content) {
    public static RenderedMail render(String code, String subject, String content, Map<String, String> variables) {
        // A subject header must never contain CR/LF, including when a variable
        // value happens to contain one.
        String renderedSubject = replace(subject, variables).replaceAll("[\\r\\n]+", " ").trim();
        // Template variables are plain text; escape them for the HTML body so
        // admin-provided site names / email addresses cannot break the markup.
        Map<String, String> htmlVariables = variables.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> HtmlUtils.htmlEscape(entry.getValue() == null ? "" : entry.getValue())
                ));
        return new RenderedMail(renderedSubject, replace(content, htmlVariables));
    }

    private static String replace(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}")
                .matcher(template);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String value = variables.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(builder, java.util.regex.Matcher.quoteReplacement(value));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}
