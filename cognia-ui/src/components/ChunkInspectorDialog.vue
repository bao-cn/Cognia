<script setup lang="ts">
import { ref, watch } from 'vue'
import { IconFileText, IconLoader2 } from '@tabler/icons-vue'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { channelApi } from '@/api/channel'
import type { DocumentChunkItem } from '@/types/api'

const props = defineProps<{
  open: boolean
  channelId: string | number | null
  documentId: string | number | null
  documentName?: string
}>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
const loading = ref(false)
const items = ref<DocumentChunkItem[]>([])
const page = ref(1)
const pages = ref(1)
const total = ref(0)

async function load() {
  if (!props.open || !props.channelId || !props.documentId) return
  loading.value = true
  try {
    const res = await channelApi.documentChunks(props.channelId, props.documentId, page.value, 10)
    items.value = res.records
    pages.value = Math.max(res.pages, 1)
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function go(delta: number) {
  page.value = Math.min(Math.max(page.value + delta, 1), pages.value)
  void load()
}

watch(() => [props.open, props.documentId], () => { page.value = 1; void load() }, { immediate: true })
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="flex max-h-[86vh] flex-col sm:max-w-3xl">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2"><IconFileText class="size-4" />分片内容</DialogTitle>
        <DialogDescription>
          {{ documentName || '当前文档' }} · 共 {{ total }} 个分片
        </DialogDescription>
      </DialogHeader>
      <div class="min-h-0 flex-1 overflow-y-auto">
        <div v-if="loading" class="flex h-40 items-center justify-center"><IconLoader2 class="size-5 animate-spin text-muted-foreground" /></div>
        <div v-else-if="!items.length" class="flex h-32 items-center justify-center text-sm text-muted-foreground">暂无分片，可能仍在处理或该文件不参与向量化。</div>
        <div v-else class="flex flex-col gap-3 py-2">
          <div v-for="chunk in items" :key="chunk.id" class="rounded-lg border bg-muted/20 p-3">
            <div class="mb-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              <Badge variant="secondary">#{{ (chunk.chunkIndex ?? 0) + 1 }}</Badge>
              <span v-if="chunk.pageNumber">第 {{ chunk.pageNumber }} 页</span>
              <span v-if="chunk.embeddingModel">{{ chunk.embeddingModel }}</span>
              <span v-if="chunk.embeddingStatus">{{ chunk.embeddingStatus }}</span>
              <span class="ml-auto">{{ chunk.text?.length || 0 }} 字符</span>
            </div>
            <p data-i18n-skip class="max-h-56 overflow-y-auto whitespace-pre-wrap text-sm leading-6">{{ chunk.text }}</p>
          </div>
        </div>
      </div>
      <div class="flex items-center justify-between border-t pt-3 text-xs text-muted-foreground">
        <span>第 {{ page }} / {{ pages }} 页</span>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" :disabled="page <= 1 || loading" @click="go(-1)">上一页</Button>
          <Button variant="outline" size="sm" :disabled="page >= pages || loading" @click="go(1)">下一页</Button>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
