<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconBrain class="size-5 text-muted-foreground" />
          模型配置
        </h1>
        <p class="text-sm text-muted-foreground">
          接入任意 OpenAI 兼容供应商。密钥使用 AES-GCM 加密存储，用户只能使用管理员开放的模型。
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Badge variant="outline" class="h-8 px-3">{{ providers.length }} 个供应商</Badge>
        <Badge variant="outline" class="h-8 px-3">{{ configs.length }} 个模型</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="loadAll">
          <IconRefresh class="mr-1 size-4" />重新加载
        </Button>
        <Button v-if="canUpdate" size="sm" @click="openProviderDialog()">
          <IconPlus class="mr-1 size-4" />添加供应商
        </Button>
      </div>
    </div>

    <Alert v-if="!canUpdate" variant="destructive">
      <IconAlertTriangle class="size-4" />
      <AlertDescription>当前账户只有查看权限，无法修改模型配置。</AlertDescription>
    </Alert>
    <Alert v-if="error" variant="destructive">
      <IconAlertTriangle class="size-4" />
      <AlertDescription>{{ error }}</AlertDescription>
    </Alert>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <Tabs v-else v-model="activeTab" class="gap-4">
      <TabsList class="h-10 w-full justify-start overflow-x-auto bg-muted/60 p-1">
        <TabsTrigger value="providers" class="flex-none px-3"><IconServer class="size-4" />供应商</TabsTrigger>
        <TabsTrigger value="models" class="flex-none px-3"><IconCpu class="size-4" />模型列表</TabsTrigger>
      </TabsList>

      <TabsContent value="providers">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <div v-if="providers.length === 0" class="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed py-16">
            <IconPlug class="size-10 text-muted-foreground" />
            <p class="text-sm text-muted-foreground">还没有供应商，添加一个 OpenAI 兼容端点开始。</p>
            <Button size="sm" @click="openProviderDialog()"><IconPlus class="mr-1 size-4" />添加供应商</Button>
          </div>
          <div v-else class="grid gap-4 xl:grid-cols-2">
            <section v-for="provider in providers" :key="provider.id" class="flex flex-col rounded-xl border bg-card">
              <header class="flex flex-wrap items-start justify-between gap-2 border-b px-4 py-3">
                <div class="min-w-0">
                  <div class="flex items-center gap-2">
                    <h2 class="truncate text-sm font-semibold">{{ provider.name }}</h2>
                    <Badge :variant="provider.enabled ? 'default' : 'secondary'">
                      {{ provider.enabled ? '已启用' : '已停用' }}
                    </Badge>
                  </div>
                  <p class="mt-0.5 truncate text-xs text-muted-foreground">{{ provider.baseUrl }}</p>
                </div>
                <div class="flex items-center gap-1">
                  <Button variant="ghost" size="icon-sm" title="编辑" @click="openProviderDialog(provider)">
                    <IconPencil class="size-4" />
                  </Button>
                  <Button variant="ghost" size="icon-sm" title="删除" :disabled="saving" @click="removeProvider(provider)">
                    <IconTrash class="size-4" />
                  </Button>
                </div>
              </header>
              <div class="grid gap-3 p-4 text-sm sm:grid-cols-2">
                <div>
                  <p class="text-xs text-muted-foreground">厂商适配</p>
                  <p class="font-medium">{{ provider.vendor || '自定义' }}</p>
                </div>
                <div>
                  <p class="text-xs text-muted-foreground">API Key</p>
                  <Badge :variant="provider.apiKeyConfigured ? 'outline' : 'secondary'">
                    {{ provider.apiKeyConfigured ? '已配置' : '未配置' }}
                  </Badge>
                </div>
                <div>
                  <p class="text-xs text-muted-foreground">模型数量</p>
                  <p class="font-medium">{{ provider.modelCount }}</p>
                </div>
                <div>
                  <p class="text-xs text-muted-foreground">更新时间</p>
                  <p class="font-medium">{{ provider.updatedAt || '—' }}</p>
                </div>
              </div>
              <footer class="mt-auto flex flex-wrap gap-2 border-t px-4 py-3">
                <Button variant="outline" size="sm" :disabled="saving" @click="openFetch(provider)">
                  <IconCloudDownload class="mr-1 size-4" />拉取模型
                </Button>
                <Button variant="outline" size="sm" @click="activeTab = 'models'">
                  <IconList class="mr-1 size-4" />查看模型
                </Button>
              </footer>
            </section>
          </div>
        </fieldset>
      </TabsContent>

      <TabsContent value="models">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">已配置模型</h2>
              <p class="text-xs text-muted-foreground">
                「对用户可见」决定普通用户能否在聊天中选择该模型；向量/重排模型由检索参数配置引用。
              </p>
            </header>
            <div class="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>模型</TableHead>
                    <TableHead>供应商</TableHead>
                    <TableHead>类型</TableHead>
                    <TableHead>启用</TableHead>
                    <TableHead>用户可见</TableHead>
                    <TableHead>工具</TableHead>
                    <TableHead>思考</TableHead>
                    <TableHead>推理强度</TableHead>
                    <TableHead class="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow v-if="configs.length === 0">
                    <TableCell :colspan="9" class="h-24 text-center text-sm text-muted-foreground">
                      暂无模型，先在供应商中拉取模型。
                    </TableCell>
                  </TableRow>
                  <TableRow v-for="model in configs" :key="model.id">
                    <TableCell>
                      <div class="flex flex-col">
                        <span class="font-medium">{{ model.displayName || model.modelId }}</span>
                        <span class="text-xs text-muted-foreground">{{ model.modelId }}</span>
                      </div>
                    </TableCell>
                    <TableCell>{{ model.providerName }}</TableCell>
                    <TableCell><Badge variant="outline">{{ typeLabel(model.modelType) }}</Badge></TableCell>
                    <TableCell><Switch :model-value="model.enabled" @update:model-value="toggle(model, 'enabled', $event)" /></TableCell>
                    <TableCell><Switch :model-value="model.visible" @update:model-value="toggle(model, 'visible', $event)" /></TableCell>
                    <TableCell><Switch :model-value="model.supportsTools" @update:model-value="toggle(model, 'supportsTools', $event)" /></TableCell>
                    <TableCell><Switch :model-value="model.thinkingEnabled" @update:model-value="toggle(model, 'thinkingEnabled', $event)" /></TableCell>
                    <TableCell>{{ model.reasoningEffort || '—' }}</TableCell>
                    <TableCell class="text-right">
                      <Button variant="ghost" size="icon-sm" title="编辑" @click="openModelDialog(model)">
                        <IconPencil class="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" title="删除" @click="removeModel(model)">
                        <IconTrash class="size-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </div>
          </section>
        </fieldset>
      </TabsContent>
    </Tabs>

    <Dialog v-model:open="providerDialogOpen">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ editingProvider ? '编辑供应商' : '添加供应商' }}</DialogTitle>
          <DialogDescription>
            支持 OpenAI Chat Completions 兼容接口；填写端点与 Key 后可拉取模型。
          </DialogDescription>
        </DialogHeader>
        <div class="grid gap-4">
          <div class="grid gap-2">
            <Label for="provider-name">名称</Label>
            <Input id="provider-name" v-model="providerForm.name" placeholder="例如 DeepSeek" />
          </div>
          <div class="grid gap-2">
            <Label for="provider-vendor">厂商适配</Label>
            <Select v-model="providerForm.vendor">
              <SelectTrigger id="provider-vendor" class="w-full"><SelectValue placeholder="自定义" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="OPENAI">OpenAI 标准</SelectItem>
                <SelectItem value="DEEPSEEK">DeepSeek</SelectItem>
                <SelectItem value="QWEN">通义千问</SelectItem>
                <SelectItem value="CUSTOM">自定义</SelectItem>
              </SelectContent>
            </Select>
            <p class="text-xs text-muted-foreground">
              选择厂商后，系统会自动注入 thinking / reasoning_effort 等兼容参数。
            </p>
          </div>
          <div class="grid gap-2">
            <Label for="provider-base">API 端点</Label>
            <Input id="provider-base" v-model="providerForm.baseUrl" placeholder="https://api.deepseek.com" />
          </div>
          <div class="grid gap-2">
            <Label for="provider-key">API Key</Label>
            <Input id="provider-key" v-model="providerForm.apiKey" type="password"
              :placeholder="editingProvider?.apiKeyConfigured ? '已配置，留空保持不变' : 'sk-...'" />
            <p class="text-xs text-muted-foreground">密钥仅存储加密后的密文，接口不会返回明文。</p>
          </div>
          <SettingSwitchRow title="启用供应商" description="停用后其下全部模型不可用" :model-value="providerForm.enabled"
            @update:model-value="providerForm.enabled = $event" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="providerDialogOpen = false">取消</Button>
          <Button :disabled="saving" @click="saveProvider">
            <Spinner v-if="saving" class="mr-2" />保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="fetchDialogOpen">
      <DialogContent class="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>拉取模型 · {{ fetchProvider?.name }}</DialogTitle>
          <DialogDescription>从供应商 /models 接口读取并选择需要接入的模型。</DialogDescription>
        </DialogHeader>
        <div class="flex items-center gap-3">
          <Label class="text-xs">添加到</Label>
          <Select v-model="bulkType">
            <SelectTrigger class="w-40"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="CHAT">聊天模型</SelectItem>
              <SelectItem value="EMBEDDING">向量模型</SelectItem>
              <SelectItem value="RERANK">重排模型</SelectItem>
            </SelectContent>
          </Select>
          <span class="text-xs text-muted-foreground">已选 {{ selectedRemote.length }} 个</span>
        </div>
        <div class="max-h-72 overflow-y-auto rounded-lg border">
          <div v-if="fetching" class="flex h-40 items-center justify-center"><Spinner /></div>
          <div v-else-if="remoteModels.length === 0" class="p-8 text-center text-sm text-muted-foreground">
            未获取到模型，请检查端点与 Key。
          </div>
          <label v-for="item in remoteModels" :key="item.id"
            class="flex cursor-pointer items-center gap-3 border-b px-3 py-2 text-sm last:border-b-0 hover:bg-muted/50">
            <input v-model="selectedRemote" type="checkbox" :value="item.id" class="size-4 accent-primary">
            <span class="min-w-0 flex-1 truncate">{{ item.id }}</span>
            <span v-if="item.ownedBy" class="text-xs text-muted-foreground">{{ item.ownedBy }}</span>
            <Badge v-if="existingModelIds.has(item.id)" variant="secondary">已添加</Badge>
          </label>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="fetchDialogOpen = false">取消</Button>
          <Button :disabled="saving || selectedRemote.length === 0" @click="addSelectedModels">
            <Spinner v-if="saving" class="mr-2" />添加所选
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="modelDialogOpen">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>编辑模型</DialogTitle>
          <DialogDescription>为单个模型配置可见性、思考模式与厂商扩展参数。</DialogDescription>
        </DialogHeader>
        <div class="grid gap-4">
          <div class="grid gap-2">
            <Label for="model-name">显示名称</Label>
            <Input id="model-name" v-model="modelForm.displayName" />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="grid gap-2">
              <Label>模型类型</Label>
              <Select v-model="modelForm.modelType">
                <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="CHAT">聊天模型</SelectItem>
                  <SelectItem value="EMBEDDING">向量模型</SelectItem>
                  <SelectItem value="RERANK">重排模型</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="grid gap-2">
              <Label>向量维度</Label>
              <Input v-model.number="modelForm.dimension" type="number" placeholder="1536" />
            </div>
          </div>
          <div class="grid gap-2">
            <Label>最大上下文长度（tokens）</Label>
            <Input v-model.number="modelForm.contextWindow" type="number" min="1000" max="2000000" placeholder="32000" />
            <p class="text-xs text-muted-foreground">用于滑动窗口裁剪与上下文压缩阈值计算，聊天模型必填。</p>
          </div>
          <div class="grid gap-2">
            <Label>推理强度</Label>
            <Select v-model="modelForm.reasoningEffort">
              <SelectTrigger class="w-full"><SelectValue placeholder="不设置" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="low">low</SelectItem>
                <SelectItem value="medium">medium</SelectItem>
                <SelectItem value="high">high</SelectItem>
                <SelectItem value="max">max</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="grid gap-2">
            <Label for="model-extra">额外请求参数（JSON）</Label>
            <Textarea id="model-extra" v-model="modelForm.extraBody" rows="4"
              placeholder='{"thinking":{"type":"enabled"}}' />
          </div>
          <SettingSwitchRow title="启用" :model-value="modelForm.enabled"
            @update:model-value="modelForm.enabled = $event" />
          <SettingSwitchRow title="对用户可见" :model-value="modelForm.visible"
            @update:model-value="modelForm.visible = $event" />
          <SettingSwitchRow title="支持工具调用" :model-value="modelForm.supportsTools"
            @update:model-value="modelForm.supportsTools = $event" />
          <SettingSwitchRow title="默认开启思考" :model-value="modelForm.thinkingEnabled"
            @update:model-value="modelForm.thinkingEnabled = $event" />
          <SettingSwitchRow title="支持图片输入（视觉）" description="仅视觉模型允许在对话中上传图片"
            :model-value="modelForm.supportsVision"
            @update:model-value="modelForm.supportsVision = $event" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="modelDialogOpen = false">取消</Button>
          <Button :disabled="saving" @click="saveModel">
            <Spinner v-if="saving" class="mr-2" />保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import {
  IconAlertTriangle, IconBrain, IconCloudDownload, IconCpu, IconList, IconPencil, IconPlug,
  IconPlus, IconRefresh, IconServer, IconTrash,
} from '@tabler/icons-vue'
import { modelApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type {
  ModelConfig, ModelProvider, ModelType, RemoteModel, SaveModelConfigPayload,
} from '@/types/api'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import SettingSwitchRow from '@/components/console/SettingSwitchRow.vue'

const userStore = useUserStore()
const canUpdate = computed(() => userStore.hasPermission('model:update') || userStore.hasPermission('settings:update'))

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const activeTab = ref('providers')
const providers = ref<ModelProvider[]>([])
const configs = ref<ModelConfig[]>([])

const providerDialogOpen = ref(false)
const editingProvider = ref<ModelProvider | null>(null)
const providerForm = reactive({
  name: '', vendor: 'OPENAI', baseUrl: '', apiKey: '', enabled: true,
})

const fetchDialogOpen = ref(false)
const fetchProvider = ref<ModelProvider | null>(null)
const fetching = ref(false)
const remoteModels = ref<RemoteModel[]>([])
const selectedRemote = ref<string[]>([])
const bulkType = ref<ModelType>('CHAT')

const modelDialogOpen = ref(false)
const editingModel = ref<ModelConfig | null>(null)
const modelForm = reactive({
  displayName: '', modelType: 'CHAT' as ModelType, dimension: undefined as number | undefined,
  contextWindow: 32000 as number | undefined,
  reasoningEffort: '', extraBody: '', enabled: true, visible: true, supportsTools: false, thinkingEnabled: false, supportsVision: false,
})

const existingModelIds = computed(() => new Set(configs.value.map(item => item.modelId)))

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [providerList, modelList] = await Promise.all([modelApi.providers(), modelApi.configs()])
    providers.value = providerList
    configs.value = modelList
  } catch (err: any) {
    error.value = err?.message || '加载模型配置失败'
  } finally {
    loading.value = false
  }
}

