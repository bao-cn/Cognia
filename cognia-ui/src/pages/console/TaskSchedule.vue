<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { taskApi } from '@/api'
import type { ScheduledTask, UpdateScheduledTaskPayload } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  IconAlertTriangle,
  IconCalendarClock,
  IconCheck,
  IconClock,
  IconCloud,
  IconDatabase,
  IconDeviceFloppy,
  IconFileAlert,
  IconInfoCircle,
  IconPlayerPlay,
  IconRefresh,
  IconTrash,
  IconUsers,
} from '@tabler/icons-vue'

const userStore = useUserStore()
const canUpdate = computed(() => userStore.hasPermission('task:update') || userStore.hasPermission('settings:update'))

const loading = ref(true)
const error = ref('')
const tasks = ref<ScheduledTask[]>([])
const savingCode = ref('')
const runningCode = ref('')
const baselines = reactive<Record<string, string>>({})

function messageOf(errorValue: unknown): string {
  return errorValue instanceof Error ? errorValue.message : '操作失败，请稍后重试'
}

function payloadOf(task: ScheduledTask): UpdateScheduledTaskPayload {
  const payload: UpdateScheduledTaskPayload = {
    enabled: task.enabled,
    cronExpression: task.cronExpression.trim(),
  }
  if (task.code === 'DOCUMENT_TIMEOUT_SCAN') {
    payload.thresholdMinutes = task.thresholdMinutes
    payload.batchSize = task.batchSize
  } else if (task.code === 'MULTIPART_CLEANUP') {
    payload.olderThanHours = task.olderThanHours
    payload.batchSize = task.batchSize
  } else if (task.code === 'RECYCLE_BIN_CLEANUP') {
    payload.retentionDays = task.retentionDays
    payload.batchSize = task.batchSize
  } else if (task.code === 'DELETED_OWNER_CLEANUP') {
    payload.batchSize = task.batchSize
  } else if (task.code === 'STORAGE_LIFECYCLE_SYNC') {
    payload.retentionDays = task.retentionDays
  }
  return payload
}

function isDirty(task: ScheduledTask): boolean {
  return baselines[task.code] !== JSON.stringify(payloadOf(task))
}

function remember(task: ScheduledTask): void {
  baselines[task.code] = JSON.stringify(payloadOf(task))
}

function iconFor(code: string) {
  switch (code) {
    case 'DOCUMENT_TIMEOUT_SCAN':
      return IconFileAlert
    case 'MULTIPART_CLEANUP':
      return IconDatabase
    case 'RECYCLE_BIN_CLEANUP':
      return IconTrash
    case 'DELETED_OWNER_CLEANUP':
      return IconUsers
    case 'STORAGE_LIFECYCLE_SYNC':
      return IconCloud
    default:
      return IconCalendarClock
  }
}

function scheduleHint(code: string): string {
  switch (code) {
    case 'DOCUMENT_TIMEOUT_SCAN':
      return '扫描处于「待处理 / 处理中」且长时间未更新的文档，超时后标记为失败并通知上传者手动重试。'
    case 'MULTIPART_CLEANUP':
      return '清理对象存储 kb/ 前缀下超过阈值仍未完成的分片上传，避免残留分片长期占用空间。'
    case 'RECYCLE_BIN_CLEANUP':
      return '永久清理回收站中超过保留天数的文件，同时删除对象、文档记录、向量与回收站记录。'
    case 'DELETED_OWNER_CLEANUP':
      return '清理已注销用户或已解散团队遗留的对象存储文件，全部完成后写入清理标记。'
    case 'STORAGE_LIFECYCLE_SYNC':
      return '将临时目录 tmp/ 的过期天数同步为桶生命周期规则，由对象存储自动清理临时文件。'
    default:
      return ''
  }
}

function retentionLabel(code: string): string {
  if (code === 'RECYCLE_BIN_CLEANUP') return '回收站保留天数'
  if (code === 'STORAGE_LIFECYCLE_SYNC') return '临时文件保留天数'
  return '保留天数'
}

function statusVariant(status?: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  if (status === 'SUCCESS') return 'default'
  if (status === 'FAILED') return 'destructive'
  return 'outline'
}

function statusLabel(status?: string): string {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  return '未运行'
}

