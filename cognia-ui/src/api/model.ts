import type {
  ModelConfig,
  ModelProvider,
  PublicChatModel,
  RemoteModel,
  SaveModelConfigPayload,
  SaveModelProviderPayload,
} from '@/types/api'
import { http } from './http'

export const modelApi = {
  providers(): Promise<ModelProvider[]> {
    return http.get<ModelProvider[]>('/admin/models/providers')
  },
  createProvider(payload: SaveModelProviderPayload): Promise<ModelProvider> {
    return http.post<ModelProvider>('/admin/models/providers', payload)
  },
  updateProvider(id: number, payload: SaveModelProviderPayload): Promise<ModelProvider> {
    return http.put<ModelProvider>(`/admin/models/providers/${id}`, payload)
  },
  deleteProvider(id: number): Promise<void> {
    return http.delete<void>(`/admin/models/providers/${id}`)
  },
  fetchModels(providerId: number): Promise<RemoteModel[]> {
    return http.post<RemoteModel[]>(`/admin/models/providers/${providerId}/fetch`)
  },
  configs(providerId?: number): Promise<ModelConfig[]> {
    return http.get<ModelConfig[]>('/admin/models/configs', { params: { providerId } })
  },
  saveConfig(payload: SaveModelConfigPayload): Promise<ModelConfig> {
    return http.post<ModelConfig>('/admin/models/configs', payload)
  },
  deleteConfig(id: number): Promise<void> {
    return http.delete<void>(`/admin/models/configs/${id}`)
  },
  available(): Promise<PublicChatModel[]> {
    return http.get<PublicChatModel[]>('/admin/models/available')
  },
}
