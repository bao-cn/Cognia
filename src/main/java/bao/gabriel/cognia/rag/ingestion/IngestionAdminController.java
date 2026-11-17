package bao.gabriel.cognia.rag.ingestion;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.rag.ingestion.dto.UpdateIngestionSettingRequest;
import bao.gabriel.cognia.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ingestion")
@RequiredArgsConstructor
public class IngestionAdminController {
    private final IngestionSettingService settingService;
    private final IngestionQueueService queueService;
    private final IngestionMetricsService metricsService;
    private final VectorIndexService indexService;

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('ingestion:read','settings:read')")
    public ApiResponse<IngestionSetting> settings() {
        return ApiResponse.success(settingService.get());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyAuthority('ingestion:update','settings:update')")
    public ApiResponse<IngestionSetting> update(@Valid @RequestBody UpdateIngestionSettingRequest request) {
        IngestionSetting input = new IngestionSetting();
        input.setConcurrency(request.concurrency());
        input.setQueueLimit(request.queueLimit());
        input.setMaxRetries(request.maxRetries());
        input.setRetryBackoffSeconds(request.retryBackoffSeconds());
        input.setHnswM(request.hnswM());
        input.setHnswEfConstruction(request.hnswEfConstruction());
        input.setHnswEfSearch(request.hnswEfSearch());
        input.setHealthFailureThreshold(request.healthFailureThreshold());
        input.setQueueAlertDepth(request.queueAlertDepth());
        input.setFailureAlertPercent(request.failureAlertPercent());
        input.setAlertEmailEnabled(request.alertEmailEnabled());
        input.setAlertRecipient(request.alertRecipient());
        input.setAlertRoleCode(request.alertRoleCode());
        return ApiResponse.success(settingService.update(input, SecurityUtils.getUserId()));
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyAuthority('ingestion:read','settings:read')")
    public ApiResponse<Map<String, Object>> metrics() {
        return ApiResponse.success(metricsService.metrics());
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAnyAuthority('ingestion:read','settings:read')")
    public ApiResponse<List<IngestionJob>> jobs(@RequestParam(defaultValue = "false") boolean deadOnly) {
        return ApiResponse.success(deadOnly ? queueService.dead(100) : queueService.recent(100));
    }

    @PostMapping("/jobs/{id}/requeue")
    @PreAuthorize("hasAnyAuthority('ingestion:update','settings:update')")
    public ApiResponse<Void> requeue(@PathVariable Long id) {
        queueService.requeueDead(id);
        return ApiResponse.success();
    }

    @PostMapping("/index/ensure")
    @PreAuthorize("hasAnyAuthority('ingestion:update','settings:update')")
    public ApiResponse<Map<String, Object>> ensureIndex() {
        return ApiResponse.success(indexService.ensureIndex());
    }

    @PostMapping("/index/rebuild")
    @PreAuthorize("hasAnyAuthority('ingestion:update','settings:update')")
    public ApiResponse<Map<String, Object>> rebuildIndex() {
        return ApiResponse.success(indexService.rebuild());
    }
}
