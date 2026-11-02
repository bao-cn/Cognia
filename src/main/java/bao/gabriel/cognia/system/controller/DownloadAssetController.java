package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.downloads.DownloadAssetResponse;
import bao.gabriel.cognia.system.dto.downloads.UpdateDownloadAssetRequest;
import bao.gabriel.cognia.system.service.DownloadAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/downloads")
@RequiredArgsConstructor
public class DownloadAssetController {

    private final DownloadAssetService downloadAssetService;

    @GetMapping("/public")
    public ApiResponse<List<DownloadAssetResponse>> publicList() {
        return ApiResponse.success(downloadAssetService.listPublic());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('settings:read')")
    public ApiResponse<List<DownloadAssetResponse>> list() {
        return ApiResponse.success(downloadAssetService.listAdmin());
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('settings:update')")
    public ApiResponse<DownloadAssetResponse> update(@PathVariable String code,
                                                     @Valid @RequestBody UpdateDownloadAssetRequest request) {
        return ApiResponse.success(downloadAssetService.update(code, request, SecurityUtils.getUserId()));
    }
}
