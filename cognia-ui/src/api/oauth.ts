import { http } from './http'
import type { LoginResult } from '@/types/api'

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

export type UpdateOAuthProviderPayload = Partial<Omit<AdminOAuthProvider, 'id' | 'code' | 'name' | 'clientSecretConfigured'>> & {
  clientSecret?: string
  clearClientSecret?: boolean
}

export const oauthApi = {
  providers(): Promise<PublicOAuthProvider[]> {
    return http.get<PublicOAuthProvider[]>('/auth/oauth/providers')
  },
  authorizeUrl(code: string, redirectUri: string): Promise<string> {
    return http.get<string>(`/auth/oauth/${code}/authorize-url`, { params: { redirectUri } })
  },
  callback(provider: string, code: string, state: string): Promise<LoginResult> {
    return http.get<LoginResult>(`/auth/oauth/${provider}/callback`, { params: { code, state } })
  },
  adminProviders(): Promise<AdminOAuthProvider[]> {
    return http.get<AdminOAuthProvider[]>('/admin/oauth/providers')
  },
  updateProvider(code: string, payload: UpdateOAuthProviderPayload): Promise<AdminOAuthProvider> {
    return http.put<AdminOAuthProvider>(`/admin/oauth/providers/${code}`, payload)
  },
}
