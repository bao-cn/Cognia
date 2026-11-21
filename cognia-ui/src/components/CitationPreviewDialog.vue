<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { IconDownload, IconExternalLink, IconFileAlert, IconLoader2 } from '@tabler/icons-vue'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import Markdown from './Markdown.vue'
import { documentApi } from '@/api/document'
import { channelApi } from '@/api/channel'
import type { CitationSource } from '@/api/chat'

const props = defineProps<{
  open: boolean
  source: CitationSource | null
  showIndex?: boolean
}>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

type View = 'loading' | 'markdown' | 'image' | 'pdf' | 'docx' | 'pptx' | 'text' | 'html' | 'web' | 'unsupported' | 'error'

const view = ref<View>('loading')
const error = ref('')
const markdownText = ref('')
const textContent = ref('')
const objectUrl = ref('')
const downloadUrl = ref('')
const docxRoot = ref<HTMLElement | null>(null)
const pptxSlides = ref<{ index: number; texts: string[] }[]>([])
let loadToken = 0
const scriptPromises = new Map<string, Promise<void>>()

const title = computed(() => props.source?.title || props.source?.fileName || '引用原文')
const isWeb = computed(() => props.source?.kind === 'WEB' || (!props.source?.documentId && !!props.source?.url))
const extension = computed(() => {
  const name = props.source?.fileName || ''
  return (name.split('.').pop() || '').toLowerCase()
})

function revoke() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
  if (downloadUrl.value) {
    URL.revokeObjectURL(downloadUrl.value)
    downloadUrl.value = ''
  }
}

function loadScript(src: string) {
  const existing = scriptPromises.get(src)
  if (existing)
    return existing
  const promise = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.onload = () => resolve()
    script.onerror = () => reject(new Error(`加载预览组件失败：${src}`))
    document.head.appendChild(script)
  })
  scriptPromises.set(src, promise)
  return promise
}

async function load() {
  const source = props.source
  if (!props.open || !source)
    return
  const token = ++loadToken
  revoke()
  view.value = 'loading'
  error.value = ''
  markdownText.value = ''
  textContent.value = ''
  try {
    if (isWeb.value) {
      view.value = 'web'
      return
    }
    if (source.documentId == null)
      throw new Error('该引用没有关联原始文档')
    const blob = await documentApi.raw(source.documentId)
    if (token !== loadToken)
      return
    const contentType = blob.type || ''
    const ext = extension.value
    downloadUrl.value = URL.createObjectURL(blob)

    if (ext === 'md' || ext === 'markdown' || contentType.includes('markdown')) {
      if (source.channelId) {
        try {
          markdownText.value = await channelApi.documentMarkdown(String(source.channelId), String(source.documentId))
        } catch {
          markdownText.value = await blob.text()
        }
      } else {
        markdownText.value = await blob.text()
      }
      view.value = 'markdown'
      return
    }
    if (ext === 'pdf' || contentType.includes('pdf')) {
      objectUrl.value = URL.createObjectURL(blob)
      view.value = 'pdf'
      return
    }
    if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp'].includes(ext) || contentType.startsWith('image/')) {
      objectUrl.value = URL.createObjectURL(blob)
      view.value = 'image'
      return
    }
    if (ext === 'pptx' || contentType.includes('presentationml')) {
      await loadScript('/vendor/jszip.min.js')
      const JsZip = (window as any).JSZip
      if (!JsZip) throw new Error('PPTX 预览组件加载失败')
      const zip = await JsZip.loadAsync(blob)
      const names = Object.keys(zip.files)
        .filter(name => /^ppt\/slides\/slide\d+\.xml$/.test(name))
        .sort((a, b) => Number(a.match(/slide(\d+)\.xml/)?.[1] || 0) - Number(b.match(/slide(\d+)\.xml/)?.[1] || 0))
      const parsed: { index: number; texts: string[] }[] = []
      for (const name of names) {
        const xml = await zip.file(name)?.async('text')
        if (!xml) continue
        const doc = new DOMParser().parseFromString(xml, 'application/xml')
        const texts = Array.from(doc.getElementsByTagName('a:t'))
          .map(node => node.textContent?.trim() || '')
          .filter(Boolean)
        parsed.push({ index: Number(name.match(/slide(\d+)\.xml/)?.[1] || parsed.length + 1), texts })
      }
      pptxSlides.value = parsed
      view.value = 'pptx'
      return
    }
    if (ext === 'docx' || contentType.includes('wordprocessingml')) {
      await loadScript('/vendor/jszip.min.js')
      await loadScript('/vendor/docx-preview.min.js')
      const lib = (window as any).docx
      if (!lib)
        throw new Error('DOCX 预览组件加载失败')
      view.value = 'docx'
      await nextTick()
      if (token !== loadToken || !docxRoot.value)
        return
      docxRoot.value.innerHTML = ''
      await lib.renderAsync(blob, docxRoot.value, undefined, { className: 'docx', inWrapper: true })
      return
    }
    if (ext === 'doc') {
      view.value = 'unsupported'
      return
    }
    if (ext === 'html' || ext === 'htm' || contentType.includes('html')) {
      textContent.value = await blob.text()
      view.value = 'html'
      return
    }
    if (['txt', 'csv', 'json', 'xml'].includes(ext) || contentType.startsWith('text/')) {
      textContent.value = await blob.text()
      view.value = 'text'
      return
    }
    view.value = 'unsupported'
  } catch (err: any) {
    if (token !== loadToken)
      return
    error.value = err?.message || '加载原文失败'
    view.value = 'error'
  }
}

