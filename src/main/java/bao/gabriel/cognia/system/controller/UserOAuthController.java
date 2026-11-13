package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.oauth.UserOAuthAccountResponse;
import bao.gabriel.cognia.system.service.OAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/oauth")
@RequiredArgsConstructor
public class UserOAuthController {

    private final OAuthService oauthService;

    @GetMapping("/accounts")
    public ApiResponse<List<UserOAuthAccountResponse>> accounts() {
        return ApiResponse.success(oauthService.userAccounts(SecurityUtils.getUserId()));
    }

    @PostMapping("/{provider}/authorize-url")
    public ApiResponse<String> authorizeUrl(@PathVariable String provider, @RequestParam String redirectUri) {
        return ApiResponse.success(oauthService.userAuthorizeUrl(SecurityUtils.getUserId(), provider, redirectUri));
    }

    @DeleteMapping("/{provider}")
    public ApiResponse<Void> unlink(@PathVariable String provider) {
        oauthService.unlinkAccount(SecurityUtils.getUserId(), provider);
        return ApiResponse.success();
    }
}
