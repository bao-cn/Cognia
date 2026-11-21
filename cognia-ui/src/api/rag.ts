import type { EmbeddingDimensionCheck, RagSettings, UpdateRagSettingsPayload } from '@/types/api'
import { http } from './http'

export const ragApi = {
  settings(): Promise<RagSettings> {
    return http.get<RagSettings>('/admin/rag/settings')
  },
  update(payload: UpdateRagSettingsPayload): Promise<RagSettings> {
    return http.put<RagSettings>('/admin/rag/settings', payload)
  },
  embeddingDimensionCheck(modelId: number): Promise<EmbeddingDimensionCheck> {
    return http.get<EmbeddingDimensionCheck>('/admin/rag/embedding-dimension-check', { params: { modelId } })
  },
}
