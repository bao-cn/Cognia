<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { channelApi, profileApi } from '@/api'
import type { ChannelCard, ChannelDetail } from '@/types/api'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import {
  IconAlertTriangle, IconArrowLeft, IconBook, IconCloudUpload, IconCode, IconPin, IconRefresh,
  IconSettings, IconTrash,
} from '@tabler/icons-vue'

const props = defineProps<{ embedded?: boolean }>()
const route = useRoute()
const router = useRouter()
const loading = ref(true)
const error = ref('')
const detail = ref<ChannelDetail | null>(null)
const saving = ref(false)
const pinning = ref(false)
const agentSaving = ref(false)
const markdownUploading = ref(false)
const readmeInput = ref<HTMLInputElement | null>(null)
const agentInput = ref<HTMLInputElement | null>(null)
const deleteOpen = ref(false)
const deleting = ref(false)
const notifyForm = reactive({ title: '', content: '' })
const notifySending = ref(false)

const ownerParam = computed(() => String(route.params.owner ?? ''))
const slugParam = computed(() => String(route.params.channelSlug ?? ''))
const channel = computed(() => detail.value?.channel)
const canManage = computed(() => detail.value?.canManage ?? false)
const canNotify = computed(() => detail.value?.canNotify ?? false)
const channelPath = computed(() => `/${ownerParam.value}/${slugParam.value}`)

const form = reactive({ name: '', description: '', isPublic: true, webSearchEnabled: true })
const agentForm = reactive({ name: '', description: '', tags: '' })

function fill(value: ChannelDetail) {
  detail.value = value
  form.name = value.channel.name
  form.description = value.channel.description ?? ''
  form.isPublic = value.channel.isPublic
  form.webSearchEnabled = value.channel.webSearchEnabled ?? true
  agentForm.name = value.channel.agentName ?? ''
  agentForm.description = value.channel.agentDescription ?? ''
  agentForm.tags = value.channel.agentTags ?? ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const profile = await profileApi.get(ownerParam.value)
    const channels = await channelApi.list(profile.type, profile.id)
    const target = channels.find((item: ChannelCard) => item.slug === slugParam.value)
    if (!target) {
      error.value = '频道不存在或你没有访问权限'
      detail.value = null
      return
    }
    fill(await channelApi.detail(target.id))
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载频道设置失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function saveBasic() {
  if (!channel.value) return
  if (!form.name.trim()) {
    toast.error('频道名称不能为空')
    return
  }
  saving.value = true
  try {
    await channelApi.update(channel.value.id, {
      name: form.name.trim(),
      description: form.description.trim(),
      isPublic: form.isPublic,
      webSearchEnabled: form.webSearchEnabled,
    })
    fill(await channelApi.detail(channel.value.id))
    toast.success('频道设置已保存')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function togglePin() {
  if (!channel.value) return
  pinning.value = true
  try {
    await channelApi.setPinned(channel.value.id, !channel.value.pinned)
    fill(await channelApi.detail(channel.value.id))
    toast.success(channel.value.pinned ? '已取消置顶' : '频道已置顶')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '操作失败')
  } finally {
    pinning.value = false
  }
}

function pickMarkdown(kind: 'README' | 'AGENT') {
  if (kind === 'README') readmeInput.value?.click()
  else agentInput.value?.click()
}

async function onMarkdownSelected(event: Event, kind: 'README' | 'AGENT') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !channel.value) return
  if (!file.name.toLowerCase().endsWith('.md')) {
    toast.error('请选择 .md 文件')
    return
  }
  markdownUploading.value = true
  try {
    await channelApi.uploadMarkdown(channel.value.id, kind, file)
    fill(await channelApi.detail(channel.value.id))
    toast.success(`${kind}.md 已更新`)
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '上传失败')
  } finally {
    markdownUploading.value = false
  }
}

async function saveAgent() {
  if (!channel.value) return
  agentSaving.value = true
  try {
    await channelApi.updateAgent(channel.value.id, {
      name: agentForm.name.trim(),
      description: agentForm.description.trim(),
      tags: agentForm.tags.trim(),
    })
    fill(await channelApi.detail(channel.value.id))
    toast.success('AGENT 元信息已更新')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    agentSaving.value = false
  }
}

async function removeChannel() {
  if (!channel.value) return
  deleting.value = true
  try {
    await channelApi.remove(channel.value.id)
    toast.success('频道已删除')
    await router.replace(`/${ownerParam.value}`)
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '删除频道失败')
  } finally {
    deleting.value = false
  }
}

async function sendNotification() {
  if (!channel.value) return
  if (!notifyForm.title.trim() || !notifyForm.content.trim()) {
    toast.error('通知标题和内容不能为空')
    return
  }
  notifySending.value = true
  try {
    await channelApi.sendNotification(channel.value.id, {
      title: notifyForm.title.trim(),
      content: notifyForm.content.trim(),
    })
    notifyForm.title = ''
    notifyForm.content = ''
    toast.success('频道通知已发送')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '发送通知失败')
  } finally {
    notifySending.value = false
  }
}

