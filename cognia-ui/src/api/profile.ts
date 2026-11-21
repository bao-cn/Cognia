import { http } from './http'
import type { OwnerProfile, UpdateOwnerProfilePayload, UserContribution } from '@/types/api'

export const profileApi = {
  contributions(identifier: string, params?: { year?: number; before?: number; limit?: number }) {
    return http.get<UserContribution>(`/profiles/owner/${encodeURIComponent(identifier)}/contributions`, { params })
  },
  updateContributionVisibility(publicVisible: boolean) {
    return http.put<void>('/profiles/me/contributions-visibility', { publicVisible })
  },
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
