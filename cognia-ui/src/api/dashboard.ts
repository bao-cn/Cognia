import { http } from './http'
import type { AdminDashboard, ChannelInsights } from '@/types/api'

export const dashboardApi = {
  overview: () => http.get<AdminDashboard>('/admin/dashboard'),
  channelInsights: (channelId: number) => http.get<ChannelInsights>(`/channels/${channelId}/insights`),
}
