<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconFilter class="size-5 text-muted-foreground" />
          检索参数配置
        </h1>
        <p class="text-sm text-muted-foreground">
          配置聊天默认模型、向量模型、重排模型与切片/召回参数。修改后新上传文档立即生效。
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Badge v-if="settings.updatedAt" variant="outline" class="h-8 px-3">更新于 {{ settings.updatedAt }}</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="loadAll">
          <IconRefresh class="mr-1 size-4" />重新加载
        </Button>
        <Button v-if="canUpdate" :disabled="saving || !isDirty" @click="save">
          <Spinner v-if="saving" class="mr-2" />保存设置
        </Button>
      </div>
    </div>

    <Alert v-if="!canUpdate" variant="destructive">
      <IconAlertTriangle class="size-4" />
      <AlertDescription>当前账户只有查看权限，无法修改检索参数。</AlertDescription>
    </Alert>
    <Alert v-if="error" variant="destructive">
      <IconAlertTriangle class="size-4" />
      <AlertDescription>{{ error }}</AlertDescription>
    </Alert>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <form v-else @submit.prevent="save">
      <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">模型选择</h2>
            <p class="text-xs text-muted-foreground">聊天模型对用户不可见时，这里仍可作为默认模型使用。</p>
          </header>
          <div class="grid gap-4 p-4 md:grid-cols-2">
            <div class="grid gap-2">
              <Label for="chat-model">默认聊天模型</Label>
              <Select v-model="form.chatModelId">
                <SelectTrigger id="chat-model" class="w-full"><SelectValue placeholder="选择聊天模型" /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="model in chatModels" :key="model.id" :value="String(model.id)">
                    {{ model.providerName }} · {{ model.displayName || model.modelId }}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="grid gap-2">
              <Label for="embedding-model">向量模型</Label>
              <Select v-model="form.embeddingModelId">
                <SelectTrigger id="embedding-model" class="w-full"><SelectValue placeholder="选择向量模型" /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="model in embeddingModels" :key="model.id" :value="String(model.id)">
                    {{ model.providerName }} · {{ model.displayName || model.modelId }}
                  </SelectItem>
                </SelectContent>
              </Select>
              <p class="text-xs text-muted-foreground">
                切换维度后，旧切片向量会被清空并在重新上传时重建。
              </p>
            </div>
          </div>
        </section>

        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">重排</h2>
            <p class="text-xs text-muted-foreground">向量召回 top_k → 重排 → 取 top_n → 拼接提示词。</p>
          </header>
          <div class="flex flex-col divide-y">
            <SettingSwitchRow title="启用重排模型" description="关闭时直接使用向量相似度排序"
              :model-value="form.rerankEnabled" @update:model-value="form.rerankEnabled = $event" />
            <div class="grid gap-2 p-4 md:grid-cols-2">
              <div class="grid gap-2">
                <Label for="rerank-model">重排模型</Label>
                <Select v-model="form.rerankModelId">
                  <SelectTrigger id="rerank-model" class="w-full"><SelectValue placeholder="选择重排模型" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem v-for="model in rerankModels" :key="model.id" :value="String(model.id)">
                      {{ model.providerName }} · {{ model.displayName || model.modelId }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
        </section>

        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">切片与召回</h2>
            <p class="text-xs text-muted-foreground">chunk 策略用于上传后的向量化；召回参数用于聊天检索。</p>
          </header>
          <div class="grid gap-4 p-4 md:grid-cols-2">
            <div class="grid gap-2">
              <Label for="chunk-size">chunk_size</Label>
              <Input id="chunk-size" v-model.number="form.chunkSize" type="number" min="100" max="8000" />
              <p class="text-xs text-muted-foreground">单个切片的目标字符数（100–8000）。</p>
            </div>
            <div class="grid gap-2">
              <Label for="chunk-overlap">chunk_overlap</Label>
              <Input id="chunk-overlap" v-model.number="form.chunkOverlap" type="number" min="0" max="2000" />
              <p class="text-xs text-muted-foreground">相邻切片重叠字符数，必须小于 chunk_size。</p>
            </div>
            <div class="grid gap-2">
              <Label for="top-k">向量召回 top_k</Label>
              <Input id="top-k" v-model.number="form.retrievalTopK" type="number" min="1" max="100" />
            </div>
            <div class="grid gap-2">
              <Label for="top-n">重排后 top_n</Label>
              <Input id="top-n" v-model.number="form.retrievalTopN" type="number" min="1" max="50" />
            </div>
          </div>
        </section>

        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">网页工具</h2>
            <p class="text-xs text-muted-foreground">
              web_search / web_fetch 使用 OpenAI function calling 协议；这里配置搜索服务端点与密钥。
            </p>
          </header>
          <div class="grid gap-4 p-4 md:grid-cols-2">
            <div class="grid gap-2">
              <Label for="search-endpoint">搜索端点</Label>
              <Input id="search-endpoint" v-model="form.webSearchEndpoint"
                placeholder="留空则使用 DuckDuckGo 网页搜索" />
            </div>
            <div class="grid gap-2">
              <Label for="search-key">搜索 API Key</Label>
              <Input id="search-key" v-model="form.webSearchApiKey" type="password"
                :placeholder="settings.webSearchApiKeyConfigured ? '已配置，留空保持不变' : '可选'" />
              <p class="text-xs text-muted-foreground">AES-GCM 加密存储；留空不修改。</p>
            </div>
          </div>
        </section>
      </fieldset>
    </form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import {
  IconAlertTriangle, IconFilter, IconRefresh,
} from '@tabler/icons-vue'
import { modelApi, ragApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { ModelConfig, RagSettings, UpdateRagSettingsPayload } from '@/types/api'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import SettingSwitchRow from '@/components/console/SettingSwitchRow.vue'

const userStore = useUserStore()
const canUpdate = computed(() => userStore.hasPermission('rag:update') || userStore.hasPermission('settings:update'))

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const settings = reactive<RagSettings>({
  chatModelId: undefined, embeddingModelId: undefined, rerankEnabled: false, rerankModelId: undefined,
  chunkSize: 1200, chunkOverlap: 120, retrievalTopK: 20, retrievalTopN: 5,
  webSearchEndpoint: '', webSearchApiKeyConfigured: false,
})
const form = reactive({
  chatModelId: '', embeddingModelId: '', rerankEnabled: false, rerankModelId: '',
  chunkSize: 1200, chunkOverlap: 120, retrievalTopK: 20, retrievalTopN: 5,
  webSearchEndpoint: '', webSearchApiKey: '',
})
const models = ref<ModelConfig[]>([])
const snapshot = ref('')

const chatModels = computed(() => models.value.filter(item => item.modelType === 'CHAT' && item.enabled))
const embeddingModels = computed(() => models.value.filter(item => item.modelType === 'EMBEDDING' && item.enabled))
const rerankModels = computed(() => models.value.filter(item => item.modelType === 'RERANK' && item.enabled))
const isDirty = computed(() => JSON.stringify(buildPayload()) !== snapshot.value)

function apply(value: RagSettings) {
  Object.assign(settings, value)
  form.chatModelId = value.chatModelId ? String(value.chatModelId) : ''
  form.embeddingModelId = value.embeddingModelId ? String(value.embeddingModelId) : ''
  form.rerankEnabled = value.rerankEnabled
  form.rerankModelId = value.rerankModelId ? String(value.rerankModelId) : ''
  form.chunkSize = value.chunkSize
  form.chunkOverlap = value.chunkOverlap
  form.retrievalTopK = value.retrievalTopK
  form.retrievalTopN = value.retrievalTopN
  form.webSearchEndpoint = value.webSearchEndpoint ?? ''
  form.webSearchApiKey = ''
  snapshot.value = JSON.stringify(buildPayload())
}

function buildPayload(): UpdateRagSettingsPayload {
  return {
    chatModelId: form.chatModelId ? Number(form.chatModelId) : undefined,
    embeddingModelId: form.embeddingModelId ? Number(form.embeddingModelId) : undefined,
    rerankEnabled: form.rerankEnabled,
    rerankModelId: form.rerankModelId ? Number(form.rerankModelId) : undefined,
    chunkSize: form.chunkSize,
    chunkOverlap: form.chunkOverlap,
    retrievalTopK: form.retrievalTopK,
    retrievalTopN: form.retrievalTopN,
    webSearchEndpoint: form.webSearchEndpoint.trim() || undefined,
    webSearchApiKey: form.webSearchApiKey.trim() || undefined,
  }
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [value, list] = await Promise.all([ragApi.settings(), modelApi.configs()])
    models.value = list
    apply(value)
  } catch (err: any) {
    error.value = err?.message || '加载检索参数失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.embeddingModelId) {
    toast.error('请选择向量模型')
    return
  }
  if (form.rerankEnabled && !form.rerankModelId) {
    toast.error('启用重排后必须选择重排模型')
    return
  }
  if (form.chunkOverlap >= form.chunkSize) {
    toast.error('chunk_overlap 必须小于 chunk_size')
    return
  }
  if (form.retrievalTopN > form.retrievalTopK) {
    toast.error('top_n 不能大于 top_k')
    return
  }
  saving.value = true
  try {
    const updated = await ragApi.update(buildPayload())
    apply(updated)
    toast.success('检索参数已保存')
  } catch (err: any) {
    toast.error(err?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => void loadAll())
</script>