function validate(task: ScheduledTask): string | null {
  const cron = task.cronExpression.trim()
  if (!cron) return 'Cron 表达式不能为空'
  if (cron.split(/\s+/).length !== 6) return 'Cron 表达式需要 6 个字段（秒 分 时 日 月 周）'
  if (task.code === 'DOCUMENT_TIMEOUT_SCAN' && (!task.thresholdMinutes || task.thresholdMinutes < 1)) {
    return '超时阈值必须大于 0 分钟'
  }
  if (task.code === 'MULTIPART_CLEANUP' && (!task.olderThanHours || task.olderThanHours < 1)) {
    return '残留分片超时时间必须大于 0 小时'
  }
  if ((task.code === 'RECYCLE_BIN_CLEANUP' || task.code === 'STORAGE_LIFECYCLE_SYNC')
    && (!task.retentionDays || task.retentionDays < 1)) {
    return '保留天数必须大于 0'
  }
  if (task.code !== 'STORAGE_LIFECYCLE_SYNC' && (!task.batchSize || task.batchSize < 1)) {
    return '单次处理上限必须大于 0'
  }
  return null
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    tasks.value = await taskApi.list()
    for (const task of tasks.value) remember(task)
  } catch (errorValue) {
    error.value = messageOf(errorValue)
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}

async function saveTask(task: ScheduledTask, silent = false): Promise<void> {
  if (!canUpdate.value) return
  const invalid = validate(task)
  if (invalid) {
    toast.error(invalid)
    return
  }
  savingCode.value = task.code
  try {
    const updated = await taskApi.update(task.code, payloadOf(task))
    Object.assign(task, updated)
    remember(task)
    if (!silent) toast.success(`${task.name}已保存`)
  } catch (errorValue) {
    toast.error(messageOf(errorValue))
    await load()
  } finally {
    savingCode.value = ''
  }
}

async function toggleTask(task: ScheduledTask, enabled: boolean): Promise<void> {
  task.enabled = enabled
  await saveTask(task, true)
}

