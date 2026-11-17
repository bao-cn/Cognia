import { http } from './http'

export interface IngestionSettings {
  id: number
  concurrency: number
  queueLimit: number
  maxRetries: number
  retryBackoffSeconds: number
  hnswM: number
  hnswEfConstruction: number
  hnswEfSearch: number
  healthFailureThreshold: number
  queueAlertDepth: number
  failureAlertPercent: number
  alertEmailEnabled: boolean
  alertRecipient?: string
  alertRoleCode?: string
}

export interface IngestionMetrics {
  queueDepth: number
  running: number
  dead: number
  succeeded24h: number
  failed24h: number
  failureRate: number
  avgLatencyMs: number
  p95LatencyMs: number
  embeddingSuccessRate: number
  embeddingHealthy: boolean
  index: { ready: boolean; message: string }
}

export interface IngestionJob {
  id: number
  documentId: number
  channelId?: number
  step: string
  status: string
  attempts: number
  maxAttempts: number
  errorMessage?: string
}

export const ingestionApi = {
  settings: () => http.get<IngestionSettings>('/admin/ingestion/settings'),
  updateSettings: (payload: Partial<IngestionSettings>) =>
    http.put<IngestionSettings>('/admin/ingestion/settings', payload),
  metrics: () => http.get<IngestionMetrics>('/admin/ingestion/metrics'),
  jobs: (deadOnly = false) => http.get<IngestionJob[]>('/admin/ingestion/jobs', { params: { deadOnly } }),
  requeue: (id: number) => http.post<void>(`/admin/ingestion/jobs/${id}/requeue`),
  rebuildIndex: () => http.post<{ ready: boolean; message: string }>('/admin/ingestion/index/rebuild'),
}
