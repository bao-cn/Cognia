package bao.gabriel.cognia.system.dto.oauth;

import bao.gabriel.cognia.system.dto.auth.LoginResponse;

import java.util.List;

/**
 * Result of an OAuth callback. When {@code linkRequired} is true, the provider
 * email already belongs to a local account and the user must confirm linking
 * before any token is issued. When {@code registrationRequired} is true, the
 * provider identity is valid but the desired username is taken, so the user
 * must choose a unique identifier to finish registration.
 */
public record OAuthCallbackResponse(
        boolean linkRequired,
        String linkToken,
        String email,
        String provider,
        boolean linked,
        boolean registrationRequired,
        String registrationToken,
        List<String> usernameSuggestions,
        LoginResponse login
) {
    public static OAuthCallbackResponse linkRequired(String linkToken, String email, String provider) {
        return new OAuthCallbackResponse(true, linkToken, email, provider, false, false, null, List.of(), null);
    }

    public static OAuthCallbackResponse loggedIn(LoginResponse login) {
        return new OAuthCallbackResponse(false, null, null, null, false, false, null, List.of(), login);
    }

    public static OAuthCallbackResponse linked(String email, String provider) {
        return new OAuthCallbackResponse(false, null, email, provider, true, false, null, List.of(), null);
    }

    public static OAuthCallbackResponse registrationRequired(String token, String email, String provider,
                                                             List<String> suggestions) {
        return new OAuthCallbackResponse(false, null, email, provider, false, true, token,
                suggestions == null ? List.of() : suggestions, null);
    }
}
