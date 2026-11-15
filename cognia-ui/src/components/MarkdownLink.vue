<script setup lang="ts">
import type { ElementNode } from '@comark/vue'
import { computed } from 'vue'

const props = defineProps<{
  href?: string
  __node?: ElementNode
}>()

const target = computed(() => {
  const attr = (props.__node?.[1] as Record<string, unknown> | undefined)?.href
  return props.href || (typeof attr === 'string' ? attr : '')
})
</script>

<template>
  <a :href="target" target="_blank" rel="noopener noreferrer"><slot /></a>
</template>
