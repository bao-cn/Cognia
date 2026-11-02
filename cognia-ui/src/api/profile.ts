import { http } from './http'
import type { OwnerProfile, UpdateOwnerProfilePayload } from '@/types/api'

export const profileApi = {
  get(identifier: string): Promise<OwnerProfile> {
    return http.get<OwnerProfile>(`/profiles/owner/${encodeURIComponent(identifier)}`)
  },
  me(): Promise<OwnerProfile> {
    return http.get<OwnerProfile>('/profiles/me')
  },
  update(payload: UpdateOwnerProfilePayload): Promise<OwnerProfile> {
    return http.put<OwnerProfile>('/profiles/me', payload)
  },
}