onMounted(load)
watch(() => [route.params.owner, route.params.channelSlug], load)
</script>

<template>
  <div :class="props.embedded ? 'flex flex-col gap-4' : 'mx-auto flex w-full max-w-5xl flex-col gap-4 p-4 md:p-6'">
    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <div v-else-if="error" class="flex flex-col items-start gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-6">
      <p class="text-sm text-destructive">{{ error }}</p>
      <Button variant="outline" size="sm" @click="router.push(channelPath)">返回频道</Button>
    </div>

    <template v-else-if="channel">
      <div v-if="!props.embedded" class="flex flex-wrap items-start justify-between gap-3">
        <div class="flex min-w-0 flex-col gap-1">
          <router-link :to="channelPath" class="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground">
            <IconArrowLeft class="size-3.5" />返回 {{ channel.name }}
          </router-link>
          <h1 class="flex items-center gap-2 text-xl font-semibold">
            <IconSettings class="size-5 text-muted-foreground" />频道设置
            <Badge variant="outline">{{ channel.isPublic ? '公开' : '私密' }}</Badge>
          </h1>
          <p class="text-sm text-muted-foreground">管理频道基本信息、置顶、AGENT 元信息与删除操作。</p>
        </div>
        <div class="flex items-center gap-2">
          <Badge v-if="channel.pinned" variant="secondary" class="h-8 px-3">已置顶</Badge>
          <Button variant="outline" size="sm" :disabled="loading" @click="load">
            <IconRefresh class="mr-1 size-4" />重新加载
          </Button>
        </div>
      </div>

      <Alert v-if="!canManage" variant="destructive">
        <AlertDescription>你没有管理该频道的权限，只有所有者或编辑者可以修改设置。</AlertDescription>
      </Alert>

      <fieldset :disabled="!canManage" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">基本信息</h2>
            <p class="text-xs text-muted-foreground">频道标识创建后不可修改；名称和简介会展示在频道页。</p>
          </header>
          <div class="grid gap-4 p-4 md:grid-cols-2">
            <div class="grid gap-2">
              <Label for="channel-name">频道名称</Label>
              <Input id="channel-name" v-model="form.name" maxlength="64" />
            </div>
            <div class="grid gap-2">
              <Label>频道标识</Label>
              <Input :model-value="channel.slug" disabled />
              <p class="text-xs text-muted-foreground">用于 /{{ ownerParam }}/{{ channel.slug }}</p>
            </div>
            <div class="grid gap-2 md:col-span-2">
              <Label for="channel-description">频道简介</Label>
              <Textarea id="channel-description" v-model="form.description" rows="3" maxlength="512" />
            </div>
            <div class="flex items-center justify-between gap-4 md:col-span-2">
              <div>
                <p class="text-sm font-medium">公开频道</p>
                <p class="text-xs text-muted-foreground">关闭后仅所有者与团队成员可以访问。</p>
              </div>
              <Switch v-model="form.isPublic" />
            </div>
            <div class="flex items-center justify-between gap-4 md:col-span-2">
              <div>
                <p class="text-sm font-medium">允许联网搜索</p>
                <p class="text-xs text-muted-foreground">仅在系统开启联网搜索后生效；关闭后使用本频道检索时不会调用联网搜索。</p>
              </div>
              <Switch v-model="form.webSearchEnabled" />
            </div>
          </div>
          <div class="flex justify-end border-t px-4 py-3">
            <Button :disabled="saving" @click="saveBasic">
              <Spinner v-if="saving" class="mr-2" />保存设置
            </Button>
          </div>
        </section>

        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="flex items-center gap-2 text-sm font-semibold"><IconPin class="size-4" />置顶展示</h2>
            <p class="text-xs text-muted-foreground">置顶频道会出现在主页的 Pinned 频道中，最多 4 个。</p>
          </header>
          <div class="flex items-center justify-between gap-4 p-4">
            <div>
              <p class="text-sm font-medium">{{ channel.pinned ? '已置顶' : '未置顶' }}</p>
              <p class="text-xs text-muted-foreground">每个所有者最多置顶 4 个频道。</p>
            </div>
            <Button variant="outline" size="sm" :disabled="pinning" @click="togglePin">
              <Spinner v-if="pinning" class="mr-2" />
              <IconPin v-else class="mr-1 size-4" />{{ channel.pinned ? '取消置顶' : '置顶频道' }}
            </Button>
          </div>
        </section>

        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="flex items-center gap-2 text-sm font-semibold"><IconBook class="size-4" />说明文档</h2>
            <p class="text-xs text-muted-foreground">README.md 会展示在频道页底部；AGENT.md 描述频道结构和检索规则。上传新文件会替换原有内容。</p>
          </header>
          <div class="grid gap-3 p-4 md:grid-cols-2">
            <div class="flex flex-col items-start gap-2 rounded-lg border p-3">
              <div>
                <p class="text-sm font-medium">README.md</p>
                <p class="text-xs text-muted-foreground">频道介绍，支持 Markdown，展示给所有可访问者。</p>
              </div>
              <Button variant="outline" size="sm" :disabled="markdownUploading" @click="pickMarkdown('README')">
                <IconCloudUpload class="mr-1 size-4" />上传 / 替换
              </Button>
            </div>
            <div class="flex flex-col items-start gap-2 rounded-lg border p-3">
              <div>
                <p class="text-sm font-medium">AGENT.md</p>
                <p class="text-xs text-muted-foreground">频道结构、检索规则与 frontmatter 元信息。</p>
              </div>
              <Button variant="outline" size="sm" :disabled="markdownUploading" @click="pickMarkdown('AGENT')">
                <IconCloudUpload class="mr-1 size-4" />上传 / 替换
              </Button>
            </div>
          </div>
          <input ref="readmeInput" type="file" accept=".md" class="hidden" @change="onMarkdownSelected($event, 'README')" />
          <input ref="agentInput" type="file" accept=".md" class="hidden" @change="onMarkdownSelected($event, 'AGENT')" />
        </section>

        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="flex items-center gap-2 text-sm font-semibold"><IconCode class="size-4" />AGENT.md 元信息</h2>
            <p class="text-xs text-muted-foreground">保存后会更新频道的 Agent 说明，聊天时会优先参考这些信息。</p>
          </header>
          <div class="grid gap-4 p-4 md:grid-cols-2">
            <div class="grid gap-2">
              <Label for="agent-name">name</Label>
              <Input id="agent-name" v-model="agentForm.name" maxlength="128" />
            </div>
            <div class="grid gap-2">
              <Label for="agent-tags">tags</Label>
              <Input id="agent-tags" v-model="agentForm.tags" placeholder="检索, 文档, 规范" />
            </div>
            <div class="grid gap-2 md:col-span-2">
              <Label for="agent-description">description</Label>
              <Textarea id="agent-description" v-model="agentForm.description" rows="3" maxlength="512" />
            </div>
          </div>
          <div class="flex justify-end border-t px-4 py-3">
            <Button variant="outline" :disabled="agentSaving" @click="saveAgent">
              <Spinner v-if="agentSaving" class="mr-2" />保存元信息
            </Button>
          </div>
        </section>

        <section v-if="canNotify" class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">频道通知</h2>
            <p class="text-xs text-muted-foreground">向开启频道通知的用户推送消息，每个频道每天最多发送 1 次。</p>
          </header>
          <div class="grid gap-4 p-4">
            <div class="grid gap-2">
              <Label for="notify-title">通知标题</Label>
              <Input id="notify-title" v-model="notifyForm.title" maxlength="200" placeholder="例如：本周新增资料" />
            </div>
            <div class="grid gap-2">
              <Label for="notify-content">通知内容</Label>
              <Textarea id="notify-content" v-model="notifyForm.content" rows="4" maxlength="2000" />
            </div>
            <div class="flex justify-end">
              <Button :disabled="notifySending" @click="sendNotification">
                <Spinner v-if="notifySending" class="mr-2" />发送通知
              </Button>
            </div>
          </div>
        </section>

        <section class="rounded-xl border border-destructive/25 bg-card">
          <header class="flex flex-col gap-1 border-b border-destructive/20 px-4 py-3">
            <h2 class="flex items-center gap-2 text-sm font-semibold text-destructive">
              <IconAlertTriangle class="size-4" />危险操作
            </h2>
            <p class="text-xs text-muted-foreground">删除后频道不可恢复；频道中的文档会移入文档回收站，可在保留期内恢复或永久删除。</p>
          </header>
          <div class="flex flex-wrap items-center justify-between gap-3 p-4">
            <div>
              <p class="text-sm font-medium">删除频道</p>
              <p class="text-xs text-muted-foreground">请确认频道中的文档已备份。</p>
            </div>
            <Button variant="destructive" size="sm" @click="deleteOpen = true">
              <IconTrash class="mr-1 size-4" />删除频道
            </Button>
          </div>
        </section>
      </fieldset>

      <Dialog v-model:open="deleteOpen">
        <DialogContent class="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>删除频道</DialogTitle>
            <DialogDescription>确定删除「{{ channel.name }}」？频道不可恢复，其中的文档会移入文档回收站。</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" :disabled="deleting" @click="deleteOpen = false">取消</Button>
            <Button variant="destructive" :disabled="deleting" @click="removeChannel">
              <Spinner v-if="deleting" class="mr-2" />删除频道
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </template>
  </div>
</template>
