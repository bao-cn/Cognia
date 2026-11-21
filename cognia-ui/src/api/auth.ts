import { http } from './http'
import type {
  AvailabilityResult,
  CaptchaSubmission,
  LoginEmailCodePayload,
  LoginOptions,
  LoginPayload,
  LoginResult,
  LoginTwoFactorPayload,
  RegisterPayload,
  SendCodeResult,
  UserProfile,
} from '@/types/api'

export const authApi = {
  sendRegisterCode(email: string, captcha?: CaptchaSubmission): Promise<SendCodeResult> {
    return http.post<SendCodeResult>('/auth/register/code', { email, captcha })
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
  loginOptions(account: string): Promise<LoginOptions> {
    return http.post<LoginOptions>('/auth/login/options', { account })
  },
  loginTwoFactor(payload: LoginTwoFactorPayload): Promise<LoginResult> {
    return http.post<LoginResult>('/auth/login/2fa', payload)
  },
  sendLoginCode(account: string, captcha?: CaptchaSubmission): Promise<SendCodeResult> {
    return http.post<SendCodeResult>('/auth/login/code', { account, captcha })
  },
  loginEmailCode(payload: LoginEmailCodePayload): Promise<LoginResult> {
    return http.post<LoginResult>('/auth/login/email-code', payload)
  },
  verifyRegisterCode(email: string, code: string): Promise<void> {
    return http.post<void>('/auth/register/verify-code', { email, code })
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
