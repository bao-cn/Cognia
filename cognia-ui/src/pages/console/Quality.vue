<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { feedbackApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { FeedbackDetail, FeedbackRecord } from '@/types/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { IconRefresh, IconThumbDown, IconThumbUp } from '@tabler/icons-vue'

const userStore = useUserStore()
const canRead = computed(() => userStore.hasPermission('feedback:read') || userStore.hasPermission('settings:read'))
const canUpdate = computed(() => userStore.hasPermission('feedback:update') || userStore.hasPermission('settings:update'))

const loading = ref(false)
const error = ref('')
const items = ref<FeedbackRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const type = ref('ALL')
const status = ref('ALL')

const dialogOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<FeedbackDetail | null>(null)
const saving = ref(false)
const form = reactive({ problemType: '', status: 'PENDING', note: '' })

const problemTypes = [
  { value: 'HALLUCINATION', label: '幻觉 / 事实错误' },
  { value: 'RETRIEVAL_MISS', label: '检索缺失 / 召回错误' },
  { value: 'RETRIEVAL_TIMEOUT', label: '检索超时' },
  { value: 'INCOMPLETE', label: '答案不完整' },
  { value: 'CITATION_ERROR', label: '引用错误' },
  { value: 'TOOL_ERROR', label: '工具调用错误' },
  { value: 'OTHER', label: '其他' },
]

const pages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const tracePretty = computed(() => {
  if (!detail.value?.traceJson) return ''
  try { return JSON.stringify(JSON.parse(detail.value.traceJson), null, 2) } catch { return detail.value.traceJson }
})

async function load() {
  if (!canRead.value) return
  loading.value = true
  error.value = ''
  try {
    const data = await feedbackApi.page({ page: page.value, size: size.value, type: type.value === 'ALL' ? undefined : type.value, status: status.value === 'ALL' ? undefined : status.value })
    items.value = data.records
    total.value = data.total
  } catch (e: any) {
    error.value = e?.message || '加载质量反馈失败'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  void load()
}

function go(delta: number) {
  const next = Math.min(pages.value, Math.max(1, page.value + delta))
  if (next === page.value) return
  page.value = next
  void load()
}

async function openDetail(item: FeedbackRecord) {
  dialogOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await feedbackApi.detail(item.id)
    form.problemType = detail.value.feedback.problemType || ''
    form.status = detail.value.feedback.status || 'PENDING'
    form.note = detail.value.feedback.note || ''
  } catch (e: any) {
    toast.error(e?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function save() {
  if (!detail.value || !canUpdate.value) return
  saving.value = true
  try {
    const updated = await feedbackApi.update(detail.value.feedback.id, {
      problemType: form.problemType || undefined,
      status: form.status,
      note: form.note || undefined,
    })
    detail.value.feedback = updated
    const index = items.value.findIndex(item => item.id === updated.id)
    if (index >= 0) items.value[index] = { ...items.value[index], ...updated }
    toast.success('评估结果已保存')
  } catch (e: any) {
    toast.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function feedbackBadge(type: string) {
  return type === 'LIKE' ? 'default' : 'destructive'
}

function statusLabel(value: string) {
  return value === 'RESOLVED' ? '已处理' : value === 'DISMISSED' ? '已忽略' : '待处理'
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconThumbUp class="size-5 text-muted-foreground" />质量评估
        </h1>
        <p class="text-sm text-muted-foreground">汇总用户点赞/点踩，关联完整链路，支持人工复核与问题标注。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <IconRefresh class="mr-1" />刷新
      </Button>
    </div>

    <Alert v-if="!canRead" variant="destructive"><AlertDescription>当前账户没有查看质量评估的权限。</AlertDescription></Alert>
    <Alert v-if="error" variant="destructive"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <section class="rounded-xl border bg-card">
      <header class="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3">
        <div class="flex flex-col gap-1"><h2 class="text-sm font-semibold">反馈记录</h2><p class="text-xs text-muted-foreground">共 {{ total }} 条</p></div>
        <div class="flex flex-wrap items-center gap-2">
          <Select v-model="type" @update:model-value="applyFilters">
            <SelectTrigger class="h-8 w-28"><SelectValue placeholder="反馈类型" /></SelectTrigger>
            <SelectContent><SelectItem value="ALL">全部反馈</SelectItem><SelectItem value="LIKE">点赞</SelectItem><SelectItem value="DISLIKE">点踩</SelectItem></SelectContent>
          </Select>
          <Select v-model="status" @update:model-value="applyFilters">
            <SelectTrigger class="h-8 w-28"><SelectValue placeholder="处理状态" /></SelectTrigger>
            <SelectContent><SelectItem value="ALL">全部状态</SelectItem><SelectItem value="PENDING">待处理</SelectItem><SelectItem value="RESOLVED">已处理</SelectItem><SelectItem value="DISMISSED">已忽略</SelectItem></SelectContent>
          </Select>
        </div>
      </header>
      <div v-if="loading" class="flex h-64 items-center justify-center"><Spinner class="size-6" /></div>
      <div v-else-if="items.length === 0" class="flex h-48 items-center justify-center text-sm text-muted-foreground">暂无反馈记录</div>
      <Table v-else>
        <TableHeader><TableRow>
          <TableHead>时间</TableHead><TableHead>用户</TableHead><TableHead>反馈</TableHead><TableHead>问题</TableHead><TableHead>状态</TableHead><TableHead>标记</TableHead><TableHead class="text-right">操作</TableHead>
        </TableRow></TableHeader>
        <TableBody>
          <TableRow v-for="item in items" :key="item.id">
            <TableCell class="whitespace-nowrap text-xs text-muted-foreground">{{ item.createTime }}</TableCell>
            <TableCell class="text-xs">{{ item.nickname || item.username || item.userId }}</TableCell>
            <TableCell><Badge :variant="feedbackBadge(item.feedbackType)" class="gap-1"><IconThumbUp v-if="item.feedbackType === 'LIKE'" class="size-3" /><IconThumbDown v-else class="size-3" />{{ item.feedbackType === 'LIKE' ? '点赞' : '点踩' }}</Badge></TableCell>
            <TableCell class="max-w-72 truncate text-xs">{{ item.question || item.reason || '—' }}</TableCell>
            <TableCell><Badge variant="outline">{{ statusLabel(item.status) }}</Badge></TableCell>
            <TableCell class="text-xs text-muted-foreground">{{ item.problemType || '—' }}</TableCell>
            <TableCell class="text-right"><Button variant="ghost" size="sm" @click="openDetail(item)">查看</Button></TableCell>
          </TableRow>
        </TableBody>
      </Table>
      <div class="flex items-center justify-between border-t px-4 py-3 text-xs text-muted-foreground">
        <span>第 {{ page }} / {{ pages }} 页</span>
        <div class="flex gap-2"><Button variant="outline" size="sm" :disabled="page <= 1" @click="go(-1)">上一页</Button><Button variant="outline" size="sm" :disabled="page >= pages" @click="go(1)">下一页</Button></div>
      </div>
    </section>

    <Dialog v-model:open="dialogOpen">
      <DialogContent class="max-h-[88vh] max-w-5xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>反馈链路详情</DialogTitle>
          <DialogDescription>查看原始问题、完整对话、模型输出与检索/工具调用链路。</DialogDescription>
        </DialogHeader>
        <div v-if="detailLoading" class="flex h-40 items-center justify-center"><Spinner class="size-6" /></div>
        <div v-else-if="detail" class="flex flex-col gap-4">
          <div class="flex flex-wrap items-center gap-2 text-xs">
            <Badge :variant="feedbackBadge(detail.feedback.feedbackType)">{{ detail.feedback.feedbackType === 'LIKE' ? '点赞' : '点踩' }}</Badge>
            <Badge variant="outline">{{ statusLabel(detail.feedback.status) }}</Badge>
            <span class="text-muted-foreground">{{ detail.feedback.nickname || detail.feedback.username }} · {{ detail.feedback.createTime }}</span>
          </div>
          <div v-if="detail.feedback.reason" class="rounded-lg border bg-muted/30 p-3 text-sm">{{ detail.feedback.reason }}</div>
          <div class="grid gap-4 lg:grid-cols-2">
            <section class="rounded-lg border">
              <header class="border-b px-3 py-2 text-sm font-semibold">对话上下文</header>
              <div class="flex max-h-80 flex-col gap-3 overflow-y-auto p-3">
                <div v-for="message in detail.messages" :key="message.id" class="flex flex-col gap-1">
                  <span class="text-[11px] font-medium uppercase text-muted-foreground">{{ message.role }}</span>
                  <p class="whitespace-pre-wrap text-xs leading-relaxed">{{ message.content }}</p>
                </div>
              </div>
            </section>
            <section class="rounded-lg border">
              <header class="border-b px-3 py-2 text-sm font-semibold">链路追踪</header>
              <pre class="max-h-80 overflow-auto whitespace-pre-wrap p-3 text-[11px] leading-relaxed text-muted-foreground">{{ tracePretty || '暂无链路数据' }}</pre>
            </section>
          </div>
          <section class="rounded-lg border p-3">
            <h3 class="mb-3 text-sm font-semibold">人工评估</h3>
            <div class="grid gap-3 md:grid-cols-2">
              <div class="grid gap-2"><Label>问题类型</Label>
                <Select v-model="form.problemType"><SelectTrigger><SelectValue placeholder="选择问题类型" /></SelectTrigger><SelectContent><SelectItem v-for="item in problemTypes" :key="item.value" :value="item.value">{{ item.label }}</SelectItem></SelectContent></Select>
              </div>
              <div class="grid gap-2"><Label>处理状态</Label>
                <Select v-model="form.status"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="PENDING">待处理</SelectItem><SelectItem value="RESOLVED">已处理</SelectItem><SelectItem value="DISMISSED">已忽略</SelectItem></SelectContent></Select>
              </div>
            </div>
            <div class="mt-3 grid gap-2"><Label>复核备注</Label><Textarea v-model="form.note" :rows="3" placeholder="记录定位结果、修复动作或结论" /></div>
            <div class="mt-3 flex justify-end"><Button :disabled="saving || !canUpdate" @click="save"><Spinner v-if="saving" class="mr-2" />保存评估</Button></div>
          </section>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
