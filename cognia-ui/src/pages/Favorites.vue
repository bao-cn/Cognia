<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { IconMessage, IconStar, IconStarFilled, IconTrash } from '@tabler/icons-vue'
import type { FavoriteConversation, FavoriteMessage } from '@/types/api'
import { favoriteApi } from '@/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

const router = useRouter()
const loading = ref(true)
const conversations = ref<FavoriteConversation[]>([])
const messages = ref<FavoriteMessage[]>([])
const activeTab = ref('conversations')

const total = computed(() => conversations.value.length + messages.value.length)

async function load() {
  loading.value = true
  try {
    const data = await favoriteApi.list()
    conversations.value = data.conversations
    messages.value = data.messages
  } catch (err: any) {
    toast.error(err?.message || '加载收藏失败')
  } finally {
    loading.value = false
  }
}

function openConversation(item: FavoriteConversation) {
  void router.push(`/chat/${item.conversationId}`)
}

function openMessage(item: FavoriteMessage) {
  void router.push(`/chat/${item.conversationId}`)
}

async function removeConversation(item: FavoriteConversation) {
  try {
    await favoriteApi.unfavoriteConversation(item.conversationId)
    conversations.value = conversations.value.filter(entry => entry.id !== item.id)
    toast.success('已取消收藏')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  }
}

async function removeMessage(item: FavoriteMessage) {
  try {
    await favoriteApi.unfavoriteMessage(item.messageId)
    messages.value = messages.value.filter(entry => entry.id !== item.id)
    toast.success('已取消收藏')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto flex w-full max-w-4xl flex-col gap-4 p-4 md:p-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconStarFilled class="size-5 text-amber-500" />
          我的收藏
        </h1>
        <p class="text-sm text-muted-foreground">收藏的对话和回答会保存在这里，方便随时回看。</p>
      </div>
      <Badge variant="outline" class="h-8 px-3">{{ total }} 条收藏</Badge>
    </header>

    <div v-if="loading" class="flex h-56 items-center justify-center rounded-xl border">
      <Spinner class="size-5" />
    </div>

    <Tabs v-else v-model="activeTab" class="gap-4">
      <TabsList class="h-10 w-full justify-start bg-muted/60 p-1">
        <TabsTrigger value="conversations" class="flex-none px-3">
          对话 {{ conversations.length }}
        </TabsTrigger>
        <TabsTrigger value="messages" class="flex-none px-3">
          消息 {{ messages.length }}
        </TabsTrigger>
      </TabsList>

      <TabsContent value="conversations">
        <div v-if="!conversations.length" class="flex flex-col items-center gap-2 rounded-xl border py-16 text-center">
          <IconStar class="size-8 text-muted-foreground/60" />
          <p class="text-sm font-medium">还没有收藏对话</p>
          <p class="text-xs text-muted-foreground">在侧边栏对话的更多菜单中选择「收藏」。</p>
        </div>
        <div v-else class="flex flex-col divide-y rounded-xl border bg-card">
          <div v-for="item in conversations" :key="item.id"
            class="group flex cursor-pointer items-center gap-3 px-4 py-3 transition-colors hover:bg-muted/40"
            @click="openConversation(item)">
            <div class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted">
              <IconMessage class="size-4 text-muted-foreground" />
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium">{{ item.title }}</p>
              <p class="mt-0.5 text-xs text-muted-foreground">收藏于 {{ item.createTime }}</p>
            </div>
            <Button variant="ghost" size="icon-sm" class="text-muted-foreground opacity-0 group-hover:opacity-100"
              title="取消收藏" @click.stop="removeConversation(item)">
              <IconTrash />
            </Button>
          </div>
        </div>
      </TabsContent>

      <TabsContent value="messages">
        <div v-if="!messages.length" class="flex flex-col items-center gap-2 rounded-xl border py-16 text-center">
          <IconStar class="size-8 text-muted-foreground/60" />
          <p class="text-sm font-medium">还没有收藏消息</p>
          <p class="text-xs text-muted-foreground">在回答下方点击星标即可收藏。</p>
        </div>
        <div v-else class="flex flex-col gap-3">
          <article v-for="item in messages" :key="item.id"
            class="group cursor-pointer rounded-xl border bg-card p-4 transition-shadow hover:shadow-sm"
            @click="openMessage(item)">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium">{{ item.conversationTitle }}</p>
                <p class="mt-0.5 text-xs text-muted-foreground">{{ item.createTime }}</p>
              </div>
              <Button variant="ghost" size="icon-sm" class="shrink-0 text-muted-foreground opacity-0 group-hover:opacity-100"
                title="取消收藏" @click.stop="removeMessage(item)">
                <IconTrash />
              </Button>
            </div>
            <p class="mt-3 line-clamp-3 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">
              {{ item.content }}
            </p>
          </article>
        </div>
      </TabsContent>
    </Tabs>
  </div>
</template>
