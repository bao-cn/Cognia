import { defineStore } from 'pinia'

export const useSessionStore = defineStore('session', {
  state: () => ({
    sessionInfo: null as any,
  }),
  actions: {
    setSessionInfo(sessionInfo: any) {
      this.sessionInfo = sessionInfo
    },
  },
})