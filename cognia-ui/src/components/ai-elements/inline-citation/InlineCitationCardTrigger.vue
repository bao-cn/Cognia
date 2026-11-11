<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { Button } from '@/components/ui/button'
import { HoverCardTrigger } from '@/components/ui/hover-card'
import { cn } from '@/lib/utils'
import { computed } from 'vue'

const props = defineProps<{
  sources: string[]
  class?: HTMLAttributes['class']
}>()

const displayText = computed(() => {
  const firstSource = props.sources[0]
  if (!firstSource)
    return 'unknown'

  try {
    const hostname = new URL(firstSource).hostname
    const additionalCount = props.sources.length - 1
    return additionalCount > 0 ? `${hostname} +${additionalCount}` : hostname
  }
  catch {
    return 'unknown'
  }
})
</script>

<template>
  <HoverCardTrigger as-child>
    <Button :class="cn('ml-1 h-6 rounded-full px-2 text-xs', props.class)" variant="secondary" size="xs">
      {{ displayText }}
    </Button>
  </HoverCardTrigger>
</template>
