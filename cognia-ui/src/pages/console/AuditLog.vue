<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { auditApi } from '@/api'
import type { AuditLogEntry } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  IconChevronLeft,
  IconChevronRight,
  IconFilter,
  IconRefresh,
  IconSearch,
  IconShieldCheck,
} from '@tabler/icons-vue'

const userStore = useUserStore()
const canRead = computed(() => userStore.hasPermission('audit:read') || userStore.hasPermission('settings:read'))

const loading = ref(false)
const error = ref('')
const rows = ref<AuditLogEntry[]>([])
const total = ref(0)
const pages = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const moduleFilter = ref('all')
const statusFilter = ref('all')
const start = ref('')
const end = ref('')
const modules = ref<string[]>([])

const detailOpen = ref(false)
const selected = ref<AuditLogEntry | null>(null)

const rangeStart = computed(() => (total.value === 0 ? 0 : (page.value - 1) * size.value + 1))
const rangeEnd = computed(() => Math.min(page.value * size.value, total.value))

function messageOf(errorValue: unknown): string {
  return errorValue instanceof Error ? errorValue.message : '操作失败，请稍后重试'
}

function statusLabel(status: string): string {
  return status === 'SUCCESS' ? '成功' : status === 'FAILED' ? '失败' : status
}

function statusVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  if (status === 'SUCCESS') return 'default'
  if (status === 'FAILED') return 'destructive'
  return 'secondary'
}

function methodVariant(method?: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch ((method ?? '').toUpperCase()) {
    case 'POST':
      return 'default'
    case 'PUT':
    case 'PATCH':
      return 'secondary'
    case 'DELETE':
      return 'destructive'
    default:
      return 'outline'
  }
}

async function fetchModules(): Promise<void> {
  try {
    modules.value = await auditApi.modules()
  } catch {
    // Filtering by module is optional.
  }
}

async function fetchLogs(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await auditApi.list({
      page: page.value,
      size: size.value,
      module: moduleFilter.value === 'all' ? undefined : moduleFilter.value,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value,
      keyword: keyword.value.trim() || undefined,
      start: start.value || undefined,
      end: end.value || undefined,
    })
    rows.value = result.records
    total.value = result.total
    pages.value = result.pages
  } catch (errorValue) {
    error.value = messageOf(errorValue)
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}

function applyFilters(): void {
  page.value = 1
  void fetchLogs()
}

function resetFilters(): void {
  keyword.value = ''
  moduleFilter.value = 'all'
  statusFilter.value = 'all'
  start.value = ''
  end.value = ''
  page.value = 1
  void fetchLogs()
}

function goToPage(target: number): void {
  if (target < 1 || (pages.value > 0 && target > pages.value)) return
  page.value = target
  void fetchLogs()
}

function showDetail(row: AuditLogEntry): void {
  selected.value = row
  detailOpen.value = true
}

