import type { ChatCapabilities, ChatConversation, ChatConversationDetail, CitationSource, PublicChatModel } from '@/types/api'
export type { CitationSource }
import { http } from './http'
import { getAccessToken } from '@/utils/token'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export interface ChatMessagePayload {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatAttachmentPayload {
  type: 'IMAGE' | 'TEXT'
  name: string
  mimeType?: string
  dataUrl?: string
  text?: string
  url?: string
  key?: string
}

export interface ChatAttachmentResult extends ChatAttachmentPayload {
  kind: 'IMAGE' | 'TEXT'
  size: number
  url?: string
  key?: string
}

export interface ChatStreamPayload {
  modelId?: number
  messages: ChatMessagePayload[]
  channelIds?: number[]
  effort?: string
  thinking?: boolean
  conversationId?: string
  temporary?: boolean
  webSearch?: boolean
  attachments?: ChatAttachmentPayload[]
  regenerate?: boolean
  editMessageIndex?: number
  debug?: boolean
  debugTopK?: number
  debugRerankEnabled?: boolean
  debugToolsEnabled?: boolean
}

export interface ChatCacheInfo {
  hitType: 'SEMANTIC' | 'RETRIEVAL' | 'NONE'
  similarity?: number
  cacheKey?: string
  hits?: number
  requests?: number
  hitRate?: number
}

export interface ChatContextInfo {
  usedTokens: number
  maxTokens: number
  percent: number
  compressionThreshold?: number
  compressed?: boolean
  droppedMessages?: number
}

export interface ChatStreamHandlers {
  onStart?: (data: { model: string, provider: string, supportsTools: boolean, supportsVision?: boolean, conversationId?: string }) => void
  onCache?: (data: ChatCacheInfo) => void
  onContext?: (data: ChatContextInfo) => void
  onCitations?: (sources: CitationSource[]) => void
  onReasoning?: (delta: string) => void
  onText?: (delta: string) => void
  onTool?: (data: { id?: string, name: string, status: string, arguments?: string, output?: string }) => void
  onDone?: (data: { assistantMessageId?: string }) => void
  onTruncated?: (message: string) => void
  onTrace?: (traceId: string) => void
  onError?: (message: string) => void
}

export const chatApi = {
  models(): Promise<PublicChatModel[]> {
    return http.get<PublicChatModel[]>('/chat/models')
  },

  uploadAttachment(file: File, conversationId?: string | null): Promise<ChatAttachmentResult> {
    const form = new FormData()
    form.append('file', file)
    if (conversationId) form.append('conversationId', conversationId)
    return http.post<ChatAttachmentResult>('/chat/attachments', form, {
      timeout: 180000,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  capabilities(): Promise<ChatCapabilities> {
    return http.get<ChatCapabilities>('/chat/capabilities')
  },

  conversations(archived = false): Promise<ChatConversation[]> {
    return http.get<ChatConversation[]>('/chat/conversations', { params: { archived } })
  },

  conversation(id: string | number): Promise<ChatConversationDetail> {
    return http.get<ChatConversationDetail>(`/chat/conversations/${id}`)
  },

  pinConversation(id: string | number, pinned: boolean): Promise<void> {
    return http.put<void>(`/chat/conversations/${id}/pin`, { pinned })
  },

  archiveConversation(id: string | number, archived = true): Promise<void> {
    return http.put<void>(`/chat/conversations/${id}/archive`, { archived })
  },

  forkConversation(id: string | number, messageIndex: number): Promise<ChatConversationDetail> {
    return http.post<ChatConversationDetail>(`/chat/conversations/${id}/fork`, { messageIndex })
  },

  async stream(payload: ChatStreamPayload, handlers: ChatStreamHandlers, signal?: AbortSignal): Promise<void> {
    const token = getAccessToken()
    const response = await fetch(`${BASE_URL}/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(payload),
      signal,
    })
    if (!response.ok) {
      const text = await response.text().catch(() => '')
      throw new Error(text || `请求失败（${response.status}）`)
    }
    if (!response.body) throw new Error('当前浏览器不支持流式响应')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        boundary = buffer.indexOf('\n\n')
        const data = frame.split('\n')
          .filter(line => line.startsWith('data:'))
          .map(line => line.slice(5).trim())
          .join('\n')
        if (!data) continue
        let event: any
        try {
          event = JSON.parse(data)
        } catch {
          continue
        }
        switch (event.type) {
          case 'start': handlers.onStart?.(event); break
          case 'cache': handlers.onCache?.(event); break
          case 'context': handlers.onContext?.(event); break
          case 'citations': handlers.onCitations?.(event.sources ?? []); break
          case 'reasoning': handlers.onReasoning?.(event.delta ?? ''); break
          case 'text': handlers.onText?.(event.delta ?? ''); break
          case 'tool': handlers.onTool?.(event); break
          case 'done': handlers.onDone?.(event); break
          case 'truncated': handlers.onTruncated?.(event.message ?? '生成已停止，请重新尝试'); break
          case 'trace': if (event.traceId) handlers.onTrace?.(String(event.traceId)); break
          case 'error': handlers.onError?.(event.message ?? '模型请求失败'); break
        }
      }
    }
  },
}
