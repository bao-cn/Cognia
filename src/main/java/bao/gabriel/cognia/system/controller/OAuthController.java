package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.system.dto.oauth.OAuthCallbackResponse;
import bao.gabriel.cognia.system.dto.oauth.OAuthLinkRequest;
import bao.gabriel.cognia.system.dto.oauth.OAuthRegistrationRequest;
import bao.gabriel.cognia.system.dto.oauth.PublicOAuthProvider;
import bao.gabriel.cognia.system.service.OAuthService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;

    @GetMapping("/providers")
    public ApiResponse<List<PublicOAuthProvider>> providers() {
        return ApiResponse.success(oauthService.publicProviders());
    }

    @GetMapping("/{code}/authorize-url")
    public ApiResponse<String> authorizeUrl(@PathVariable String code,
                                            @RequestParam String redirectUri,
                                            @RequestParam(required = false) String intent) {
        return ApiResponse.success(oauthService.authorizeUrl(code, redirectUri, intent));
    }

    @GetMapping("/{provider}/callback")
    public ApiResponse<OAuthCallbackResponse> callback(@PathVariable String provider,
                                                       @RequestParam("code") String code,
                                                       @RequestParam String state) {
        return ApiResponse.success(oauthService.callback(code, state));
    }

    @PostMapping("/link")
    public ApiResponse<OAuthCallbackResponse> link(@Valid @RequestBody OAuthLinkRequest request) {
        return ApiResponse.success(oauthService.link(request.linkToken()));
    }

    @PostMapping("/register")
    public ApiResponse<OAuthCallbackResponse> register(@Valid @RequestBody OAuthRegistrationRequest request) {
        return ApiResponse.success(oauthService.completeRegistration(
                request.registrationToken(), request.username()));
    }
}
