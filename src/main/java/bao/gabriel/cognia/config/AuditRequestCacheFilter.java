package bao.gabriel.cognia.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * Caches JSON request bodies so the audit interceptor can persist a sanitized
 * copy after the controller has consumed the stream. Multipart bodies are not
 * cached (the file payload is never written to the audit log).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuditRequestCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contentType = request.getContentType();
        boolean cacheable = contentType != null
                && (contentType.startsWith("application/json") || contentType.startsWith("application/x-www-form-urlencoded"));
        if (cacheable) {
            filterChain.doFilter(new ContentCachingRequestWrapper(request, 64 * 1024), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
