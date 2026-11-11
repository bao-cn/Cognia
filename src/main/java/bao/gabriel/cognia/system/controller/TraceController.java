package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.rag.service.ChatTraceService;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.rag.service.TraceReplayService;
import bao.gabriel.cognia.system.dto.trace.TraceDetailResponse;
import bao.gabriel.cognia.system.dto.trace.ReplayResponse;
import bao.gabriel.cognia.system.dto.trace.TraceStatsResponse;
import bao.gabriel.cognia.system.dto.trace.TraceSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/traces")
@RequiredArgsConstructor
public class TraceController {

    private final ChatTraceService chatTraceService;
    private final TraceReplayService traceReplayService;

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('trace:read', 'settings:read')")
    public ApiResponse<TraceStatsResponse> stats() {
        return ApiResponse.success(chatTraceService.stats());
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('trace:read', 'settings:read')")
    public ApiResponse<PageResult<TraceSummaryResponse>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(chatTraceService.page(page, size, status, keyword));
    }

    @GetMapping("/{traceId}")
    @PreAuthorize("hasAnyAuthority('trace:read', 'settings:read')")
    public ApiResponse<TraceDetailResponse> detail(@PathVariable String traceId) {
        TraceDetailResponse detail = chatTraceService.detail(traceId);
        if (detail == null) {
            throw BusinessException.notFound("链路记录不存在或已过期");
        }
        return ApiResponse.success(detail);
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('trace:write', 'settings:update')")
    public ApiResponse<Void> clear() {
        chatTraceService.clearAll();
        return ApiResponse.success();
    }

    /** Re-runs retrieval or the LLM call using the recorded trace. */
    @PostMapping("/{traceId}/replay")
    @PreAuthorize("hasAnyAuthority('trace:write', 'settings:update')")
    public ApiResponse<ReplayResponse> replay(@PathVariable String traceId,
                                              @RequestParam(defaultValue = "retrieval") String stage) {
        return ApiResponse.success(traceReplayService.replay(traceId, stage, SecurityUtils.getUserId()));
    }
}