async function runTask(task: ScheduledTask): Promise<void> {
  if (!canUpdate.value) return
  runningCode.value = task.code
  try {
    const result = await taskApi.run(task.code)
    if (result.status === 'SUCCESS') {
      toast.success(`${task.name}执行成功：${result.message}`)
    } else {
      toast.error(`${task.name}执行失败：${result.message}`)
    }
    await load()
  } catch (errorValue) {
    toast.error(messageOf(errorValue))
  } finally {
    runningCode.value = ''
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconCalendarClock class="size-5 text-muted-foreground" />
          计划任务
        </h1>
        <p class="text-sm text-muted-foreground">
          预设的系统巡检与清理任务。任务由系统提供，管理员可调整启用状态、执行周期与阈值参数。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Badge variant="outline" class="h-8 px-3">{{ tasks.length }} 个预设任务</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="load">
          <IconRefresh class="mr-1 size-4" />
          刷新
        </Button>
      </div>
    </div>

    <Alert>
      <IconInfoCircle class="size-4" />
      <AlertDescription>
        仅支持以下预设任务，不提供自定义任务。Cron 表达式为 6 段格式（秒 分 时 日 月 周），修改后由调度器在 30 秒内生效。
      </AlertDescription>
    </Alert>

    <Alert v-if="!canUpdate">
      <IconAlertTriangle class="size-4" />
      <AlertDescription>当前账户只有查看计划任务的权限，无法修改配置或立即执行。</AlertDescription>
    </Alert>

    <Alert v-if="error" variant="destructive">
      <AlertDescription>{{ error }}</AlertDescription>
    </Alert>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <div v-else class="grid gap-4 xl:grid-cols-2">
      <section v-for="task in tasks" :key="task.code" class="flex flex-col rounded-xl border bg-card">
        <header class="flex flex-wrap items-start justify-between gap-3 border-b px-4 py-3">
          <div class="flex min-w-0 items-start gap-3">
            <div class="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted">
              <component :is="iconFor(task.code)" class="size-5 text-muted-foreground" />
            </div>
            <div class="min-w-0">
              <h2 class="flex flex-wrap items-center gap-2 text-sm font-semibold">
                {{ task.name }}
                <Badge variant="outline" class="font-mono text-[10px] font-normal">{{ task.code }}</Badge>
              </h2>
              <p class="mt-1 text-xs text-muted-foreground">{{ task.description || scheduleHint(task.code) }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <Badge :variant="task.enabled ? 'default' : 'secondary'">{{ task.enabled ? '已启用' : '已停用' }}</Badge>
            <Switch
              :model-value="task.enabled"
              :disabled="!canUpdate || savingCode === task.code"
              @update:model-value="(value: boolean) => toggleTask(task, value)"
            />
          </div>
        </header>

        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-1 flex-col gap-4 border-0 p-4">
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="grid gap-2 sm:col-span-2">
              <Label :for="`cron-${task.code}`">Cron 表达式</Label>
              <Input
                :id="`cron-${task.code}`"
                v-model="task.cronExpression"
                class="font-mono"
                placeholder="0 */5 * * * *"
              />
              <p class="text-xs text-muted-foreground">示例：每 5 分钟 `0 */5 * * * *`，每天 04:00 `0 0 4 * * *`。</p>
            </div>

            <div v-if="task.code === 'DOCUMENT_TIMEOUT_SCAN'" class="grid gap-2">
              <Label :for="`threshold-${task.code}`">超时阈值（分钟）</Label>
              <Input
                :id="`threshold-${task.code}`"
                v-model.number="task.thresholdMinutes"
                type="number"
                min="1"
                max="10080"
              />
              <p class="text-xs text-muted-foreground">文档停留在处理中或待处理超过该时长即判定为卡死。</p>
            </div>

            <div v-if="task.code === 'MULTIPART_CLEANUP'" class="grid gap-2">
              <Label :for="`older-${task.code}`">残留分片超时（小时）</Label>
              <Input
                :id="`older-${task.code}`"
                v-model.number="task.olderThanHours"
                type="number"
                min="1"
                max="8760"
              />
              <p class="text-xs text-muted-foreground">仅清理初始化时间早于该阈值的未完成分片上传。</p>
            </div>

            <div
              v-if="task.code === 'RECYCLE_BIN_CLEANUP' || task.code === 'STORAGE_LIFECYCLE_SYNC'"
              class="grid gap-2"
            >
              <Label :for="`retention-${task.code}`">{{ retentionLabel(task.code) }}</Label>
              <Input
                :id="`retention-${task.code}`"
                v-model.number="task.retentionDays"
                type="number"
                min="1"
                max="3650"
              />
              <p class="text-xs text-muted-foreground">
                {{ task.code === 'RECYCLE_BIN_CLEANUP'
                  ? '回收站中的文件超过该天数后永久删除。'
                  : '同步为 tmp/ 前缀的生命周期过期天数，由对象存储自动清理。' }}
              </p>
            </div>

            <div v-if="task.code !== 'STORAGE_LIFECYCLE_SYNC'" class="grid gap-2">
              <Label :for="`batch-${task.code}`">单次处理上限</Label>
              <Input
                :id="`batch-${task.code}`"
                v-model.number="task.batchSize"
                type="number"
                min="1"
                max="1000"
              />
              <p class="text-xs text-muted-foreground">单次执行最多处理的对象数量，避免长时间占用资源。</p>
            </div>
          </div>

          <div class="mt-auto flex flex-wrap items-center justify-between gap-2 border-t pt-3 text-xs text-muted-foreground">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
              <Badge :variant="statusVariant(task.lastStatus)">
                {{ statusLabel(task.lastStatus) }}
              </Badge>
              <span v-if="task.lastRunAt" class="inline-flex items-center gap-1">
                <IconClock class="size-3.5" />
                {{ task.lastRunAt }}
              </span>
              <span v-if="task.lastDurationMs != null">{{ task.lastDurationMs }} ms</span>
            </div>
            <div class="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                :disabled="runningCode === task.code || !canUpdate"
                @click="runTask(task)"
              >
                <Spinner v-if="runningCode === task.code" class="mr-1 size-3.5" />
                <IconPlayerPlay v-else class="mr-1 size-3.5" />
                立即执行
              </Button>
              <Button
                size="sm"
                :disabled="savingCode === task.code || !canUpdate || !isDirty(task)"
                @click="saveTask(task)"
              >
                <Spinner v-if="savingCode === task.code" class="mr-1 size-3.5" />
                <IconDeviceFloppy v-else class="mr-1 size-3.5" />
                保存
              </Button>
            </div>
          </div>

          <p
            v-if="task.lastMessage"
            class="rounded-md border bg-muted/30 px-3 py-2 text-xs"
            :class="task.lastStatus === 'FAILED' ? 'border-destructive/40 text-destructive' : 'text-muted-foreground'"
          >
            <IconCheck v-if="task.lastStatus === 'SUCCESS'" class="mr-1 inline size-3.5" />
            最近结果：{{ task.lastMessage }}
          </p>
        </fieldset>
      </section>
    </div>
  </div>
</template>
