import { http } from './http'
import type { AdminTeam, CreateTeamPayload, PageResult, TeamChannel, TeamDetail, TeamInvitation, TeamLink, TeamLimits, TeamSummary, TeamUserSearch } from '@/types/api'

export const teamApi = {
  adminList(params: { page: number; size: number; keyword?: string }): Promise<PageResult<AdminTeam>> {
    return http.get<PageResult<AdminTeam>>('/admin/teams', { params })
  },
  adminDelete(id: string): Promise<void> {
    return http.delete<void>(`/admin/teams/${encodeURIComponent(id)}`)
  },
  mine(): Promise<TeamSummary[]> {
    return http.get<TeamSummary[]>('/teams/mine')
  },
  limits(): Promise<TeamLimits> {
    return http.get<TeamLimits>('/teams/limits')
  },
  detail(id: string): Promise<TeamDetail> {
    return http.get<TeamDetail>(`/teams/${encodeURIComponent(id)}`)
  },
  create(payload: CreateTeamPayload): Promise<TeamDetail> {
    return http.post<TeamDetail>('/teams', payload)
  },
  incomingInvitations(): Promise<TeamInvitation[]> {
    return http.get<TeamInvitation[]>('/teams/invitations')
  },
  acceptInvitation(id: string): Promise<TeamDetail> {
    return http.post<TeamDetail>(`/teams/invitations/${encodeURIComponent(id)}/accept`)
  },
  declineInvitation(id: string): Promise<TeamInvitation> {
    return http.post<TeamInvitation>(`/teams/invitations/${encodeURIComponent(id)}/decline`)
  },
  searchInvitees(id: string, keyword?: string): Promise<TeamUserSearch[]> {
    return http.get<TeamUserSearch[]>(`/teams/${encodeURIComponent(id)}/invitees`, {
      params: keyword ? { keyword } : undefined,
    })
  },
  invite(id: string, userId: string): Promise<TeamInvitation> {
    return http.post<TeamInvitation>(`/teams/${encodeURIComponent(id)}/invitations`, { userId })
  },
  updateReadme(id: string, readme: string): Promise<TeamDetail> {
    return http.put<TeamDetail>(`/teams/${encodeURIComponent(id)}/readme`, { readme })
  },
  updateMemberRole(id: string, userId: string, role: string): Promise<TeamDetail> {
    return http.put<TeamDetail>(`/teams/${encodeURIComponent(id)}/members/${encodeURIComponent(userId)}/role`, { role })
  },
  createChannel(id: string, payload: { name: string; description?: string; isPublic: boolean }): Promise<TeamChannel> {
    return http.post<TeamChannel>(`/teams/${encodeURIComponent(id)}/channels`, payload)
  },
  updateChannel(id: string, channelId: string, payload: { name: string; description?: string; isPublic: boolean }): Promise<TeamChannel> {
    return http.put<TeamChannel>(`/teams/${encodeURIComponent(id)}/channels/${encodeURIComponent(channelId)}`, payload)
  },
  deleteChannel(id: string, channelId: string): Promise<void> {
    return http.delete<void>(`/teams/${encodeURIComponent(id)}/channels/${encodeURIComponent(channelId)}`)
  },
  pinChannel(id: string, channelId: string, pinned: boolean): Promise<TeamChannel> {
    return http.post<TeamChannel>(`/teams/${encodeURIComponent(id)}/channels/${encodeURIComponent(channelId)}/pin`, { pinned })
  },
  updateTeam(id: string, payload: {
    name: string
    description?: string
    logoUrl?: string
    links: TeamLink[]
    joinRequestsEnabled: boolean
    defaultMemberRole: string
  }): Promise<TeamDetail> {
    return http.put<TeamDetail>(`/teams/${encodeURIComponent(id)}`, payload)
  },
  deleteTeam(id: string): Promise<void> {
    return http.delete<void>(`/teams/${encodeURIComponent(id)}`)
  },
  transferOwnership(id: string, userId: string): Promise<TeamDetail> {
    return http.post<TeamDetail>(`/teams/${encodeURIComponent(id)}/transfer`, { userId })
  },
  removeMember(id: string, userId: string): Promise<void> {
    return http.delete<void>(`/teams/${encodeURIComponent(id)}/members/${encodeURIComponent(userId)}`)
  },
}
