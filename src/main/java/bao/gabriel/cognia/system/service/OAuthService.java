package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.oauth.AdminOAuthProviderResponse;
import bao.gabriel.cognia.system.dto.oauth.PublicOAuthProvider;
import bao.gabriel.cognia.system.dto.oauth.UpdateOAuthProviderRequest;
import bao.gabriel.cognia.security.TokenPair;

import java.util.List;

public interface OAuthService {
    List<AdminOAuthProviderResponse> adminProviders();
    AdminOAuthProviderResponse updateProvider(String code, UpdateOAuthProviderRequest request);
    List<PublicOAuthProvider> publicProviders();
    String authorizeUrl(String code, String redirectUri);
    TokenPair callback(String code, String state);
}
