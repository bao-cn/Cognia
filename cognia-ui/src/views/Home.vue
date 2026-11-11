<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import AppSidebar from '@/components/AppSidebar.vue'
import SiteHeader from '@/components/SiteHeader.vue'
import TraceFab from '@/components/TraceFab.vue'
import { useSiteStore } from '@/stores/site'
import { useChatStore } from '@/stores/chat'
import { channelApi, chatApi, discoverApi, favoriteApi, profileApi } from '@/api'
import {
  SidebarInset,
  SidebarProvider,
} from '@/components/ui/sidebar'
import { IconHelp, IconSearch, IconSettings } from '@tabler/icons-vue'

interface SidebarChannel {
  id: string
  name: string
  url: string
}

const siteStore = useSiteStore()
const chatStore = useChatStore()
const route = useRoute()
const router = useRouter()
const channels = ref<SidebarChannel[]>([])

const navSecondary = [
  { title: 'Settings', url: '#', icon: IconSettings },
  { title: 'Get Help', url: '#', icon: IconHelp },
  { title: 'Search', url: '#', icon: IconSearch },
]

const data = computed(() => ({
  channels: channels.value,
  documents: chatStore.conversations.map(item => ({
    id: item.id,
    name: item.title,
    url: `/chat/${item.id}`,
    pinned: item.pinned,
    favorited: item.favorited,
  })),
  navSecondary,
}))

async function loadChannels() {
  try {
    const profile = await profileApi.me()
    const next: SidebarChannel[] = profile.channels.map(channel => ({
      id: `user-${channel.id}`,
      name: channel.name,
      url: `/${profile.username}/${channel.slug}`,
    }))
    for (const team of profile.teams) {
      try {
        const list = await channelApi.list('TEAM', team.id)
        next.push(...list.map(channel => ({
          id: `team-${channel.id}`,
          name: `${team.name || team.slug} / ${channel.name}`,
          url: `/${team.slug}/${channel.slug}`,
        })))
      } catch {
        // A team the viewer left should not block the sidebar.
      }
    }
    const subscriptions = await discoverApi.subscriptions().catch(() => [])
    const subscribed = subscriptions.map(channel => ({
      id: `sub-${channel.id}`,
      name: channel.name,
      url: `/${channel.ownerSlug}/${channel.slug}`,
    }))
    const merged = [...subscribed, ...next]
    const seen = new Set<string>()
    channels.value = merged.filter(channel => {
      if (seen.has(channel.url)) return false
      seen.add(channel.url)
      return true
    }).slice(0, 3)
  } catch {
    // Sidebar is optional while the profile is still loading.
  }
}

function newChat() {
  if (route.name === 'Chat')
    window.dispatchEvent(new CustomEvent('chat:reset'))
  else
    router.push('/chat')
}

function temporaryChat() {
  if (route.name === 'ChatTemporary')
    window.dispatchEvent(new CustomEvent('chat:reset'))
  else
    router.push('/chat/temporary')
}

async function pinConversation(item: { id: string, name: string, pinned?: boolean }) {
  try {
    await chatApi.pinConversation(item.id, !item.pinned)
    await chatStore.loadConversations()
    toast.success(item.pinned ? '已取消置顶' : '已置顶')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  }
}

async function favoriteConversation(item: { id: string, name: string, favorited?: boolean }) {
  try {
    if (item.favorited)
      await favoriteApi.unfavoriteConversation(item.id)
    else
      await favoriteApi.favoriteConversation(item.id)
    await chatStore.loadConversations()
    toast.success(item.favorited ? '已取消收藏' : '已收藏')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  }
}

async function archiveConversation(item: { id: string, name: string }) {
  try {
    await chatStore.archiveConversation(item.id)
    toast.success('对话已归档')
    if (String(route.params.conversationId) === String(item.id))
      router.push('/chat')
  } catch (err: any) {
    toast.error(err?.message || '归档对话失败')
  }
}

onMounted(() => {
  void chatStore.loadConversations().catch(() => {})
  void loadChannels()
})

watch(() => route.fullPath, () => {
  void loadChannels()
})
</script>

<template>
  <SidebarProvider
    class="h-full min-h-0 overflow-hidden"
    :style="{
      '--sidebar-width': 'calc(var(--spacing) * 72)',
      '--header-height': 'calc(var(--spacing) * 12)',
    }"
  >
    <AppSidebar
      variant="inset"
      :isAdmin="false"
      :data="data"
      :logo="siteStore.logo"
      :title="siteStore.siteName"
      :badge_status="true"
      :badge_value="'Beta'"
      @new-chat="newChat"
      @temporary-chat="temporaryChat"
      @archive-conversation="archiveConversation"
      @pin-conversation="pinConversation"
      @favorite-conversation="favoriteConversation"
    />

    <SidebarInset class="flex h-full min-h-0 flex-1 flex-col overflow-hidden">
      <SiteHeader class="z-49 shrink-0 rounded-t-xl bg-background" />

      <div class="@container/main flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto">
        <router-view />
      </div>
    </SidebarInset>

    <TraceFab />
  </SidebarProvider>
</template>
