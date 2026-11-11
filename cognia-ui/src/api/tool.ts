import { http } from './http'
import type { ToolSettings, UpdateToolSettingsPayload } from '@/types/api'

export const toolApi = {
  getSettings(): Promise<ToolSettings> {
    return http.get<ToolSettings>('/admin/tools/settings')
  },
  updateSettings(payload: UpdateToolSettingsPayload): Promise<ToolSettings> {
    return http.put<ToolSettings>('/admin/tools/settings', payload)
  },
}
