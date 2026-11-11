package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.tool.ToolSettingResponse;
import bao.gabriel.cognia.system.dto.tool.UpdateToolSettingRequest;
import bao.gabriel.cognia.system.service.ToolSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tools")
@RequiredArgsConstructor
public class ToolSettingController {

    private final ToolSettingService toolSettingService;

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('settings:read', 'tool:read')")
    public ApiResponse<ToolSettingResponse> settings() {
        return ApiResponse.success(toolSettingService.getAdminSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyAuthority('settings:update', 'tool:update')")
    public ApiResponse<ToolSettingResponse> update(@Valid @RequestBody UpdateToolSettingRequest request) {
        return ApiResponse.success(toolSettingService.updateSettings(request, SecurityUtils.getUserId()));
    }
}
