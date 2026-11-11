<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconTool class="size-5 text-muted-foreground" />
          工具配置
        </h1>
        <p class="text-sm text-muted-foreground">
          内置 web_search 与 web_fetch，按 OpenAI function calling 协议提供给支持工具调用的模型。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Badge v-if="settings.updatedAt" variant="outline" class="h-8 px-3">
          更新于 {{ settings.updatedAt }}
        </Badge>
        <Button variant="outline" size="sm" :disabled="loading || saving" @click="loadSettings">
          <IconRefresh class="mr-1 size-4" />重新加载
        </Button>
        <Button v-if="canUpdate" :disabled="loading || saving || !isDirty" @click="saveSettings">
          <Spinner v-if="saving" class="mr-2" />保存配置
        </Button>
      </div>
    </div>

    <Alert v-if="!canUpdate" variant="destructive">
      <IconAlertTriangle class="size-4" />
      <AlertDescription>当前账户只有查看工具配置的权限，如需修改请联系管理员。</AlertDescription>
    </Alert>
    <Alert v-if="error" variant="destructive">
      <IconAlertTriangle class="size-4" />
      <AlertDescription>{{ error }}</AlertDescription>
    </Alert>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <form v-else class="flex flex-col gap-4" @submit.prevent="saveSettings">
      <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <div class="flex items-center justify-between gap-2">
              <h2 class="text-sm font-semibold">联网搜索服务</h2>
              <Badge v-if="settings.webSearchApiKeyConfigured" variant="secondary">密钥已配置</Badge>
            </div>
            <p class="text-xs text-muted-foreground">
              配置 web_search 使用的搜索服务端点与密钥，并控制 web_search / web_fetch 是否启用。
            </p>
          </header>
          <div class="flex flex-col divide-y">
            <SettingSwitchRow
              title="启用联网搜索"
              description="关闭后所有用户和频道都无法调用 web_search；仍需配置搜索端点与密钥"
              :model-value="form.webSearchEnabled"
              @update:model-value="form.webSearchEnabled = $event"
            />
            <SettingSwitchRow
              title="启用网页抓取"
              description="允许模型通过 web_fetch 抓取单个网页并转为纯文本 / Markdown"
              :model-value="form.webFetchEnabled"
              @update:model-value="form.webFetchEnabled = $event"
            />
          </div>
          <div class="grid gap-4 border-t p-4 md:grid-cols-2">
            <div class="grid gap-2 md:col-span-2">
              <Label for="search-endpoint">搜索端点</Label>
              <Input
                id="search-endpoint"
                v-model="form.webSearchEndpoint"
                autocomplete="off"
                placeholder="https://api.tavily.com/search"
              />
              <p class="text-xs text-muted-foreground">
                兼容 Tavily / SearXNG 等返回 JSON 的搜索服务；未配置时 web_search 工具不会启用。
              </p>
            </div>
            <div class="grid gap-2">
              <Label for="search-api-key">搜索 API Key</Label>
              <Input
                id="search-api-key"
                v-model="form.webSearchApiKey"
                type="password"
                autocomplete="new-password"
                :disabled="form.clearWebSearchApiKey"
                :placeholder="settings.webSearchApiKeyConfigured ? '已配置，留空保持不变' : '请输入搜索 API Key'"
              />
              <p class="text-xs text-muted-foreground">密钥加密存储；留空不修改。</p>
            </div>
            <div class="grid gap-2">
              <Label for="search-max-results">最大结果数</Label>
              <Input
                id="search-max-results"
                v-model.number="form.maxResults"
                type="number"
                min="1"
                max="20"
              />
              <p class="text-xs text-muted-foreground">单次 web_search 返回的结果条数，默认 5，范围 1–20。</p>
            </div>
            <div v-if="settings.webSearchApiKeyConfigured" class="flex items-center gap-2 md:col-span-2">
              <Checkbox
                id="clear-search-api-key"
                :model-value="form.clearWebSearchApiKey"
                @update:model-value="value => (form.clearWebSearchApiKey = value === true)"
              />
              <Label for="clear-search-api-key" class="font-normal">清除已保存的 Key</Label>
            </div>
            <p class="text-xs text-muted-foreground md:col-span-2">
              修改后保存立即生效；未配置搜索端点或密钥时，web_search 工具不会启用，web_fetch 不受影响。
            </p>
          </div>
        </section>
      </fieldset>

      <div class="grid gap-4 md:grid-cols-2">
        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">web_search</h2>
            <p class="text-xs text-muted-foreground">按 query 搜索网页，返回标题、链接与摘要。</p>
          </header>
          <div class="p-4 text-sm text-muted-foreground">
            使用上方「联网搜索服务」中配置的端点与密钥，结果条数由「最大结果数」控制。
          </div>
        </section>
        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">web_fetch</h2>
            <p class="text-xs text-muted-foreground">抓取单个网页并转为纯文本 / Markdown。</p>
          </header>
          <div class="p-4 text-sm text-muted-foreground">
            自动移除脚本和样式，限制返回长度，避免超出模型上下文；由上方「启用网页抓取」开关控制。
          </div>
        </section>
      </div>

      <div class="flex flex-wrap items-center gap-3 rounded-xl border bg-muted/30 px-4 py-3 text-sm">
        <IconInfoCircle class="size-4 text-muted-foreground" />
        <span class="text-muted-foreground">
          联网搜索总开关与「允许用户自行关闭」等权限策略在检索参数配置中维护。
        </span>
        <RouterLink to="/console/system/retrieval" class="ml-auto font-medium text-primary hover:underline">
          前往检索参数配置
        </RouterLink>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { IconAlertTriangle, IconInfoCircle, IconRefresh, IconTool } from '@tabler/icons-vue'
import { toolApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { ToolSettings, UpdateToolSettingsPayload } from '@/types/api'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import SettingSwitchRow from '@/components/console/SettingSwitchRow.vue'

const userStore = useUserStore()
const canUpdate = computed(
  () => userStore.hasPermission('settings:update') || userStore.hasPermission('tool:update'),
)

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const snapshot = ref('')
const settings = reactive<ToolSettings>({
  webSearchEnabled: true,
  webSearchEndpoint: '',
  webSearchApiKeyConfigured: false,
  webFetchEnabled: true,
  maxResults: 5,
})
const form = reactive({
  webSearchEnabled: true,
  webSearchEndpoint: '',
  webSearchApiKey: '',
  clearWebSearchApiKey: false,
  webFetchEnabled: true,
  maxResults: 5,
})

const isDirty = computed(() => JSON.stringify(buildPayload()) !== snapshot.value)

function apply(value: ToolSettings) {
  Object.assign(settings, value)
  form.webSearchEnabled = value.webSearchEnabled ?? true
  form.webSearchEndpoint = value.webSearchEndpoint ?? ''
  form.webSearchApiKey = ''
  form.clearWebSearchApiKey = false
  form.webFetchEnabled = value.webFetchEnabled ?? true
  form.maxResults = value.maxResults ?? 5
  snapshot.value = JSON.stringify(buildPayload())
}

function buildPayload(): UpdateToolSettingsPayload {
  const payload: UpdateToolSettingsPayload = {
    webSearchEnabled: form.webSearchEnabled,
    webFetchEnabled: form.webFetchEnabled,
    maxResults: form.maxResults,
  }
  const endpoint = form.webSearchEndpoint.trim()
  if (endpoint) payload.webSearchEndpoint = endpoint
  if (form.clearWebSearchApiKey) {
    payload.clearWebSearchApiKey = true
  } else {
    const apiKey = form.webSearchApiKey.trim()
    if (apiKey) payload.webSearchApiKey = apiKey
  }
  return payload
}

async function loadSettings() {
  loading.value = true
  error.value = ''
  try {
    apply(await toolApi.getSettings())
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载工具配置失败'
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  if (!Number.isInteger(form.maxResults) || form.maxResults < 1 || form.maxResults > 20) {
    toast.error('最大结果数需为 1–20 之间的整数')
    return
  }
  saving.value = true
  error.value = ''
  try {
    apply(await toolApi.updateSettings(buildPayload()))
    toast.success('工具配置已保存')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存工具配置失败'
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadSettings()
})
</script>
