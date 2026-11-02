import { defineStore } from 'pinia'
import { chatApi } from '@/api'
import type { ChatConversation } from '@/types/api'

export const useChatStore = defineStore('chat', {
  state: () => ({
    conversations: [] as ChatConversation[],
    loading: false,
  }),
  actions: {
    async loadConversations() {
      this.loading = true
      try {
        this.conversations = await chatApi.conversations()
      } finally {
        this.loading = false
      }
    },
    async removeConversation(id: string) {
      await chatApi.deleteConversation(id)
      this.conversations = this.conversations.filter(item => String(item.id) !== String(id))
    },
  },
})
