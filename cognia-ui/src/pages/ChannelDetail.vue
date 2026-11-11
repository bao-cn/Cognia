<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { channelApi, dashboardApi, discoverApi, profileApi } from '@/api'
import type { ChannelDetail, ChannelDocument, ChannelFolder, ChannelInsights } from '@/types/api'
import DashboardTrendChart from '@/components/DashboardTrendChart.vue'
import ChannelSettings from '@/pages/ChannelSettings.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import Markdown from '@/components/Markdown.vue'
import {
  IconBook, IconBookmark, IconBookmarkFilled, IconChartBar, IconChevronRight, IconCloudUpload,
  IconEye, IconEyeOff, IconFile, IconFileText, IconFolder, IconFolderPlus, IconInfoCircle,
  IconPlus, IconRefresh, IconSettings, IconTrash, IconAlertTriangle, IconCheck,
} from '@tabler/icons-vue'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const detail = ref<ChannelDetail | null>(null)
const selectedFolder = ref<string | null>(null)

const ownerParam = computed(() => String(route.params.owner ?? ''))
const slugParam = computed(() => String(route.params.channelSlug ?? ''))
const channel = computed(() => detail.value?.channel)
const ownerPath = computed(() => `/${ownerParam.value}`)
const canWrite = computed(() => detail.value?.canWrite ?? false)
const canManage = computed(() => detail.value?.canManage ?? false)
const canViewInsights = computed(() => detail.value?.canViewInsights ?? false)
const activeView = ref<'documents' | 'insights' | 'settings'>('documents')
const insights = ref<ChannelInsights | null>(null)
const insightsLoading = ref(false)

async function openInsights() {
  activeView.value = 'insights'
  if (!channel.value || insights.value) return
  insightsLoading.value = true
  try {
    insights.value = await dashboardApi.channelInsights(Number(channel.value.id))
  } catch (err: any) {
    toast.error(err?.message || '加载频道洞察失败')
  } finally {
    insightsLoading.value = false
  }
}
const subscribed = ref(false)
const notifySubscribed = ref(false)
const engagementBusy = ref(false)

async function loadEngagement() {
  if (!channel.value?.isPublic)
    return
  try {
    const list = await discoverApi.subscriptions()
    const found = list.find(item => item.id === channel.value?.id)
    subscribed.value = Boolean(found)
    notifySubscribed.value = Boolean(found?.notifySubscribed)
  } catch { /* engagement is optional */ }
}

async function toggleSubscribe() {
  if (!channel.value) return
  engagementBusy.value = true
  try {
    if (subscribed.value) await discoverApi.unsubscribe(channel.value.id)
    else await discoverApi.subscribe(channel.value.id)
    subscribed.value = !subscribed.value
    toast.success(subscribed.value ? '已订阅频道' : '已取消订阅')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  } finally {
    engagementBusy.value = false
  }
}

async function toggleNotify() {
  if (!channel.value) return
  engagementBusy.value = true
  try {
    if (notifySubscribed.value) await discoverApi.unsubscribeNotify(channel.value.id)
    else await discoverApi.subscribeNotify(channel.value.id)
    notifySubscribed.value = !notifySubscribed.value
    toast.success(notifySubscribed.value ? '已开启频道通知' : '已关闭频道通知')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  } finally {
    engagementBusy.value = false
  }
}

const currentFolder = computed(() =>
  detail.value?.folders.find(folder => folder.id === selectedFolder.value) ?? null,
)

const allDocuments = computed(() => detail.value?.documents ?? [])
const currentDocuments = computed(() => {
  if (selectedFolder.value === null)
    return allDocuments.value.filter(document => !document.folderId)
  return allDocuments.value.filter(document => document.folderId === selectedFolder.value)
})
const visibleFolders = computed(() =>
  (detail.value?.folders ?? []).filter(folder => (folder.parentId ?? null) === selectedFolder.value),
)
const documentCount = computed(() => currentDocuments.value.length)

