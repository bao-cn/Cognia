package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.audit.AuditLogResponse;
import bao.gabriel.cognia.system.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Durable audit trail for console operations and scheduled task runs.
 */
public interface AuditService {

    void record(AuditLog log);

    void recordSystem(String module, String action, String description, String status,
                      String errorMessage, Integer durationMs);

    PageResult<AuditLogResponse> page(long page, long size, String module, String status,
                                      String keyword, LocalDateTime start, LocalDateTime end);

    List<String> modules();
}
