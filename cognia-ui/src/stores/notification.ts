import { defineStore } from 'pinia'
import type { AppNotification } from '@/types/api'
import { notificationApi } from '@/api'
import { getAccessToken } from '@/utils/token'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

let controller: AbortController | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectDelay = 3000

interface NotificationState {
  items: AppNotification[]
  unread: number
  loading: boolean
  connected: boolean
}

export const useNotificationStore = defineStore('notification', {
  state: (): NotificationState => ({
    items: [],
    unread: 0,
    loading: false,
    connected: false,
  }),

  getters: {
    hasUnread: state => state.unread > 0,
    recent: state => state.items.slice(0, 8),
  },

  actions: {
    async fetchUnread(): Promise<void> {
      if (!getAccessToken()) return
      try {
        this.unread = await notificationApi.unreadCount()
      } catch {
        // The bell is non-critical; the next SSE event refreshes the count.
      }
    },

    async fetchList(unreadOnly = false): Promise<void> {
      if (!getAccessToken()) return
      this.loading = true
      try {
        const result = await notificationApi.list({ page: 1, size: 50, unreadOnly })
        this.items = result.records
      } finally {
        this.loading = false
      }
    },

    async markRead(id: number): Promise<void> {
      const item = this.items.find(entry => entry.id === id)
      if (item?.read) return
      await notificationApi.markRead(id)
      if (item) item.read = true
      this.unread = Math.max(0, this.unread - 1)
    },

    async markAllRead(): Promise<void> {
      if (this.unread === 0) return
      await notificationApi.markAllRead()
      this.items.forEach(item => { item.read = true })
      this.unread = 0
    },

    start(): void {
      if (!getAccessToken() || controller) return
      void this.fetchUnread()
      this.connect()
    },

    stop(): void {
      controller?.abort()
      controller = null
      this.connected = false
      if (reconnectTimer) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
      }
      reconnectDelay = 3000
    },

    connect(): void {
      if (controller || !getAccessToken()) return
      controller = new AbortController()
      void this.consume(controller.signal)
    },

    async consume(signal: AbortSignal): Promise<void> {
      try {
        const response = await fetch(`${BASE_URL}/notifications/stream`, {
          headers: {
            Accept: 'text/event-stream',
            Authorization: `Bearer ${getAccessToken() ?? ''}`,
          },
          signal,
        })
        if (response.status === 401) {
          this.stop()
          return
        }
        if (!response.ok || !response.body) {
          throw new Error(`SSE 连接失败（HTTP ${response.status}）`)
        }
        this.connected = true
        reconnectDelay = 3000

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        while (!signal.aborted) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          let boundary = buffer.indexOf('\n\n')
          while (boundary >= 0) {
            const chunk = buffer.slice(0, boundary)
            buffer = buffer.slice(boundary + 2)
            this.handleEvent(chunk)
            boundary = buffer.indexOf('\n\n')
          }
        }
      } catch {
        // Aborted by logout/unmount or a transient network failure.
      } finally {
        this.connected = false
        if (controller?.signal === signal) {
          controller = null
        }
        if (!signal.aborted && getAccessToken()) {
          this.scheduleReconnect()
        }
      }
    },

    handleEvent(chunk: string): void {
      let event = 'message'
      const dataLines: string[] = []
      for (const line of chunk.split('\n')) {
        if (line.startsWith('event:')) {
          event = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart())
        }
      }
      if (dataLines.length === 0) return
      const raw = dataLines.join('\n')
      try {
        const data = JSON.parse(raw) as unknown
        if (event === 'notification') {
          const notification = data as AppNotification
          if (!this.items.some(item => item.id === notification.id)) {
            this.items = [notification, ...this.items].slice(0, 100)
          }
        } else if (event === 'unread-count') {
          const payload = data as number | { count?: number }
          this.unread = typeof payload === 'number'
            ? payload
            : Math.max(0, Number(payload?.count) || 0)
        }
      } catch {
        // Ignore keep-alive comments or malformed frames.
      }
    },

    scheduleReconnect(): void {
      if (reconnectTimer) return
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        reconnectDelay = Math.min(reconnectDelay * 2, 30000)
        this.connect()
      }, reconnectDelay)
    },
  },
})
