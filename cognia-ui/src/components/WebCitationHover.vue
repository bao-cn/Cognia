<script setup lang="ts">
import { computed } from 'vue'
import type { CitationSource } from '@/api/chat'
import {
  InlineCitation,
  InlineCitationCard,
  InlineCitationCardBody,
  InlineCitationCardTrigger,
  InlineCitationSource,
} from '@/components/ai-elements/inline-citation'

const props = defineProps<{ source: CitationSource }>()

const host = computed(() => {
  try {
    return new URL(props.source.url ?? '').hostname.replace(/^www\./, '')
  } catch {
    return props.source.title || props.source.url || '网页'
  }
})
</script>

<template>
  <InlineCitation>
    <InlineCitationCard :open-delay="60" :close-delay="120">
      <InlineCitationCardTrigger :sources="props.source.url ? [props.source.url] : []" />
      <InlineCitationCardBody>
        <div class="flex flex-col gap-2 p-3">
          <InlineCitationSource
            :title="props.source.title || host"
            :url="props.source.url"
            :description="props.source.snippet"
          />
        </div>
      </InlineCitationCardBody>
    </InlineCitationCard>
  </InlineCitation>
</template>
