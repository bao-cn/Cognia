<script setup lang="ts">
import type { ElementNode } from '@comark/vue'
import type { Ref } from 'vue'
import { computed, inject } from 'vue'
import {
  CodeBlock,
  CodeBlockActions,
  CodeBlockContent,
  CodeBlockCopyButton,
  CodeBlockHeader,
  CodeBlockLine,
  CodeBlockTitle,
  CodeBlockToken,
  useHighlightedCode,
} from '@/components/ui/code-block'

const props = defineProps<{
  language?: string
  __node?: ElementNode // ['pre', attrs, ['code', attrs, text]]
}>()

const code = computed(() => {
  const child = props.__node?.[2]
  return Array.isArray(child) && typeof child[2] === 'string' ? child[2] : ''
})

// True while the closing fence has not arrived yet.
const openFence = inject<Ref<string | null> | null>(
  'markdown-open-fence',
  null,
)

const incomplete = computed(
  () => openFence?.value?.trimEnd() === code.value.trimEnd(),
)

const lines = useHighlightedCode(code, () => props.language ?? '')
</script>

<template>
  <CodeBlock>
    <CodeBlockHeader>
      <CodeBlockTitle>{{ language || 'text' }}</CodeBlockTitle>
      <CodeBlockActions>
        <CodeBlockCopyButton :value="code" :disabled="incomplete" />
      </CodeBlockActions>
    </CodeBlockHeader>
    <CodeBlockContent>
      <CodeBlockLine v-for="(line, index) in lines" :key="index">
        <CodeBlockToken v-for="(token, i) in line" :key="i" :token="token" />
      </CodeBlockLine>
    </CodeBlockContent>
  </CodeBlock>
</template>
