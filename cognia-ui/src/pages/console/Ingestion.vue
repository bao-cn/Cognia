<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { ingestionApi, roleApi } from '@/api'
import type { IngestionJob, IngestionMetrics, IngestionSettings } from '@/api/ingestion'
import type { RoleItem } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { IconDatabase, IconRefresh, IconRotateClockwise } from '@tabler/icons-vue'

const userStore = useUserStore()
const canUpdate = computed(() => userStore.isAdmin || userStore.hasPermission('ingestion:update') || userStore.hasPermission('settings:update'))
const loading = ref(true)
const saving = ref(false)
const rebuilding = ref(false)
const metrics = ref<IngestionMetrics | null>(null)
const jobs = ref<IngestionJob[]>([])
const roles = ref<RoleItem[]>([])
const form = reactive<IngestionSettings>({
  id: 1, concurrency: 2, queueLimit: 500, maxRetries: 5, retryBackoffSeconds: 30,
  hnswM: 16, hnswEfConstruction: 200, hnswEfSearch: 100, healthFailureThreshold: 3,
  queueAlertDepth: 100, failureAlertPercent: 10, alertEmailEnabled: false, alertRecipient: '', alertRoleCode: 'OPS',
})

async function load() {
  loading.value = true
  try {
    const [settings, m, j] = await Promise.all([ingestionApi.settings(), ingestionApi.metrics(), ingestionApi.jobs()])
    Object.assign(form, settings)
    metrics.value = m
    jobs.value = j
    try { roles.value = await roleApi.list() } catch { roles.value = [] }
  } catch (e: any) {
    toast.error(e?.message || '加载失败')
  } finally { loading.value = false }
}

async function save() {
  saving.value = true
  try {
    Object.assign(form, await ingestionApi.updateSettings({ ...form }))
    toast.success('配置已保存')
    metrics.value = await ingestionApi.metrics()
  } catch (e: any) { toast.error(e?.message || '保存失败') }
  finally { saving.value = false }
}

async function rebuild() {
  rebuilding.value = true
  try {
    const result = await ingestionApi.rebuildIndex()
    toast[result.ready ? 'success' : 'error'](result.message)
    metrics.value = await ingestionApi.metrics()
  } catch (e: any) { toast.error(e?.message || '重建失败') }
  finally { rebuilding.value = false }
}

