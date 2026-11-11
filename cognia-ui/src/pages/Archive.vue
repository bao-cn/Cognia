<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { IconArchive, IconArrowBackUp, IconClock, IconMessageCircle } from '@tabler/icons-vue'
import { chatApi } from '@/api'
import type { ChatConversation } from '@/types/api'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Spinner } from '@/components/ui/spinner'

const router = useRouter()
const items = ref<ChatConversation[]>([])
const loading = ref(true)
const restoring = ref<string | null>(null)

async function load() {
  loading.value = true
  try {
    items.value = await chatApi.conversations(true)
  } catch (err: any) {
    toast.error(err?.message || '加载归档对话失败')
  } finally {
    loading.value = false
  }
}

async function restore(item: ChatConversation) {
  restoring.value = item.id
  try {
    await chatApi.archiveConversation(item.id, false)
    items.value = items.value.filter(value => value.id !== item.id)
    toast.success('对话已恢复')
  } catch (err: any) {
    toast.error(err?.message || '恢复对话失败')
  } finally {
    restoring.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto flex w-full max-w-4xl flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconArchive class="size-5 text-muted-foreground" />
          归档对话
        </h1>
        <p class="text-sm text-muted-foreground">归档后对话只读，不能继续对话；恢复后可继续。账户注销后，对话才会被删除。</p>
      </div>
      <Badge variant="outline" class="h-8 px-3">{{ items.length }} 条</Badge>
    </div>

    <div v-if="loading" class="flex h-48 items-center justify-center rounded-xl border bg-card">
      <Spinner class="size-6" />
    </div>
    <div v-else-if="items.length === 0" class="flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed bg-card py-16 text-center">
      <IconArchive class="size-8 text-muted-foreground" />
      <p class="text-sm font-medium">暂无归档对话</p>
      <p class="text-xs text-muted-foreground">在最近对话的更多操作中选择“归档”即可移到这里。</p>
    </div>
    <div v-else class="divide-y overflow-hidden rounded-xl border bg-card">
      <div v-for="item in items" :key="item.id" class="flex flex-wrap items-center gap-3 px-4 py-3">
        <IconMessageCircle class="size-4 shrink-0 text-muted-foreground" />
        <button class="min-w-0 flex-1 text-left" @click="router.push(`/chat/${item.id}`)">
          <p class="truncate text-sm font-medium hover:underline">{{ item.title || '未命名对话' }}</p>
          <p class="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
            <IconClock class="size-3" />
            更新于 {{ item.updateTime || '—' }}
          </p>
        </button>
        <Badge variant="secondary">已归档</Badge>
        <Button variant="outline" size="sm" :disabled="restoring === item.id" @click="restore(item)">
          <Spinner v-if="restoring === item.id" class="mr-1 size-3" />
          <IconArrowBackUp v-else class="mr-1 size-3.5" />
          恢复
        </Button>
      </div>
    </div>
  </div>
</template>
