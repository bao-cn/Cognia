import { http } from './http'
import type {
  ChannelDetail,
  ChannelDocument,
  ChannelCard,
  ChannelFolder,
  CreateChannelPayload,
  UpdateChannelPayload,
} from '@/types/api'

export const channelApi = {
  list(ownerType: 'USER' | 'TEAM', ownerId: string): Promise<ChannelCard[]> {
    return http.get<ChannelCard[]>(`/channels/owner/${ownerType}/${ownerId}`)
  },
  detail(id: string): Promise<ChannelDetail> {
    return http.get<ChannelDetail>(`/channels/${id}`)
  },
  create(payload: CreateChannelPayload): Promise<ChannelCard> {
    return http.post<ChannelCard>('/channels', payload)
  },
  update(id: string, payload: UpdateChannelPayload): Promise<ChannelCard> {
    return http.put<ChannelCard>(`/channels/${id}`, payload)
  },
  remove(id: string): Promise<void> {
    return http.delete<void>(`/channels/${id}`)
  },
  setPinned(id: string, pinned: boolean): Promise<ChannelCard> {
    return http.put<ChannelCard>(`/channels/${id}/pin`, undefined, { params: { pinned } })
  },
  createFolder(channelId: string, payload: { parentId?: string; name: string }): Promise<ChannelFolder> {
    return http.post<ChannelFolder>(`/channels/${channelId}/folders`, payload)
  },
  deleteFolder(channelId: string, folderId: string): Promise<void> {
    return http.delete<void>(`/channels/${channelId}/folders/${folderId}`)
  },
  documents(channelId: string, folderId?: string): Promise<ChannelDocument[]> {
    return http.get<ChannelDocument[]>(`/channels/${channelId}/documents`, {
      params: folderId ? { folderId } : undefined,
    })
  },
  uploadDocument(
    channelId: string,
    file: File,
    folderId?: string,
    onProgress?: (percent: number) => void,
    meta?: { title?: string; documentType?: string; expirationTime?: string },
  ): Promise<ChannelDocument> {
    const form = new FormData()
    form.append('file', file)
    if (folderId) form.append('folderId', folderId)
    if (meta?.title) form.append('title', meta.title)
    if (meta?.documentType) form.append('documentType', meta.documentType)
    if (meta?.expirationTime) form.append('expirationTime', meta.expirationTime)
    return http.post<ChannelDocument>(`/channels/${channelId}/documents`, form, {
      timeout: 300000,
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.round((event.loaded / event.total) * 100))
        }
      },
    })
  },
  uploadMarkdown(channelId: string, kind: 'README' | 'AGENT', file: File): Promise<void> {
    const form = new FormData()
    form.append('file', file)
    return http.post<void>(`/channels/${channelId}/markdown?kind=${kind}`, form, {
      timeout: 120000,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  deleteDocument(channelId: string, documentId: string): Promise<void> {
    return http.delete<void>(`/channels/${channelId}/documents/${documentId}`)
  },

  readMarkdown(channelId: string, kind: 'README' | 'AGENT'): Promise<string> {
    return http.get<string>(`/channels/${channelId}/markdown/${kind}`)
  },
  retryDocument(channelId: string, documentId: string): Promise<ChannelDocument> {
    return http.post<ChannelDocument>(`/channels/${channelId}/documents/${documentId}/retry`)
  },
  sendNotification(channelId: string, payload: { title: string; content: string }): Promise<void> {
    return http.post<void>(`/channels/${channelId}/notifications`, payload)
  },
  updateAgent(
    channelId: string,
    payload: { name?: string; description?: string; tags?: string },
  ): Promise<ChannelCard> {
    return http.put<ChannelCard>(`/channels/${channelId}/agent`, payload)
  },
}
