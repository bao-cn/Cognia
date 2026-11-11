<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import {
  IconActivity,
  IconAlertTriangle,
  IconBrain,
  IconCheck,
  IconCopy,
  IconDatabaseSearch,
  IconFileText,
  IconJson,
  IconLoader2,
  IconPlayerPlay,
  IconPrompt,
  IconRefresh,
  IconSearch,
  IconTool,
  IconX,
} from '@tabler/icons-vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { useDebugStore } from '@/stores/debug'
import { traceApi } from '@/api'
import type { ChatTraceDetail, ChatTraceSummary, TraceReplayResult } from '@/types/api'
import Markdown from '@/components/Markdown.vue'

const debugStore = useDebugStore()

const view = ref<'list' | 'detail'>('list')
const records = ref<ChatTraceSummary[]>([])
const listLoading = ref(false)
const listError = ref('')
const keyword = ref('')
const appliedKeyword = ref('')
const status = ref('ALL')
const appliedStatus = ref('ALL')
const page = ref(1)
const pageSize = 20
const total = ref(0)
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const detail = ref<ChatTraceDetail | null>(null)
const loading = ref(false)
const error = ref('')
const activeTab = ref('overview')
const replaying = ref<'retrieval' | 'llm' | null>(null)
const replayResult = ref<TraceReplayResult | null>(null)

const trace = computed<Record<string, any>>(() => detail.value?.trace ?? {})
const meta = computed<Record<string, any>>(() => trace.value.meta ?? {})
const input = computed<Record<string, any>>(() => trace.value.input ?? {})
const retrieval = computed<Record<string, any>>(() => trace.value.retrieval ?? {})
const prompt = computed<Record<string, any>>(() => trace.value.prompt ?? {})
const llm = computed<Record<string, any>>(() => trace.value.llm ?? {})
const tools = computed<Record<string, any>>(() => trace.value.tools ?? {})
const output = computed<Record<string, any>>(() => trace.value.output ?? {})
const cache = computed<Record<string, any>>(() => trace.value.cache ?? {})
const timing = computed<Record<string, any>>(() => trace.value.timing ?? {})
const stages = computed<any[]>(() => Array.isArray(trace.value.stages) ? trace.value.stages : [])
const candidates = computed<any[]>(() => Array.isArray(retrieval.value.candidates) ? retrieval.value.candidates : [])
const toolCalls = computed<any[]>(() => Array.isArray(tools.value.calls) ? tools.value.calls : [])
const chunks = computed<any[]>(() => Array.isArray(llm.value.chunks) ? llm.value.chunks : [])
const messages = computed<any[]>(() => Array.isArray(prompt.value.messages) ? prompt.value.messages : [])
const tokenStats = computed<Record<string, any>>(() => prompt.value.tokenStats ?? {})
const windowInfo = computed<Record<string, any>>(() => prompt.value.window ?? {})
const hasError = computed(() => Boolean(trace.value.error?.message) || detail.value?.status === 'FAILED')

const tabs = [
  { value: 'overview', label: '概览', icon: IconActivity },
  { value: 'input', label: '输入', icon: IconFileText },
  { value: 'retrieval', label: '检索', icon: IconDatabaseSearch },
  { value: 'prompt', label: 'Prompt', icon: IconPrompt },
  { value: 'tools', label: '工具', icon: IconTool },
  { value: 'llm', label: 'LLM', icon: IconBrain },
  { value: 'output', label: '输出', icon: IconCheck },
  { value: 'timeline', label: '时序', icon: IconActivity },
]

const totalMs = computed(() => Number(detail.value?.durationMs ?? timing.value.totalMs ?? 0))
const maxStageMs = computed(() => Math.max(1, ...stages.value.map(stage => Number(stage.durationMs ?? 0))))

function pretty(value: any) {
  try {
    return JSON.stringify(value ?? null, null, 2)
  } catch {
    return String(value)
  }
}

function stageWidth(stage: any) {
  return `${Math.max(2, (Number(stage.durationMs ?? 0) / maxStageMs.value) * 100)}%`
}

