<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { dashboardApi } from '@/api'
import type { AdminDashboard } from '@/types/api'
import DashboardTrendChart from '@/components/DashboardTrendChart.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  IconActivity, IconAlertTriangle, IconBooks, IconChartBar, IconDatabase,
  IconFileText, IconRefresh, IconServer, IconUsers, IconCloud, IconClock,
} from '@tabler/icons-vue'

const data = ref<AdminDashboard | null>(null)
const loading = ref(true)
const error = ref('')

const o = computed(() => data.value?.overview)
const ops = computed(() => data.value?.ops)

function pct(value?: number) { return `${(value ?? 0).toFixed(1)}%` }
function num(value?: number) { return (value ?? 0).toLocaleString('zh-CN') }
function bytes(value?: number) {
  const n = value ?? 0
  if (n < 1024) return `${n} B`
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 ** 3) return `${(n / 1024 ** 2).toFixed(1)} MB`
  return `${(n / 1024 ** 3).toFixed(2)} GB`
}
function ms(value?: number) { return value ? `${num(value)} ms` : '-' }

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await dashboardApi.overview()
  } catch (e: any) {
    error.value = e?.message || '加载看板失败'
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}

const maxFile = computed(() => Math.max(1, ...(data.value?.fileTypes ?? []).map(t => t.count)))

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconChartBar class="size-5 text-muted-foreground" />运营看板
        </h1>
        <p class="text-sm text-muted-foreground">系统全局状态、问答质量与底层运维指标</p>
      </div>
      <div class="flex items-center gap-2">
        <Badge v-if="data" variant="outline" class="h-8 px-3">更新于 {{ data.generatedAt }}</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="load">
          <IconRefresh class="mr-1 size-4" />刷新
        </Button>
      </div>
    </div>

    <Alert v-if="error" variant="destructive"><AlertDescription>{{ error }}</AlertDescription></Alert>
    <Alert v-if="ops?.warnings?.length" variant="destructive">
      <IconAlertTriangle class="size-4" />
      <AlertDescription>{{ ops.warnings.join('；') }}</AlertDescription>
    </Alert>

    <div v-if="loading && !data" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <template v-else-if="o">
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <section class="rounded-xl border bg-card p-4">
          <div class="flex items-center justify-between text-xs text-muted-foreground"><span>用户总数</span><IconUsers class="size-4" /></div>
          <div class="mt-2 text-2xl font-semibold">{{ num(o.totalUsers) }}</div>
          <div class="mt-2 flex gap-3 text-xs text-muted-foreground">
            <span class="text-emerald-600">正常 {{ num(o.activeUsers) }}</span>
            <span class="text-destructive">封禁 {{ num(o.bannedUsers) }}</span>
          </div>
          <div class="mt-1 text-xs text-muted-foreground">30 天不活跃 {{ num(o.inactiveUsers) }}</div>
        </section>
        <section class="rounded-xl border bg-card p-4">
          <div class="flex items-center justify-between text-xs text-muted-foreground"><span>频道</span><IconBooks class="size-4" /></div>
          <div class="mt-2 text-2xl font-semibold">{{ num(o.totalChannels) }}</div>
          <div class="mt-2 text-xs text-muted-foreground">公开 {{ num(o.publicChannels) }} · 私有 {{ num(o.privateChannels) }}</div>
        </section>
        <section class="rounded-xl border bg-card p-4">
          <div class="flex items-center justify-between text-xs text-muted-foreground"><span>文件总量</span><IconFileText class="size-4" /></div>
          <div class="mt-2 text-2xl font-semibold">{{ num(o.totalFiles) }}</div>
          <div class="mt-2 text-xs text-muted-foreground">{{ bytes(o.storageBytes) }} 已存储</div>
        </section>
        <section class="rounded-xl border bg-card p-4">
          <div class="flex items-center justify-between text-xs text-muted-foreground"><span>问答请求</span><IconActivity class="size-4" /></div>
          <div class="mt-2 text-2xl font-semibold">{{ num(o.qaRequests) }}</div>
          <div class="mt-2 text-xs text-muted-foreground">今日 {{ num(o.qaToday) }} · 7 天 {{ num(o.qaWeek) }}</div>
        </section>
      </div>

      <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">请求趋势</h2>
            <p class="text-xs text-muted-foreground">近 14 天问答 / 检索 / LLM 调用</p>
          </header>
          <div class="px-2 pb-3 pt-4">
            <DashboardTrendChart :data="data?.trends ?? []" :height="240" />
          </div>
        </section>

        <section class="rounded-xl border bg-card">
          <header class="border-b px-4 py-3"><h2 class="text-sm font-semibold">质量指标</h2></header>
          <div class="grid grid-cols-2 gap-px bg-border">
            <div class="bg-card p-4"><div class="text-xs text-muted-foreground">检索命中率</div><div class="mt-1 text-xl font-semibold">{{ pct(o.retrievalHitRate) }}</div></div>
            <div class="bg-card p-4"><div class="text-xs text-muted-foreground">纯 LLM 占比</div><div class="mt-1 text-xl font-semibold">{{ pct(o.noRetrievalRate) }}</div></div>
            <div class="bg-card p-4"><div class="text-xs text-muted-foreground">拒绝回答</div><div class="mt-1 text-xl font-semibold">{{ pct(o.refusalRate) }}</div></div>
            <div class="bg-card p-4"><div class="text-xs text-muted-foreground">会话留存</div><div class="mt-1 text-xl font-semibold">{{ pct(o.sessionRetentionRate) }}</div></div>
            <div class="bg-card p-4"><div class="text-xs text-muted-foreground">平均轮次</div><div class="mt-1 text-xl font-semibold">{{ o.avgRounds }}</div></div>
            <div class="bg-card p-4"><div class="text-xs text-muted-foreground">会话数</div><div class="mt-1 text-xl font-semibold">{{ num(o.conversations) }}</div></div>
          </div>
        </section>
      </div>

      <div class="grid gap-4 xl:grid-cols-3">
        <section class="rounded-xl border bg-card">
          <header class="border-b px-4 py-3"><h2 class="text-sm font-semibold">新增用户</h2></header>
          <div class="grid grid-cols-3 divide-x">
            <div class="p-4"><div class="text-xs text-muted-foreground">今日</div><div class="mt-1 text-lg font-semibold">{{ num(o.newUsersToday) }}</div></div>
            <div class="p-4"><div class="text-xs text-muted-foreground">近 7 天</div><div class="mt-1 text-lg font-semibold">{{ num(o.newUsersWeek) }}</div></div>
            <div class="p-4"><div class="text-xs text-muted-foreground">近 30 天</div><div class="mt-1 text-lg font-semibold">{{ num(o.newUsersMonth) }}</div></div>
          </div>
        </section>
        <section class="rounded-xl border bg-card xl:col-span-2">
          <header class="flex items-center justify-between border-b px-4 py-3">
            <h2 class="text-sm font-semibold">文件格式分布</h2>
            <span class="text-xs text-muted-foreground">{{ num(o.totalFiles) }} 个文件</span>
          </header>
          <div class="flex flex-col gap-2 p-4">
            <div v-if="!data?.fileTypes?.length" class="py-6 text-center text-sm text-muted-foreground">暂无文件</div>
            <div v-for="item in data?.fileTypes" :key="item.type" class="grid grid-cols-[56px_minmax(0,1fr)_100px] items-center gap-2 text-xs">
              <span class="font-mono text-muted-foreground">{{ item.type }}</span>
              <div class="h-2 overflow-hidden rounded-full bg-muted">
                <div class="h-full rounded-full bg-primary/70" :style="{ width: `${(item.count / maxFile) * 100}%` }" />
              </div>
              <span class="text-right text-muted-foreground">{{ num(item.count) }} · {{ bytes(item.bytes) }}</span>
            </div>
          </div>
        </section>
      </div>

      <section class="rounded-xl border bg-card">
        <header class="flex flex-col gap-1 border-b px-4 py-3">
          <h2 class="text-sm font-semibold">检索与生成性能</h2>
          <p class="text-xs text-muted-foreground">延迟统计来自成功问答请求</p>
        </header>
        <div class="grid gap-4 p-4 sm:grid-cols-2 xl:grid-cols-6">
          <div><div class="text-xs text-muted-foreground">平均召回</div><div class="mt-1 text-lg font-semibold">{{ ms(data?.latency.avgRetrievalMs) }}</div></div>
          <div><div class="text-xs text-muted-foreground">平均重排</div><div class="mt-1 text-lg font-semibold">{{ ms(data?.latency.avgRerankMs) }}</div></div>
          <div><div class="text-xs text-muted-foreground">总问答均值</div><div class="mt-1 text-lg font-semibold">{{ ms(data?.latency.avgQaMs) }}</div></div>
          <div><div class="text-xs text-muted-foreground">P50</div><div class="mt-1 text-lg font-semibold">{{ ms(data?.latency.p50QaMs) }}</div></div>
          <div><div class="text-xs text-muted-foreground">P90</div><div class="mt-1 text-lg font-semibold">{{ ms(data?.latency.p90QaMs) }}</div></div>
          <div><div class="text-xs text-muted-foreground">P99</div><div class="mt-1 text-lg font-semibold">{{ ms(data?.latency.p99QaMs) }}</div></div>
        </div>
      </section>

      <div class="grid gap-4 xl:grid-cols-2">
        <section class="rounded-xl border bg-card">
          <header class="flex items-center justify-between border-b px-4 py-3">
            <h2 class="text-sm font-semibold">模型调用</h2><span class="text-xs text-muted-foreground">LLM / 检索 / 重排</span>
          </header>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead class="bg-muted/40 text-xs text-muted-foreground">
                <tr><th class="px-4 py-2 text-left font-medium">模型</th><th class="px-4 py-2 text-right font-medium">调用</th><th class="px-4 py-2 text-right font-medium">输入</th><th class="px-4 py-2 text-right font-medium">输出</th><th class="px-4 py-2 text-right font-medium">错误</th></tr>
              </thead>
              <tbody>
                <tr v-for="m in data?.models" :key="m.modelName" class="border-t">
                  <td class="px-4 py-2">{{ m.modelName }}</td>
                  <td class="px-4 py-2 text-right">{{ num(m.calls) }}</td>
                  <td class="px-4 py-2 text-right">{{ num(m.inputTokens) }}</td>
                  <td class="px-4 py-2 text-right">{{ num(m.outputTokens) }}</td>
                  <td class="px-4 py-2 text-right" :class="{ 'text-destructive': m.errors > 0 }">{{ num(m.errors) }}</td>
                </tr>
                <tr v-if="!data?.models?.length"><td colspan="5" class="px-4 py-8 text-center text-sm text-muted-foreground">暂无调用数据</td></tr>
              </tbody>
            </table>
          </div>
          <div class="grid grid-cols-3 divide-x border-t text-xs">
            <div class="p-3"><div class="text-muted-foreground">Embedding 模型调用</div><div class="mt-1 text-base font-semibold">{{ num(o.retrievalRequests) }}</div></div>
            <div class="p-3"><div class="text-muted-foreground">LLM 生成调用</div><div class="mt-1 text-base font-semibold">{{ num(o.llmCalls) }}</div></div>
            <div class="p-3"><div class="text-muted-foreground">检索请求</div><div class="mt-1 text-base font-semibold">{{ num(o.retrievalRequests) }}</div></div>
          </div>
        </section>

        <section class="rounded-xl border bg-card">
          <header class="border-b px-4 py-3"><h2 class="text-sm font-semibold">Token 消耗</h2></header>
          <div class="grid gap-4 p-4 md:grid-cols-2">
            <div>
              <div class="mb-2 text-xs font-medium text-muted-foreground">按用户</div>
              <div v-for="t in data?.tokensByUser" :key="t.name" class="flex items-center justify-between border-b py-2 text-sm last:border-0">
                <span class="truncate">{{ t.name }}</span><span class="shrink-0 text-xs text-muted-foreground">{{ num(t.inputTokens) }} / {{ num(t.outputTokens) }}</span>
              </div>
              <div v-if="!data?.tokensByUser?.length" class="py-6 text-center text-xs text-muted-foreground">暂无数据</div>
            </div>
            <div>
              <div class="mb-2 text-xs font-medium text-muted-foreground">按模型</div>
              <div v-for="t in data?.tokensByModel" :key="t.name" class="flex items-center justify-between border-b py-2 text-sm last:border-0">
                <span class="truncate">{{ t.name }}</span><span class="shrink-0 text-xs text-muted-foreground">{{ num(t.inputTokens) }} / {{ num(t.outputTokens) }}</span>
              </div>
              <div v-if="!data?.tokensByModel?.length" class="py-6 text-center text-xs text-muted-foreground">暂无数据</div>
            </div>
          </div>
        </section>
      </div>

      <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <section class="rounded-xl border bg-card">
          <header class="border-b px-4 py-3"><h2 class="text-sm font-semibold">底层运维监控</h2></header>
          <div class="grid gap-px bg-border sm:grid-cols-2 lg:grid-cols-3">
            <div class="flex items-center gap-3 bg-card p-4"><IconServer class="size-5 text-muted-foreground" /><div class="min-w-0"><div class="text-xs text-muted-foreground">API 服务</div><div class="text-sm font-medium">{{ ops?.apiStatus }} · 连接 {{ ops?.dbConnections }}</div></div></div>
            <div class="flex items-center gap-3 bg-card p-4"><IconActivity class="size-5 text-muted-foreground" /><div class="min-w-0"><div class="text-xs text-muted-foreground">SSE 推送</div><div class="text-sm font-medium">{{ ops?.sseStatus }} · 在线 {{ ops?.sseOnline }}</div></div></div>
            <div class="flex items-center gap-3 bg-card p-4"><IconFileText class="size-5 text-muted-foreground" /><div class="min-w-0"><div class="text-xs text-muted-foreground">解析 / 向量化</div><div class="text-sm font-medium">{{ ops?.documentWorkerStatus }} · 队列 {{ ops?.documentQueue }}</div></div></div>
            <div class="flex items-center gap-3 bg-card p-4"><IconClock class="size-5 text-muted-foreground" /><div class="min-w-0"><div class="text-xs text-muted-foreground">计划任务</div><div class="text-sm font-medium">{{ ops?.scheduledStatus }} · {{ ops?.scheduledEnabled }} 启用</div></div></div>
            <div class="flex items-center gap-3 bg-card p-4"><IconDatabase class="size-5 text-muted-foreground" /><div class="min-w-0"><div class="text-xs text-muted-foreground">向量索引</div><div class="text-sm font-medium">{{ bytes(ops?.vectorIndexBytes) }} · 慢查询 {{ ops?.slowQueries }}</div></div></div>
            <div class="flex items-center gap-3 bg-card p-4"><IconCloud class="size-5 text-muted-foreground" /><div class="min-w-0"><div class="text-xs text-muted-foreground">MinIO / Redis</div><div class="text-sm font-medium">{{ bytes(ops?.minioStorageBytes) }} · {{ ops?.redisMemory }}</div></div></div>
          </div>
          <div class="flex flex-wrap gap-3 border-t px-4 py-3 text-xs text-muted-foreground">
            <span>Redis 命中率 {{ pct(ops?.redisHitRate) }}</span>
            <span>对象 {{ num(ops?.minioObjects) }}</span>
            <span>临时文件生命周期 {{ ops?.lifecycleDays }} 天</span>
            <span>消息队列 {{ ops?.queueAvailable ? '已接入' : '未启用（同步处理）' }}</span>
          </div>
        </section>

        <section class="rounded-xl border bg-card">
          <header class="flex items-center justify-between border-b px-4 py-3">
            <h2 class="text-sm font-semibold">错误分布</h2><Badge variant="outline">{{ data?.errorCodes?.length ?? 0 }} 类</Badge>
          </header>
          <div class="p-4">
            <div v-for="e in data?.errorCodes" :key="e.errorType" class="mb-2 flex items-center justify-between text-sm">
              <span class="font-mono text-xs">{{ e.errorType }}</span><span class="text-muted-foreground">{{ num(e.count) }}</span>
            </div>
            <div v-if="!data?.errorCodes?.length" class="py-8 text-center text-sm text-muted-foreground">暂无错误</div>
          </div>
        </section>
      </div>

      <section class="rounded-xl border bg-card">
        <header class="border-b px-4 py-3"><h2 class="text-sm font-semibold">定时任务</h2></header>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead class="bg-muted/40 text-xs text-muted-foreground"><tr><th class="px-4 py-2 text-left font-medium">任务</th><th class="px-4 py-2 text-left font-medium">状态</th><th class="px-4 py-2 text-left font-medium">最近执行</th><th class="px-4 py-2 text-left font-medium">结果</th></tr></thead>
            <tbody>
              <tr v-for="t in data?.tasks" :key="t.code" class="border-t">
                <td class="px-4 py-2"><div class="font-medium">{{ t.name }}</div><div class="font-mono text-[10px] text-muted-foreground">{{ t.code }}</div></td>
                <td class="px-4 py-2"><Badge :variant="t.lastStatus === 'SUCCESS' ? 'default' : t.lastStatus === 'FAILED' ? 'destructive' : 'secondary'">{{ t.enabled ? (t.lastStatus || '待执行') : '已停用' }}</Badge></td>
                <td class="px-4 py-2 text-xs text-muted-foreground">{{ t.lastRunAt || '-' }}</td>
                <td class="max-w-xs truncate px-4 py-2 text-xs text-muted-foreground">{{ t.lastMessage || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>
