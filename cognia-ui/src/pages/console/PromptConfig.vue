<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconTool class="size-5 text-muted-foreground" />
          Prompt 配置
        </h1>
        <p class="text-sm text-muted-foreground">
          配置聊天助手的系统提示词、知识库回答规则、过期知识提示与工具调用提示。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Badge v-if="updatedAt" variant="outline" class="h-8 px-3">更新于 {{ updatedAt }}</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="loadSettings">
          <IconRefresh class="mr-1" />重新加载
        </Button>
        <Button v-if="canUpdate" variant="outline" size="sm" :disabled="loading || resetting" @click="resetOpen = true">
          <Spinner v-if="resetting" class="mr-1" />恢复默认
        </Button>
        <Button v-if="canUpdate" :disabled="saving || !isDirty" @click="saveSettings">
          <Spinner v-if="saving" class="mr-2" />保存配置
        </Button>
      </div>
    </div>

    <Alert v-if="!canUpdate" variant="destructive">
      <AlertDescription>当前账户只有查看 Prompt 配置的权限，如需修改请联系管理员。</AlertDescription>
    </Alert>
    <Alert v-if="error" variant="destructive">
      <AlertDescription>{{ error }}</AlertDescription>
    </Alert>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <form v-else class="flex flex-col gap-4" @submit.prevent="saveSettings">
      <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <div class="flex items-center justify-between gap-3">
              <h2 class="text-sm font-semibold">基础系统提示词</h2>
              <Badge variant="secondary">必填</Badge>
            </div>
            <p class="text-xs text-muted-foreground">
              定义助手的角色、回答语言和总体行为，会作为所有对话的第一段 system 消息。
            </p>
          </header>
          <div class="p-4">
            <Textarea
              v-model="form.systemPrompt"
              :rows="5"
              placeholder="You are Cognia, a precise knowledge-base assistant..."
            />
            <p class="mt-2 text-xs text-muted-foreground">
              建议保持简洁明确；具体的知识库引用规则放在下方独立配置。
            </p>
          </div>
        </section>

        <div class="grid gap-4 lg:grid-cols-2">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">知识库回答规则</h2>
              <p class="text-xs text-muted-foreground">
                当检索到知识片段时追加，用于约束引用来源和回答依据。
              </p>
            </header>
            <div class="p-4">
              <Textarea v-model="form.ragPrompt" :rows="7" placeholder="When retrieved knowledge is provided..." />
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">过期知识提示</h2>
              <p class="text-xs text-muted-foreground">
                检索片段被标记为已过期时追加，要求模型明确提示时效性。
              </p>
            </header>
            <div class="p-4">
              <Textarea v-model="form.expiredPrompt" :rows="7" placeholder="If a retrieved chunk is marked 状态：已过期..." />
            </div>
          </section>
        </div>

        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">工具调用规则</h2>
            <p class="text-xs text-muted-foreground">
              适用于支持 Function Calling 的模型，约束 web_search / web_fetch 的使用时机。
            </p>
          </header>
          <div class="p-4">
            <Textarea
              v-model="form.toolPrompt"
              :rows="4"
              placeholder="Use web_search and web_fetch when current external information is needed..."
            />
          </div>
        </section>

        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <div class="flex items-center justify-between gap-3">
              <h2 class="text-sm font-semibold">上下文压缩</h2>
              <Badge variant="secondary">必填</Badge>
            </div>
            <p class="text-xs text-muted-foreground">
              对话上下文占用达到压缩阈值时，使用该提示词把历史对话压缩为摘要，再继续回答。
            </p>
          </header>
          <div class="p-4">
            <Textarea
              id="compression-prompt"
              v-model="form.compressionPrompt"
              :rows="5"
              :placeholder="compressionPlaceholder"
              :aria-invalid="compressionPromptError ? true : undefined"
            />
            <p v-if="compressionPromptError" class="mt-2 text-xs text-destructive">
              {{ compressionPromptError }}
            </p>
            <p class="mt-2 text-xs text-muted-foreground">
              压缩触发阈值与上下文窗口在「检索参数配置」中调整；留空无法保存，恢复默认会还原为后端内置提示词。
            </p>
          </div>
        </section>

        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">提示词预览</h2>
            <p class="text-xs text-muted-foreground">
              实际下发时会按顺序拼接以下内容；检索结果会以 Retrieved knowledge 区块自动追加在末尾。
            </p>
          </header>
          <div class="p-4">
            <pre class="max-h-80 overflow-auto whitespace-pre-wrap rounded-lg border bg-muted/40 p-4 text-xs leading-relaxed">{{ preview }}</pre>
            <p class="mt-2 text-xs text-muted-foreground">
              无需在此处填写 context 占位符，检索到的文件、频道、页码和过期状态由后端自动组织。
            </p>
          </div>
        </section>
      </fieldset>
    </form>

    <Dialog v-model:open="resetOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>恢复默认 Prompt？</DialogTitle>
          <DialogDescription>
            当前配置将恢复为默认设置，保存后立即影响新对话。此操作不可撤销。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" :disabled="resetting" @click="resetOpen = false">取消</Button>
          <Button variant="destructive" :disabled="resetting" @click="resetDefaults">
            <Spinner v-if="resetting" class="mr-2" />确认恢复
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { IconRefresh, IconTool } from '@tabler/icons-vue'
import { promptApi } from '@/api'
import type { PromptSettings, UpdatePromptSettingsPayload } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/textarea'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