function close() {
  emit('update:open', false)
}

watch(() => [props.open, props.source], load, { immediate: true })
onBeforeUnmount(() => {
  loadToken++
  revoke()
})
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="flex h-[86vh] max-w-[min(1100px,94vw)] flex-col gap-0 overflow-hidden p-0 sm:max-w-[min(1100px,94vw)]">
      <DialogHeader class="border-b px-5 py-4">
        <DialogTitle class="flex items-center gap-2 text-base">
          <span v-if="showIndex !== false" class="inline-flex size-6 items-center justify-center rounded bg-primary/10 text-xs font-semibold text-primary">
            {{ source?.index }}
          </span>
          <span class="truncate">{{ title }}</span>
        </DialogTitle>
        <DialogDescription class="flex flex-wrap items-center gap-2 text-xs">
          <span v-if="isWeb && source?.url">{{ source.url }}</span>
          <span v-else-if="source?.channelName">频道：{{ source.channelName }}</span>
          <span v-if="source?.pageNumber">第 {{ source.pageNumber }} 页</span>
          <span v-if="source?.chunkIndex != null">片段 {{ source.chunkIndex + 1 }}</span>
          <a v-if="downloadUrl" :href="downloadUrl" :download="source?.fileName || 'document'">
            <Button variant="ghost" size="sm" class="h-7 px-2 text-xs">
              <IconDownload class="mr-1 size-3.5" />下载
            </Button>
          </a>
        </DialogDescription>
      </DialogHeader>

      <div class="min-h-0 flex-1 overflow-auto bg-muted/20">
        <div v-if="view === 'loading'" class="flex h-full items-center justify-center">
          <IconLoader2 class="size-6 animate-spin text-muted-foreground" />
        </div>
        <div v-else-if="view === 'error'" class="flex h-full flex-col items-center justify-center gap-2 p-6 text-sm text-muted-foreground">
          <IconFileAlert class="size-8" />
          <p>{{ error }}</p>
          <Button variant="outline" size="sm" @click="load">重试</Button>
        </div>
        <div v-else-if="view === 'markdown'" class="p-8">
          <Markdown :text="markdownText" />
        </div>
        <div v-else-if="view === 'pdf'" class="h-full">
          <iframe :src="objectUrl" class="h-full w-full border-0" title="PDF 预览" />
        </div>
        <div v-else-if="view === 'image'" class="flex h-full items-center justify-center p-6">
          <img :src="objectUrl" alt="引用图片" class="max-h-full max-w-full rounded-lg object-contain shadow-sm">
        </div>
        <div v-else-if="view === 'docx'" ref="docxRoot" class="docx-preview-host bg-white p-6" />
        <div v-else-if="view === 'pptx'" class="mx-auto flex max-w-4xl flex-col gap-4 p-6">
          <p class="text-xs text-muted-foreground">PPTX 在线预览展示每页文字内容，复杂版式请下载原文查看。</p>
          <div v-for="slide in pptxSlides" :key="slide.index" class="rounded-lg border bg-white p-5 shadow-sm">
            <div class="mb-3 text-xs font-medium text-slate-500">第 {{ slide.index }} 页</div>
            <div v-if="slide.texts.length" class="space-y-1.5 text-sm leading-6 text-slate-800">
              <p v-for="(line, i) in slide.texts" :key="i">{{ line }}</p>
            </div>
            <p v-else class="text-sm text-slate-400">本页无文字内容</p>
          </div>
        </div>
        <pre v-else-if="view === 'text'" class="whitespace-pre-wrap p-8 text-sm leading-6">{{ textContent }}</pre>
        <iframe
          v-else-if="view === 'html'"
          :srcdoc="textContent"
          class="h-full w-full border-0 bg-white"
          sandbox=""
          title="HTML 预览"
        />
        <div v-else-if="view === 'web'" class="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
          <IconExternalLink class="size-8 text-muted-foreground" />
          <p class="text-sm font-medium">{{ title }}</p>
          <p data-i18n-skip class="max-w-xl text-xs text-muted-foreground">{{ source?.snippet || '网页引用暂无摘要。' }}</p>
          <a v-if="source?.url" :href="source.url" target="_blank" rel="noreferrer">
            <Button variant="outline" size="sm"><IconExternalLink class="mr-1 size-4" />打开网页</Button>
          </a>
        </div>
        <div v-else class="flex h-full flex-col items-center justify-center gap-3 p-6 text-sm text-muted-foreground">
          <IconFileAlert class="size-8" />
          <p>该格式暂不支持在线预览，可下载后查看。</p>
          <a v-if="downloadUrl" :href="downloadUrl" :download="source?.fileName || 'document'">
            <Button variant="outline" size="sm"><IconDownload class="mr-1 size-4" />下载原文</Button>
          </a>
          <Button variant="ghost" size="sm" @click="close">关闭</Button>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>

<style scoped>
.docx-preview-host :deep(.docx-wrapper) {
  background: transparent;
  padding: 0;
}
</style>