onMounted(() => {
  if (!canRead.value) return
  void fetchModules()
  void fetchLogs()
})
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconShieldCheck class="size-5 text-muted-foreground" />
          审计日志
        </h1>
        <p class="text-sm text-muted-foreground">
          记录管理员对后台资源的写操作与计划任务执行结果，用于安全追溯。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Badge variant="outline" class="h-8 px-3">共 {{ total }} 条记录</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="fetchLogs">
          <IconRefresh class="mr-1 size-4" />
          刷新
        </Button>
      </div>
    </div>

    <Alert v-if="!canRead" variant="destructive">
      <AlertDescription>当前账户没有查看审计日志的权限（audit:read）。</AlertDescription>
    </Alert>

    <template v-else>
      <Alert v-if="error" variant="destructive">
        <AlertDescription>{{ error }}</AlertDescription>
      </Alert>

      <section class="rounded-xl border bg-card">
        <header class="flex flex-col gap-1 border-b px-4 py-3">
          <h2 class="flex items-center gap-2 text-sm font-semibold">
            <IconFilter class="size-4 text-muted-foreground" />
            筛选条件
          </h2>
          <p class="text-xs text-muted-foreground">支持按模块、结果、操作者/描述/路径关键字与时间范围过滤。</p>
        </header>
        <div class="grid gap-4 p-4 md:grid-cols-2 xl:grid-cols-3">
          <div class="grid gap-2">
            <label class="text-sm font-medium" for="audit-keyword">关键字</label>
            <Input
              id="audit-keyword"
              v-model="keyword"
              placeholder="操作者、描述、路径或资源 ID"
              @keyup.enter="applyFilters"
            />
          </div>
          <div class="grid gap-2">
            <label class="text-sm font-medium" for="audit-module">模块</label>
            <Select v-model="moduleFilter">
              <SelectTrigger id="audit-module">
                <SelectValue placeholder="全部模块" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部模块</SelectItem>
                <SelectItem v-for="item in modules" :key="item" :value="item">{{ item }}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="grid gap-2">
            <label class="text-sm font-medium" for="audit-status">结果</label>
            <Select v-model="statusFilter">
              <SelectTrigger id="audit-status">
                <SelectValue placeholder="全部结果" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部结果</SelectItem>
                <SelectItem value="SUCCESS">成功</SelectItem>
                <SelectItem value="FAILED">失败</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="grid gap-2">
            <label class="text-sm font-medium" for="audit-start">开始时间</label>
            <Input id="audit-start" v-model="start" type="datetime-local" />
          </div>
          <div class="grid gap-2">
            <label class="text-sm font-medium" for="audit-end">结束时间</label>
            <Input id="audit-end" v-model="end" type="datetime-local" />
          </div>
          <div class="flex items-end gap-2">
            <Button :disabled="loading" @click="applyFilters">
              <IconSearch class="mr-1 size-4" />
              查询
            </Button>
            <Button variant="outline" :disabled="loading" @click="resetFilters">重置</Button>
          </div>
        </div>
      </section>

      <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
        <Spinner class="size-6" />
      </div>

      <section v-else class="overflow-hidden rounded-xl border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead class="w-44">时间</TableHead>
              <TableHead class="w-36">操作者</TableHead>
              <TableHead class="w-28">模块</TableHead>
              <TableHead class="min-w-64">操作</TableHead>
              <TableHead class="w-24">方法</TableHead>
              <TableHead class="w-40">路径</TableHead>
              <TableHead class="w-24">IP</TableHead>
              <TableHead class="w-20">结果</TableHead>
              <TableHead class="w-20">耗时</TableHead>
              <TableHead class="w-16"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-if="rows.length === 0">
              <TableCell colspan="10" class="h-32 text-center text-sm text-muted-foreground">
                暂无审计记录
              </TableCell>
            </TableRow>
            <TableRow v-for="row in rows" :key="row.id">
              <TableCell class="whitespace-nowrap text-xs text-muted-foreground">{{ row.createTime }}</TableCell>
              <TableCell class="max-w-36 truncate text-sm">{{ row.username || '系统' }}</TableCell>
              <TableCell>
                <Badge variant="outline" class="font-normal">{{ row.module }}</Badge>
              </TableCell>
              <TableCell class="max-w-72">
                <p class="truncate text-sm">{{ row.description || row.action }}</p>
                <p v-if="row.errorMessage" class="truncate text-xs text-destructive">{{ row.errorMessage }}</p>
              </TableCell>
              <TableCell>
                <Badge :variant="methodVariant(row.method)" class="font-mono text-[11px]">
                  {{ row.method || '-' }}
                </Badge>
              </TableCell>
              <TableCell class="max-w-40 truncate font-mono text-xs text-muted-foreground">{{ row.path || '-' }}</TableCell>
              <TableCell class="font-mono text-xs text-muted-foreground">{{ row.ip || '-' }}</TableCell>
              <TableCell>
                <Badge :variant="statusVariant(row.status)">{{ statusLabel(row.status) }}</Badge>
              </TableCell>
              <TableCell class="text-xs text-muted-foreground">{{ row.durationMs != null ? `${row.durationMs} ms` : '-' }}</TableCell>
              <TableCell>
                <Button variant="ghost" size="sm" class="h-7 px-2 text-xs" @click="showDetail(row)">
                  详情
                </Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </section>

      <div class="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
        <span>共 {{ total }} 条，显示 {{ rangeStart }}-{{ rangeEnd }}</span>
        <div class="flex items-center gap-2">
          <Button variant="outline" size="sm" :disabled="page <= 1" @click="goToPage(page - 1)">
            <IconChevronLeft />
            上一页
          </Button>
          <span>{{ page }} / {{ Math.max(1, pages) }}</span>
          <Button variant="outline" size="sm" :disabled="page >= pages" @click="goToPage(page + 1)">
            下一页
            <IconChevronRight />
          </Button>
        </div>
      </div>
    </template>

    <Dialog v-model:open="detailOpen">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>审计详情</DialogTitle>
          <DialogDescription>
            {{ selected?.createTime }} · {{ selected?.username || '系统' }} · {{ selected?.module }}
          </DialogDescription>
        </DialogHeader>
        <div v-if="selected" class="grid max-h-[60vh] gap-3 overflow-y-auto text-sm">
          <div class="grid gap-1">
            <span class="text-xs text-muted-foreground">操作</span>
            <span>{{ selected.description || selected.action }}</span>
          </div>
          <div class="grid gap-1 sm:grid-cols-2 sm:gap-3">
            <div class="grid gap-1">
              <span class="text-xs text-muted-foreground">请求</span>
              <span class="font-mono text-xs">{{ selected.method }} {{ selected.path }}</span>
            </div>
            <div class="grid gap-1">
              <span class="text-xs text-muted-foreground">资源</span>
              <span class="font-mono text-xs">{{ selected.resourceType || '-' }} / {{ selected.resourceId || '-' }}</span>
            </div>
            <div class="grid gap-1">
              <span class="text-xs text-muted-foreground">IP</span>
              <span class="font-mono text-xs">{{ selected.ip || '-' }}</span>
            </div>
            <div class="grid gap-1">
              <span class="text-xs text-muted-foreground">耗时</span>
              <span class="font-mono text-xs">{{ selected.durationMs != null ? `${selected.durationMs} ms` : '-' }}</span>
            </div>
          </div>
          <div class="grid gap-1">
            <span class="text-xs text-muted-foreground">User-Agent</span>
            <span class="break-all font-mono text-xs">{{ selected.userAgent || '-' }}</span>
          </div>
          <div v-if="selected.errorMessage" class="grid gap-1">
            <span class="text-xs text-muted-foreground">错误信息</span>
            <span class="rounded-md border border-destructive/40 bg-destructive/5 p-2 font-mono text-xs text-destructive">{{ selected.errorMessage }}</span>
          </div>
          <div class="grid gap-1">
            <span class="text-xs text-muted-foreground">请求内容（敏感字段已脱敏）</span>
            <pre class="max-h-60 overflow-auto rounded-md border bg-muted/30 p-2 font-mono text-xs">{{ selected.payload || '（空）' }}</pre>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
