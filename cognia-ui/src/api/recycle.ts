import { http, httpClient } from './http'
import type { AdminRecycleBinEntry, PageResult, RecycleBinEntry, RecycleChannelOption } from '@/types/api'

export interface AdminRecycleQuery {
  page: number
  size: number
  channelId?: string
  keyword?: string
  channelKeyword?: string
}

export const recycleApi = {
  list(ownerType: 'USER' | 'TEAM', ownerId: string) {
    return http.get<RecycleBinEntry[]>('/recycle-bin', { params: { ownerType, ownerId } })
  },
  listByChannel(channelId: string | number) {
    return http.get<RecycleBinEntry[]>(`/channels/${channelId}/recycle`)
  },
  restoreChannel(channelId: string | number, entryId: string) {
    return http.post<void>(`/channels/${channelId}/recycle/${entryId}/restore`)
  },
  purge(id: string) {
    return http.delete<void>(`/recycle-bin/${id}`)
  },
  adminList(params: AdminRecycleQuery) {
    return http.get<PageResult<AdminRecycleBinEntry>>('/admin/recycle-bin', { params })
  },
  adminChannels() {
    return http.get<RecycleChannelOption[]>('/admin/recycle-bin/channels')
  },
  adminRestore(id: string) {
    return http.post<void>(`/admin/recycle-bin/${id}/restore`)
  },
  adminPurge(id: string) {
    return http.delete<void>(`/admin/recycle-bin/${id}`)
  },
  async adminDownload(id: string) {
    const response = await httpClient.get(`/admin/recycle-bin/${id}/raw`, { responseType: 'blob' })
    return response.data as Blob
  },
}
