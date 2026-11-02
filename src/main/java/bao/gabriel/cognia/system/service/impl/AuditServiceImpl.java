package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.audit.AuditLogResponse;
import bao.gabriel.cognia.system.entity.AuditLog;
import bao.gabriel.cognia.system.mapper.AuditLogMapper;
import bao.gabriel.cognia.system.service.AuditService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final int MAX_PAYLOAD = 4000;

    private final AuditLogMapper auditLogMapper;
    private final SiteSettingService siteSettingService;

    @Override
    public void record(AuditLog entry) {
        if (entry == null) {
            return;
        }
        try {
            if (entry.getCreateTime() == null) {
                entry.setCreateTime(LocalDateTime.now());
            }
            if (entry.getStatus() == null) {
                entry.setStatus("SUCCESS");
            }
            if (entry.getRequestPayload() != null && entry.getRequestPayload().length() > MAX_PAYLOAD) {
                entry.setRequestPayload(entry.getRequestPayload().substring(0, MAX_PAYLOAD) + "...");
            }
            auditLogMapper.insert(entry);
        } catch (Exception ex) {
            // Auditing must never break the business request.
            log.warn("Unable to persist audit log for {}: {}", entry.getAction(), ex.getMessage());
        }
    }

    @Override
    public void recordSystem(String module, String action, String description, String status,
                             String errorMessage, Integer durationMs) {
        AuditLog entry = new AuditLog();
        entry.setModule(module);
        entry.setAction(action);
        entry.setDescription(description);
        entry.setStatus(status);
        entry.setErrorMessage(truncate(errorMessage, 1000));
        entry.setDurationMs(durationMs);
        entry.setCreateTime(LocalDateTime.now());
        record(entry);
    }

    @Override
    public PageResult<AuditLogResponse> page(long page, long size, String module, String status,
                                             String keyword, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<AuditLog> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(module)) {
            query.eq(AuditLog::getModule, module);
        }
        if (StringUtils.hasText(status)) {
            query.eq(AuditLog::getStatus, status.toUpperCase());
        }
        if (start != null) {
            query.ge(AuditLog::getCreateTime, start);
        }
        if (end != null) {
            query.le(AuditLog::getCreateTime, end);
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            query.and(w -> w.like(AuditLog::getUsername, value)
                    .or().like(AuditLog::getAction, value)
                    .or().like(AuditLog::getDescription, value)
                    .or().like(AuditLog::getPath, value)
                    .or().like(AuditLog::getResourceId, value));
        }
        query.orderByDesc(AuditLog::getCreateTime).orderByDesc(AuditLog::getId);

        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(page, size), query);
        List<AuditLogResponse> records = result.getRecords().stream().map(this::toResponse).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    @Override
    public List<String> modules() {
        return auditLogMapper.selectObjs(new LambdaQueryWrapper<AuditLog>()
                        .select(AuditLog::getModule)
                        .groupBy(AuditLog::getModule)
                        .orderByAsc(AuditLog::getModule))
                .stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getUserId(), log.getUsername(), log.getModule(), log.getAction(),
                log.getDescription(), log.getResourceType(), log.getResourceId(), log.getMethod(),
                log.getPath(), log.getIp(), log.getUserAgent(), log.getRequestPayload(), log.getStatus(),
                log.getErrorMessage(), log.getDurationMs(),
                log.getCreateTime() == null ? null : siteSettingService.formatDateTime(log.getCreateTime()));
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
