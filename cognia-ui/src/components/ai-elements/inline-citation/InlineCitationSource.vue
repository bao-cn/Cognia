<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  title?: string
  url?: string
  description?: string
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <div :class="cn('space-y-1', props.class)">
    <h4 v-if="title" class="truncate font-medium text-sm leading-tight">
      {{ title }}
    </h4>
    <a
      v-if="url"
      :href="url"
      target="_blank"
      rel="noreferrer"
      class="block truncate break-all text-xs text-muted-foreground underline-offset-4 hover:underline"
    >
      {{ url }}
      <span class="sr-only"> (opens in a new tab)</span>
    </a>
    <p v-if="description" class="line-clamp-3 text-muted-foreground text-sm leading-relaxed">
      {{ description }}
    </p>
    <slot />
  </div>
</template>
