import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { debugApi } from '@/api'
import type { DebugSetting } from '@/types/api'

/**
 * Admin-only dev mode. The switch itself lives in the backend so the hardcoded
 * administrator's conversations are recorded without any per-message flag.
 */
export const useDebugStore = defineStore('debug', () => {
  const enabled = ref(false)
  const hardcodedUsername = ref('admin')
  const updatedAt = ref<string | undefined>()
  const loading = ref(false)
  const loaded = ref(false)
  const drawerOpen = ref(false)
  const traceId = ref<string | null>(null)
  /** false = 精简模式, true = 完整 raw 模式 (shows prompt/messages/raw chunks). */
  const fullRaw = ref(false)
  const overrides = ref({ topK: undefined as number | undefined, rerankEnabled: undefined as boolean | undefined, toolsEnabled: undefined as boolean | undefined })

  const isActive = computed(() => enabled.value && Boolean(hardcodedUsername.value))

  async function load() {
    if (loading.value) return
    loading.value = true
    try {
      apply(await debugApi.getSettings())
    } catch {
      // Non-admins (or a backend without dev mode) simply see dev mode disabled.
      enabled.value = false
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  function apply(setting: DebugSetting) {
    enabled.value = setting.enabled
    hardcodedUsername.value = setting.hardcodedUsername || 'admin'
    updatedAt.value = setting.updatedAt
  }

  async function setEnabled(value: boolean) {
    apply(await debugApi.updateSettings(value))
  }

  function openList() {
    traceId.value = null
    drawerOpen.value = true
  }

  function showTrace(id: string) {
    traceId.value = id
    drawerOpen.value = true
  }

  function close() {
    drawerOpen.value = false
  }

  function toggleDrawer() {
    if (drawerOpen.value) {
      close()
      return
    }
    openList()
  }

  function resetOverrides() {
    overrides.value = { topK: undefined, rerankEnabled: undefined, toolsEnabled: undefined }
  }

  return {
    enabled,
    hardcodedUsername,
    updatedAt,
    loading,
    loaded,
    isActive,
    drawerOpen,
    traceId,
    fullRaw,
    overrides,
    load,
    setEnabled,
    openList,
    showTrace,
    close,
    toggleDrawer,
    resetOverrides,
  }
})
