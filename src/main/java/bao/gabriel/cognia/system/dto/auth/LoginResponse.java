package bao.gabriel.cognia.system.dto.auth;

import bao.gabriel.cognia.system.dto.user.UserResponse;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        UserResponse user
) {
}
