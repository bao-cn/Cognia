<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { IconActivity, IconCheck, IconClock, IconList, IconRefresh, IconShieldCheck, IconX } from '@tabler/icons-vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { Switch } from '@/components/ui/switch'
import { traceApi } from '@/api'
import { useDebugStore } from '@/stores/debug'
import { useUserStore } from '@/stores/user'
import type { TraceStats } from '@/types/api'

const debugStore = useDebugStore()
const userStore = useUserStore()

const canRead = computed(() => userStore.isAdmin || userStore.hasPermission('trace:read') || userStore.hasPermission('settings:read'))
const canWrite = computed(() => userStore.isAdmin || userStore.hasPermission('trace:write') || userStore.hasPermission('settings:update'))
const stats = ref<TraceStats | null>(null)
const loading = ref(false)
const saving = ref(false)

async function loadStats() {
  if (!canRead.value) return
  loading.value = true
  try {
    stats.value = await traceApi.stats()
  } catch (err: any) {
    toast.error(err?.message || '统计加载失败')
  } finally {
    loading.value = false
  }
}

async function toggle(value: boolean) {
  saving.value = true
  try {
    await debugStore.setEnabled(value)
    toast.success(value ? `开发模式已开启，将记录 ${debugStore.hardcodedUsername} 的全部对话` : '开发模式已关闭')
  } catch (err: any) {
    toast.error(err?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void debugStore.load()
  void loadStats()
})
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconActivity class="size-5 text-muted-foreground" />开发模式
        </h1>
        <p class="text-sm text-muted-foreground">
          开启后自动记录硬编码管理员（{{ debugStore.hardcodedUsername }}）的全部对话链路。明细在前台悬浮按钮中查看。
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Badge variant="outline" class="h-8 px-3">{{ debugStore.enabled ? '已开启' : '已关闭' }}</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="loadStats">
          <IconRefresh class="mr-1 size-4" />刷新
        </Button>
      </div>
    </div>

    <div v-if="!canRead" class="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
      <IconShieldCheck class="mt-0.5 size-4 shrink-0" />
      当前账户没有查看开发链路的权限。
    </div>

    <section class="rounded-xl border bg-card">
      <header class="flex flex-col gap-1 border-b px-4 py-3">
        <h2 class="text-sm font-semibold">全局开关</h2>
        <p class="text-xs text-muted-foreground">只对硬编码管理员生效，普通用户不会记录任何链路数据。</p>
      </header>
      <div class="flex flex-wrap items-center justify-between gap-3 p-4">
        <div class="flex flex-col gap-1">
          <span class="text-sm font-medium">记录 {{ debugStore.hardcodedUsername }} 的全部对话</span>
          <span class="text-xs text-muted-foreground">
            开启后，该用户的每次对话都会生成 trace，并在前台显示悬浮入口；无需在输入框手动开启。
          </span>
        </div>
        <div class="flex items-center gap-3">
          <Spinner v-if="saving" class="size-4" />
          <Switch :model-value="debugStore.enabled" :disabled="!canWrite || saving" @update:model-value="toggle(Boolean($event))" />
        </div>
      </div>
    </section>

    <section class="rounded-xl border bg-card">
      <header class="flex flex-col gap-1 border-b px-4 py-3">
        <h2 class="text-sm font-semibold">链路统计</h2>
        <p class="text-xs text-muted-foreground">全量 trace 的汇总信息；单条明细请在前台悬浮抽屉中查看。</p>
      </header>
      <div v-if="loading" class="flex h-32 items-center justify-center">
        <Spinner class="size-6" />
      </div>
      <div v-else class="grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
        <div class="rounded-lg border bg-muted/20 p-3">
          <div class="flex items-center gap-2 text-xs text-muted-foreground"><IconList class="size-3.5" />总记录</div>
          <p class="mt-1 text-2xl font-semibold">{{ stats?.total ?? 0 }}</p>
        </div>
        <div class="rounded-lg border bg-muted/20 p-3">
          <div class="flex items-center gap-2 text-xs text-muted-foreground"><IconClock class="size-3.5" />今日</div>
          <p class="mt-1 text-2xl font-semibold">{{ stats?.today ?? 0 }}</p>
        </div>
        <div class="rounded-lg border bg-muted/20 p-3">
          <div class="flex items-center gap-2 text-xs text-muted-foreground"><IconActivity class="size-3.5" />平均耗时</div>
          <p class="mt-1 text-2xl font-semibold">{{ stats?.avgDurationMs ?? 0 }} <span class="text-sm font-normal text-muted-foreground">ms</span></p>
        </div>
        <div class="rounded-lg border bg-emerald-500/5 p-3">
          <div class="flex items-center gap-2 text-xs text-muted-foreground"><IconCheck class="size-3.5" />成功</div>
          <p class="mt-1 text-2xl font-semibold text-emerald-600">{{ stats?.success ?? 0 }}</p>
        </div>
        <div class="rounded-lg border bg-destructive/5 p-3">
          <div class="flex items-center gap-2 text-xs text-muted-foreground"><IconX class="size-3.5" />失败</div>
          <p class="mt-1 text-2xl font-semibold text-destructive">{{ stats?.failed ?? 0 }}</p>
        </div>
        <div class="rounded-lg border bg-muted/20 p-3">
          <div class="flex items-center gap-2 text-xs text-muted-foreground"><IconActivity class="size-3.5" />截断</div>
          <p class="mt-1 text-2xl font-semibold">{{ stats?.truncated ?? 0 }}</p>
        </div>
      </div>
      <div class="border-t px-4 py-3 text-xs text-muted-foreground">
        提示：前台右下角的「开发链路」悬浮按钮会打开全部记录，支持搜索、查看详情、重跑检索 / LLM。
      </div>
    </section>
  </div>
</template>
