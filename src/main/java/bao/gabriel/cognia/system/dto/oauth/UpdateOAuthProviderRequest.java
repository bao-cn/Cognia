package bao.gabriel.cognia.system.dto.oauth;

public record UpdateOAuthProviderRequest(
        Boolean enabled, String clientId, String clientSecret, Boolean clearClientSecret,
        String issuer, String authorizationUri, String tokenUri, String userinfoUri,
        String scopes, String redirectUri, String usernameClaim, String emailClaim, String nameClaim,
        Boolean autoCreate) {
}
