package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.system.dto.oauth.AdminOAuthProviderResponse;
import bao.gabriel.cognia.system.dto.oauth.UpdateOAuthProviderRequest;
import bao.gabriel.cognia.system.service.OAuthService;
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
@RequestMapping("/api/v1/admin/oauth/providers")
@RequiredArgsConstructor
public class AdminOAuthController {

    private final OAuthService oauthService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('settings:read', 'user:read')")
    public ApiResponse<List<AdminOAuthProviderResponse>> list() {
        return ApiResponse.success(oauthService.adminProviders());
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyAuthority('settings:update', 'user:update')")
    public ApiResponse<AdminOAuthProviderResponse> update(@PathVariable String code,
                                                          @RequestBody UpdateOAuthProviderRequest request) {
        return ApiResponse.success(oauthService.updateProvider(code, request));
    }
}
