import { http } from './http'
import type { LoginResult } from '@/types/api'

export interface OAuthCallbackResult {
  linkRequired: boolean
  linkToken?: string
  email?: string
  provider?: string
  linked: boolean
  registrationRequired: boolean
  registrationToken?: string
  usernameSuggestions?: string[]
  login?: LoginResult
}

export interface PublicOAuthProvider {
  code: string
  name: string
  providerType: string
}

export interface AdminOAuthProvider {
  id: number
  code: string
  name: string
  providerType: string
  enabled: boolean
  clientId?: string
  clientSecretConfigured: boolean
  issuer?: string
  authorizationUri?: string
  tokenUri?: string
  userinfoUri?: string
  scopes?: string
  redirectUri?: string
  usernameClaim?: string
  emailClaim?: string
  nameClaim?: string
  autoCreate: boolean
}

export interface UserOAuthAccount {
  code: string
  name: string
  providerType: string
  enabled: boolean
  linked: boolean
  email?: string
  linkedAt?: string
}

export type UpdateOAuthProviderPayload = Partial<Omit<AdminOAuthProvider, 'id' | 'code' | 'name' | 'clientSecretConfigured'>> & {
  clientSecret?: string
  clearClientSecret?: boolean
}

export const oauthApi = {
  providers(): Promise<PublicOAuthProvider[]> {
    return http.get<PublicOAuthProvider[]>('/auth/oauth/providers')
  },
  authorizeUrl(code: string, redirectUri: string, intent?: string): Promise<string> {
    return http.get<string>(`/auth/oauth/${code}/authorize-url`, { params: { redirectUri, intent } })
  },
  callback(provider: string, code: string, state: string): Promise<OAuthCallbackResult> {
    return http.get<OAuthCallbackResult>(`/auth/oauth/${provider}/callback`, { params: { code, state } })
  },
  link(linkToken: string): Promise<OAuthCallbackResult> {
    return http.post<OAuthCallbackResult>('/auth/oauth/link', { linkToken })
  },
  completeRegistration(registrationToken: string, username: string): Promise<OAuthCallbackResult> {
    return http.post<OAuthCallbackResult>('/auth/oauth/register', { registrationToken, username })
  },
  userAccounts(): Promise<UserOAuthAccount[]> {
    return http.get<UserOAuthAccount[]>('/users/me/oauth/accounts')
  },
  userAuthorizeUrl(code: string, redirectUri: string): Promise<string> {
    return http.post<string>(`/users/me/oauth/${code}/authorize-url`, null, { params: { redirectUri } })
  },
  unlinkAccount(code: string): Promise<void> {
    return http.delete<void>(`/users/me/oauth/${code}`)
  },
  adminProviders(): Promise<AdminOAuthProvider[]> {
    return http.get<AdminOAuthProvider[]>('/admin/oauth/providers')
  },
  updateProvider(code: string, payload: UpdateOAuthProviderPayload): Promise<AdminOAuthProvider> {
    return http.put<AdminOAuthProvider>(`/admin/oauth/providers/${code}`, payload)
  },
}