function openProviderDialog(provider?: ModelProvider) {
  editingProvider.value = provider ?? null
  providerForm.name = provider?.name ?? ''
  providerForm.vendor = provider?.vendor || 'OPENAI'
  providerForm.baseUrl = provider?.baseUrl ?? ''
  providerForm.apiKey = ''
  providerForm.enabled = provider?.enabled ?? true
  providerDialogOpen.value = true
}

async function saveProvider() {
  if (!providerForm.name.trim() || !providerForm.baseUrl.trim()) {
    toast.error('请填写名称和 API 端点')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: providerForm.name.trim(),
      vendor: providerForm.vendor,
      baseUrl: providerForm.baseUrl.trim(),
      apiKey: providerForm.apiKey.trim() || undefined,
      enabled: providerForm.enabled,
    }
    const saved = editingProvider.value
      ? await modelApi.updateProvider(editingProvider.value.id, payload)
      : await modelApi.createProvider(payload)
    toast.success(editingProvider.value ? '供应商已更新' : '供应商已添加')
    providerDialogOpen.value = false
    await loadAll()
    if (!editingProvider.value && saved) {
      openFetch(saved)
    }
  } catch (err: any) {
    toast.error(err?.message || '保存供应商失败')
  } finally {
    saving.value = false
  }
}

