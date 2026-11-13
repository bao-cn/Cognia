package bao.gabriel.cognia.system.dto.oauth;

public record UserOAuthAccountResponse(
        String code,
        String name,
        String providerType,
        boolean enabled,
        boolean linked,
        String email,
        String linkedAt
) {
}
