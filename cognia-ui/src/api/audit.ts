import { http } from './http'
import type { AuditLogEntry, PageResult } from '@/types/api'

export interface AuditLogQuery {
  page: number
  size: number
  module?: string
  status?: string
  keyword?: string
  start?: string
  end?: string
}

export const auditApi = {
  list(query: AuditLogQuery): Promise<PageResult<AuditLogEntry>> {
    return http.get<PageResult<AuditLogEntry>>('/audit-logs', { params: query })
  },
  modules(): Promise<string[]> {
    return http.get<string[]>('/audit-logs/modules')
  },
}
