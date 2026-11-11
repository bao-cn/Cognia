import { defineStore } from 'pinia'
import { chatApi } from '@/api'
import type { ChatConversation } from '@/types/api'

export const useChatStore = defineStore('chat', {
  state: () => ({
    conversations: [] as ChatConversation[],
    archivedConversations: [] as ChatConversation[],
    activeConversationId: null as string | null,
    activeTitle: '新对话',
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
    setActiveConversation(id: string | null, title: string) {
      this.activeConversationId = id
      this.activeTitle = title.trim() || '新对话'
    },
    async loadArchivedConversations() {
      this.archivedConversations = await chatApi.conversations(true)
    },
    async archiveConversation(id: string) {
      await chatApi.archiveConversation(id, true)
      this.conversations = this.conversations.filter(item => String(item.id) !== String(id))
      if (String(this.activeConversationId) === String(id))
        this.$patch({ activeConversationId: null, activeTitle: '新对话' })
    },
    async restoreConversation(id: string) {
      await chatApi.archiveConversation(id, false)
      this.archivedConversations = this.archivedConversations.filter(item => String(item.id) !== String(id))
      await this.loadConversations()
    },
  },
})
