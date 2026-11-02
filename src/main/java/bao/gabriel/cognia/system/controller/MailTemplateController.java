package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.settings.MailTemplatePreviewResponse;
import bao.gabriel.cognia.system.dto.settings.MailTemplateResponse;
import bao.gabriel.cognia.system.dto.settings.UpdateMailTemplateRequest;
import bao.gabriel.cognia.system.service.MailTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/mail-templates")
@RequiredArgsConstructor
public class MailTemplateController {

    private final MailTemplateService mailTemplateService;

    @GetMapping
    @PreAuthorize("hasAuthority('settings:read')")
    public ApiResponse<List<MailTemplateResponse>> list() {
        return ApiResponse.success(mailTemplateService.listTemplates());
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('settings:update')")
    public ApiResponse<MailTemplateResponse> update(@PathVariable String code,
                                                    @Valid @RequestBody UpdateMailTemplateRequest request) {
        return ApiResponse.success(mailTemplateService.updateTemplate(code, request, SecurityUtils.getUserId()));
    }

    @PostMapping("/{code}/reset")
    @PreAuthorize("hasAuthority('settings:update')")
    public ApiResponse<MailTemplateResponse> reset(@PathVariable String code) {
        return ApiResponse.success(mailTemplateService.resetTemplate(code, SecurityUtils.getUserId()));
    }

    @PostMapping("/{code}/preview")
    @PreAuthorize("hasAuthority('settings:read')")
    public ApiResponse<MailTemplatePreviewResponse> preview(@PathVariable String code,
                                                            @Valid @RequestBody UpdateMailTemplateRequest request) {
        return ApiResponse.success(mailTemplateService.previewTemplate(code, request));
    }
}
