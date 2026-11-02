package bao.gabriel.cognia.config;

import bao.gabriel.cognia.common.util.WebUtils;
import bao.gabriel.cognia.security.LoginUser;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.entity.AuditLog;
import bao.gabriel.cognia.system.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Records authenticated mutating API requests. The log stores the controller,
 * method, path, client address and a redacted request body; auth and
 * notification endpoints are excluded to avoid noise and secret leakage.
 */
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(\"(?:password|oldPassword|newPassword|secretKey|token|accessToken|refreshToken|secret|authorization|turnstileToken)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);
    private static final String ATTR_START = AuditInterceptor.class.getName() + ".start";
    private static final String[] SKIP_PREFIXES = {
            "/api/v1/auth", "/api/v1/notifications", "/api/v1/settings/public", "/api/v1/legal/documents"
    };

    private final AuditService auditService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        request.setAttribute(ATTR_START, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, @Nullable Exception ex) {
        if (!(handler instanceof HandlerMethod handlerMethod) || !shouldAudit(request)) {
            return;
        }
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            return;
        }

        String module = handlerMethod.getBeanType().getSimpleName()
                .replace("Controller", "");
        String action = handlerMethod.getMethod().getName();
        String path = request.getRequestURI();
        boolean failed = ex != null || response.getStatus() >= 400;

        AuditLog entry = new AuditLog();
        entry.setUserId(loginUser.getUserId());
        entry.setUsername(loginUser.getUsername());
        entry.setModule(module);
        entry.setAction(action);
        entry.setDescription(describe(module, action));
        entry.setResourceType(module);
        entry.setResourceId(extractResourceId(path));
        entry.setMethod(request.getMethod());
        entry.setPath(trim(path, 512));
        entry.setIp(WebUtils.getClientIp(request));
        entry.setUserAgent(WebUtils.getUserAgent(request));
        entry.setRequestPayload(readPayload(request));
        entry.setStatus(failed ? "FAILED" : "SUCCESS");
        entry.setErrorMessage(failed ? describeFailure(ex, response.getStatus()) : null);
        Object start = request.getAttribute(ATTR_START);
        if (start instanceof Long startedAt) {
            entry.setDurationMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt));
        }
        auditService.record(entry);
    }

    private boolean shouldAudit(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return false;
        }
        String path = request.getRequestURI();
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private String readPayload(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return null;
        }
        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        String payload = new String(content, StandardCharsets.UTF_8).trim();
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        payload = SECRET_FIELD.matcher(payload).replaceAll("$1\"***\"");
        return trim(payload, 4000);
    }

    private String describe(String module, String action) {
        String verb = switch (action) {
            case "create" -> "创建";
            case "update", "updateMe", "updateStatus" -> "修改";
            case "delete" -> "删除";
            case "upload", "uploadAvatar" -> "上传";
            case "run", "runNow" -> "执行";
            case "testSmtp", "testConnection" -> "测试";
            default -> "";
        };
        return verb.isBlank() ? module + " · " + action : verb + module;
    }

    private String describeFailure(Exception ex, int status) {
        if (ex != null) {
            return trim(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), 1000);
        }
        return "HTTP " + status;
    }

    private String extractResourceId(String path) {
        if (path == null) {
            return null;
        }
        int index = path.lastIndexOf('/');
        if (index >= 0 && index < path.length() - 1) {
            String value = path.substring(index + 1);
            if (value.chars().allMatch(Character::isDigit)) {
                return value;
            }
        }
        return null;
    }

    private String trim(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
