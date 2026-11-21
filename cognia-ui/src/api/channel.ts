import { http } from './http'
import type {
  ChannelDetail,
  ChannelDocument,
  ChannelCard,
  ChannelFolder,
  ChannelEmbeddingStatus,
  DocumentChunkItem,
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
  setVisibility(id: string, isPublic: boolean, verificationCode?: string): Promise<ChannelCard> {
    return http.put<ChannelCard>(`/channels/${id}/visibility`, { isPublic, verificationCode })
  },
  setArchived(id: string, archived: boolean, verificationCode?: string): Promise<ChannelCard> {
    return http.put<ChannelCard>(`/channels/${id}/archive`, { archived, verificationCode })
  },
  reembed(id: string): Promise<number> {
    return http.post<number>(`/channels/${id}/reembed`)
  },
  embedding(id: string): Promise<ChannelEmbeddingStatus> {
    return http.get<ChannelEmbeddingStatus>(`/channels/${id}/embedding`)
  },
  retryEmbeddingJob(id: string, jobId: string): Promise<void> {
    return http.post<void>(`/channels/${id}/embedding/jobs/${jobId}/retry`)
  },
  cancelEmbedding(id: string): Promise<number> {
    return http.post<number>(`/channels/${id}/embedding/cancel`)
  },
  embeddingReadiness(ids: number[]): Promise<Record<string, boolean>> {
    return http.get<Record<string, boolean>>('/channels/embedding-readiness', { params: { ids: ids.join(',') } })
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
  documentChunks(channelId: string | number, documentId: string | number, page = 1, size = 20) {
    return http.get<{ records: DocumentChunkItem[]; total: number; page: number; size: number; pages: number }>(
      `/channels/${channelId}/documents/${documentId}/chunks`,
      { params: { page, size } },
    )
  },

  uploadDocument(
    channelId: string,
    file: File,
    folderId?: string,
    onProgress?: (percent: number) => void,
    meta?: { title?: string; documentType?: string; expirationTime?: string; uploadBatchId?: string },
  ): Promise<ChannelDocument> {
    const form = new FormData()
    form.append('file', file)
    if (folderId) form.append('folderId', folderId)
    if (meta?.title) form.append('title', meta.title)
    if (meta?.documentType) form.append('documentType', meta.documentType)
    if (meta?.expirationTime) form.append('expirationTime', meta.expirationTime)
    if (meta?.uploadBatchId) form.append('uploadBatchId', meta.uploadBatchId)
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
  deleteDocument(channelId: string, documentId: string): Promise<void> {
    return http.delete<void>(`/channels/${channelId}/documents/${documentId}`)
  },
  moveDocument(channelId: string, documentId: string, folderId?: string): Promise<ChannelDocument> {
    return http.put<ChannelDocument>(`/channels/${channelId}/documents/${documentId}/move`, { folderId })
  },
  moveDocuments(channelId: string, documentIds: string[], folderId?: string): Promise<number> {
    return http.put<number>(`/channels/${channelId}/documents/move`, { documentIds, folderId })
  },

  readMarkdown(channelId: string, kind: 'README' | 'AGENT'): Promise<string> {
    return http.get<string>(`/channels/${channelId}/markdown/${kind}`)
  },
  documentMarkdown(channelId: string, documentId: string): Promise<string> {
    return http.get<string>(`/channels/${channelId}/documents/${documentId}/markdown`)
  },
  retryDocument(channelId: string, documentId: string): Promise<ChannelDocument> {
    return http.post<ChannelDocument>(`/channels/${channelId}/documents/${documentId}/retry`)
  },
  sendNotification(channelId: string, payload: { title: string; content: string }): Promise<void> {
    return http.post<void>(`/channels/${channelId}/notifications`, payload)
  },
}
