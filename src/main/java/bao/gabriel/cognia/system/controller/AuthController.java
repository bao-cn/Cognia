package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.util.WebUtils;
import bao.gabriel.cognia.system.dto.auth.LoginOptionsRequest;
import bao.gabriel.cognia.system.dto.auth.LoginOptionsResponse;
import bao.gabriel.cognia.system.dto.auth.LoginRequest;
import bao.gabriel.cognia.system.dto.auth.LoginCodeSendRequest;
import bao.gabriel.cognia.system.dto.auth.LoginEmailCodeRequest;
import bao.gabriel.cognia.system.dto.auth.LoginTwoFactorRequest;
import bao.gabriel.cognia.system.dto.auth.VerifyCodeRequest;
import bao.gabriel.cognia.system.dto.auth.LoginResponse;
import bao.gabriel.cognia.system.dto.auth.RefreshTokenRequest;
import bao.gabriel.cognia.system.dto.auth.RegisterRequest;
import bao.gabriel.cognia.system.dto.auth.AvailabilityResponse;
import bao.gabriel.cognia.system.dto.auth.SendCodeRequest;
import bao.gabriel.cognia.system.dto.auth.SendCodeResponse;
import bao.gabriel.cognia.system.dto.user.UserResponse;
import bao.gabriel.cognia.system.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Sends a registration verification code to the given email.
     */
    @PostMapping("/register/code")
    public ApiResponse<SendCodeResponse> sendRegisterCode(@Valid @RequestBody SendCodeRequest request,
                                                          HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.sendRegisterCode(request, WebUtils.getClientIp(httpRequest)));
    }

    @GetMapping("/register/check-email")
    public ApiResponse<AvailabilityResponse> checkEmail(@RequestParam String email) {
        return ApiResponse.success(authService.checkEmail(email));
    }

    @GetMapping("/register/check-username")
    public ApiResponse<AvailabilityResponse> checkUsername(@RequestParam String username) {
        return ApiResponse.success(authService.checkUsername(username));
    }

    @GetMapping("/register/suggestions")
    public ApiResponse<java.util.List<String>> suggestions(@RequestParam(required = false) String base) {
        return ApiResponse.success(authService.usernameSuggestions(base));
    }

    @PostMapping("/register/verify-code")
    public ApiResponse<Void> verifyRegisterCode(@Valid @RequestBody VerifyCodeRequest request) {
        authService.verifyRegisterCode(request.email(), request.code());
        return ApiResponse.success();
    }

    @PostMapping("/login/options")
    public ApiResponse<LoginOptionsResponse> loginOptions(@Valid @RequestBody LoginOptionsRequest request) {
        return ApiResponse.success(authService.loginOptions(request.account()));
    }

    @PostMapping("/login/code")
    public ApiResponse<SendCodeResponse> sendLoginCode(@Valid @RequestBody LoginCodeSendRequest request,
                                                       HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.sendLoginCode(request, WebUtils.getClientIp(httpRequest)));
    }

    @PostMapping("/login/email-code")
    public ApiResponse<LoginResponse> loginWithEmailCode(@Valid @RequestBody LoginEmailCodeRequest request,
                                                         HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.loginWithEmailCode(request,
                WebUtils.getClientIp(httpRequest), WebUtils.getUserAgent(httpRequest)));
    }

    @PostMapping("/login/2fa")
    public ApiResponse<LoginResponse> loginWithTwoFactor(@Valid @RequestBody LoginTwoFactorRequest request,
                                                         HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.loginWithTwoFactor(request,
                WebUtils.getClientIp(httpRequest), WebUtils.getUserAgent(httpRequest)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.register(request)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.login(request,
                WebUtils.getClientIp(httpRequest), WebUtils.getUserAgent(httpRequest)));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                              HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.refresh(request.refreshToken(),
                WebUtils.getClientIp(httpRequest), WebUtils.getUserAgent(httpRequest)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.success(authService.me());
    }
}
