import { http } from './http'
import type {
  CreateIssueCommentPayload,
  CreateIssuePayload,
  IssueComment,
  IssueDetail,
  IssueLabel,
  IssueSummary,
  IssueUser,
  PageResult,
} from '@/types/api'

export const issueApi = {
  list(channelId: number, params: { status?: string; labelId?: number; page?: number; size?: number }) {
    return http.get<PageResult<IssueSummary>>(`/channels/${channelId}/issues`, { params })
  },
  detail(channelId: number, number: number) {
    return http.get<IssueDetail>(`/channels/${channelId}/issues/${number}`)
  },
  create(channelId: number, payload: CreateIssuePayload) {
    return http.post<IssueDetail>(`/channels/${channelId}/issues`, payload)
  },
  setStatus(channelId: number, number: number, status: 'OPEN' | 'CLOSED') {
    return http.put<IssueDetail>(`/channels/${channelId}/issues/${number}/status`, { status })
  },
  setAssignee(channelId: number, number: number, assigneeId: number | null) {
    return http.put<IssueDetail>(`/channels/${channelId}/issues/${number}/assignee`, { assigneeId })
  },
  setLabels(channelId: number, number: number, labelIds: number[]) {
    return http.put<IssueDetail>(`/channels/${channelId}/issues/${number}/labels`, { labelIds })
  },
  comment(channelId: number, number: number, payload: CreateIssueCommentPayload) {
    return http.post<IssueComment>(`/channels/${channelId}/issues/${number}/comments`, payload)
  },
  labels(channelId: number) {
    return http.get<IssueLabel[]>(`/channels/${channelId}/issues/labels`)
  },
  createLabel(channelId: number, payload: { name: string; color: string }) {
    return http.post<IssueLabel>(`/channels/${channelId}/issues/labels`, payload)
  },
  deleteLabel(channelId: number, labelId: number) {
    return http.delete<void>(`/channels/${channelId}/issues/labels/${labelId}`)
  },
  assignees(channelId: number) {
    return http.get<IssueUser[]>(`/channels/${channelId}/issues/assignees`)
  },
}