const userStore = useUserStore()
const canUpdate = computed(
  () => userStore.hasPermission('rag:update') || userStore.hasPermission('settings:update'),
)

const loading = ref(true)
const saving = ref(false)
const resetting = ref(false)
const error = ref('')
const updatedAt = ref('')
const originalSnapshot = ref('')
const resetOpen = ref(false)
const compressionPromptError = ref('')
const defaultCompressionPrompt = ref('')
const compressionPlaceholder = computed(
  () => defaultCompressionPrompt.value || '请将以下对话压缩为简洁摘要，保留事实、决定、未完成事项与用户偏好……',
)

const form = reactive<PromptSettings>({
  systemPrompt: '',
  ragPrompt: '',
  expiredPrompt: '',
  toolPrompt: '',
  compressionPrompt: '',
})

watch(
  () => form.compressionPrompt,
  () => {
    if (compressionPromptError.value) compressionPromptError.value = ''
  },
)

const isDirty = computed(() => JSON.stringify(buildPayload()) !== originalSnapshot.value)

const preview = computed(() =>
  [form.systemPrompt, form.ragPrompt, form.expiredPrompt, form.toolPrompt]
    .map((value) => value?.trim())
    .filter(Boolean)
    .join('\n\n'),
)

function buildPayload(): UpdatePromptSettingsPayload {
  return {
    systemPrompt: form.systemPrompt.trim(),
    ragPrompt: form.ragPrompt?.trim() ?? '',
    expiredPrompt: form.expiredPrompt?.trim() ?? '',
    toolPrompt: form.toolPrompt?.trim() ?? '',
    compressionPrompt: form.compressionPrompt?.trim() ?? '',
  }
}

function applySettings(settings: PromptSettings) {
  form.systemPrompt = settings.systemPrompt ?? ''
  form.ragPrompt = settings.ragPrompt ?? ''
  form.expiredPrompt = settings.expiredPrompt ?? ''
  form.toolPrompt = settings.toolPrompt ?? ''
  form.compressionPrompt = settings.compressionPrompt ?? ''
  updatedAt.value = settings.updatedAt ?? ''
  originalSnapshot.value = JSON.stringify(buildPayload())
}

async function loadDefaults() {
  try {
    const defaults = await promptApi.defaults()
    defaultCompressionPrompt.value = defaults.compressionPrompt ?? ''
  } catch {
    // 仅用于占位提示，加载失败时继续使用本地兜底文案。
  }
}

async function loadSettings() {
  loading.value = true
  error.value = ''
  try {
    applySettings(await promptApi.settings())
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载 Prompt 配置失败'
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  if (!form.systemPrompt.trim()) {
    toast.error('系统提示词不能为空')
    return
  }
  if (!(form.compressionPrompt ?? '').trim()) {
    compressionPromptError.value = '上下文压缩提示词不能为空'
    toast.error('上下文压缩提示词不能为空')
    return
  }
  saving.value = true
  error.value = ''
  try {
    applySettings(await promptApi.update(buildPayload()))
    toast.success('Prompt 配置已保存')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存 Prompt 配置失败'
  } finally {
    saving.value = false
  }
}

async function resetDefaults() {
  resetting.value = true
  error.value = ''
  try {
    applySettings(await promptApi.reset())
    resetOpen.value = false
    toast.success('已恢复默认 Prompt')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '恢复默认 Prompt 失败'
  } finally {
    resetting.value = false
  }
}

onMounted(() => {
  void loadSettings()
  void loadDefaults()
})
</script>
