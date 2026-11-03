<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { SidebarTrigger } from '@/components/ui/sidebar'
import NotificationBell from '@/components/NotificationBell.vue'

const route = useRoute()
const chatStore = useChatStore()
const isChatRoute = computed(() => route.name === 'Chat' || route.name === 'ChatConversation' || route.name === 'ChatTemporary')
const conversationId = computed(() => route.params.conversationId ? String(route.params.conversationId) : null)
const currentConversation = computed(() => conversationId.value
  ? chatStore.conversations.find(item => String(item.id) === conversationId.value)
  : undefined)
const title = computed(() => {
  if (isChatRoute.value) {
    if (route.name === 'ChatTemporary')
      return chatStore.activeTitle || '临时对话'
    if (currentConversation.value?.title)
      return currentConversation.value.title
    if (conversationId.value)
      return chatStore.activeTitle || '对话'
    return chatStore.activeTitle || '新对话'
  }
  return String(route.meta.title ?? 'Cognia')
})
const MAX_HEADER_TITLE = 24
const displayTitle = computed(() => {
  const value = title.value.trim()
  return value.length > MAX_HEADER_TITLE ? `${value.slice(0, MAX_HEADER_TITLE)}…` : value
})
const subtitle = computed(() => {
  if (isChatRoute.value)
    return 'Cognia 可能会出错，请核查重要信息'
  switch (route.name) {
    case 'Favorites':
      return '把重要内容留在手边'
    case 'SettingsTeams':
      return '管理团队、成员与公开频道'
    case 'OwnerProfile':
      return '个人与团队主页'
    case 'Download':
      return '选择适合你的客户端'
    case 'Settings':
      return '个性化你的使用体验'
    default:
      return '欢迎回来'
  }
})
</script>

<template>
  <header class="flex h-13 shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-13">
    <div class="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
      <SidebarTrigger class="-ml-1" />
      <div class="flex-1"></div>
      <div class="flex flex-col gap-1 text-center">
        <h1 class="max-w-[min(50vw,520px)] truncate text-base font-medium" :title="title">
        {{ displayTitle }}
      </h1>
      <p class="text-xs text-muted-foreground">
        {{ subtitle }}
      </p>
      </div>
      <div class="flex-1"></div>
      <div class="ml-auto flex items-center gap-2">
        <NotificationBell />
        <!-- <Button variant="ghost" as-child size="sm" class="hidden sm:flex">
          <a
            href="#"
            class="dark:text-foreground"
          >
            <IconDots />
          </a>
        </Button> -->
      </div>
    </div>
  </header>
</template>
