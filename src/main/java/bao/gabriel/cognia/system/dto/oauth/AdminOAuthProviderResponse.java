package bao.gabriel.cognia.system.dto.oauth;

public record AdminOAuthProviderResponse(
        Long id, String code, String name, String providerType, boolean enabled,
        String clientId, boolean clientSecretConfigured,
        String issuer, String authorizationUri, String tokenUri, String userinfoUri,
        String scopes, String redirectUri, String usernameClaim, String emailClaim, String nameClaim,
        boolean autoCreate) {
}
