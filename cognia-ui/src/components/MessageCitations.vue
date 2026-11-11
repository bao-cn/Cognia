<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import {
  IconChevronDown,
  IconChevronUp,
  IconExternalLink,
  IconFileText,
  IconLink,
} from '@tabler/icons-vue'
import type { CitationSource } from '@/api/chat'

const props = defineProps<{
  messageId: string
  sources: CitationSource[]
  revealIndex?: number
}>()

const emit = defineEmits<{ preview: [source: CitationSource] }>()

interface CitationGroup {
  key: string
  primary: CitationSource
  indexes: number[]
  sources: CitationSource[]
}

const showAll = ref(false)
const expanded = ref(new Set<string>())

const groups = computed<CitationGroup[]>(() => {
  const map = new Map<string, CitationGroup>()
  for (const source of props.sources) {
    const isWeb = source.kind === 'WEB' || (!source.documentId && !!source.url)
    const key = isWeb ? `web:${source.url}` : `doc:${source.documentId ?? source.index}`
    const group = map.get(key)
    if (group) {
      group.indexes.push(source.index)
      group.sources.push(source)
    } else {
      map.set(key, { key, primary: source, indexes: [source.index], sources: [source] })
    }
  }
  return [...map.values()].sort((a, b) => a.primary.index - b.primary.index)
})

const visibleGroups = computed(() => showAll.value ? groups.value : groups.value.slice(0, 5))
const hasMore = computed(() => groups.value.length > 5)

function isWeb(group: CitationGroup) {
  return group.primary.kind === 'WEB' || (!group.primary.documentId && !!group.primary.url)
}

function title(group: CitationGroup) {
  const source = group.primary
  return source.title || source.fileName || (isWeb(group) ? source.url : `引用 ${source.index}`)
}

function typeLabel(group: CitationGroup) {
  if (isWeb(group))
    return '网页'
  const type = group.primary.documentType || group.primary.fileName?.split('.').pop() || '文档'
  return type.toUpperCase()
}

function elementId(group: CitationGroup) {
  return `citation-${props.messageId}-${group.primary.index}`
}

function toggleSnippet(key: string) {
  const next = new Set(expanded.value)
  if (next.has(key))
    next.delete(key)
  else
    next.add(key)
  expanded.value = next
}

function flashAndScroll(group: CitationGroup) {
  const element = document.getElementById(elementId(group))
  if (!element)
    return
  element.scrollIntoView({ behavior: 'smooth', block: 'center' })
  element.classList.remove('citation-flash')
  void element.offsetWidth
  element.classList.add('citation-flash')
  window.setTimeout(() => element.classList.remove('citation-flash'), 1100)
}

async function reveal(index: number) {
  const group = groups.value.find(item => item.indexes.includes(index))
  if (!group)
    return
  if (groups.value.indexOf(group) >= 5)
    showAll.value = true
  expanded.value = new Set([...expanded.value, group.key])
  await nextTick()
  flashAndScroll(group)
}

watch(() => props.revealIndex, value => {
  if (value != null)
    void reveal(value)
})
</script>

<template>
  <section v-if="groups.length" class="mt-2 text-xs">
    <div class="flex items-center gap-1.5 text-[11px] text-muted-foreground">
      <IconLink class="size-3" />
      <span>引用 {{ groups.length }}</span>
      <button
        v-if="hasMore"
        type="button"
        class="ml-1 inline-flex items-center gap-0.5 rounded px-1 py-0.5 transition-colors hover:bg-muted hover:text-foreground"
        @click="showAll = !showAll"
      >
        {{ showAll ? '收起' : `展开全部（${groups.length}）` }}
        <IconChevronUp v-if="showAll" class="size-3" />
        <IconChevronDown v-else class="size-3" />
      </button>
    </div>

    <TransitionGroup tag="ul" name="citation-list" class="mt-1 divide-y rounded-lg border">
      <li
        v-for="group in visibleGroups"
        :id="elementId(group)"
        :key="group.key"
        class="citation-row transition-colors"
      >
        <div class="flex items-center gap-2 px-2 py-1.5">
          <button
            type="button"
            class="flex min-w-0 flex-1 items-center gap-1.5 text-left"
            @click="toggleSnippet(group.key)"
          >
            <span class="inline-flex size-5 shrink-0 items-center justify-center rounded bg-primary/10 text-[10px] font-semibold text-primary">
              {{ group.primary.index }}
            </span>
            <span class="truncate text-[11px] font-medium">{{ title(group) }}</span>
            <span class="shrink-0 rounded bg-muted px-1 py-0.5 text-[10px] text-muted-foreground">{{ typeLabel(group) }}</span>
            <span v-if="group.indexes.length > 1" class="shrink-0 text-[10px] text-muted-foreground">
              +{{ group.indexes.length - 1 }}
            </span>
          </button>
          <a
            v-if="isWeb(group) && group.primary.url"
            :href="group.primary.url"
            target="_blank"
            rel="noreferrer"
            class="inline-flex size-6 shrink-0 items-center justify-center rounded text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            title="打开网页"
            @click.stop
          >
            <IconExternalLink class="size-3.5" />
          </a>
          <button
            v-else
            type="button"
            class="inline-flex size-6 shrink-0 items-center justify-center rounded text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            title="查看原文"
            @click="emit('preview', group.primary)"
          >
            <IconFileText class="size-3.5" />
          </button>
        </div>
        <div class="citation-snippet" :class="{ open: expanded.has(group.key) }">
          <div class="overflow-hidden">
            <p v-if="group.primary.snippet" data-i18n-skip class="px-8 pb-2 text-[11px] leading-4 text-muted-foreground">
              {{ group.primary.snippet }}
            </p>
          </div>
        </div>
      </li>
    </TransitionGroup>
  </section>
</template>

<style scoped>
.citation-snippet {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.25s ease;
}

.citation-snippet.open {
  grid-template-rows: 1fr;
}

.citation-snippet > div {
  overflow: hidden;
}

.citation-list-enter-active,
.citation-list-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.citation-list-enter-from,
.citation-list-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.citation-list-move {
  transition: transform 0.25s ease;
}

.citation-row.citation-flash {
  animation: citation-flash 1s ease;
}

@keyframes citation-flash {
  0%, 100% { background: transparent; }
  15%, 55% { background: color-mix(in oklab, var(--primary) 14%, transparent); }
}
</style>
