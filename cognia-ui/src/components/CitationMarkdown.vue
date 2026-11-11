<script setup lang="ts">
import { computed, createVNode, nextTick, onBeforeUnmount, onMounted, ref, render, watch } from 'vue'
import Markdown from './Markdown.vue'
import type { CitationSource } from '@/api/chat'
import WebCitationHover from './WebCitationHover.vue'

const props = defineProps<{
  text: string
  citations?: CitationSource[]
  streaming?: boolean
}>()

const emit = defineEmits<{ cite: [index: number] }>()
const displayText = computed(() => {
  const available = new Set((props.citations ?? []).map(item => item.index))
  return props.text.replace(/\[(\d+)\]/g, (match, value) => {
    const index = Number(value)
    return available.has(index) ? `@@CITE_${index}@@` : match
  })
})
const root = ref<HTMLElement | null>(null)
let scheduled: number | null = null
let observer: MutationObserver | null = null
const webMounts: HTMLElement[] = []

function scheduleProcess() {
  if (scheduled != null)
    window.clearTimeout(scheduled)
  scheduled = window.setTimeout(() => {
    scheduled = null
    void process()
  }, 40)
}

async function process() {
  if (props.streaming || !root.value)
    return
  await nextTick()
  const element = root.value
  if (!element)
    return

  const byIndex = new Map((props.citations ?? []).map(item => [item.index, item]))
  if (!byIndex.size)
    return

  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      const parent = node.parentElement
      if (!parent || parent.closest('pre, code, a, button, script, style'))
        return NodeFilter.FILTER_REJECT
      return /@@CITE_\d+@@/.test(node.nodeValue ?? '') ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT
    },
  })

  const nodes: Text[] = []
  while (walker.nextNode())
    nodes.push(walker.currentNode as Text)

  for (const node of nodes) {
    const value = node.nodeValue ?? ''
    const regex = /@@CITE_(\d+)@@/g
    let match: RegExpExecArray | null
    let cursor = 0
    let changed = false
    const fragment = document.createDocumentFragment()

    while ((match = regex.exec(value))) {
      const index = Number(match[1])
      const source = byIndex.get(index)
      if (!source)
        continue
      changed = true
      if (match.index > cursor)
        fragment.appendChild(document.createTextNode(value.slice(cursor, match.index)))
      const isWeb = source.kind === 'WEB' || (!source.documentId && !!source.url)
      if (isWeb) {
        const mount = document.createElement('span')
        mount.className = 'citation-web-marker'
        mount.dataset.citation = String(index)
        render(createVNode(WebCitationHover, { source }), mount)
        webMounts.push(mount)
        fragment.appendChild(mount)
      } else {
        const button = document.createElement('button')
        button.type = 'button'
        button.className = 'citation-marker'
        button.dataset.citation = String(index)
        button.textContent = `[${index}]`
        fragment.appendChild(button)
      }
      cursor = match.index + match[0].length
    }

    if (!changed)
      continue
    if (cursor < value.length)
      fragment.appendChild(document.createTextNode(value.slice(cursor)))
    node.parentNode?.replaceChild(fragment, node)
  }
}

function onClick(event: MouseEvent) {
  const target = (event.target as HTMLElement | null)?.closest<HTMLElement>('.citation-marker, .citation-web-marker')
  if (!target)
    return
  event.preventDefault()
  const index = Number(target.dataset.citation)
  if (Number.isFinite(index))
    emit('cite', index)
}

function cleanupWebMounts() {
  for (const mount of webMounts.splice(0))
    render(null, mount)
}

watch(() => [props.text, props.streaming, props.citations], () => {
  cleanupWebMounts()
  scheduleProcess()
}, { deep: true })
onMounted(() => {
  if (root.value) {
    observer = new MutationObserver(scheduleProcess)
    observer.observe(root.value, { childList: true, subtree: true, characterData: true })
  }
  scheduleProcess()
})
onBeforeUnmount(() => {
  cleanupWebMounts()
  observer?.disconnect()
  if (scheduled != null)
    window.clearTimeout(scheduled)
})
</script>

<template>
  <div ref="root" class="citation-markdown" @click="onClick">
    <Markdown :text="displayText" :streaming="streaming" />
  </div>
</template>

<style scoped>
:deep(.citation-web-marker) {
  display: inline-flex;
  align-items: center;
  margin-inline: 2px;
  vertical-align: super;
  line-height: 1;
}

:deep(.citation-marker) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-inline: 2px;
  padding: 0 4px;
  border-radius: 4px;
  background: color-mix(in oklab, var(--primary) 12%, transparent);
  color: var(--primary);
  font-size: 0.75em;
  font-weight: 600;
  line-height: 1.4;
  vertical-align: super;
  cursor: pointer;
  transition: background 150ms ease, color 150ms ease, transform 150ms ease;
}

:deep(.citation-marker:hover) {
  background: var(--primary);
  color: var(--primary-foreground);
  transform: translateY(-1px);
}
</style>