function folderDocumentCount(folder: ChannelFolder) {
  return allDocuments.value.filter(document => document.folderId === folder.id).length
}

const readmeContent = ref('')
const readmeLoading = ref(false)

async function loadReadme(channelId: string) {
  if (!detail.value?.channel) {
    readmeContent.value = ''
    return
  }
  readmeLoading.value = true
  try {
    readmeContent.value = await channelApi.readMarkdown(channelId, 'README')
  } catch {
    readmeContent.value = ''
  } finally {
    readmeLoading.value = false
  }
}

const statusMeta: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  PENDING: { label: '待处理', variant: 'secondary' },
  PROCESSING: { label: '处理中', variant: 'outline' },
  READY: { label: '已就绪', variant: 'default' },
  FAILED: { label: '失败', variant: 'destructive' },
  DELETED: { label: '已删除', variant: 'secondary' },
}

function statusOf(document: ChannelDocument) {
  return statusMeta[document.status] ?? { label: document.status, variant: 'outline' as const }
}

function formatBytes(value: number) {
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  return `${(value / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const profile = await profileApi.get(ownerParam.value)
    const channels = await channelApi.list(profile.type, profile.id)
    const target = channels.find(item => item.slug === slugParam.value)
    if (!target) {
      error.value = '频道不存在或你没有访问权限'
      detail.value = null
      return
    }
    detail.value = await channelApi.detail(target.id)
    void loadEngagement()
    await loadReadme(target.id)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载频道失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}


function selectFolder(folder: ChannelFolder | null) {
  selectedFolder.value = folder?.id ?? null
}

const createFolderOpen = ref(false)
const folderName = ref('')
const creatingFolder = ref(false)

function openCreateFolder() {
  folderName.value = ''
  createFolderOpen.value = true
}

async function createFolder() {
  if (!channel.value) return
  if (!folderName.value.trim()) {
    toast.error('请填写文件夹名称')
    return
  }
  creatingFolder.value = true
  try {
    const folder = await channelApi.createFolder(channel.value.id, {
      parentId: selectedFolder.value ?? undefined,
      name: folderName.value.trim(),
    })
    detail.value = await channelApi.detail(channel.value.id)
    selectedFolder.value = folder.id
    createFolderOpen.value = false
    toast.success('文件夹已创建')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '创建文件夹失败')
  } finally {
    creatingFolder.value = false
  }
}

async function deleteFolder(folder: ChannelFolder) {
  if (!channel.value) return
  try {
    await channelApi.deleteFolder(channel.value.id, folder.id)
    if (selectedFolder.value === folder.id) selectedFolder.value = null
    detail.value = await channelApi.detail(channel.value.id)
    toast.success('文件夹已删除')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '删除文件夹失败')
  }
}

const fileInput = ref<HTMLInputElement | null>(null)

type UploadStatus = 'pending' | 'uploading' | 'done' | 'error'

interface UploadItem {
  id: string
  file: File
  title: string
  documentType: string
  expirationTime: string
  status: UploadStatus
  progress: number
  error: string
}

const uploading = ref(false)
const uploadProgress = ref(0)
const uploadingName = ref('')
const uploadOpen = ref(false)
const uploadItems = ref<UploadItem[]>([])

const uploadPendingCount = computed(() => uploadItems.value.filter(item => item.status !== 'done').length)
const uploadDoneCount = computed(() => uploadItems.value.filter(item => item.status === 'done').length)

function triggerUpload() {
  if (!canWrite.value) return
  uploadItems.value = []
  fileInput.value?.click()
}

function uploadItemId() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto)
    return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function onFileSelected(event: Event) {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files ?? [])
  target.value = ''
  if (!files.length || !channel.value) return
  uploadItems.value = files.map(file => ({
    id: uploadItemId(),
    file,
    title: file.name.replace(/\.[^.]+$/, ''),
    documentType: file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : 'text',
    expirationTime: '',
    status: 'pending',
    progress: 0,
    error: '',
  }))
  uploadOpen.value = true
}

function removeUploadItem(id: string) {
  if (uploading.value) return
  uploadItems.value = uploadItems.value.filter(item => item.id !== id)
}

function uploadStatusLabel(status: UploadStatus) {
  if (status === 'uploading') return '上传中'
  if (status === 'done') return '已完成'
  if (status === 'error') return '失败'
  return '待上传'
}

function uploadStatusVariant(status: UploadStatus) {
  if (status === 'error') return 'destructive'
  if (status === 'done') return 'default'
  return 'secondary'
}

function closeUpload() {
  if (uploading.value) return
  uploadOpen.value = false
  uploadItems.value = []
}

function onUploadOpenChange(open: boolean) {
  if (open) uploadOpen.value = true
  else closeUpload()
}

async function confirmUpload() {
  if (!channel.value || !uploadItems.value.length || uploading.value) return
  const pending = uploadItems.value.filter(item => item.status !== 'done')
  if (!pending.length) {
    closeUpload()
    return
  }

  uploading.value = true
  let succeeded = 0
  let failed = 0

  for (const item of pending) {
    item.status = 'uploading'
    item.progress = 0
    item.error = ''
    uploadingName.value = item.file.name
    uploadProgress.value = 0
    try {
      await channelApi.uploadDocument(channel.value.id, item.file, selectedFolder.value ?? undefined, (percent) => {
        item.progress = percent
        uploadProgress.value = percent
      }, {
        title: item.title.trim() || undefined,
        documentType: item.documentType.trim() || undefined,
        expirationTime: item.expirationTime || undefined,
      })
      item.status = 'done'
      item.progress = 100
      succeeded += 1
    } catch (cause) {
      item.status = 'error'
      item.error = cause instanceof Error ? cause.message : '上传失败'
      failed += 1
    }
  }

  detail.value = await channelApi.detail(channel.value.id).catch(() => detail.value)
  uploading.value = false
  uploadingName.value = ''
  uploadProgress.value = 0

  if (failed === 0) {
    uploadOpen.value = false
    uploadItems.value = []
    toast.success(`${succeeded} 个文件已上传，正在处理`)
  } else {
    toast.error(`${succeeded} 个成功，${failed} 个失败，请检查后重试`)
  }
}

async function retryDocument(document: ChannelDocument) {
  if (!channel.value) return
  try {
    await channelApi.retryDocument(channel.value.id, document.id)
    detail.value = await channelApi.detail(channel.value.id)
    toast.success('已重新提交处理')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '重试失败')
  }
}

const deleteOpen = ref(false)
const deleting = ref(false)
const deleteTarget = ref<ChannelDocument | null>(null)

function askDelete(document: ChannelDocument) {
  deleteTarget.value = document
  deleteOpen.value = true
}

async function confirmDelete() {
  if (!channel.value || !deleteTarget.value) return
  deleting.value = true
  try {
    await channelApi.deleteDocument(channel.value.id, deleteTarget.value.id)
    detail.value = await channelApi.detail(channel.value.id)
    deleteOpen.value = false
    toast.success('文件已移入回收站')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '删除失败')
  } finally {
    deleting.value = false
  }
}

const uploadTargetLabel = computed(() => currentFolder.value?.name ?? '根目录')

onMounted(load)
watch(() => [route.params.owner, route.params.channelSlug], load)
</script>

<template>
  <div class="mx-auto flex w-full max-w-7xl flex-col gap-4 p-4 md:p-6">
    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <div v-else-if="error" class="flex flex-col items-start gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-6">
      <p class="text-sm text-destructive">{{ error }}</p>
      <div class="flex gap-2">
        <Button variant="outline" size="sm" @click="router.push(ownerPath)">返回主页</Button>
        <Button size="sm" @click="load">重新加载</Button>
      </div>
    </div>

    <template v-else-if="channel">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="flex min-w-0 flex-col gap-1">
          <div class="flex items-center gap-1.5 text-xs text-muted-foreground">
            <router-link :to="ownerPath" class="hover:text-foreground">{{ ownerParam }}</router-link>
            <IconChevronRight class="size-3" />
            <span>{{ channel.slug }}</span>
          </div>
          <h1 class="flex items-center gap-2 text-xl font-semibold">
            <IconFolder class="size-5 text-muted-foreground" /><span data-i18n-skip>{{ channel.name }}</span>
            <Badge :variant="channel.isPublic ? 'secondary' : 'outline'">{{ channel.isPublic ? '公开' : '私密' }}</Badge>
          </h1>
          <p class="max-w-2xl text-sm text-muted-foreground">{{ channel.description || '这个频道还没有简介。' }}</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <template v-if="channel.isPublic">
            <Button variant="outline" size="sm" :disabled="engagementBusy" :aria-pressed="subscribed" @click="toggleSubscribe">
              <IconBookmarkFilled v-if="subscribed" class="mr-1 size-4 text-primary" />
              <IconBookmark v-else class="mr-1 size-4" />
              {{ subscribed ? '已订阅' : '订阅' }}
            </Button>
            <Button variant="outline" size="sm" :disabled="engagementBusy" :aria-pressed="notifySubscribed" @click="toggleNotify">
              <IconEye v-if="notifySubscribed" class="mr-1 size-4 text-primary" />
              <IconEyeOff v-else class="mr-1 size-4" />
              {{ notifySubscribed ? '通知已开启' : '接收通知' }}
            </Button>
          </template>
          <input ref="fileInput" type="file" multiple class="hidden" @change="onFileSelected" />
        </div>
      </div>

      <div class="flex items-center gap-1 border-b">
        <button type="button" class="border-b-2 px-3 py-2 text-sm transition"
          :class="activeView === 'documents' ? 'border-primary font-medium text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="activeView = 'documents'">文档</button>
        <button v-if="canViewInsights" type="button" class="flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition"
          :class="activeView === 'insights' ? 'border-primary font-medium text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="openInsights">
          <IconChartBar class="size-4" />数据洞察
        </button>
        <button v-if="canManage" type="button" class="flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition"
          :class="activeView === 'settings' ? 'border-primary font-medium text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="activeView = 'settings'">
          <IconSettings class="size-4" />频道设置
        </button>
      </div>

      <template v-if="activeView === 'documents'">

      <div v-if="uploading" class="rounded-xl border bg-card p-4">
        <div class="mb-2 flex items-center justify-between text-sm">
          <span class="truncate">{{ uploadingName }}</span>
          <span class="text-muted-foreground">{{ uploadDoneCount }}/{{ uploadItems.length }} · {{ uploadProgress }}%</span>
        </div>
        <div class="h-1.5 overflow-hidden rounded-full bg-muted">
          <div class="h-full rounded-full bg-primary transition-all" :style="{ width: `${uploadProgress}%` }" />
        </div>
      </div>

      <div class="grid gap-4 lg:grid-cols-[240px_minmax(0,1fr)]">
        <div class="flex flex-col gap-4">
          <section class="rounded-xl border bg-card">
            <header class="flex items-center justify-between border-b px-3 py-2.5">
              <h2 class="text-sm font-semibold">目录</h2>
              <Button v-if="canWrite" variant="ghost" size="icon" class="size-7" @click="openCreateFolder">
                <IconFolderPlus class="size-4" />
              </Button>
            </header>
            <div class="flex flex-col gap-0.5 p-2">
              <button type="button"
                class="flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition"
                :class="selectedFolder === null ? 'bg-muted font-medium' : 'hover:bg-muted/60'"
                @click="selectFolder(null)">
                <IconFile class="size-4 text-muted-foreground" />全部文件
                <span class="ml-auto text-xs text-muted-foreground">{{ documentCount }}</span>
              </button>
              <div v-for="folder in detail?.folders ?? []" :key="folder.id" class="group flex items-center">
                <button type="button"
                  class="flex flex-1 items-center gap-2 rounded-md py-1.5 pr-1 text-left text-sm transition"
                  :class="selectedFolder === folder.id ? 'bg-muted font-medium' : 'hover:bg-muted/60'"
                  :style="{ paddingLeft: `${8 + (folder.depth - 1) * 12}px` }"
                  @click="selectFolder(folder)">
                  <IconFolder class="size-4 text-muted-foreground" />
                  <span class="truncate">{{ folder.name }}</span>
                </button>
                <Button v-if="canWrite" variant="ghost" size="icon"
                  class="size-6 opacity-0 group-hover:opacity-100" @click="deleteFolder(folder)">
                  <IconTrash class="size-3.5" />
                </Button>
              </div>
            </div>
          </section>

        </div>

        <section class="min-w-0 rounded-xl border bg-card">
          <header class="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-3">
            <div>
              <h2 class="flex items-center gap-2 text-sm font-semibold">
                <IconFolder class="size-4" />{{ currentFolder?.name || '全部文件' }}
              </h2>
              <p class="mt-0.5 text-xs text-muted-foreground">{{ documentCount }} 个文档 · 上传后自动处理，可用于检索</p>
            </div>
            <div class="flex items-center gap-2">
              <Button variant="outline" size="sm" :disabled="loading" @click="load">
                <IconRefresh class="mr-1 size-4" />刷新
              </Button>
              <Button v-if="canWrite" size="sm" :disabled="uploading" @click="triggerUpload">
                <IconPlus class="mr-1 size-4" />添加
              </Button>
            </div>
          </header>

          <div v-if="visibleFolders.length" class="border-b p-4">
            <div class="mb-2 flex items-center justify-between">
              <h3 class="text-xs font-medium text-muted-foreground">文件夹</h3>
              <span class="text-xs text-muted-foreground">{{ visibleFolders.length }}</span>
            </div>
            <div class="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
              <button v-for="folder in visibleFolders" :key="folder.id" type="button"
                class="flex items-center gap-3 rounded-lg border p-3 text-left transition hover:border-primary/40 hover:bg-muted/40"
                @click="selectFolder(folder)">
                <div class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <IconFolder class="size-4" />
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium">{{ folder.name }}</p>
                  <p class="text-xs text-muted-foreground">{{ folderDocumentCount(folder) }} 个文档</p>
                </div>
                <IconChevronRight class="size-4 shrink-0 text-muted-foreground" />
              </button>
            </div>
          </div>

          <div v-if="!currentDocuments.length && !visibleFolders.length" class="flex flex-col items-center gap-3 px-4 py-16 text-center">
            <div class="flex size-12 items-center justify-center rounded-full bg-muted">
              <IconFileText class="size-6 text-muted-foreground" />
            </div>
            <div>
              <p class="text-sm font-medium">这里还没有文档</p>
              <p class="mt-1 text-xs text-muted-foreground">上传 PDF、Markdown、TXT 等文件，系统会自动解析并处理，完成后即可用于检索。</p>
            </div>
            <Button v-if="canWrite" variant="outline" size="sm" @click="triggerUpload">
              <IconCloudUpload class="mr-1 size-4" />上传第一个文档
            </Button>
          </div>

          <div v-else-if="currentDocuments.length" class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead class="border-b bg-muted/40 text-xs text-muted-foreground">
                <tr>
                  <th class="px-4 py-2.5 text-left font-medium">文件</th>
                  <th class="px-3 py-2.5 text-left font-medium">大小</th>
                  <th class="px-3 py-2.5 text-left font-medium">状态</th>
                  <th class="px-3 py-2.5 text-left font-medium">时效</th>
                  <th class="px-3 py-2.5 text-left font-medium">上传时间</th>
                  <th class="px-3 py-2.5 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y">
                <tr v-for="document in currentDocuments" :key="document.id" class="hover:bg-muted/30">
                  <td class="max-w-[280px] px-4 py-3">
                    <div class="flex items-center gap-2">
                      <IconFileText class="size-4 shrink-0 text-muted-foreground" />
                      <span class="truncate font-medium">{{ document.name }}</span>
                    </div>
                    <p v-if="document.errorMessage" class="mt-1 truncate text-xs text-destructive" :title="document.errorMessage">
                      {{ document.errorMessage }}
                    </p>
                  </td>
                  <td class="whitespace-nowrap px-3 py-3 text-muted-foreground">{{ formatBytes(document.size) }}</td>
                  <td class="whitespace-nowrap px-3 py-3">
                    <Badge :variant="statusOf(document).variant" class="gap-1">
                      <Spinner v-if="document.status === 'PROCESSING'" class="size-3" />
                      <IconCheck v-else-if="document.status === 'READY'" class="size-3" />
                      <IconAlertTriangle v-else-if="document.status === 'FAILED'" class="size-3" />
                      {{ statusOf(document).label }}
                    </Badge>
                  </td>
                  <td class="whitespace-nowrap px-3 py-3 text-xs text-muted-foreground">
                    <span v-if="document.expirationTime">{{ document.expirationTime }}</span>
                    <span v-else class="text-muted-foreground/60">长期</span>
                  </td>
                  <td class="whitespace-nowrap px-3 py-3 text-xs text-muted-foreground">{{ document.createTime || '-' }}</td>
                  <td class="whitespace-nowrap px-3 py-3">
                    <div class="flex justify-end gap-1">
                      <Button v-if="document.status === 'FAILED' && canWrite" variant="ghost" size="sm" @click="retryDocument(document)">
                        <IconRefresh class="mr-1 size-3.5" />重试
                      </Button>
                      <Button v-if="canWrite" variant="ghost" size="icon" class="size-8 text-muted-foreground hover:text-destructive" @click="askDelete(document)">
                        <IconTrash class="size-4" />
                      </Button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <section v-if="readmeContent" class="rounded-xl border bg-card">
        <header class="flex items-center justify-between border-b px-4 py-3">
          <h2 class="flex items-center gap-2 text-sm font-semibold"><IconBook class="size-4" />README.md</h2>
        </header>
        <div class="p-4">
          <Markdown :text="readmeContent" />
        </div>
      </section>

      <div class="flex items-center gap-2 text-xs text-muted-foreground">
        <IconInfoCircle class="size-3.5" />
        AGENT.md 会被检索系统优先读取，用于描述频道结构与检索规则。
      </div>
      </template>

      <div v-else-if="activeView === 'insights'" class="flex flex-col gap-4">
        <div v-if="insightsLoading" class="flex h-48 items-center justify-center rounded-xl border"><Spinner class="size-6" /></div>
        <template v-else-if="insights">
          <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <section class="rounded-xl border bg-card p-4"><div class="text-xs text-muted-foreground">订阅用户</div><div class="mt-1 text-2xl font-semibold">{{ insights.subscribers }}</div></section>
            <section class="rounded-xl border bg-card p-4"><div class="text-xs text-muted-foreground">文档 / 存储</div><div class="mt-1 text-2xl font-semibold">{{ insights.documentCount }}</div><div class="text-xs text-muted-foreground">{{ (insights.storageBytes / 1024 / 1024).toFixed(1) }} MB</div></section>
            <section class="rounded-xl border bg-card p-4"><div class="text-xs text-muted-foreground">问答请求</div><div class="mt-1 text-2xl font-semibold">{{ insights.qaRequests }}</div><div class="text-xs text-muted-foreground">检索 {{ insights.retrievalRequests }}</div></section>
            <section class="rounded-xl border bg-card p-4"><div class="text-xs text-muted-foreground">检索命中率</div><div class="mt-1 text-2xl font-semibold">{{ insights.retrievalHitRate.toFixed(1) }}%</div><div class="text-xs text-muted-foreground">拒答 {{ insights.refusalRate.toFixed(1) }}%</div></section>
          </div>
          <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
            <section class="rounded-xl border bg-card">
              <header class="border-b px-4 py-3"><h2 class="text-sm font-semibold">近 14 天问答趋势</h2></header>
              <div class="px-2 pb-3 pt-4">
                <DashboardTrendChart :data="insights.trends" :series="[{ key: 'qa', label: '问答请求' }]" :height="220" />
              </div>
            </section>
            <section class="rounded-xl border bg-card">
              <header class="border-b px-4 py-3"><h2 class="text-sm font-semibold">性能与 Token</h2></header>
              <div class="grid grid-cols-2 gap-px bg-border">
                <div class="bg-card p-3"><div class="text-xs text-muted-foreground">平均耗时</div><div class="mt-1 font-semibold">{{ insights.avgLatencyMs }} ms</div></div>
                <div class="bg-card p-3"><div class="text-xs text-muted-foreground">P90 耗时</div><div class="mt-1 font-semibold">{{ insights.p90LatencyMs }} ms</div></div>
                <div class="bg-card p-3"><div class="text-xs text-muted-foreground">输入 Token</div><div class="mt-1 font-semibold">{{ insights.inputTokens.toLocaleString() }}</div></div>
                <div class="bg-card p-3"><div class="text-xs text-muted-foreground">输出 Token</div><div class="mt-1 font-semibold">{{ insights.outputTokens.toLocaleString() }}</div></div>
              </div>
            </section>
          </div>
          <div class="grid gap-4 xl:grid-cols-2">
            <section class="rounded-xl border bg-card">
              <header class="border-b px-4 py-3"><h2 class="text-sm font-semibold">文件格式</h2></header>
              <div class="flex flex-col gap-2 p-4">
                <div v-for="t in insights.fileTypes" :key="t.type" class="flex items-center justify-between text-sm"><span class="font-mono text-xs">{{ t.type }}</span><span class="text-muted-foreground">{{ t.count }} · {{ (t.bytes / 1024 / 1024).toFixed(1) }} MB</span></div>
                <div v-if="!insights.fileTypes.length" class="py-4 text-center text-sm text-muted-foreground">暂无文件</div>
              </div>
            </section>
            <section class="rounded-xl border bg-card">
              <header class="border-b px-4 py-3"><h2 class="text-sm font-semibold">最近文档</h2></header>
              <div class="divide-y">
                <div v-for="d in insights.topDocuments" :key="d.id" class="flex items-center justify-between px-4 py-2 text-sm"><span class="truncate">{{ d.name }}</span><Badge variant="outline">{{ d.status }}</Badge></div>
                <div v-if="!insights.topDocuments.length" class="py-6 text-center text-sm text-muted-foreground">暂无文档</div>
              </div>
            </section>
          </div>
        </template>
      </div>

      <div v-else-if="activeView === 'settings'">
        <ChannelSettings embedded />
      </div>
    </template>

    <Dialog :open="uploadOpen" @update:open="onUploadOpenChange">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>上传 {{ uploadItems.length }} 个文件</DialogTitle>
          <DialogDescription>上传到 {{ uploadTargetLabel }} · 可一次选择多个文件，将逐个上传并处理。</DialogDescription>
        </DialogHeader>
        <div class="max-h-[58vh] space-y-3 overflow-y-auto py-2 pr-1">
          <div v-for="item in uploadItems" :key="item.id" class="rounded-lg border bg-muted/20 p-3">
            <div class="flex items-center justify-between gap-2">
              <div class="flex min-w-0 items-center gap-2">
                <IconFileText class="size-4 shrink-0 text-muted-foreground" />
                <span class="truncate text-sm font-medium">{{ item.file.name }}</span>
                <span class="shrink-0 text-xs text-muted-foreground">{{ formatBytes(item.file.size) }}</span>
              </div>
              <div class="flex shrink-0 items-center gap-1">
                <Badge :variant="uploadStatusVariant(item.status)">{{ uploadStatusLabel(item.status) }}</Badge>
                <Button v-if="!uploading && item.status !== 'uploading'" variant="ghost" size="icon"
                  class="size-7 text-muted-foreground hover:text-destructive" title="移除" @click="removeUploadItem(item.id)">
                  <IconTrash class="size-3.5" />
                </Button>
              </div>
            </div>

            <div class="mt-3 grid gap-3 md:grid-cols-2">
              <div class="grid gap-2 md:col-span-2">
                <Label :for="`upload-title-${item.id}`">标题</Label>
                <Input :id="`upload-title-${item.id}`" v-model="item.title" maxlength="255"
                  placeholder="文档标题" :disabled="uploading || item.status === 'done'" />
              </div>
              <div class="grid gap-2">
                <Label :for="`upload-type-${item.id}`">文档类型</Label>
                <Input :id="`upload-type-${item.id}`" v-model="item.documentType" maxlength="32"
                  placeholder="例如：md、pdf" :disabled="uploading || item.status === 'done'" />
              </div>
              <div class="grid gap-2">
                <Label :for="`upload-expiration-${item.id}`">时效时间（可选）</Label>
                <Input :id="`upload-expiration-${item.id}`" v-model="item.expirationTime" type="datetime-local"
                  :disabled="uploading || item.status === 'done'" />
              </div>
            </div>

            <div v-if="item.status === 'uploading' || item.status === 'done' || item.status === 'error'" class="mt-3 flex flex-col gap-1">
              <div class="flex justify-between gap-2 text-xs text-muted-foreground">
                <span class="truncate">{{ item.status === 'error' ? item.error : item.status === 'done' ? '已上传并处理完成' : '正在上传…' }}</span>
                <span class="shrink-0">{{ item.progress }}%</span>
              </div>
              <div class="h-1.5 overflow-hidden rounded-full bg-muted">
                <div class="h-full rounded-full transition-all" :class="item.status === 'error' ? 'bg-destructive' : 'bg-primary'"
                  :style="{ width: `${item.progress}%` }" />
              </div>
            </div>
          </div>
        </div>
        <p class="text-xs text-muted-foreground">超过时效的文档仍可被检索，回答时会注明内容已过期。</p>
        <DialogFooter>
          <Button variant="outline" :disabled="uploading" @click="closeUpload">取消</Button>
          <Button :disabled="uploading || uploadPendingCount === 0" @click="confirmUpload">
            <Spinner v-if="uploading" class="mr-2" />
            {{ uploadDoneCount > 0 ? `继续上传（${uploadPendingCount}）` : `上传 ${uploadItems.length} 个文件` }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="createFolderOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>新建文件夹</DialogTitle>
          <DialogDescription>最多支持 5 层目录（可在系统设置中调整）。</DialogDescription>
        </DialogHeader>
        <div class="grid gap-2 py-2">
          <Label for="folder-name">文件夹名称</Label>
          <Input id="folder-name" v-model="folderName" maxlength="128" placeholder="例如：设计文档" @keyup.enter="createFolder" />
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="creatingFolder" @click="createFolderOpen = false">取消</Button>
          <Button :disabled="creatingFolder" @click="createFolder">
            <Spinner v-if="creatingFolder" class="mr-2" />创建
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="deleteOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>删除文档</DialogTitle>
          <DialogDescription>{{ deleteTarget?.name }}</DialogDescription>
        </DialogHeader>
        <div class="py-2">
          <div class="rounded-lg border bg-muted/30 p-3 text-sm">
            <p class="font-medium">移入文档回收站</p>
            <p class="mt-1 text-xs text-muted-foreground">文件会移入文档回收站，可在保留期内恢复；超过保留期后将自动永久删除。</p>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="deleting" @click="deleteOpen = false">取消</Button>
          <Button :disabled="deleting" @click="confirmDelete">
            <Spinner v-if="deleting" class="mr-2" />移入回收站
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

  </div>
</template>
