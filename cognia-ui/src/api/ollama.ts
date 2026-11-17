import { http } from './http'

export interface OllamaStatus {
  configured: boolean
  connected: boolean
  providerId?: string
  providerName?: string
  baseUrl?: string
  models: string[]
  message: string
}

export interface OllamaPullStatus {
  taskId: string
  model: string
  status: string
  message: string
  done: boolean
  completed?: number
  total?: number
  percent?: number
}

export const ollamaApi = {
  status(providerId?: string): Promise<OllamaStatus> {
    return http.get<OllamaStatus>('/admin/models/ollama/status', {
      params: providerId ? { providerId } : undefined,
    })
  },
  pull(providerId: string | undefined, model: string, insecure = false): Promise<OllamaPullStatus> {
    return http.post<OllamaPullStatus>('/admin/models/ollama/pull', { providerId, model, insecure })
  },
  pullStatus(taskId: string): Promise<OllamaPullStatus> {
    return http.get<OllamaPullStatus>(`/admin/models/ollama/pull/${taskId}`)
  },
}