function statusVariant(status?: string) {
  if (status === 'SUCCESS') return 'default'
  if (status === 'TRUNCATED') return 'outline'
  return 'destructive'
}

async function copy(value: string | any, label = '已复制') {
  const text = typeof value === 'string' ? value : pretty(value)
  try {
    await navigator.clipboard.writeText(text)
    toast.success(label)
  } catch {
    toast.error('复制失败，请手动选择复制')
  }
}

async function loadList() {
  listLoading.value = true
  listError.value = ''
  try {
    const result = await traceApi.list({
      page: page.value,
      size: pageSize,
      status: appliedStatus.value === 'ALL' ? undefined : appliedStatus.value,
      keyword: appliedKeyword.value.trim() || undefined,
    })
    records.value = result.records
    total.value = result.total
  } catch (err: any) {
    listError.value = err?.message || '链路列表加载失败'
  } finally {
    listLoading.value = false
  }
}

function applyFilter() {
  appliedKeyword.value = keyword.value
  appliedStatus.value = status.value
  page.value = 1
  void loadList()
}

function resetFilter() {
  keyword.value = ''
  status.value = 'ALL'
  applyFilter()
}

function goPage(next: number) {
  if (next < 1 || next > pages.value) return
  page.value = next
  void loadList()
}

function openDetail(traceId: string) {
  debugStore.traceId = traceId
  view.value = 'detail'
  activeTab.value = 'overview'
  replayResult.value = null
  void load()
}

function backToList() {
  view.value = 'list'
  debugStore.traceId = null
  void loadList()
}

