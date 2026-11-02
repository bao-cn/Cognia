import type { PromptSettings, UpdatePromptSettingsPayload } from '@/types/api'
import { http } from './http'

export const promptApi = {
  settings(): Promise<PromptSettings> {
    return http.get<PromptSettings>('/admin/prompts/settings')
  },
  defaults(): Promise<PromptSettings> {
    return http.get<PromptSettings>('/admin/prompts/defaults')
  },
  update(payload: UpdatePromptSettingsPayload): Promise<PromptSettings> {
    return http.put<PromptSettings>('/admin/prompts/settings', payload)
  },
  reset(): Promise<PromptSettings> {
    return http.post<PromptSettings>('/admin/prompts/reset')
  },
}
