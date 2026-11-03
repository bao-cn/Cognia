package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.rag.service.RagCacheService;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.rag.RagSettingResponse;
import bao.gabriel.cognia.system.dto.rag.UpdateRagSettingRequest;
import bao.gabriel.cognia.system.service.RagSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/rag")
@RequiredArgsConstructor
public class RagAdminController {

    private final RagSettingService ragSettingService;
    private final RagCacheService ragCacheService;

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('rag:read', 'settings:read')")
    public ApiResponse<RagSettingResponse> settings() {
        return ApiResponse.success(ragSettingService.getAdminSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyAuthority('rag:update', 'settings:update')")
    public ApiResponse<RagSettingResponse> update(@Valid @RequestBody UpdateRagSettingRequest request) {
        return ApiResponse.success(ragSettingService.updateSettings(request, SecurityUtils.getUserId()));
    }

    @GetMapping("/cache/stats")
    @PreAuthorize("hasAnyAuthority('rag:read', 'settings:read')")
    public ApiResponse<RagCacheService.Stats> cacheStats() {
        return ApiResponse.success(ragCacheService.stats());
    }

    /**
     * Precise invalidation: removes retrieval cache rows that reference the given
     * document. Semantic cache entries are intentionally kept (they are answers,
     * not document-bound).
     */
    @DeleteMapping("/cache/retrieval")
    @PreAuthorize("hasAnyAuthority('rag:update', 'settings:update')")
    public ApiResponse<Map<String, Object>> invalidateRetrieval(@RequestParam Long documentId) {
        long deleted = ragCacheService.invalidateDocument(documentId);
        return ApiResponse.success(Map.of("documentId", documentId, "deleted", deleted));
    }

    @DeleteMapping("/cache/semantic")
    @PreAuthorize("hasAnyAuthority('rag:update', 'settings:update')")
    public ApiResponse<Map<String, Object>> clearSemantic() {
        return ApiResponse.success(Map.of("deleted", ragCacheService.clearSemantic()));
    }
}