async function removeProvider(provider: ModelProvider) {
  if (!window.confirm(`删除供应商「${provider.name}」及其下全部模型？`))
    return
  saving.value = true
  try {
    await modelApi.deleteProvider(provider.id)
    toast.success('供应商已删除')
    await loadAll()
  } catch (err: any) {
    toast.error(err?.message || '删除失败')
  } finally {
    saving.value = false
  }
}

async function openFetch(provider: ModelProvider) {
  fetchProvider.value = provider
  remoteModels.value = []
  selectedRemote.value = []
  fetchDialogOpen.value = true
  fetching.value = true
  try {
    remoteModels.value = await modelApi.fetchModels(provider.id)
    if (remoteModels.value.length === 0)
      toast.error('供应商未返回任何模型')
  } catch (err: any) {
    toast.error(err?.message || '拉取模型失败')
  } finally {
    fetching.value = false
  }
}

async function addSelectedModels() {
  if (!fetchProvider.value)
    return
  saving.value = true
  try {
    for (const modelId of selectedRemote.value) {
      if (existingModelIds.value.has(modelId))
        continue
      const payload: SaveModelConfigPayload = {
        providerId: fetchProvider.value.id,
        modelId,
        modelType: bulkType.value,
        displayName: modelId,
        enabled: true,
        visible: bulkType.value === 'CHAT',
        supportsTools: bulkType.value === 'CHAT',
      }
      await modelApi.saveConfig(payload)
    }
    toast.success(`已添加 ${selectedRemote.value.length} 个模型`)
    fetchDialogOpen.value = false
    await loadAll()
    activeTab.value = 'models'
  } catch (err: any) {
    toast.error(err?.message || '添加模型失败')
  } finally {
    saving.value = false
  }
}

