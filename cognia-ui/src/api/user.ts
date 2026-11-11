import { http } from './http'
import type {
  CreateUserPayload,
  PageResult,
  UpdateUserPayload,
  UserProfile,
  UserQuery,
} from '@/types/api'

function cleanParams(params: UserQuery): Record<string, string | number> {
  const result: Record<string, string | number> = {}
  if (params.page != null) result.page = params.page
  if (params.size != null) result.size = params.size
  if (params.keyword?.trim()) result.keyword = params.keyword.trim()
  if (params.status != null) result.status = params.status
  if (params.roleId) result.roleId = params.roleId
  return result
}

export const userApi = {
  list(params: UserQuery): Promise<PageResult<UserProfile>> {
    return http.get<PageResult<UserProfile>>('/users', { params: cleanParams(params) })
  },
  get(id: string): Promise<UserProfile> {
    return http.get<UserProfile>(`/users/${id}`)
  },
  create(payload: CreateUserPayload): Promise<UserProfile> {
    return http.post<UserProfile>('/users', payload)
  },
  update(id: string, payload: UpdateUserPayload): Promise<UserProfile> {
    return http.put<UserProfile>(`/users/${id}`, payload)
  },
  remove(id: string): Promise<void> {
    return http.delete<void>(`/users/${id}`)
  },
  updateStatus(id: string, status: number): Promise<void> {
    return http.patch<void>(`/users/${id}/status`, { status })
  },
  assignRoles(id: string, roleIds: string[]): Promise<UserProfile> {
    return http.put<UserProfile>(`/users/${id}/roles`, { roleIds })
  },
  resetPassword(id: string, newPassword: string): Promise<void> {
    return http.put<void>(`/users/${id}/password`, { newPassword })
  },
  updateMe(payload: { username?: string; nickname?: string; avatar?: string }): Promise<UserProfile> {
    return http.put<UserProfile>('/users/me', payload)
  },
  updatePreferences(payload: { autoWebSearch: boolean }): Promise<UserProfile> {
    return http.put<UserProfile>('/users/me/preferences', payload)
  },
  setMyPassword(payload: { newPassword: string; twoFactorCode?: string }): Promise<UserProfile> {
    return http.post<UserProfile>('/users/me/password/set', payload)
  },
  setupTwoFactor(): Promise<{ secret: string; otpauthUri: string }> {
    return http.post<{ secret: string; otpauthUri: string }>('/users/me/two-factor/setup')
  },
  enableTwoFactor(code: string): Promise<UserProfile> {
    return http.post<UserProfile>('/users/me/two-factor/enable', { code })
  },
  disableTwoFactor(code: string): Promise<UserProfile> {
    return http.post<UserProfile>('/users/me/two-factor/disable', { code })
  },
  sendEmailChangeCode(email: string): Promise<{ message: string; ttlSeconds: number; resendIntervalSeconds: number; devCode?: string }> {
    return http.post('/users/me/email/code', { email })
  },
  updateEmail(payload: { email: string; code: string; twoFactorCode?: string }): Promise<UserProfile> {
    return http.put<UserProfile>('/users/me/email', payload)
  },
  deleteMe(payload: { password?: string; twoFactorCode?: string }): Promise<void> {
    return http.post<void>('/users/me/delete', payload)
  },
  uploadAvatar(file: Blob): Promise<UserProfile> {
    const form = new FormData()
    form.append('file', file, 'avatar.webp')
    return http.post<UserProfile>('/users/me/avatar', form, {
      timeout: 120000,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  changeMyPassword(payload: { oldPassword: string; newPassword: string; twoFactorCode?: string }): Promise<void> {
    return http.put<void>('/users/me/password', payload)
  },
}
