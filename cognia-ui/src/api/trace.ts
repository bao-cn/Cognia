import { http } from './http'
import type { ChatTraceDetail, ChatTraceSummary, PageResult, TraceReplayResult, TraceStats } from '@/types/api'

export interface TraceQuery {
  page?: number
  size?: number
  status?: string
  keyword?: string
}

export const traceApi = {
  list(query: TraceQuery = {}): Promise<PageResult<ChatTraceSummary>> {
    return http.get<PageResult<ChatTraceSummary>>('/admin/traces', { params: query })
  },
  stats(): Promise<TraceStats> {
    return http.get<TraceStats>('/admin/traces/stats')
  },
  detail(traceId: string): Promise<ChatTraceDetail> {
    return http.get<ChatTraceDetail>(`/admin/traces/${traceId}`)
  },
  replay(traceId: string, stage: 'retrieval' | 'llm'): Promise<TraceReplayResult> {
    return http.post<TraceReplayResult>(`/admin/traces/${traceId}/replay`, undefined, {
      params: { stage },
      timeout: 180000,
    })
  },
  clear(): Promise<void> {
    return http.delete<void>('/admin/traces')
  },
}
