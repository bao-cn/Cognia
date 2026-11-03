import { http } from './http'
import type { DiscoverChannel, PageResult } from '@/types/api'

export interface DiscoverQuery {
  tab: 'featured' | 'hot'
  period?: 'week' | 'month' | 'year'
  page?: number
  size?: number
}

export const discoverApi = {
  channels(query: DiscoverQuery): Promise<PageResult<DiscoverChannel>> {
    return http.get<PageResult<DiscoverChannel>>('/discover/channels', { params: query })
  },
  subscriptions(): Promise<DiscoverChannel[]> {
    return http.get<DiscoverChannel[]>('/discover/subscriptions')
  },
  subscribe(id: string): Promise<void> {
    return http.post<void>(`/discover/channels/${id}/subscribe`)
  },
  unsubscribe(id: string): Promise<void> {
    return http.delete<void>(`/discover/channels/${id}/subscribe`)
  },
  subscribeNotify(id: string): Promise<void> {
    return http.post<void>(`/discover/channels/${id}/notify-subscription`)
  },
  unsubscribeNotify(id: string): Promise<void> {
    return http.delete<void>(`/discover/channels/${id}/notify-subscription`)
  },
  setFeatured(id: string, featured: boolean): Promise<void> {
    return http.put<void>(`/discover/channels/${id}/featured`, { featured })
  },
}
