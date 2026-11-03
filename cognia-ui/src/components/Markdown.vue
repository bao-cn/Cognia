<script setup lang="ts">
import { Markdown } from '@comark/vue'
import { computed, provide, ref, watch } from 'vue'
import MarkdownCodeBlock from './MarkdownCodeBlock.vue'

const props = defineProps<{
  text: string
  streaming?: boolean
}>()

const openFence = computed(() => {
  if (!props.streaming)
    return null

  const lines = props.text.trim().split('\n')
  const fences = lines.flatMap((line, index) =>
    line.startsWith('```') ? [index] : [],
  )

  return fences.length % 2
    ? lines.slice(fences.at(-1)! + 1).join('\n')
    : null
})

provide('markdown-open-fence', openFence)

/**
 * While streaming, keep the last part of the answer out of the Markdown
 * renderer and render it as tiny character chunks. Every new chunk gets its
 * own left-to-right clip-path reveal, so only the freshly received text
 * fades in instead of the whole message re-fading on every SSE delta.
 *
 * The stable prefix advances only at line breaks, which keeps the Markdown
 * output stable and avoids replaying the tail on every token.
 */
const splitEnabled = computed(() => Boolean(props.streaming) && !openFence.value)

const stableEnd = computed(() => {
  if (!splitEnabled.value)
    return props.text.length

  const limit = props.text.length - 72
  if (limit < 96)
    return 0

  const boundary = props.text.lastIndexOf('\n', limit)
  return boundary > 0 ? boundary + 1 : 0
})

const stable = computed(() => props.text.slice(0, stableEnd.value))
const tail = computed(() => props.text.slice(stableEnd.value))

interface StreamChunk {
  id: number
  text: string
  delay: number
}

const chunks = ref<StreamChunk[]>([])
let chunkId = 0
let previousTail = ''

watch(tail, (value) => {
  if (!splitEnabled.value) {
    chunks.value = []
    previousTail = ''
    return
  }

  if (!value.startsWith(previousTail)) {
    chunks.value = []
    previousTail = ''
  }

  const added = value.slice(previousTail.length)
  previousTail = value
  if (!added)
    return

  const parts: string[] = []
  let buffer = ''
  for (const character of Array.from(added)) {
    buffer += character
    if (buffer.length >= 3 || /[\s，。！？、,.!?;；:：]/.test(character)) {
      parts.push(buffer)
      buffer = ''
    }
  }
  if (buffer)
    parts.push(buffer)

  parts.forEach((part, index) => {
    chunks.value.push({ id: ++chunkId, text: part, delay: index * 45 })
  })
}, { immediate: true })
</script>

<template>
  <div class="stream-fade">
    <Suspense v-if="stable">
      <Markdown
        class="typeset typeset-docs"
        :value="stable"
        :components="{ pre: MarkdownCodeBlock }"
        :streaming="streaming"
      />
    </Suspense>
    <span v-if="splitEnabled && tail" class="stream-tail">
      <span
        v-for="chunk in chunks"
        :key="chunk.id"
        class="stream-chunk"
        :style="{ animationDelay: `${chunk.delay}ms` }"
      >{{ chunk.text }}</span>
    </span>
  </div>
</template>

<style scoped>
.stream-tail {
  white-space: pre-wrap;
}

.stream-chunk {
  display: inline-block;
  white-space: pre-wrap;
  animation: stream-chunk-in 260ms cubic-bezier(0.22, 1, 0.36, 1) both;
  will-change: opacity, transform, clip-path;
}

@keyframes stream-chunk-in {
  from {
    opacity: 0;
    transform: translateX(-3px);
    clip-path: inset(0 100% 0 0);
  }
  to {
    opacity: 1;
    transform: translateX(0);
    clip-path: inset(0 0 0 0);
  }
}
</style>