async function toggle(model: ModelConfig, field: 'enabled' | 'visible' | 'supportsTools' | 'thinkingEnabled', value: boolean) {
  const previous = model[field]
  model[field] = value
  try {
    await modelApi.saveConfig({ ...model, [field]: value })
  } catch (err: any) {
    model[field] = previous
    toast.error(err?.message || '更新失败')
  }
}

function openModelDialog(model: ModelConfig) {
  editingModel.value = model
  modelForm.displayName = model.displayName
  modelForm.modelType = model.modelType
  modelForm.dimension = model.dimension
  modelForm.contextWindow = model.contextWindow ?? 32000
  modelForm.reasoningEffort = model.reasoningEffort ?? ''
  modelForm.extraBody = model.extraBody ?? ''
  modelForm.enabled = model.enabled
  modelForm.visible = model.visible
  modelForm.supportsTools = model.supportsTools
  modelForm.thinkingEnabled = model.thinkingEnabled
  modelForm.supportsVision = !!model.supportsVision
  modelDialogOpen.value = true
}

async function saveModel() {
  if (!editingModel.value)
    return
  saving.value = true
  try {
    await modelApi.saveConfig({
      providerId: editingModel.value.providerId,
      modelId: editingModel.value.modelId,
      modelType: modelForm.modelType,
      displayName: modelForm.displayName.trim() || editingModel.value.modelId,
      enabled: modelForm.enabled,
      visible: modelForm.visible,
      supportsTools: modelForm.supportsTools,
      thinkingEnabled: modelForm.thinkingEnabled,
      supportsVision: modelForm.supportsVision,
      reasoningEffort: modelForm.reasoningEffort || undefined,
      extraBody: modelForm.extraBody.trim() || undefined,
      dimension: modelForm.dimension,
      contextWindow: modelForm.contextWindow,
    })
    toast.success('模型已更新')
    modelDialogOpen.value = false
    await loadAll()
  } catch (err: any) {
    toast.error(err?.message || '保存模型失败')
  } finally {
    saving.value = false
  }
}

async function removeModel(model: ModelConfig) {
  if (!window.confirm(`删除模型「${model.displayName || model.modelId}」？`))
    return
  try {
    await modelApi.deleteConfig(model.id)
    toast.success('模型已删除')
    await loadAll()
  } catch (err: any) {
    toast.error(err?.message || '删除失败')
  }
}

function typeLabel(type: ModelType) {
  return type === 'CHAT' ? '聊天' : type === 'EMBEDDING' ? '向量' : '重排'
}

onMounted(() => void loadAll())
</script>
