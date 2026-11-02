import { defineStore } from 'pinia'
import { legalApi } from '@/api'
import type { ConsentStatus, LegalDocument } from '@/types/api'
import { useUserStore } from '@/stores/user'

interface LegalState {
  status: ConsentStatus | null
  initialized: boolean
  loading: boolean
  submitting: boolean
}

export const useLegalStore = defineStore('legal', {
  state: (): LegalState => ({
    status: null,
    initialized: false,
    loading: false,
    submitting: false,
  }),

  getters: {
    documents: state => state.status?.documents ?? [],
    privacy(): LegalDocument | null {
      return this.documents.find(document => document.type === 'PRIVACY') ?? null
    },
    terms(): LegalDocument | null {
      return this.documents.find(document => document.type === 'TERMS') ?? null
    },
    requiresConsent: state => Boolean(state.status?.requiresConsent),
    isRevision(): boolean {
      if (!this.status) return false
      const accepted = this.status.consents
      if (!accepted || Object.keys(accepted).length === 0) return false
      return this.documents.some(document => (accepted[document.type] ?? 0) < document.version)
    },
  },

  actions: {
    async ensureStatus(force = false): Promise<void> {
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) {
        this.reset()
        return
      }
      if (this.initialized && !force) {
        return
      }
      this.loading = true
      try {
        this.status = await legalApi.consentStatus()
        this.initialized = true
      } catch (error) {
        console.warn('Failed to load legal consent status', error)
      } finally {
        this.loading = false
      }
    },

    async agree(): Promise<void> {
      if (!this.status || !this.privacy || !this.terms) {
        throw new Error('协议内容尚未加载')
      }
      this.submitting = true
      try {
        this.status = await legalApi.submitConsent(this.privacy.version, this.terms.version)
        this.initialized = true
      } finally {
        this.submitting = false
      }
    },

    reset(): void {
      this.status = null
      this.initialized = false
      this.loading = false
      this.submitting = false
    },
  },
})
