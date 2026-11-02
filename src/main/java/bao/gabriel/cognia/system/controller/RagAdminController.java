package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.rag.RagSettingResponse;
import bao.gabriel.cognia.system.dto.rag.UpdateRagSettingRequest;
import bao.gabriel.cognia.system.service.RagSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/rag")
@RequiredArgsConstructor
public class RagAdminController {

    private final RagSettingService ragSettingService;

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
}
