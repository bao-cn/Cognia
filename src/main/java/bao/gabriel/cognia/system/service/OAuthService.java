package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.oauth.AdminOAuthProviderResponse;
import bao.gabriel.cognia.system.dto.oauth.PublicOAuthProvider;
import bao.gabriel.cognia.system.dto.oauth.UpdateOAuthProviderRequest;
import bao.gabriel.cognia.system.dto.oauth.OAuthCallbackResponse;
import bao.gabriel.cognia.system.dto.oauth.UserOAuthAccountResponse;

import java.util.List;

public interface OAuthService {
    List<AdminOAuthProviderResponse> adminProviders();
    AdminOAuthProviderResponse updateProvider(String code, UpdateOAuthProviderRequest request);
    List<PublicOAuthProvider> publicProviders();
    String authorizeUrl(String code, String redirectUri);

    String authorizeUrl(String code, String redirectUri, String intent);
    OAuthCallbackResponse callback(String code, String state);

    OAuthCallbackResponse link(String linkToken);

    OAuthCallbackResponse completeRegistration(String registrationToken, String username);

    java.util.List<UserOAuthAccountResponse> userAccounts(Long userId);

    String userAuthorizeUrl(Long userId, String code, String redirectUri);

    void unlinkAccount(Long userId, String code);
}
