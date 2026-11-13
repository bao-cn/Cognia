import { http } from './http'
import type {
  AvailabilityResult,
  LoginPayload,
  LoginResult,
  RegisterPayload,
  SendCodeResult,
  UserProfile,
} from '@/types/api'

export const authApi = {
  sendRegisterCode(email: string, turnstileToken?: string): Promise<SendCodeResult> {
    return http.post<SendCodeResult>('/auth/register/code', { email, turnstileToken })
  },
  checkEmail(email: string): Promise<AvailabilityResult> {
    return http.get<AvailabilityResult>('/auth/register/check-email', { params: { email } })
  },
  checkUsername(username: string): Promise<AvailabilityResult> {
    return http.get<AvailabilityResult>('/auth/register/check-username', { params: { username } })
  },
  usernameSuggestions(base: string): Promise<string[]> {
    return http.get<string[]>('/auth/register/suggestions', { params: { base } })
  },
  register(payload: RegisterPayload): Promise<UserProfile> {
    return http.post<UserProfile>('/auth/register', payload)
  },
  login(payload: LoginPayload): Promise<LoginResult> {
    return http.post<LoginResult>('/auth/login', payload)
  },
  refresh(refreshToken: string): Promise<LoginResult> {
    return http.post<LoginResult>('/auth/refresh', { refreshToken })
  },
  logout(): Promise<void> {
    return http.post<void>('/auth/logout')
  },
  me(): Promise<UserProfile> {
    return http.get<UserProfile>('/auth/me')
  },
}
