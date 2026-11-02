package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.LoginUser;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.profile.OwnerProfileResponse;
import bao.gabriel.cognia.system.dto.profile.UpdateProfileRequest;
import bao.gabriel.cognia.system.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/owner/{identifier}")
    public ApiResponse<OwnerProfileResponse> profile(@PathVariable String identifier) {
        LoginUser viewer = SecurityUtils.getLoginUserOrNull();
        return ApiResponse.success(profileService.getProfile(identifier, viewer == null ? null : viewer.getUserId()));
    }

    @GetMapping("/me")
    public ApiResponse<OwnerProfileResponse> me() {
        return ApiResponse.success(profileService.getMyProfile(SecurityUtils.getUserId()));
    }

    @PutMapping("/me")
    public ApiResponse<OwnerProfileResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(profileService.updateMyProfile(SecurityUtils.getUserId(), request));
    }
}
