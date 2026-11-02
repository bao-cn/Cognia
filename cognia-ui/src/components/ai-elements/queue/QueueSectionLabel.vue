<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { IconChevronDown } from '@tabler/icons-vue'
import { cn } from '@/lib/utils'

interface QueueSectionLabelProps {
  count?: number
  label: string
  class?: HTMLAttributes['class']
}

const props = defineProps<QueueSectionLabelProps>()
</script>

<template>
  <span :class="cn('flex items-center gap-2', props.class)">
    <IconChevronDown class="group-data-[state=closed]:-rotate-90 size-4 transition-transform" />
    <slot name="icon" />
    <span>
      {{ props.count }}
      {{ props.label }}
    </span>
  </span>
</template>
