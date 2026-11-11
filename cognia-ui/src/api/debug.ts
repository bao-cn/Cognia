import { http } from './http'
import type { DebugSetting } from '@/types/api'

export const debugApi = {
  getSettings(): Promise<DebugSetting> {
    return http.get<DebugSetting>('/admin/debug/settings')
  },
  updateSettings(enabled: boolean): Promise<DebugSetting> {
    return http.put<DebugSetting>('/admin/debug/settings', { enabled })
  },
}
