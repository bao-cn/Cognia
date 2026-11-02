import { http } from './http'
import type { AppNotification, PageResult } from '@/types/api'

export interface NotificationQuery {
  page: number
  size: number
  unreadOnly?: boolean
}

export const notificationApi = {
  list(query: NotificationQuery): Promise<PageResult<AppNotification>> {
    return http.get<PageResult<AppNotification>>('/notifications', { params: query })
  },
  unreadCount(): Promise<number> {
    return http.get<number>('/notifications/unread-count')
  },
  markRead(id: number): Promise<void> {
    return http.put<void>(`/notifications/${id}/read`)
  },
  markAllRead(): Promise<void> {
    return http.put<void>('/notifications/read-all')
  },
}
