<script setup lang="ts">
import { Markdown } from '@comark/vue'
import { computed, provide } from 'vue'
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
</script>

<template>
  <Suspense>
    <Markdown
    class="typeset typeset-docs"
    :value="text"
    :components="{ pre: MarkdownCodeBlock }"
    :streaming="streaming"
  />
  </Suspense>
</template>
