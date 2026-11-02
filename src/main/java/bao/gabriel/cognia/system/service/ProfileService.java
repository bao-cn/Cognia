package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.profile.OwnerProfileResponse;
import bao.gabriel.cognia.system.dto.profile.UpdateProfileRequest;

public interface ProfileService {

    OwnerProfileResponse getProfile(String identifier, Long viewerId);

    OwnerProfileResponse getMyProfile(Long userId);

    OwnerProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request);
}
