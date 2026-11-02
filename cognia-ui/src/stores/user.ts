import { defineStore } from 'pinia'
import { authApi } from '@/api'
import type { LoginPayload, LoginResult, RegisterPayload, SendCodeResult, UserProfile } from '@/types/api'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/token'

interface UserState {
  accessToken: string | null
  refreshToken: string | null
  profile: UserProfile | null
  initialized: boolean
  loading: boolean
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    accessToken: getAccessToken(),
    refreshToken: getRefreshToken(),
    profile: null,
    initialized: false,
    loading: false,
  }),

  getters: {
    isLoggedIn: state => Boolean(state.accessToken),
    roles: state => state.profile?.roles?.map(role => role.code) ?? [],
    permissions: state => state.profile?.permissions ?? [],
    isAdmin(): boolean {
      return this.roles.includes('ADMIN')
    },
    displayName: state => state.profile?.nickname || state.profile?.username || '未登录',
    avatar: state => state.profile?.avatar || '/logo.png',
  },

  actions: {
    applyTokens(accessToken: string, refreshToken: string) {
      this.accessToken = accessToken
      this.refreshToken = refreshToken
      setTokens(accessToken, refreshToken)
    },

    setProfile(profile: UserProfile | null) {
      this.profile = profile
    },

    hasRole(role: string): boolean {
      return this.roles.includes(role.toUpperCase())
    },

    hasPermission(permission: string): boolean {
      // ADMIN is a super role: the backend always grants it every permission.
      return this.isAdmin || this.permissions.includes(permission)
    },

    async login(payload: LoginPayload): Promise<UserProfile> {
      this.loading = true
      try {
        const result: LoginResult = await authApi.login(payload)
        this.applyTokens(result.accessToken, result.refreshToken)
        this.setProfile(result.user)
        this.initialized = true
        return result.user
      } finally {
        this.loading = false
      }
    },

    async register(payload: RegisterPayload): Promise<UserProfile> {
      return authApi.register(payload)
    },

    async sendRegisterCode(email: string, turnstileToken?: string): Promise<SendCodeResult> {
      return authApi.sendRegisterCode(email, turnstileToken)
    },

    async fetchProfile(): Promise<UserProfile> {
      const profile = await authApi.me()
      this.setProfile(profile)
      return profile
    },

    /**
     * Restores the session on a hard refresh. The axios layer transparently
     * refreshes an expired access token.
     */
    async restoreSession(): Promise<void> {
      if (this.initialized) return
      try {
        if (!this.accessToken && this.refreshToken) {
          const result = await authApi.refresh(this.refreshToken)
          this.applyTokens(result.accessToken, result.refreshToken)
          this.setProfile(result.user)
        } else if (this.accessToken) {
          await this.fetchProfile()
        }
      } catch {
        this.clearAuth()
      } finally {
        this.initialized = true
      }
    },

    async logout(): Promise<void> {
      try {
        if (this.accessToken) {
          await authApi.logout()
        }
      } catch {
        // The local session must be cleared even if the server call fails.
      } finally {
        this.clearAuth()
      }
    },

    clearAuth(): void {
      this.accessToken = null
      this.refreshToken = null
      this.profile = null
      this.initialized = true
      clearTokens()
    },
  },
})
