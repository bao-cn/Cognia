<script setup lang="ts">
import { ref } from 'vue'
import { useHighlightedCode } from '@/components/ui/code-block'

const model = defineModel<string>({ required: true })

const props = withDefaults(defineProps<{
  id?: string
  rows?: number
  placeholder?: string
}>(), {
  rows: 12,
  placeholder: '',
})

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const highlightRef = ref<HTMLPreElement | null>(null)
const composing = ref(false)
const lines = useHighlightedCode(() => model.value ?? '', () => 'html')

function onInput(event: Event): void {
  model.value = (event.target as HTMLTextAreaElement).value
}

function syncScroll(event: Event): void {
  const textarea = event.target as HTMLTextAreaElement
  if (!highlightRef.value) return
  highlightRef.value.scrollTop = textarea.scrollTop
  highlightRef.value.scrollLeft = textarea.scrollLeft
}

function onCompositionStart(): void {
  composing.value = true
}

function onCompositionEnd(): void {
  composing.value = false
}
</script>

<template>
  <div
    class="relative overflow-hidden rounded-md border border-input bg-muted/20 transition-[color,box-shadow] focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/50"
  >
    <pre
      v-show="!composing"
      ref="highlightRef"
      aria-hidden="true"
      class="pointer-events-none absolute inset-0 m-0 overflow-hidden whitespace-pre p-3 font-mono text-sm leading-6"
      style="tab-size: 2;"
    ><code><span
      v-for="(line, index) in lines"
      :key="index"
      class="block min-h-6"
    ><span
      v-for="(token, tokenIndex) in line"
      :key="tokenIndex"
      :style="token.htmlStyle"
      class="text-(--shiki-light) dark:text-(--shiki-dark)"
    >{{ token.content }}</span></span></code></pre>
    <textarea
      :id="props.id"
      ref="textareaRef"
      :value="model"
      :rows="props.rows"
      wrap="off"
      spellcheck="false"
      autocomplete="off"
      autocapitalize="off"
      class="relative block w-full resize-y whitespace-pre border-0 bg-transparent p-3 font-mono text-sm leading-6 outline-none"
      :style="composing
        ? 'caret-color: var(--foreground); tab-size: 2;'
        : 'color: transparent; caret-color: var(--foreground); tab-size: 2; -webkit-text-fill-color: transparent;'"
      @input="onInput"
      @scroll="syncScroll"
      @compositionstart="onCompositionStart"
      @compositionend="onCompositionEnd"
    />
    <div
      v-if="!model && props.placeholder"
      class="pointer-events-none absolute left-3 top-3 font-mono text-sm leading-6 text-muted-foreground"
    >
      {{ props.placeholder }}
    </div>
  </div>
</template>
