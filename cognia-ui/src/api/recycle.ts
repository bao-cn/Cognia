import { http } from './http'
import type { RecycleBinEntry } from '@/types/api'

export const recycleApi = {
  list(ownerType: 'USER' | 'TEAM', ownerId: string) {
    return http.get<RecycleBinEntry[]>('/recycle-bin', { params: { ownerType, ownerId } })
  },
  purge(id: string) {
    return http.delete<void>(`/recycle-bin/${id}`)
  },
}
