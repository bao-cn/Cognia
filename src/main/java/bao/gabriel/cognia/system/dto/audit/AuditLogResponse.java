package bao.gabriel.cognia.system.dto.audit;

/**
 * Audit log row shown in the console. {@code payload} is already sanitized and
 * truncated when recorded.
 */
public record AuditLogResponse(
        Long id,
        Long userId,
        String username,
        String module,
        String action,
        String description,
        String resourceType,
        String resourceId,
        String method,
        String path,
        String ip,
        String userAgent,
        String payload,
        String status,
        String errorMessage,
        Integer durationMs,
        String createTime
) {
}
