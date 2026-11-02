package bao.gabriel.cognia.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Small servlet helpers that are shared by auth and logging code.
 */
public final class WebUtils {

    private static final String UNKNOWN = "unknown";

    private WebUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !UNKNOWN.equalsIgnoreCase(value)) {
                // X-Forwarded-For can contain a chain: client, proxy1, proxy2
                return truncate(value.split(",")[0].trim(), 64);
            }
        }
        String remote = request.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote)) {
            return "127.0.0.1";
        }
        return remote == null ? UNKNOWN : truncate(remote, 64);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public static String getUserAgent(HttpServletRequest request) {
        String ua = request == null ? null : request.getHeader("User-Agent");
        if (ua == null) {
            return UNKNOWN;
        }
        return ua.length() > 512 ? ua.substring(0, 512) : ua;
    }

    public static String bearerToken(HttpServletRequest request) {
        String header = request == null ? null : request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
