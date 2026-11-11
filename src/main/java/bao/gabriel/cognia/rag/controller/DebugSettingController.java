package bao.gabriel.cognia.rag.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.rag.service.DebugSettingService;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.trace.DebugSettingResponse;
import bao.gabriel.cognia.system.dto.trace.UpdateDebugSettingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/debug")
@RequiredArgsConstructor
public class DebugSettingController {

    private final DebugSettingService debugSettingService;

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('trace:read', 'settings:read')")
    public ApiResponse<DebugSettingResponse> settings() {
        return ApiResponse.success(debugSettingService.getSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyAuthority('trace:write', 'settings:update')")
    public ApiResponse<DebugSettingResponse> update(@RequestBody UpdateDebugSettingRequest request) {
        return ApiResponse.success(debugSettingService.updateSettings(request, SecurityUtils.getUserId()));
    }
}
