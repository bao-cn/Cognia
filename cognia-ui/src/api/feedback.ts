import { http } from './http'
import type { FeedbackDetail, FeedbackRecord, PageResult } from '@/types/api'

export interface FeedbackQuery {
  page?: number
  size?: number
  type?: string
  status?: string
}

export const feedbackApi = {
  page(query: FeedbackQuery = {}): Promise<PageResult<FeedbackRecord>> {
    return http.get<PageResult<FeedbackRecord>>('/admin/feedback', { params: query })
  },
  detail(id: number): Promise<FeedbackDetail> {
    return http.get<FeedbackDetail>(`/admin/feedback/${id}`)
  },
  update(id: number, payload: { problemType?: string; status?: string; note?: string }): Promise<FeedbackRecord> {
    return http.put<FeedbackRecord>(`/admin/feedback/${id}`, payload)
  },
}