async function requeue(job: IngestionJob) {
  try {
    await ingestionApi.requeue(job.id)
    toast.success('已重新入队')
    jobs.value = await ingestionApi.jobs()
    metrics.value = await ingestionApi.metrics()
  } catch (e: any) { toast.error(e?.message || '操作失败') }
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold"><IconDatabase class="size-5 text-muted-foreground" />文档处理</h1>
        <p class="text-sm text-muted-foreground">解析与向量化任务队列、并发、重试和向量索引配置。</p>
      </div>
      <div class="flex gap-2">
        <Button variant="outline" size="sm" :disabled="loading" @click="load"><IconRefresh class="mr-1 size-4" />刷新</Button>
        <Button v-if="canUpdate" :disabled="saving" @click="save"><Spinner v-if="saving" class="mr-2 size-4" />保存配置</Button>
      </div>
    </div>

    <div v-if="loading" class="flex h-48 items-center justify-center rounded-xl border"><Spinner class="size-6" /></div>
    <template v-else>
      <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <section class="rounded-xl border bg-card p-4"><p class="text-xs text-muted-foreground">队列深度</p><p class="mt-1 text-2xl font-semibold tabular-nums">{{ metrics?.queueDepth ?? 0 }}</p></section>
        <section class="rounded-xl border bg-card p-4"><p class="text-xs text-muted-foreground">处理中</p><p class="mt-1 text-2xl font-semibold tabular-nums">{{ metrics?.running ?? 0 }}</p></section>
        <section class="rounded-xl border bg-card p-4"><p class="text-xs text-muted-foreground">失败率（24h）</p><p class="mt-1 text-2xl font-semibold tabular-nums">{{ (metrics?.failureRate ?? 0).toFixed(1) }}%</p></section>
        <section class="rounded-xl border bg-card p-4"><p class="text-xs text-muted-foreground">Embedding 健康度</p><p class="mt-1 text-2xl font-semibold tabular-nums">{{ (metrics?.embeddingSuccessRate ?? 0).toFixed(1) }}%</p></section>
      </div>

      <section class="rounded-xl border bg-card">
        <header class="flex flex-col gap-1 border-b px-4 py-3">
          <h2 class="text-sm font-semibold">资源池配置</h2>
          <p class="text-xs text-muted-foreground">并发与重试仅在下一个任务开始时生效。</p>
        </header>
        <div class="grid gap-4 p-4 md:grid-cols-2 xl:grid-cols-3">
          <div class="grid gap-2"><Label>并发 Worker 数</Label><Input v-model.number="form.concurrency" type="number" min="1" max="16" /></div>
          <div class="grid gap-2"><Label>队列上限</Label><Input v-model.number="form.queueLimit" type="number" min="1" max="5000" /></div>
          <div class="grid gap-2"><Label>最大重试次数</Label><Input v-model.number="form.maxRetries" type="number" min="0" max="20" /></div>
          <div class="grid gap-2"><Label>重试退避基数（秒）</Label><Input v-model.number="form.retryBackoffSeconds" type="number" min="1" max="3600" /></div>
          <div class="grid gap-2"><Label>HNSW m</Label><Input v-model.number="form.hnswM" type="number" min="4" max="64" /></div>
          <div class="grid gap-2"><Label>HNSW ef_construction</Label><Input v-model.number="form.hnswEfConstruction" type="number" min="16" max="1000" /></div>
          <div class="grid gap-2"><Label>查询 ef_search</Label><Input v-model.number="form.hnswEfSearch" type="number" min="10" max="1000" /></div>
          <div class="grid gap-2"><Label>队列告警深度</Label><Input v-model.number="form.queueAlertDepth" type="number" min="1" max="10000" /></div>
          <div class="grid gap-2"><Label>失败率告警（%）</Label><Input v-model.number="form.failureAlertPercent" type="number" min="1" max="100" /></div>
          <div class="grid gap-2"><Label>健康度连续失败阈值</Label><Input v-model.number="form.healthFailureThreshold" type="number" min="1" max="100" /></div>
          <div class="grid gap-2"><Label>告警收件人</Label><Input v-model="form.alertRecipient" placeholder="留空则发送给所选运维角色" /></div>
          <div class="grid gap-2"><Label>运维角色</Label>
            <Select v-model="form.alertRoleCode">
              <SelectTrigger><SelectValue placeholder="选择系统角色" /></SelectTrigger>
              <SelectContent>
                <SelectItem v-if="form.alertRoleCode && !roles.some((role) => role.code === form.alertRoleCode)" :value="form.alertRoleCode">{{ form.alertRoleCode }}（未创建）</SelectItem>
                <SelectItem v-for="role in roles" :key="role.code" :value="role.code">{{ role.name }}（{{ role.code }}）</SelectItem>
              </SelectContent>
            </Select>
            <p class="text-xs text-muted-foreground">告警收件人留空时，发送给该角色下所有启用用户的邮箱。</p>
          </div>
          <div class="flex items-center gap-3 pt-6"><Switch v-model="form.alertEmailEnabled" /><Label>邮件告警</Label></div>
        </div>
      </section>

      <section class="rounded-xl border bg-card">
        <header class="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-3">
          <div><h2 class="text-sm font-semibold">向量索引</h2><p class="text-xs text-muted-foreground">{{ metrics?.index?.message }}</p></div>
          <div class="flex items-center gap-2">
            <Badge :variant="metrics?.index?.ready ? 'default' : 'destructive'">{{ metrics?.index?.ready ? '已就绪' : '未就绪' }}</Badge>
            <Button v-if="canUpdate" variant="outline" size="sm" :disabled="rebuilding" @click="rebuild"><IconRotateClockwise class="mr-1 size-4" />重建索引</Button>
          </div>
        </header>
      </section>

      <section class="rounded-xl border bg-card">
        <header class="flex items-center justify-between border-b px-4 py-3">
          <div><h2 class="text-sm font-semibold">最近任务</h2><p class="text-xs text-muted-foreground">失败任务保留在死信区，可手动重新入队。</p></div>
          <Badge variant="outline">{{ jobs.length }}</Badge>
        </header>
        <Table>
          <TableHeader><TableRow><TableHead>ID</TableHead><TableHead>文档</TableHead><TableHead>步骤</TableHead><TableHead>状态</TableHead><TableHead>尝试</TableHead><TableHead>错误</TableHead><TableHead /></TableRow></TableHeader>
          <TableBody>
            <TableRow v-for="job in jobs" :key="job.id">
              <TableCell class="tabular-nums">{{ job.id }}</TableCell>
              <TableCell class="tabular-nums">{{ job.documentId }}</TableCell>
              <TableCell>{{ job.step }}</TableCell>
              <TableCell><Badge :variant="job.status === 'DEAD' ? 'destructive' : job.status === 'SUCCEEDED' ? 'secondary' : 'outline'">{{ job.status }}</Badge></TableCell>
              <TableCell class="tabular-nums">{{ job.attempts }}/{{ job.maxAttempts }}</TableCell>
              <TableCell class="max-w-[420px] truncate text-xs text-destructive">{{ job.errorMessage }}</TableCell>
              <TableCell class="text-right"><Button v-if="job.status === 'DEAD' && canUpdate" variant="ghost" size="sm" @click="requeue(job)">重新入队</Button></TableCell>
            </TableRow>
            <TableRow v-if="!jobs.length"><TableCell colspan="7" class="h-24 text-center text-sm text-muted-foreground">暂无任务</TableCell></TableRow>
          </TableBody>
        </Table>
      </section>
    </template>
  </div>
</template>
