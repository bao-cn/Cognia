import { defineStore } from 'pinia'

export const useSearchStore = defineStore('search', {
  state: () => ({
    open: false,
  }),
  actions: {
    openSearch() {
      this.open = true
    },
    closeSearch() {
      this.open = false
    },
  },
})
