<script lang="ts" setup>
import type { HTMLAttributes } from 'vue'
import { IconBookmark } from '@tabler/icons-vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <slot v-if="$slots.default" />

  <IconBookmark v-else :class="cn('size-4 shrink-0', props.class)" v-bind="$attrs" />
</template>
