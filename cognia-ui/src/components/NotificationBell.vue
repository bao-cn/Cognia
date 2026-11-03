<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { AppNotification } from '@/types/api'
import { toast } from 'vue-sonner'
import {
  IconBell,
  IconBellRinging,
  IconChecks,
  IconInbox,
} from '@tabler/icons-vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Spinner } from '@/components/ui/spinner'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { ScrollArea } from '@/components/ui/scroll-area'
import { useNotificationStore } from '@/stores/notification'

const store = useNotificationStore()
const open = ref(false)
const router = useRouter()

const unreadLabel = computed(() => (store.unread > 99 ? '99+' : String(store.unread)))

watch(open, (visible) => {
  if (visible) {
    void store.fetchList(false).catch(() => undefined)
  }
})

async function handleRead(id: number): Promise<void> {
  try {
    await store.markRead(id)
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '标记已读失败')
  }
}

async function handleReadAll(): Promise<void> {
  try {
    await store.markAllRead()
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '标记全部已读失败')
  }
}

function openChannel(item: AppNotification) {
  const payload = item.payload as Record<string, any> | undefined
  if (item.type !== 'CHANNEL_NOTIFICATION' || !payload?.channelSlug)
    return
  const owner = payload.ownerSlug
  if (owner)
    void router.push(`/${owner}/${payload.channelSlug}`)
}

function typeLabel(type: string): string {
  switch (type) {
    case 'DOCUMENT_TIMEOUT':
      return '文档超时'
    case 'TASK_FAILED':
      return '任务失败'
    case 'CHANNEL_NOTIFICATION':
      return '频道通知'
    default:
      return '通知'
  }
}
</script>

<template>
  <Popover v-model:open="open">
    <PopoverTrigger as-child>
      <Button variant="ghost" size="icon" class="relative size-8" aria-label="通知">
        <component :is="store.unread > 0 ? IconBellRinging : IconBell" class="size-4" />
        <span
          v-if="store.unread > 0"
          class="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-medium leading-none text-white"
        >
          {{ unreadLabel }}
        </span>
      </Button>
    </PopoverTrigger>
    <PopoverContent align="end" class="w-80 overflow-hidden p-0">
      <div class="flex items-center justify-between border-b px-4 py-3">
        <div class="flex items-center gap-2">
          <h2 class="text-sm font-semibold">通知</h2>
          <Badge v-if="store.unread > 0" variant="secondary" class="h-5 px-1.5 text-[11px]">
            {{ store.unread }} 条未读
          </Badge>
        </div>
        <Button
          v-if="store.unread > 0"
          variant="ghost"
          size="sm"
          class="h-7 px-2 text-xs"
          @click="handleReadAll"
        >
          <IconChecks class="mr-1 size-3.5" />
          全部已读
        </Button>
      </div>

      <div v-if="store.loading" class="flex h-48 items-center justify-center">
        <Spinner class="size-5" />
      </div>
      <ScrollArea v-else class="h-80">
        <div v-if="store.items.length === 0" class="flex h-48 flex-col items-center justify-center gap-2 text-muted-foreground">
          <IconInbox class="size-8" />
          <p class="text-sm">暂无通知</p>
        </div>
        <div v-else class="flex flex-col divide-y">
          <button
            v-for="item in store.items"
            :key="item.id"
            type="button"
            class="flex w-full flex-col gap-1 px-4 py-3 text-left transition-colors hover:bg-muted/50"
            :class="item.read ? 'opacity-70' : ''"
            @click="handleRead(item.id); openChannel(item)"
          >
            <div class="flex items-center gap-2">
              <Badge variant="outline" class="h-5 shrink-0 px-1.5 text-[10px]">
                {{ typeLabel(item.type) }}
              </Badge>
              <span class="truncate text-sm font-medium">{{ item.title }}</span>
              <span v-if="!item.read" class="ml-auto size-2 shrink-0 rounded-full bg-primary" />
            </div>
            <p v-if="item.content" class="line-clamp-2 text-xs leading-5 text-muted-foreground">
              {{ item.content }}
            </p>
            <span class="text-[11px] text-muted-foreground">{{ item.createTime }}</span>
          </button>
        </div>
      </ScrollArea>
    </PopoverContent>
  </Popover>
</template>
