package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.user.AssignRolesRequest;
import bao.gabriel.cognia.system.dto.user.ChangePasswordRequest;
import bao.gabriel.cognia.system.dto.user.ProfileUpdateRequest;
import bao.gabriel.cognia.system.dto.user.ResetPasswordRequest;
import bao.gabriel.cognia.system.dto.user.UpdateStatusRequest;
import bao.gabriel.cognia.system.dto.user.UpdatePreferencesRequest;
import bao.gabriel.cognia.system.dto.user.SetPasswordRequest;
import bao.gabriel.cognia.system.dto.user.DeleteAccountRequest;
import bao.gabriel.cognia.system.dto.user.UpdateEmailRequest;
import bao.gabriel.cognia.system.dto.user.SendEmailChangeCodeRequest;
import bao.gabriel.cognia.system.service.VerificationCodeService;
import bao.gabriel.cognia.system.dto.auth.SendCodeResponse;
import bao.gabriel.cognia.system.dto.twofactor.TwoFactorCodeRequest;
import bao.gabriel.cognia.system.dto.twofactor.TwoFactorSetupResponse;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.service.TwoFactorService;
import bao.gabriel.cognia.system.dto.user.UserCreateRequest;
import bao.gabriel.cognia.system.dto.user.UserResponse;
import bao.gabriel.cognia.system.dto.user.UserUpdateRequest;
import bao.gabriel.cognia.system.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * User management. Administrative endpoints are protected with fine grained
 * permissions, while {@code /me} endpoints only require authentication.
 */
@Validated
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final TwoFactorService twoFactorService;
    private final VerificationCodeService verificationCodeService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<PageResult<UserResponse>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 最小为 1") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 最小为 1")
            @Max(value = 100, message = "size 最大为 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(value = 0, message = "状态只能为 0 或 1")
            @Max(value = 1, message = "状态只能为 0 或 1") Integer status,
            @RequestParam(required = false) Long roleId) {
        return ApiResponse.success(userService.pageUsers(page, size, keyword, status, roleId));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.success(userService.getProfile(SecurityUtils.getUserId()));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMe(@Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.success(userService.updateProfile(SecurityUtils.getUserId(), request));
    }

    @PostMapping("/me/avatar")
    public ApiResponse<UserResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(userService.updateAvatar(SecurityUtils.getUserId(), file));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangePasswordRequest request) {
        User user = userService.requireUser(SecurityUtils.getUserId());
        twoFactorService.require(user, request.twoFactorCode());
        userService.changePassword(SecurityUtils.getUserId(), request.oldPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/password/set")
    public ApiResponse<UserResponse> setMyPassword(@Valid @RequestBody SetPasswordRequest request) {
        User user = userService.requireUser(SecurityUtils.getUserId());
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            throw BusinessException.badRequest("当前账户已设置密码，请使用修改密码");
        }
        twoFactorService.require(user, request.twoFactorCode());
        userService.setPassword(SecurityUtils.getUserId(), request.newPassword());
        return ApiResponse.success(userService.getProfile(SecurityUtils.getUserId()));
    }

    @PutMapping("/me/preferences")
    public ApiResponse<UserResponse> updatePreferences(@RequestBody UpdatePreferencesRequest request) {
        return ApiResponse.success(userService.updatePreferences(SecurityUtils.getUserId(),
                Boolean.TRUE.equals(request.autoWebSearch())));
    }

    @PostMapping("/me/two-factor/setup")
    public ApiResponse<TwoFactorSetupResponse> setupTwoFactor() {
        return ApiResponse.success(twoFactorService.setup(userService.requireUser(SecurityUtils.getUserId())));
    }

    @PostMapping("/me/two-factor/enable")
    public ApiResponse<UserResponse> enableTwoFactor(@Valid @RequestBody TwoFactorCodeRequest request) {
        User user = userService.requireUser(SecurityUtils.getUserId());
        twoFactorService.enable(user, request.code());
        return ApiResponse.success(userService.getProfile(SecurityUtils.getUserId()));
    }

    @PostMapping("/me/two-factor/disable")
    public ApiResponse<UserResponse> disableTwoFactor(@Valid @RequestBody TwoFactorCodeRequest request) {
        User user = userService.requireUser(SecurityUtils.getUserId());
        twoFactorService.disable(user, request.code());
        return ApiResponse.success(userService.getProfile(SecurityUtils.getUserId()));
    }

    @PostMapping("/me/email/code")
    public ApiResponse<SendCodeResponse> sendEmailChangeCode(@Valid @RequestBody SendEmailChangeCodeRequest request) {
        return ApiResponse.success(verificationCodeService.sendEmailChangeCode(SecurityUtils.getUserId(), request.email()));
    }

    @PutMapping("/me/email")
    public ApiResponse<UserResponse> updateEmail(@Valid @RequestBody UpdateEmailRequest request) {
        User user = userService.requireUser(SecurityUtils.getUserId());
        verificationCodeService.verifyEmailChangeCode(request.email(), request.code());
        twoFactorService.require(user, request.twoFactorCode());
        return ApiResponse.success(userService.updateEmail(SecurityUtils.getUserId(), request.email()));
    }

    @PostMapping("/me/delete")
    public ResponseEntity<Void> deleteMe(@RequestBody(required = false) DeleteAccountRequest request) {
        Long userId = SecurityUtils.getUserId();
        User user = userService.requireUser(userId);
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            if (request == null || request.password() == null
                    || !userService.matchesPassword(userId, request.password())) {
                throw BusinessException.badRequest("密码不正确");
            }
        }
        twoFactorService.require(user, request == null ? null : request.twoFactorCode());
        userService.deleteSelf(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.success(userService.getProfile(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserCreateRequest request) {
        if (request.roleIds() != null && !request.roleIds().isEmpty()
                && !SecurityUtils.hasPermission("user:assign-role")) {
            throw new BusinessException(ResultCode.FORBIDDEN, "没有分配角色的权限");
        }
        UserResponse created = userService.createUser(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    public ApiResponse<UserResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateUser(id, request, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id, SecurityUtils.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:update')")
    public ResponseEntity<Void> updateStatus(@PathVariable Long id,
                                             @Valid @RequestBody UpdateStatusRequest request) {
        userService.updateStatus(id, request.status(), SecurityUtils.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user:assign-role')")
    public ApiResponse<UserResponse> assignRoles(@PathVariable Long id,
                                                 @Valid @RequestBody AssignRolesRequest request) {
        return ApiResponse.success(userService.assignRoles(id, request.roleIds(), SecurityUtils.getUserId()));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('user:reset-password')")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
