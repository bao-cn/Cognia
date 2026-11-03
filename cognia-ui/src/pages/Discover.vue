<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { IconBell, IconBellOff, IconBookmark, IconCompass, IconFileText, IconRefresh, IconStar, IconUsers } from '@tabler/icons-vue'
import { discoverApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { DiscoverChannel } from '@/types/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'

const userStore = useUserStore()
const tab = ref<'featured' | 'hot'>('hot')
const period = ref<'week' | 'month' | 'year'>('week')
const page = ref(1)
const size = 9
const total = ref(0)
const pages = ref(0)
const items = ref<DiscoverChannel[]>([])
const loading = ref(false)
const error = ref('')
const busy = ref<string | null>(null)
const canManage = computed(() => userStore.hasPermission('settings:update'))

const periodLabels = { week: '本周', month: '本月', year: '今年' } as const

function channelUrl(channel: DiscoverChannel) {
  return `/${channel.ownerSlug}/${channel.slug}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await discoverApi.channels({ tab: tab.value, period: tab.value === 'hot' ? period.value : undefined, page: page.value, size })
    items.value = result.records
    total.value = result.total
    pages.value = result.pages
  } catch (err: any) {
    error.value = err?.message || '加载发现广场失败'
  } finally {
    loading.value = false
  }
}

async function toggleSubscribe(channel: DiscoverChannel) {
  busy.value = channel.id
  try {
    if (channel.subscribed) await discoverApi.unsubscribe(channel.id)
    else await discoverApi.subscribe(channel.id)
    channel.subscribed = !channel.subscribed
    channel.subscriberCount += channel.subscribed ? 1 : -1
    toast.success(channel.subscribed ? '已加入我的频道' : '已取消订阅')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  } finally {
    busy.value = null
  }
}

async function toggleNotify(channel: DiscoverChannel) {
  busy.value = channel.id
  try {
    if (channel.notifySubscribed) await discoverApi.unsubscribeNotify(channel.id)
    else await discoverApi.subscribeNotify(channel.id)
    channel.notifySubscribed = !channel.notifySubscribed
    toast.success(channel.notifySubscribed ? '已开启频道通知' : '已关闭频道通知')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  } finally {
    busy.value = null
  }
}

async function toggleFeatured(channel: DiscoverChannel) {
  busy.value = channel.id
  try {
    await discoverApi.setFeatured(channel.id, !channel.featured)
    channel.featured = !channel.featured
    toast.success(channel.featured ? '已设为精选频道' : '已取消精选')
    await load()
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  } finally {
    busy.value = null
  }
}

function goPage(next: number) {
  if (next < 1 || next > pages.value || loading.value) return
  page.value = next
  void load()
}


watch([tab, period], () => {
  page.value = 1
  void load()
})

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconCompass class="size-5 text-muted-foreground" />发现广场
        </h1>
        <p class="text-sm text-muted-foreground">发现公开频道，订阅后用它们构建你的知识库对话。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <IconRefresh class="mr-1 size-4" />刷新
      </Button>
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <div class="inline-flex h-9 items-center rounded-lg bg-muted p-1">
        <button type="button" class="rounded-md px-3 py-1 text-sm transition-colors"
          :class="tab === 'featured' ? 'bg-background font-medium shadow-sm' : 'text-muted-foreground'"
          @click="tab = 'featured'">精选频道</button>
        <button type="button" class="rounded-md px-3 py-1 text-sm transition-colors"
          :class="tab === 'hot' ? 'bg-background font-medium shadow-sm' : 'text-muted-foreground'"
          @click="tab = 'hot'">热门频道</button>
      </div>
      <div v-if="tab === 'hot'" class="inline-flex items-center gap-1">
        <Button v-for="(label, key) in periodLabels" :key="key" size="sm"
          :variant="period === key ? 'default' : 'outline'" @click="period = key">{{ label }}</Button>
      </div>
      <span class="ml-auto text-xs text-muted-foreground">共 {{ total }} 个频道</span>
    </div>

    <Alert v-if="error" variant="destructive"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>
    <div v-else-if="items.length === 0" class="flex h-48 flex-col items-center justify-center gap-2 rounded-xl border text-muted-foreground">
      <IconCompass class="size-8" />
      <p class="text-sm">暂时没有{{ tab === 'featured' ? '精选' : '热门' }}频道</p>
    </div>
    <div v-else class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
      <article v-for="channel in items" :key="channel.id" class="flex min-w-0 flex-col gap-3 rounded-xl border bg-card p-4">
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <router-link :to="channelUrl(channel)" class="truncate text-base font-semibold hover:underline">{{ channel.name }}</router-link>
              <Badge v-if="channel.featured" variant="secondary" class="shrink-0">精选</Badge>
            </div>
            <p class="mt-0.5 truncate text-xs text-muted-foreground">@{{ channel.ownerSlug }} · {{ channel.ownerName }}</p>
          </div>
          <Button v-if="canManage" variant="ghost" size="icon" class="size-8 shrink-0" :disabled="busy === channel.id"
            :title="channel.featured ? '取消精选' : '设为精选'" @click="toggleFeatured(channel)">
            <IconStar class="size-4" :class="channel.featured ? 'fill-amber-400 text-amber-500' : 'text-muted-foreground'" />
          </Button>
        </div>
        <p class="line-clamp-2 min-h-10 text-sm text-muted-foreground">{{ channel.description || '这个频道还没有简介。' }}</p>
        <div class="flex items-center gap-3 text-xs text-muted-foreground">
          <span class="flex items-center gap-1"><IconFileText class="size-3.5" />{{ channel.documentCount }} 文档</span>
          <span class="flex items-center gap-1"><IconUsers class="size-3.5" />{{ channel.subscriberCount }} 订阅</span>
        </div>
        <div class="mt-auto flex items-center gap-2">
          <Button size="sm" class="flex-1" :variant="channel.subscribed ? 'secondary' : 'default'" :disabled="busy === channel.id"
            @click="toggleSubscribe(channel)">
            <IconBookmark class="mr-1 size-4" />{{ channel.subscribed ? '已订阅' : '订阅频道' }}
          </Button>
          <Button size="icon" variant="outline" class="size-8" :disabled="busy === channel.id"
            :title="channel.notifySubscribed ? '关闭频道通知' : '接收频道通知'" @click="toggleNotify(channel)">
            <IconBell v-if="channel.notifySubscribed" class="size-4 text-primary" />
            <IconBellOff v-else class="size-4" />
          </Button>
        </div>
      </article>
    </div>

    <div v-if="pages > 1" class="flex items-center justify-between gap-2">
      <p class="text-xs text-muted-foreground">第 {{ page }} / {{ pages }} 页</p>
      <div class="flex gap-2">
        <Button variant="outline" size="sm" :disabled="page <= 1 || loading" @click="goPage(page - 1)">上一页</Button>
        <Button variant="outline" size="sm" :disabled="page >= pages || loading" @click="goPage(page + 1)">下一页</Button>
      </div>
    </div>
  </div>
</template>
