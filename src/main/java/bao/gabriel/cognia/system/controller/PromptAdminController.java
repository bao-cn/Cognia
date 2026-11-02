package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.prompt.PromptSettingResponse;
import bao.gabriel.cognia.system.dto.prompt.UpdatePromptSettingRequest;
import bao.gabriel.cognia.system.service.PromptSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/prompts")
@RequiredArgsConstructor
public class PromptAdminController {

    private final PromptSettingService promptSettingService;

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('rag:read', 'settings:read')")
    public ApiResponse<PromptSettingResponse> settings() {
        return ApiResponse.success(promptSettingService.getAdminSettings());
    }

    @GetMapping("/defaults")
    @PreAuthorize("hasAnyAuthority('rag:read', 'settings:read')")
    public ApiResponse<PromptSettingResponse> defaults() {
        return ApiResponse.success(promptSettingService.getDefaults());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyAuthority('rag:update', 'settings:update')")
    public ApiResponse<PromptSettingResponse> update(@Valid @RequestBody UpdatePromptSettingRequest request) {
        return ApiResponse.success(promptSettingService.updateSettings(request, SecurityUtils.getUserId()));
    }

    @PostMapping("/reset")
    @PreAuthorize("hasAnyAuthority('rag:update', 'settings:update')")
    public ApiResponse<PromptSettingResponse> reset() {
        return ApiResponse.success(promptSettingService.reset(SecurityUtils.getUserId()));
    }
}
