<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  IconArrowDown,
  IconArrowUp,
  IconCornerDownLeft,
  IconHash,
  IconMessageCircle,
  IconSearch,
  IconUser,
} from '@tabler/icons-vue'
import type { Component } from 'vue'
import { searchApi } from '@/api'
import type { GlobalSearchResult } from '@/types/api'
import { useSearchStore } from '@/stores/search'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Kbd } from '@/components/ui/kbd'
import { Spinner } from '@/components/ui/spinner'

interface PaletteItem {
  index: number
  group: 'channel' | 'user' | 'conversation'
  icon: Component
  title: string
  subtitle?: string
  to: string
}

const router = useRouter()
const store = useSearchStore()
const query = ref('')
const results = ref<GlobalSearchResult | null>(null)
const loading = ref(false)
const error = ref('')
const active = ref(0)
const inputRef = ref<{ $el?: HTMLInputElement } | null>(null)
const listRef = ref<HTMLElement | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined
let sequence = 0

const flat = computed<PaletteItem[]>(() => {
  const data = results.value
  if (!data) return []
  const items: PaletteItem[] = []
  for (const channel of data.channels) {
    items.push({
      index: items.length,
      group: 'channel',
      icon: IconHash,
      title: channel.name,
      subtitle: `/${channel.ownerSlug}/${channel.slug}`,
      to: `/${channel.ownerSlug}/${channel.slug}`,
    })
  }
  for (const user of data.users) {
    items.push({
      index: items.length,
      group: 'user',
      icon: IconUser,
      title: user.nickname || user.username,
      subtitle: `@${user.username}`,
      to: `/${user.username}`,
    })
  }
  for (const conversation of data.conversations) {
    items.push({
      index: items.length,
      group: 'conversation',
      icon: IconMessageCircle,
      title: conversation.title || '未命名对话',
      subtitle: conversation.updateTime ? `最近更新 ${conversation.updateTime}` : '历史对话',
      to: `/chat/${conversation.id}`,
    })
  }
  return items
})

const sections = computed(() => [
  { label: '公开频道', items: flat.value.filter(item => item.group === 'channel') },
  { label: '用户', items: flat.value.filter(item => item.group === 'user') },
  { label: '历史对话', items: flat.value.filter(item => item.group === 'conversation') },
].filter(section => section.items.length > 0))

const hasQuery = computed(() => query.value.trim().length > 0)
const isEmpty = computed(() => hasQuery.value && !loading.value && !error.value && results.value !== null && flat.value.length === 0)

function reset() {
  query.value = ''
  results.value = null
  error.value = ''
  active.value = 0
  loading.value = false
}

async function load(keyword: string) {
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    const data = await searchApi.query(keyword)
    if (current !== sequence) return
    results.value = data
    active.value = 0
  } catch {
    if (current !== sequence) return
    results.value = null
    error.value = '搜索失败，请稍后再试'
  } finally {
    if (current === sequence) loading.value = false
  }
}

function choose(item: PaletteItem) {
  store.closeSearch()
  void router.push(item.to)
}

function scrollActiveIntoView() {
  nextTick(() => {
    listRef.value?.querySelector<HTMLElement>(`[data-palette-index="${active.value}"]`)?.scrollIntoView({ block: 'nearest' })
  })
}

function move(delta: number) {
  if (!flat.value.length) return
  active.value = (active.value + delta + flat.value.length) % flat.value.length
  scrollActiveIntoView()
}

function onKeydown(event: KeyboardEvent) {
  if (event.isComposing) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    move(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    move(-1)
  } else if (event.key === 'Enter') {
    if (flat.value.length) {
      event.preventDefault()
      choose(flat.value[active.value]!)
    }
  }
}

function onGlobalKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    if (store.open) store.closeSearch()
    else store.openSearch()
  }
}

watch(query, value => {
  if (timer) clearTimeout(timer)
  const keyword = value.trim()
  if (!keyword) {
    sequence++
    loading.value = false
    results.value = null
    error.value = ''
    active.value = 0
    return
  }
  timer = setTimeout(() => void load(keyword), 180)
})

watch(flat, () => {
  if (!flat.value.length) active.value = 0
})

watch(() => store.open, open => {
  if (open) {
    reset()
    nextTick(() => inputRef.value?.$el?.focus())
  }
})

onMounted(() => window.addEventListener('keydown', onGlobalKeydown))
onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <Dialog :open="store.open" @update:open="value => value ? store.openSearch() : store.closeSearch()">
    <DialogContent class="top-[16%] translate-y-0 gap-0 overflow-hidden p-0 sm:max-w-xl">
      <DialogTitle class="sr-only">全局搜索</DialogTitle>
      <DialogDescription class="sr-only">搜索公开频道、用户和历史对话</DialogDescription>
      <div class="flex items-center gap-2 border-b px-3">
        <IconSearch class="size-4 shrink-0 text-muted-foreground" />
        <Input
          ref="inputRef"
          v-model="query"
          class="h-12 border-0 bg-transparent px-0 text-base shadow-none focus-visible:ring-0"
          placeholder="搜索公开频道、用户、历史对话…"
          @keydown="onKeydown"
        />
        <Spinner v-if="loading" class="size-4 text-muted-foreground" />
        <Kbd v-else class="shrink-0 mr-8">Esc</Kbd>
      </div>

      <div ref="listRef" class="max-h-[min(28rem,60vh)] overflow-y-auto p-2">
        <div v-if="!hasQuery" class="flex flex-col items-center gap-1 px-4 py-10 text-center">
          <IconSearch class="size-7 text-muted-foreground/60" />
          <p class="text-sm font-medium">搜索整个平台</p>
          <p class="text-xs text-muted-foreground">支持公开频道名称/标识、用户、历史对话</p>
        </div>

        <div v-else-if="error" class="px-3 py-10 text-center text-sm text-destructive">{{ error }}</div>
        <div v-else-if="isEmpty" class="px-3 py-10 text-center text-sm text-muted-foreground">
          没有找到“{{ query.trim() }}”相关结果
        </div>

        <template v-else-if="results">
          <section v-for="section in sections" :key="section.label" class="pb-1">
            <p class="px-2 py-1.5 text-[11px] font-medium text-muted-foreground">{{ section.label }}</p>
            <button
              v-for="item in section.items"
              :key="`${item.group}-${item.index}`"
              type="button"
              :data-palette-index="item.index"
              class="flex w-full items-center gap-3 rounded-md px-2 py-2 text-left transition-colors"
              :class="item.index === active ? 'bg-accent text-accent-foreground' : 'hover:bg-accent/60'"
              @mouseenter="active = item.index"
              @click="choose(item)"
            >
              <component :is="item.icon" class="size-4 shrink-0 text-muted-foreground" />
              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm font-medium">{{ item.title }}</span>
                <span v-if="item.subtitle" class="block truncate text-xs text-muted-foreground">{{ item.subtitle }}</span>
              </span>
              <IconCornerDownLeft v-if="item.index === active" class="size-3.5 shrink-0 text-muted-foreground" />
            </button>
          </section>
        </template>
      </div>

      <div class="flex items-center justify-between border-t px-3 py-2 text-[11px] text-muted-foreground">
        <span class="flex items-center gap-3">
          <span class="flex items-center gap-1"><IconArrowUp class="size-3" /><IconArrowDown class="size-3" />选择</span>
          <span class="flex items-center gap-1"><IconCornerDownLeft class="size-3" />打开</span>
        </span>
        <span>Ctrl/⌘ K 打开或关闭</span>
      </div>
    </DialogContent>
  </Dialog>
</template>
