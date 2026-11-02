import type { ChatConversation, ChatConversationDetail, PublicChatModel } from '@/types/api'
import { http } from './http'
import { getAccessToken } from '@/utils/token'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export interface ChatMessagePayload {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatStreamPayload {
  modelId?: number
  messages: ChatMessagePayload[]
  channelIds?: number[]
  effort?: string
  thinking?: boolean
  conversationId?: string
}

export interface ChatStreamHandlers {
  onStart?: (data: { model: string, provider: string, supportsTools: boolean, conversationId?: string }) => void
  onReasoning?: (delta: string) => void
  onText?: (delta: string) => void
  onTool?: (data: { id?: string, name: string, status: string, arguments?: string, output?: string }) => void
  onDone?: () => void
  onError?: (message: string) => void
}

export const chatApi = {
  models(): Promise<PublicChatModel[]> {
    return http.get<PublicChatModel[]>('/chat/models')
  },

  conversations(): Promise<ChatConversation[]> {
    return http.get<ChatConversation[]>('/chat/conversations')
  },

  conversation(id: string | number): Promise<ChatConversationDetail> {
    return http.get<ChatConversationDetail>(`/chat/conversations/${id}`)
  },

  deleteConversation(id: string | number): Promise<void> {
    return http.delete<void>(`/chat/conversations/${id}`)
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
          case 'reasoning': handlers.onReasoning?.(event.delta ?? ''); break
          case 'text': handlers.onText?.(event.delta ?? ''); break
          case 'tool': handlers.onTool?.(event); break
          case 'done': handlers.onDone?.(); break
          case 'error': handlers.onError?.(event.message ?? '模型请求失败'); break
        }
      }
    }
  },
}