async function load() {
  const id = debugStore.traceId
  if (!id) return
  loading.value = true
  error.value = ''
  try {
    detail.value = await traceApi.detail(id)
  } catch (err: any) {
    error.value = err?.message || '链路加载失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function replay(stage: 'retrieval' | 'llm') {
  const id = debugStore.traceId
  if (!id) return
  replaying.value = stage
  replayResult.value = null
  try {
    replayResult.value = await traceApi.replay(id, stage)
    toast.success(replayResult.value.message || '重放完成')
  } catch (err: any) {
    toast.error(err?.message || '重放失败')
  } finally {
    replaying.value = null
  }
}

watch(() => [debugStore.drawerOpen, debugStore.traceId], () => {
  if (!debugStore.drawerOpen) return
  if (debugStore.traceId) {
    view.value = 'detail'
    replayResult.value = null
    void load()
  } else {
    view.value = 'list'
    void loadList()
  }
})

watch(() => debugStore.drawerOpen, (value) => {
  if (value) activeTab.value = 'overview'
})
</script>

<template>
  <Sheet v-model:open="debugStore.drawerOpen">
    <SheetContent side="right" class="w-full gap-0 border-l p-0 sm:max-w-[880px]">
      <SheetHeader class="border-b px-5 py-4">
        <div class="flex flex-wrap items-start justify-between gap-2">
          <div class="flex min-w-0 flex-col gap-1">
            <SheetTitle class="flex items-center gap-2 text-base">
              <IconActivity class="size-4 text-muted-foreground" />
              开发链路
              <Badge v-if="detail" :variant="statusVariant(detail.status)" class="h-5 px-2 text-[11px]">
                {{ detail.status }}
              </Badge>
            </SheetTitle>
            <SheetDescription v-if="view === 'list'" class="truncate text-xs">
              管理员 {{ debugStore.hardcodedUsername }} 的全部对话 · {{ total }} 条
            </SheetDescription>
            <SheetDescription v-else class="truncate text-xs">
              {{ detail?.traceId || debugStore.traceId }}
              <span v-if="detail?.modelName"> · {{ detail.modelName }}</span>
              <span v-if="meta.protocol"> · {{ meta.protocol }}</span>
              <span v-if="totalMs"> · {{ totalMs }} ms</span>
            </SheetDescription>
          </div>
          <div class="flex items-center gap-1">
            <Button v-if="view === 'detail'" variant="ghost" size="sm" class="h-8" @click="backToList">
              返回列表
            </Button>
            <Button variant="ghost" size="icon" class="size-8" @click="view === 'list' ? loadList() : load()">
              <IconRefresh class="size-4" />
            </Button>
            <Button variant="ghost" size="icon" class="size-8" @click="debugStore.close()">
              <IconX class="size-4" />
            </Button>
          </div>
        </div>
        <div v-if="view === 'detail'" class="mt-2 flex flex-wrap items-center gap-3">
          <Button variant="outline" size="sm" :disabled="!detail" @click="copy(trace, '已复制完整 trace')">
            <IconJson class="mr-1 size-4" />复制 trace JSON
          </Button>
          <div class="flex items-center gap-2">
            <Switch :model-value="debugStore.fullRaw" @update:model-value="debugStore.fullRaw = Boolean($event)" />
            <Label class="text-xs text-muted-foreground">完整 raw 模式</Label>
          </div>
        </div>
      </SheetHeader>

      <div v-if="view === 'detail' && hasError" class="flex items-start gap-2 border-b bg-destructive/10 px-5 py-3 text-sm text-destructive">
        <IconAlertTriangle class="mt-0.5 size-4 shrink-0" />
        <span class="break-all">{{ trace.error?.message || detail?.status }}</span>
      </div>


      <!-- 列表：所有记录 -->
      <div v-if="view === 'list'" class="flex min-h-0 flex-1 flex-col">
        <div class="flex flex-wrap items-center gap-2 border-b px-4 py-3">
          <div class="relative w-56">
            <IconSearch class="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
            <input
              v-model="keyword"
              class="h-8 w-full rounded-md border bg-background pl-8 pr-2 text-xs outline-none focus:ring-1 focus:ring-ring"
              placeholder="搜索问题 / trace ID"
              @keyup.enter="applyFilter"
            >
          </div>
          <Select v-model="status">
            <SelectTrigger class="h-8 w-32 text-xs">
              <SelectValue placeholder="全部状态" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">全部状态</SelectItem>
              <SelectItem value="SUCCESS">成功</SelectItem>
              <SelectItem value="FAILED">失败</SelectItem>
              <SelectItem value="TRUNCATED">已截断</SelectItem>
            </SelectContent>
          </Select>
          <Button size="sm" variant="outline" class="h-8" @click="applyFilter">查询</Button>
          <Button size="sm" variant="ghost" class="h-8" @click="resetFilter">重置</Button>
          <span class="ml-auto text-xs text-muted-foreground">共 {{ total }} 条</span>
        </div>

        <div v-if="listLoading" class="flex h-52 items-center justify-center">
          <Spinner class="size-6" />
        </div>
        <div v-else-if="listError" class="p-4 text-sm text-destructive">{{ listError }}</div>
        <div v-else class="min-h-0 flex-1 overflow-y-auto">
          <button
            v-for="record in records"
            :key="record.traceId"
            class="flex w-full flex-col gap-1 border-b px-4 py-3 text-left transition-colors hover:bg-muted/50"
            @click="openDetail(record.traceId)"
          >
            <div class="flex items-center gap-2">
              <Badge :variant="statusVariant(record.status)" class="h-5 shrink-0 px-1.5 text-[10px]">
                {{ record.status }}
              </Badge>
              <span class="min-w-0 flex-1 truncate text-sm">{{ record.question || '（空问题）' }}</span>
              <span class="shrink-0 text-[11px] text-muted-foreground">{{ record.durationMs ?? 0 }} ms</span>
            </div>
            <div class="flex items-center gap-2 text-[11px] text-muted-foreground">
              <span class="truncate font-mono">{{ record.traceId }}</span>
              <span v-if="record.modelName">· {{ record.modelName }}</span>
              <span v-if="record.protocol">· {{ record.protocol }}</span>
              <span class="ml-auto shrink-0">{{ record.createTime }}</span>
            </div>
          </button>
          <div v-if="records.length === 0" class="flex h-40 items-center justify-center text-sm text-muted-foreground">
            暂无记录。开发模式下，管理员 {{ debugStore.hardcodedUsername }} 的对话会自动记录。
          </div>
        </div>

        <div class="flex items-center justify-between border-t px-4 py-2 text-xs text-muted-foreground">
          <span>{{ page }} / {{ pages }}</span>
          <div class="flex items-center gap-1">
            <Button variant="outline" size="sm" class="h-7" :disabled="page <= 1" @click="goPage(page - 1)">上一页</Button>
            <Button variant="outline" size="sm" class="h-7" :disabled="page >= pages" @click="goPage(page + 1)">下一页</Button>
          </div>
        </div>
      </div>
      <div v-else-if="loading" class="flex h-64 items-center justify-center">
        <Spinner class="size-6" />
      </div>
      <div v-else-if="error" class="p-5 text-sm text-destructive">{{ error }}</div>

      <template v-else-if="detail">
        <div class="flex flex-wrap gap-1 border-b bg-muted/40 px-3 py-2">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            class="flex items-center gap-1 rounded-md px-2.5 py-1.5 text-xs transition-colors"
            :class="activeTab === tab.value ? 'bg-background font-medium shadow-sm' : 'text-muted-foreground hover:text-foreground'"
            @click="activeTab = tab.value"
          >
            <component :is="tab.icon" class="size-3.5" />{{ tab.label }}
          </button>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <!-- 概览 -->
          <div v-if="activeTab === 'overview'" class="flex flex-col gap-4">
            <section class="rounded-xl border bg-card">
              <header class="border-b px-4 py-2.5 text-sm font-semibold">会话基础信息</header>
              <dl class="grid grid-cols-2 gap-3 p-4 text-xs lg:grid-cols-3">
                <div><dt class="text-muted-foreground">Trace ID</dt><dd class="break-all font-mono">{{ detail.traceId }}</dd></div>
                <div><dt class="text-muted-foreground">会话 ID</dt><dd class="break-all font-mono">{{ detail.conversationId || '临时/新会话' }}</dd></div>
                <div><dt class="text-muted-foreground">用户 ID</dt><dd>{{ detail.userId }}</dd></div>
                <div><dt class="text-muted-foreground">模型</dt><dd>{{ detail.modelName || '—' }}</dd></div>
                <div><dt class="text-muted-foreground">协议</dt><dd>{{ meta.protocol || '—' }}</dd></div>
                <div><dt class="text-muted-foreground">时间</dt><dd>{{ detail.createTime }}</dd></div>
                <div><dt class="text-muted-foreground">总耗时</dt><dd>{{ totalMs }} ms</dd></div>
                <div><dt class="text-muted-foreground">首字耗时</dt><dd>{{ timing.firstTokenMs ?? '—' }} ms</dd></div>
                <div><dt class="text-muted-foreground">思考 / 工具 / RAG</dt><dd>{{ meta.thinking ? '思考' : '直答' }} · {{ tools.count ? '工具' : '无工具' }} · {{ meta.rag ? 'RAG' : '无检索' }}</dd></div>
                <div><dt class="text-muted-foreground">上下文窗口</dt><dd>{{ meta.contextWindow ?? '—' }}</dd></div>
                <div><dt class="text-muted-foreground">最大输出</dt><dd>{{ meta.maxOutputTokens ?? '—' }}</dd></div>
                <div><dt class="text-muted-foreground">知识库</dt><dd>{{ (meta.channels || []).join(', ') || '—' }}</dd></div>
                <div><dt class="text-muted-foreground">RAG 引擎</dt><dd>{{ meta.ragEngineVersion || '—' }}</dd></div>
                <div><dt class="text-muted-foreground">Prompt 版本</dt><dd>{{ meta.promptVersion || '—' }}</dd></div>
                <div><dt class="text-muted-foreground">向量库版本</dt><dd>{{ meta.vectorVersion || '—' }}</dd></div>
              </dl>
            </section>
            <section class="rounded-xl border bg-card">
              <header class="flex items-center justify-between border-b px-4 py-2.5">
                <span class="text-sm font-semibold">缓存与路由</span>
                <Badge variant="outline" class="h-5 px-2 text-[11px]">
                  {{ cache.hitType || 'NONE' }}
                </Badge>
              </header>
              <div class="grid grid-cols-2 gap-3 p-4 text-xs lg:grid-cols-3">
                <div><dt class="text-muted-foreground">语义缓存</dt><dd>{{ cache.semanticEnabled ? '开启' : '关闭' }}</dd></div>
                <div><dt class="text-muted-foreground">检索缓存</dt><dd>{{ cache.retrievalEnabled ? '开启' : '关闭' }}</dd></div>
                <div><dt class="text-muted-foreground">相似度</dt><dd>{{ cache.similarity ?? '—' }}</dd></div>
                <div class="col-span-2"><dt class="text-muted-foreground">缓存 key</dt><dd class="break-all font-mono">{{ cache.cacheKey || '—' }}</dd></div>
              </div>
            </section>
            <section class="rounded-xl border bg-card">
              <header class="border-b px-4 py-2.5 text-sm font-semibold">用户问题</header>
              <p class="whitespace-pre-wrap p-4 text-sm">{{ detail.question || '—' }}</p>
            </section>
          </div>

          <!-- 输入 -->
          <div v-else-if="activeTab === 'input'" class="flex flex-col gap-4">
            <section class="rounded-xl border bg-card">
              <header class="flex items-center justify-between border-b px-4 py-2.5">
                <span class="text-sm font-semibold">Query 链</span>
                <Button variant="ghost" size="sm" @click="copy(input, '已复制输入链路')"><IconCopy class="mr-1 size-3.5" />复制</Button>
              </header>
              <div class="flex flex-col gap-3 p-4 text-xs">
                <div><p class="text-muted-foreground">原始 query</p><p class="mt-1 whitespace-pre-wrap text-sm">{{ input.rawQuery || '—' }}</p></div>
                <div><p class="text-muted-foreground">最终检索 query</p><p class="mt-1 whitespace-pre-wrap text-sm">{{ input.finalQuery || '—' }}</p></div>
                <div class="grid grid-cols-2 gap-3">
                  <div><p class="text-muted-foreground">改写</p><p>{{ input.rewrite?.enabled ? '已启用' : '未启用' }}（{{ input.rewrite?.strategy || 'none' }}）</p></div>
                  <div><p class="text-muted-foreground">输入校验</p><p>附件 {{ input.validation?.attachments ?? 0 }} · 长度 {{ input.validation?.length ?? 0 }}</p></div>
                </div>
              </div>
            </section>
          </div>

          <!-- 检索 -->
          <div v-else-if="activeTab === 'retrieval'" class="flex flex-col gap-4">
            <section class="rounded-xl border bg-card">
              <header class="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-2.5">
                <span class="text-sm font-semibold">检索参数（{{ retrieval.hitType || 'NONE' }}）</span>
                <Button variant="outline" size="sm" :disabled="replaying !== null" @click="replay('retrieval')">
                  <IconLoader2 v-if="replaying === 'retrieval'" class="mr-1 size-3.5 animate-spin" />
                  <IconPlayerPlay v-else class="mr-1 size-3.5" />重跑检索
                </Button>
              </header>
              <div class="grid grid-cols-2 gap-3 p-4 text-xs lg:grid-cols-4">
                <div><p class="text-muted-foreground">策略</p><p>{{ retrieval.strategy || '—' }}</p></div>
                <div><p class="text-muted-foreground">Top K / Top N</p><p>{{ retrieval.topK ?? '—' }} / {{ retrieval.topN ?? '—' }}</p></div>
                <div><p class="text-muted-foreground">重排</p><p>{{ retrieval.rerankEnabled ? '开启' : '关闭' }}</p></div>
                <div><p class="text-muted-foreground">召回数量</p><p>{{ retrieval.resultCount ?? 0 }}</p></div>
                <div><p class="text-muted-foreground">向量维度</p><p>{{ retrieval.vectorDimension ?? '—' }}</p></div>
                <div><p class="text-muted-foreground">知识库</p><p>{{ (retrieval.channels || []).join(', ') || '—' }}</p></div>
              </div>
            </section>

            <section class="rounded-xl border bg-card">
              <header class="flex items-center justify-between border-b px-4 py-2.5">
                <span class="text-sm font-semibold">召回片段（{{ candidates.length }}）</span>
                <Button variant="ghost" size="sm" @click="copy(candidates, '已复制召回结果')"><IconCopy class="mr-1 size-3.5" />复制</Button>
              </header>
              <div v-if="!candidates.length" class="p-4 text-xs text-muted-foreground">本次未召回任何片段</div>
              <div v-else class="flex flex-col divide-y">
                <div v-for="(candidate, index) in candidates" :key="index" class="flex flex-col gap-1 px-4 py-3">
                  <div class="flex flex-wrap items-center gap-2 text-xs">
                    <Badge variant="outline" class="h-5 px-2 text-[11px]">doc {{ candidate.documentId }}</Badge>
                    <span class="font-medium">{{ candidate.fileName || candidate.title }}</span>
                    <span class="text-muted-foreground">score {{ candidate.score }}</span>
                    <span v-if="candidate.expired" class="text-destructive">已过期</span>
                  </div>
                  <p class="line-clamp-3 whitespace-pre-wrap text-xs text-muted-foreground">{{ candidate.text }}</p>
                </div>
              </div>
            </section>

            <section v-if="replayResult" class="rounded-xl border border-primary/30 bg-primary/5">
              <header class="flex items-center justify-between border-b px-4 py-2.5">
                <span class="text-sm font-semibold">重跑结果 · {{ replayResult.durationMs }} ms</span>
                <Button variant="ghost" size="sm" @click="copy(replayResult.result, '已复制重跑结果')"><IconCopy class="mr-1 size-3.5" />复制</Button>
              </header>
              <pre class="max-h-64 overflow-auto p-4 text-[11px] leading-relaxed">{{ pretty(replayResult.result) }}</pre>
            </section>
          </div>

          <!-- Prompt -->
          <div v-else-if="activeTab === 'prompt'" class="flex flex-col gap-4">
            <section class="rounded-xl border bg-card">
              <header class="flex items-center justify-between border-b px-4 py-2.5">
                <span class="text-sm font-semibold">Token 统计</span>
              </header>
              <div class="grid grid-cols-2 gap-3 p-4 text-xs lg:grid-cols-4">
                <div><p class="text-muted-foreground">System</p><p>{{ tokenStats.system ?? '—' }}</p></div>
                <div><p class="text-muted-foreground">历史</p><p>{{ tokenStats.history ?? '—' }}</p></div>
                <div><p class="text-muted-foreground">用户</p><p>{{ tokenStats.user ?? '—' }}</p></div>
                <div><p class="text-muted-foreground">预留输出</p><p>{{ tokenStats.reservedOutput ?? '—' }}</p></div>
                <div><p class="text-muted-foreground">已用 / 上限</p><p>{{ tokenStats.used ?? '—' }} / {{ tokenStats.max ?? '—' }}</p></div>
                <div><p class="text-muted-foreground">压缩阈值</p><p>{{ windowInfo.threshold ?? '—' }}%</p></div>
                <div><p class="text-muted-foreground">已压缩</p><p>{{ windowInfo.compressed ? '是' : '否' }}</p></div>
                <div><p class="text-muted-foreground">丢弃消息</p><p>{{ windowInfo.droppedMessages ?? 0 }}</p></div>
              </div>
            </section>

            <section class="rounded-xl border bg-card">
              <header class="flex items-center justify-between border-b px-4 py-2.5">
                <span class="text-sm font-semibold">System Prompt 模板</span>
                <Button variant="ghost" size="sm" @click="copy(prompt.systemPrompt || '', '已复制 System Prompt')"><IconCopy class="mr-1 size-3.5" />复制</Button>
              </header>
              <pre class="max-h-64 overflow-auto whitespace-pre-wrap p-4 text-[11px] leading-relaxed">{{ prompt.systemPrompt || '—' }}</pre>
            </section>

            <section class="rounded-xl border bg-card">
              <header class="flex items-center justify-between border-b px-4 py-2.5">
                <span class="text-sm font-semibold">最终消息（{{ messages.length }}）</span>
                <Button variant="ghost" size="sm" @click="copy(messages, '已复制最终 prompt')"><IconCopy class="mr-1 size-3.5" />复制 Prompt</Button>
              </header>
              <pre
                class="overflow-auto p-4 text-[11px] leading-relaxed"
                :class="debugStore.fullRaw ? 'max-h-[520px]' : 'max-h-40'"
              >{{ pretty(messages) }}</pre>
            </section>

            <section v-if="debugStore.fullRaw" class="rounded-xl border bg-card">
              <header class="border-b px-4 py-2.5 text-sm font-semibold">压缩提示词</header>
              <pre class="max-h-48 overflow-auto whitespace-pre-wrap p-4 text-[11px]">{{ prompt.compressionPrompt || '—' }}</pre>
            </section>
          </div>

          <!-- 工具 -->
          <div v-else-if="activeTab === 'tools'" class="flex flex-col gap-4">
            <section class="rounded-xl border bg-card">
              <header class="flex items-center justify-between border-b px-4 py-2.5">
                <span class="text-sm font-semibold">工具调用（{{ toolCalls.length }}）</span>
                <Button variant="ghost" size="sm" @click="copy(toolCalls, '已复制工具调用')"><IconCopy class="mr-1 size-3.5" />复制</Button>
              </header>
              <div v-if="!toolCalls.length" class="p-4 text-xs text-muted-foreground">本次未触发工具调用</div>
              <div v-else class="flex flex-col divide-y">
                <div v-for="(call, index) in toolCalls" :key="index" class="flex flex-col gap-2 px-4 py-3">
                  <div class="flex flex-wrap items-center gap-2 text-xs">
                    <Badge class="h-5 px-2 text-[11px]">第 {{ call.round }} 轮</Badge>
                    <span class="font-medium">{{ call.name }}</span>
                    <Badge :variant="call.argumentsValid ? 'secondary' : 'destructive'" class="h-5 px-2 text-[11px]">
                      {{ call.argumentsValid ? '参数合法' : '参数异常' }}
                    </Badge>
                    <span class="text-muted-foreground">{{ call.durationMs }} ms</span>
                    <span v-if="call.error" class="text-destructive">{{ call.error }}</span>
                  </div>
                  <div class="grid gap-2 lg:grid-cols-2">
                    <div>
                      <p class="text-[11px] text-muted-foreground">入参</p>
                      <pre class="mt-1 max-h-32 overflow-auto rounded-md bg-muted/50 p-2 text-[11px]">{{ call.arguments }}</pre>
                    </div>
                    <div>
                      <p class="text-[11px] text-muted-foreground">原始返回</p>
                      <pre class="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded-md bg-muted/50 p-2 text-[11px]">{{ call.output }}</pre>
                    </div>
                  </div>
                </div>
              </div>
            </section>
          </div>

          <!-- LLM -->
          <div v-else-if="activeTab === 'llm'" class="flex flex-col gap-4">
            <section class="rounded-xl border bg-card">
              <header class="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-2.5">
                <span class="text-sm font-semibold">请求（{{ llm.protocol || '—' }}）</span>
                <div class="flex items-center gap-2">
                  <Button variant="ghost" size="sm" @click="copy(llm.request || {}, '已复制请求体')"><IconCopy class="mr-1 size-3.5" />复制请求</Button>
                  <Button variant="outline" size="sm" :disabled="replaying !== null" @click="replay('llm')">
                    <IconLoader2 v-if="replaying === 'llm'" class="mr-1 size-3.5 animate-spin" />
                    <IconPlayerPlay v-else class="mr-1 size-3.5" />重跑 LLM
                  </Button>
                </div>
              </header>
              <p class="border-b px-4 py-2 text-xs text-muted-foreground">{{ llm.endpoint || '—' }}</p>
              <pre
                class="overflow-auto p-4 text-[11px] leading-relaxed"
                :class="debugStore.fullRaw ? 'max-h-[480px]' : 'max-h-32'"
              >{{ pretty(llm.request) }}</pre>
            </section>

            <section class="rounded-xl border bg-card">
              <header class="border-b px-4 py-2.5 text-sm font-semibold">流式时序（{{ chunks.length }} 块）</header>
              <div class="grid grid-cols-3 gap-3 border-b p-4 text-xs">
                <div><p class="text-muted-foreground">首字</p><p>{{ timing.firstTokenMs ?? '—' }} ms</p></div>
                <div><p class="text-muted-foreground">总耗时</p><p>{{ totalMs }} ms</p></div>
                <div><p class="text-muted-foreground">块数</p><p>{{ chunks.length }}</p></div>
              </div>
              <div class="max-h-64 overflow-auto">
                <div v-for="(chunk, index) in chunks" :key="index" class="flex gap-2 border-b px-4 py-1.5 text-[11px] last:border-0">
                  <span class="w-24 shrink-0 font-mono text-muted-foreground">{{ chunk.t }}</span>
                  <Badge variant="outline" class="h-4 shrink-0 px-1.5 text-[10px]">{{ chunk.kind }}</Badge>
                  <span class="whitespace-pre-wrap break-all">{{ chunk.text }}</span>
                </div>
              </div>
            </section>

            <section v-if="replayResult" class="rounded-xl border border-primary/30 bg-primary/5">
              <header class="border-b px-4 py-2.5 text-sm font-semibold">重跑结果 · {{ replayResult.durationMs }} ms</header>
              <div class="p-4 text-sm">
                <Markdown :text="replayResult.result?.content || ''" />
              </div>
            </section>
          </div>

          <!-- 输出 -->
          <div v-else-if="activeTab === 'output'" class="flex flex-col gap-4">
            <section class="rounded-xl border bg-card">
              <header class="flex items-center justify-between border-b px-4 py-2.5">
                <span class="text-sm font-semibold">最终回答（{{ output.source || '—' }}）</span>
                <Button variant="ghost" size="sm" @click="copy(output.answer || '', '已复制回答')"><IconCopy class="mr-1 size-3.5" />复制</Button>
              </header>
              <div class="p-4">
                <Markdown :text="output.answer || detail.answer || ''" />
              </div>
            </section>
            <section class="rounded-xl border bg-card">
              <header class="border-b px-4 py-2.5 text-sm font-semibold">引用校验</header>
              <div class="grid grid-cols-2 gap-3 p-4 text-xs lg:grid-cols-4">
                <div><p class="text-muted-foreground">来源数</p><p>{{ output.citations ?? 0 }}</p></div>
                <div><p class="text-muted-foreground">可用编号</p><p>{{ output.citationCheck?.available ?? '—' }}</p></div>
                <div><p class="text-muted-foreground">已使用</p><p>{{ (output.citationCheck?.used || []).join(', ') || '—' }}</p></div>
                <div>
                  <p class="text-muted-foreground">越界编号</p>
                  <p :class="output.citationCheck?.valid === false ? 'text-destructive' : ''">
                    {{ (output.citationCheck?.unknown || []).join(', ') || '无' }}
                  </p>
                </div>
              </div>
            </section>
          </div>

          <!-- 时序 -->
          <div v-else-if="activeTab === 'timeline'" class="flex flex-col gap-3">
            <section class="rounded-xl border bg-card">
              <header class="flex items-center justify-between border-b px-4 py-2.5">
                <span class="text-sm font-semibold">阶段耗时（总 {{ totalMs }} ms）</span>
                <Button variant="ghost" size="sm" @click="copy(stages, '已复制阶段数据')"><IconCopy class="mr-1 size-3.5" />复制</Button>
              </header>
              <div class="flex flex-col gap-2 p-4">
                <div v-for="(stage, index) in stages" :key="index" class="flex flex-col gap-1">
                  <div class="flex items-center justify-between text-xs">
                    <span class="font-medium">{{ stage.name }}</span>
                    <span :class="stage.status === 'SUCCESS' ? 'text-muted-foreground' : 'text-destructive'">
                      {{ stage.status }} · {{ stage.durationMs }} ms
                    </span>
                  </div>
                  <div class="h-1.5 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      class="h-full rounded-full"
                      :class="stage.status === 'SUCCESS' ? 'bg-primary' : 'bg-destructive'"
                      :style="{ width: stageWidth(stage) }"
                    />
                  </div>
                </div>
              </div>
            </section>
          </div>
        </div>
      </template>
    </SheetContent>
  </Sheet>
</template>
