<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

defineOptions({ name: 'ChannelDetail' })
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { channelApi, dashboardApi, discoverApi, issueApi, profileApi, recycleApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { ChannelDetail, ChannelDocument, ChannelEmbeddingStatus, ChannelFolder, ChannelInsights, ChannelProcessingRecord, OwnerProfile, RecycleBinEntry } from '@/types/api'
import DashboardTrendChart from '@/components/DashboardTrendChart.vue'
import ChannelSettings from '@/pages/ChannelSettings.vue'
import ChannelIssues from '@/pages/ChannelIssues.vue'
import IssueDetail from '@/pages/IssueDetail.vue'
import IssueCreate from '@/pages/IssueCreate.vue'
import CitationPreviewDialog from '@/components/CitationPreviewDialog.vue'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import Markdown from '@/components/Markdown.vue'
import ChunkInspectorDialog from '@/components/ChunkInspectorDialog.vue'
import {
  IconArrowLeft, IconArrowRight, IconArrowUp, IconBookmark, IconBookmarkFilled,
  IconActivity, IconBook, IconChartBar, IconChevronDown, IconChevronRight, IconCloudUpload, IconEye, IconEyeOff, IconFileText,
  IconFolder, IconFolderPlus, IconLayoutGrid, IconList, IconPin, IconPlus, IconRefresh,
  IconSettings, IconStar, IconTrash, IconMessageCircle,
} from '@tabler/icons-vue'
import type { CitationSource } from '@/types/api'

let cachedDetailKey = ''
let cachedDetail: ChannelDetail | null = null
let cachedOwner: OwnerProfile | null = null

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const routeKey = computed(() => `${String(route.params.owner ?? '')}/${String(route.params.channelSlug ?? '')}`)
const canUseCache = Boolean(cachedDetail && cachedDetailKey === routeKey.value)
const loading = ref(!canUseCache)
const error = ref('')
const detail = ref<ChannelDetail | null>(canUseCache ? cachedDetail : null)
const viewMode = ref<'grid' | 'list'>(localStorage.getItem('cognia.channel.viewMode') === 'grid' ? 'grid' : 'list')
const chunkOpen = ref(false)
const chunkDocument = ref<{ id: string | number; name: string } | null>(null)

function openChunks(document: { id: string | number; name: string }) {
  chunkDocument.value = document
  chunkOpen.value = true
}



function setViewMode(mode: 'grid' | 'list') {
  viewMode.value = mode
  localStorage.setItem('cognia.channel.viewMode', mode)
}

const ownerParam = computed(() => String(route.params.owner ?? ''))
const slugParam = computed(() => String(route.params.channelSlug ?? ''))
const channel = computed(() => detail.value?.channel)
const ownerPath = computed(() => `/${ownerParam.value}`)
const canWrite = computed(() => detail.value?.canWrite ?? false)
const canManage = computed(() => detail.value?.canManage ?? false)
const canUpload = computed(() => canWrite.value && userStore.hasPermission('document:upload'))
const canViewInsights = computed(() => detail.value?.canViewInsights ?? false)
const about = computed(() => detail.value?.about)
const ownerProfile = ref<OwnerProfile | null>(canUseCache ? cachedOwner : null)
const baseChannelPath = computed(() => `/${ownerParam.value}/${slugParam.value}`)
const activeView = computed<'documents' | 'insights' | 'settings' | 'recycle' | 'embedding' | 'issues'>(() => {
  if (route.name === 'ChannelInsights') return 'insights'
  if (route.name === 'ChannelEmbedding') return 'embedding'
  if (route.name === 'ChannelRecycle') return 'recycle'
  if (route.name === 'ChannelSettings') return 'settings'
  if (route.name === 'ChannelIssues' || route.name === 'IssueDetail' || route.name === 'ChannelIssueCreate') return 'issues'
  return 'documents'
})
const openIssueCount = ref(0)

function goTab(tab: 'documents' | 'insights' | 'settings' | 'recycle' | 'embedding' | 'issues') {
  if (tab === 'documents') router.push(baseChannelPath.value)
  else if (tab === 'settings') router.push(`${baseChannelPath.value}/settings`)
  else router.push(`${baseChannelPath.value}/${tab}`)
}

async function loadIssueCount() {
  if (!channel.value) return
  try {
    const res = await issueApi.list(Number(channel.value.id), { status: 'OPEN', page: 1, size: 1 })
    openIssueCount.value = res.total
  } catch {
    openIssueCount.value = 0
  }
}
const recycleEntries = ref<RecycleBinEntry[]>([])
const recycleLoading = ref(false)
const restoringId = ref<string | null>(null)
const insights = ref<ChannelInsights | null>(null)
const insightsLoading = ref(false)

async function openInsights() {
  if (route.name !== 'ChannelInsights') { await router.push(`${baseChannelPath.value}/insights`) }
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

let embeddingPoll: ReturnType<typeof setInterval> | null = null

function stopEmbeddingPoll() {
  if (embeddingPoll) { clearInterval(embeddingPoll); embeddingPoll = null }
}

function scheduleEmbeddingPoll() {
  stopEmbeddingPoll()
  if (!channel.value) return
  if (!embeddingStatus.value?.reembedding && !embeddingStatus.value?.jobs.some(j => j.status === 'QUEUED' || j.status === 'RUNNING')) return
  embeddingPoll = setInterval(async () => {
    if (!channel.value) return stopEmbeddingPoll()
    try {
      embeddingStatus.value = await channelApi.embedding(channel.value.id)
      if (!embeddingStatus.value.reembedding && !embeddingStatus.value.jobs.some(j => j.status === 'QUEUED' || j.status === 'RUNNING')) {
        stopEmbeddingPoll()
        await load()
      }
    } catch { stopEmbeddingPoll() }
  }, 3000)
}

async function reembed() {
  if (!detail.value?.channel || reembedding.value || embeddingStatus.value?.reembedding) return
  reembedding.value = true
  try {
    const queued = await channelApi.reembed(detail.value.channel.id)
    toast.success(`已加入重新向量化队列（${queued} 个文档）`)
    if (route.name !== 'ChannelEmbedding') await router.push(`${baseChannelPath.value}/embedding`)
    await refreshEmbedding()
  } catch (e: any) {
    toast.error(e?.message || '重新向量化失败')
  } finally {
    reembedding.value = false
  }
}

async function cancelEmbedding() {
  if (!channel.value) return
  reembedding.value = true
  try {
    const cancelled = await channelApi.cancelEmbedding(channel.value.id)
    toast.success(`已取消 ${cancelled} 个处理任务`)
    await refreshEmbedding()
    await load()
  } catch (e: any) {
    toast.error(e?.message || '取消失败')
  } finally {
    reembedding.value = false
  }
}

async function refreshEmbedding() {
  if (!channel.value) return
  try {
    embeddingStatus.value = await channelApi.embedding(channel.value.id)
    scheduleEmbeddingPoll()
  } catch (e: any) {
    toast.error(e?.message || '加载向量化状态失败')
  }
}

async function openEmbedding() {
  if (!channel.value) return
  if (route.name !== 'ChannelEmbedding') { await router.push(`${baseChannelPath.value}/embedding`) }
  embeddingLoading.value = true
  try {
    await refreshEmbedding()
  } finally {
    embeddingLoading.value = false
  }
}

function processingStatusLabel(status: string) {
  return ({ QUEUED: '等待中', PROCESSING: '处理中', SUCCESS: '已完成', FAILED: '失败' } as Record<string, string>)[status] ?? status
}

function processingStatusVariant(status: string) {
  if (status === 'FAILED') return 'destructive' as const
  if (status === 'SUCCESS') return 'default' as const
  if (status === 'PROCESSING') return 'secondary' as const
  return 'outline' as const
}

function processingStageLabel(stage: string) {
  return ({ UPLOAD: '上传', PARSE: '解析/OCR', EMBED: '向量化' } as Record<string, string>)[stage] ?? stage
}

function recordProgress(record: ChannelProcessingRecord) {
  if (!record.chunkTotal) return record.status === 'SUCCESS' ? 100 : 0
  return Math.min(100, Math.round((record.chunkDone / record.chunkTotal) * 100))
}

async function retryRecord(record: ChannelProcessingRecord) {
  if (!channel.value) return
  try {
    await channelApi.retryDocument(channel.value.id, record.documentId)
    toast.success('已重新加入处理队列')
    await refreshEmbedding()
  } catch (e: any) {
    toast.error(e?.message || '重试失败')
  }
}


async function openRecycle() {
  if (route.name !== 'ChannelRecycle') await router.push(`${baseChannelPath.value}/recycle`)
  await loadRecycle()
}

async function loadRecycle() {
  if (!channel.value) return
  recycleLoading.value = true
  try {
    recycleEntries.value = await recycleApi.listByChannel(channel.value.id)
  } catch (err: any) {
    toast.error(err?.message || '加载回收站失败')
  } finally {
    recycleLoading.value = false
  }
}

async function restoreRecycleEntry(entry: RecycleBinEntry) {
  if (!channel.value) return
  restoringId.value = entry.id
  try {
    await recycleApi.restoreChannel(channel.value.id, entry.id)
    toast.success('文件已恢复')
    await Promise.all([loadRecycle(), load()])
  } catch (err: any) {
    toast.error(err?.message || '恢复失败')
  } finally {
    restoringId.value = null
  }
}
const subscribed = ref(false)
const notifySubscribed = ref(false)
const engagementBusy = ref(false)
const pinning = ref(false)

async function loadEngagement() {
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
    detail.value = await channelApi.detail(channel.value.id)
    toast.success(subscribed.value ? '已订阅频道' : '已取消订阅')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  } finally {
    engagementBusy.value = false
  }
}

async function togglePin() {
  if (!channel.value) return
  pinning.value = true
  try {
    await channelApi.setPinned(channel.value.id, !channel.value.pinned)
    detail.value = await channelApi.detail(channel.value.id)
    toast.success(channel.value?.pinned ? '频道已置顶' : '已取消置顶')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  } finally {
    pinning.value = false
  }
}

function formatCompact(value: number) {
  const count = Number(value || 0)
  if (count < 1000) return String(count)
  if (count < 1_000_000) {
    const formatted = (count / 1000).toFixed(count >= 10_000 ? 0 : 1).replace(/\.0$/, '')
    return `${formatted}k`
  }
  const formatted = (count / 1_000_000).toFixed(count >= 10_000_000 ? 0 : 1).replace(/\.0$/, '')
  return `${formatted}M`
}

function formatDetailed(value: number) {
  return new Intl.NumberFormat('en-US').format(Number(value || 0))
}

async function toggleNotify() {
  if (!channel.value) return
  engagementBusy.value = true
  try {
    if (notifySubscribed.value) await discoverApi.unsubscribeNotify(channel.value.id)
    else await discoverApi.subscribeNotify(channel.value.id)
    notifySubscribed.value = !notifySubscribed.value
    detail.value = await channelApi.detail(channel.value.id)
    toast.success(notifySubscribed.value ? '已开启频道通知' : '已关闭频道通知')
  } catch (err: any) {
    toast.error(err?.message || '操作失败')
  } finally {
    engagementBusy.value = false
  }
}

const folderPathParam = computed(() => {
  const raw = route.params.folderPath
  const values = Array.isArray(raw) ? raw : raw ? [raw] : []
  return values.flatMap(value => String(value).split('/')).filter(Boolean)
})

const folderTrail = computed<ChannelFolder[]>(() => {
  const folders = detail.value?.folders ?? []
  const trail: ChannelFolder[] = []
  let parentId: string | null = null
  for (const name of folderPathParam.value) {
    const folder = folders.find(item => (item.parentId ?? null) === parentId && item.name === name)
    if (!folder) break
    trail.push(folder)
    parentId = folder.id
  }
  return trail
})

const currentFolder = computed(() => folderTrail.value.at(-1) ?? null)
const selectedFolder = computed(() => currentFolder.value?.id ?? null)

const allDocuments = computed(() => detail.value?.documents ?? [])
const currentDocuments = computed(() => {
  const documents = selectedFolder.value === null
    ? allDocuments.value.filter(document => !document.folderId)
    : allDocuments.value.filter(document => document.folderId === selectedFolder.value)
  return documents.filter(document => document.status === 'READY')
})
const visibleFolders = computed(() =>
  (detail.value?.folders ?? []).filter(folder => (folder.parentId ?? null) === selectedFolder.value),
)
const documentCount = computed(() => currentDocuments.value.length)
const folderCount = computed(() => visibleFolders.value.length)

function folderDocumentCount(folder: ChannelFolder) {
  return allDocuments.value.filter(document => document.folderId === folder.id && document.status === 'READY').length
}

function pathForTrail(trail: ChannelFolder[]) {
  const suffix = trail.map(folder => `/${encodeURIComponent(folder.name)}`).join('')
  return `/${ownerParam.value}/${slugParam.value}${suffix}`
}

function trailForFolder(folder: ChannelFolder | null) {
  const folders = detail.value?.folders ?? []
  if (!folder) return []
  const trail: ChannelFolder[] = []
  let current: ChannelFolder | undefined = folder
  const seen = new Set<string>()
  while (current && !seen.has(current.id)) {
    seen.add(current.id)
    trail.unshift(current)
    current = current.parentId ? folders.find(item => item.id === current?.parentId) : undefined
  }
  return trail
}

function goFolder(folder: ChannelFolder | null) {
  void router.push(pathForTrail(trailForFolder(folder)))
}

function goUp() {
  const parentId = currentFolder.value?.parentId ?? null
  if (!parentId) {
    void router.push(pathForTrail([]))
    return
  }
  const parent = (detail.value?.folders ?? []).find(folder => folder.id === parentId) ?? null
  goFolder(parent)
}

const readmeContent = ref('')
const readmeLoading = ref(false)

async function loadReadme(channelId: string) {
  if (!detail.value?.channel || !detail.value.about?.hasReadme) {
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

function formatBytes(value: number) {
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  return `${(value / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

async function load() {
  const key = routeKey.value
  const hasCached = Boolean(cachedDetail && cachedDetailKey === key)
  if (!hasCached) {
    loading.value = true
    detail.value = null
  }
  error.value = ''
  try {
    const profile = await profileApi.get(ownerParam.value)
    ownerProfile.value = profile
    const channels = await channelApi.list(profile.type, profile.id)
    const target = channels.find(item => item.slug === slugParam.value)
    if (!target) {
      error.value = '频道不存在或你没有访问权限'
      detail.value = null
      cachedDetail = null
      cachedDetailKey = ''
      return
    }
    detail.value = await channelApi.detail(target.id)
    cachedDetail = detail.value
    cachedDetailKey = key
    cachedOwner = profile
    void loadEngagement()
    void loadIssueCount()
    await loadReadme(target.id)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载频道失败'
    detail.value = null
    cachedDetail = null
    cachedDetailKey = ''
  } finally {
    loading.value = false
  }
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
    createFolderOpen.value = false
    goFolder((detail.value?.folders ?? []).find(item => item.id === folder.id) ?? folder)
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
    const parentId = folder.parentId ?? null
    const deletingCurrent = folderTrail.value.some(item => item.id === folder.id)
    await channelApi.deleteFolder(channel.value.id, folder.id)
    detail.value = await channelApi.detail(channel.value.id)
    if (deletingCurrent) {
      const parent = parentId ? (detail.value?.folders ?? []).find(item => item.id === parentId) ?? null : null
      goFolder(parent)
    }
    toast.success('文件夹已删除')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '删除文件夹失败')
  }
}

const fileInput = ref<HTMLInputElement | null>(null)
const directoryInput = ref<HTMLInputElement | null>(null)

type UploadStatus = 'pending' | 'uploading' | 'done' | 'error'

interface UploadItem {
  id: string
  file: File
  title: string
  documentType: string
  expirationTime: string
  folderId?: string
  status: UploadStatus
  progress: number
  error: string
}

const uploading = ref(false)
const uploadProgress = ref(0)
const uploadingName = ref('')
const uploadOpen = ref(false)
const uploadItems = ref<UploadItem[]>([])
const reembedding = ref(false)
const reembedLocked = computed(() => reembedding.value || Boolean(embeddingStatus.value?.reembedding) || Boolean(about.value?.reembedding))
const embeddingStatus = ref<ChannelEmbeddingStatus | null>(null)

const recordsPage = ref(1)
const recordsPageSize = 10
const showCompletedRecords = ref(false)
const completedRecords = computed(() => embeddingStatus.value?.records.filter(r => r.status === 'SUCCESS') ?? [])
const pendingRecords = computed(() => embeddingStatus.value?.records.filter(r => r.status !== 'SUCCESS') ?? [])
const filteredRecords = computed(() => showCompletedRecords.value
  ? (embeddingStatus.value?.records ?? [])
  : pendingRecords.value)
const recordsTotalPages = computed(() => Math.max(1, Math.ceil(filteredRecords.value.length / recordsPageSize)))
const pagedRecords = computed(() => {
  const page = Math.min(recordsPage.value, recordsTotalPages.value)
  return filteredRecords.value.slice((page - 1) * recordsPageSize, page * recordsPageSize)
})
watch(recordsTotalPages, (pages) => { if (recordsPage.value > pages) recordsPage.value = pages })
const embeddingLoading = ref(false)

const uploadPendingCount = computed(() => uploadItems.value.filter(item => item.status !== 'done').length)
const uploadDoneCount = computed(() => uploadItems.value.filter(item => item.status === 'done').length)

function triggerUpload() {
  if (!canWrite.value) return
  uploadItems.value = []
  fileInput.value?.click()
}

function triggerDirectoryUpload() {
  if (!canWrite.value) return
  uploadItems.value = []
  directoryInput.value?.click()
}

async function onDirectorySelected(event: Event) {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files ?? [])
  target.value = ''
  if (!files.length || !channel.value) return
  const folders = detail.value?.folders ?? []
  const byParentName = new Map<string, string>()
  for (const folder of folders) byParentName.set(`${folder.parentId ?? 'root'}/${folder.name}`, folder.id)
  const baseParent = selectedFolder.value ?? 'root'
  const resolve = async (segments: string[]): Promise<string | undefined> => {
    let parent = baseParent
    for (const segment of segments) {
      const key = `${parent}/${segment}`
      let id = byParentName.get(key)
      if (!id) {
        const created = await channelApi.createFolder(channel.value!.id, {
          parentId: parent === 'root' ? undefined : parent,
          name: segment,
        })
        id = created.id
        byParentName.set(key, id)
        detail.value?.folders.push(created)
      }
      parent = id
    }
    return parent === 'root' ? undefined : parent
  }
  const items: UploadItem[] = []
  for (const file of files) {
    const relative = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name
    const parts = relative.split('/').filter(Boolean)
    const segments = parts.slice(0, -1)
    const folderId = await resolve(segments)
    items.push({
      id: uploadItemId(), file, folderId,
      title: file.name.replace(/\.[^.]+$/, ''),
      documentType: file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : 'text',
      expirationTime: '', status: 'pending', progress: 0, error: '',
    })
  }
  uploadItems.value = items
  uploadOpen.value = true
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
  const uploadBatchId = crypto.randomUUID()
  let succeeded = 0
  let failed = 0

  for (const item of pending) {
    item.status = 'uploading'
    item.progress = 0
    item.error = ''
    uploadingName.value = item.file.name
    uploadProgress.value = 0
    try {
      await channelApi.uploadDocument(channel.value.id, item.file, item.folderId ?? selectedFolder.value ?? undefined, (percent) => {
        item.progress = percent
        uploadProgress.value = percent
      }, {
        title: item.title.trim() || undefined,
        documentType: item.documentType.trim() || undefined,
        expirationTime: item.expirationTime || undefined,
        uploadBatchId,
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

const selectedDocuments = ref<string[]>([])
const moveBuffer = ref<string[]>([])
const moving = ref(false)

function isSelected(id: string) {
  return selectedDocuments.value.includes(id)
}

function toggleDocumentSelection(id: string, checked: boolean) {
  if (checked) {
    if (!selectedDocuments.value.includes(id)) selectedDocuments.value.push(id)
  } else {
    selectedDocuments.value = selectedDocuments.value.filter(item => item !== id)
  }
}

function stageMove() {
  if (!selectedDocuments.value.length) return
  moveBuffer.value = [...selectedDocuments.value]
  selectedDocuments.value = []
  toast.info(`已记录 ${moveBuffer.value.length} 个文件，进入目标文件夹后点击“移动到这里”`)
}

async function moveBufferHere() {
  if (!channel.value || !moveBuffer.value.length) return
  moving.value = true
  try {
    const moved = await channelApi.moveDocuments(channel.value.id, moveBuffer.value, currentFolder.value?.id)
    moveBuffer.value = []
    detail.value = await channelApi.detail(channel.value.id)
    toast.success(`已移动 ${moved} 个文件`)
  } catch (e: any) {
    toast.error(e?.message || '移动失败')
  } finally {
    moving.value = false
  }
}

watch(() => route.fullPath, () => { selectedDocuments.value = [] })

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

const previewOpen = ref(false)
const previewDocument = ref<ChannelDocument | null>(null)
const previewSource = computed<CitationSource | null>(() => {
  const document = previewDocument.value
  if (!document) return null
  return {
    index: 0,
    documentId: document.id,
    channelId: channel.value?.id,
    channelName: channel.value?.name,
    fileName: document.name,
    title: document.title || document.name,
    documentType: document.documentType,
  }
})

function openDocument(document: ChannelDocument) {
  previewDocument.value = document
  previewOpen.value = true
}

function baseName(name: string) {
  return name.toLowerCase().replace(/\.[^.]+$/, '')
}

const readmeDocument = computed(() => allDocuments.value.find(document =>
  !document.folderId && baseName(document.name) === 'readme'))

function openReadme() {
  if (readmeDocument.value) openDocument(readmeDocument.value)
  else toast.info('README 暂不可预览')
}

const contributorLink = (username: string) => `/${username}`

function formatFileType(type: string) {
  const value = (type || 'file').toLowerCase()
  return value.length <= 3 ? value.toUpperCase() : value.charAt(0).toUpperCase() + value.slice(1)
}

const FILE_TYPE_COLORS = ['#6366f1', '#22c55e', '#f59e0b', '#ec4899', '#06b6d4', '#8b5cf6', '#ef4444', '#84cc16', '#f97316', '#14b8a6']
const fileTypeSegments = computed(() => {
  const types = about.value?.fileTypes ?? []
  const total = types.reduce((sum, item) => sum + item.count, 0) || 1
  return types.map((item, index) => ({
    ...item,
    color: FILE_TYPE_COLORS[index % FILE_TYPE_COLORS.length],
    percent: (item.count / total) * 100,
    label: formatFileType(item.type),
  }))
})

onUnmounted(() => stopEmbeddingPoll())
onMounted(load)
watch(() => [route.params.owner, route.params.channelSlug], load)
watch(() => route.name, (name) => {
  if (name === 'ChannelInsights') void openInsights()
  else if (name === 'ChannelEmbedding') void openEmbedding()
  else if (name === 'ChannelRecycle') void openRecycle()
})
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
            <Avatar class="size-6">
              <AvatarImage :src="ownerProfile?.avatar || '/logo.png'" :alt="ownerProfile?.nickname || channel.name" class="object-cover" />
              <AvatarFallback class="text-[10px]">{{ (ownerProfile?.nickname || channel.name).slice(0, 1).toUpperCase() }}</AvatarFallback>
            </Avatar>
            <span data-i18n-skip>{{ channel.name }}</span>
            <Badge :variant="channel.isPublic ? 'secondary' : 'outline'">{{ channel.isPublic ? '公开' : '私密' }}</Badge>
          </h1>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <Button v-if="canManage" variant="outline" size="sm" :disabled="pinning" @click="togglePin">
            <IconPin class="mr-1 size-4" />{{ channel.pinned ? '取消置顶' : '置顶' }}
          </Button>
          <Button v-if="subscribed || channel.isPublic" variant="outline" size="sm"
            :disabled="engagementBusy" :aria-pressed="subscribed" @click="toggleSubscribe">
            <IconBookmarkFilled v-if="subscribed" class="mr-1 size-4 text-primary" />
            <IconBookmark v-else class="mr-1 size-4" />
            {{ subscribed ? (channel.isPublic ? '已订阅' : '取消订阅') : '订阅' }}
            <Badge variant="secondary" class="ml-1 h-5 min-w-5 justify-center px-1.5 text-[10px]">
              {{ formatCompact(about?.subscriberCount ?? 0) }}
            </Badge>
          </Button>
          <Button v-if="notifySubscribed || channel.isPublic" variant="outline" size="sm"
            :disabled="engagementBusy" :aria-pressed="notifySubscribed" @click="toggleNotify">
            <IconEye v-if="notifySubscribed" class="mr-1 size-4 text-primary" />
            <IconEyeOff v-else class="mr-1 size-4" />
            {{ notifySubscribed ? (channel.isPublic ? '通知已开启' : '关闭通知') : '接收通知' }}
            <Badge variant="secondary" class="ml-1 h-5 min-w-5 justify-center px-1.5 text-[10px]">
              {{ formatCompact(about?.notifyCount ?? 0) }}
            </Badge>
          </Button>
          <input ref="fileInput" type="file" multiple class="hidden" @change="onFileSelected" />
          <input ref="directoryInput" type="file" webkitdirectory multiple class="hidden" @change="onDirectorySelected" />
        </div>
      </div>

      <div class="flex items-center gap-1 border-b">
        <button type="button" class="border-b-2 px-3 py-2 text-sm transition"
          :class="activeView === 'documents' ? 'border-primary font-medium text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="goTab('documents')">文档</button>
        <button v-if="canViewInsights" type="button" class="flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition"
          :class="activeView === 'insights' ? 'border-primary font-medium text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="openInsights">
          <IconChartBar class="size-4" />数据洞察
        </button>
        <button v-if="canManage" type="button" class="flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition"
          :class="activeView === 'embedding' ? 'border-primary font-medium text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="openEmbedding">
          <IconActivity class="size-4" />向量化
        </button>
        <button v-if="channel.isPublic || canManage" type="button" class="flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition"
          :class="activeView === 'issues' ? 'border-primary font-medium text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="goTab('issues')">
          <IconMessageCircle class="size-4" />工单
          <Badge v-if="openIssueCount" variant="secondary" class="h-5 min-w-5 px-1.5 text-[10px]">{{ openIssueCount }}</Badge>
        </button>
        <button v-if="canManage" type="button" class="flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition"
          :class="activeView === 'settings' ? 'border-primary font-medium text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="goTab('settings')">
          <IconSettings class="size-4" />频道设置
        </button>
        <button v-if="canManage" type="button" class="flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition"
          :class="activeView === 'recycle' ? 'border-primary font-medium text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="openRecycle">
          <IconTrash class="size-4" />回收站
          <Badge v-if="recycleEntries.length" variant="secondary" class="h-5 min-w-5 px-1.5 text-[10px]">{{ recycleEntries.length }}</Badge>
        </button>
      </div>

      <template v-if="activeView === 'documents'">
      <div class="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
      <div class="flex min-w-0 flex-col gap-4">
      <Alert v-if="channel.archived">
        <AlertDescription>频道已存档，暂停上传与文件管理；订阅者仍可查看和检索。取消存档后可继续编辑。</AlertDescription>
      </Alert>
      <Alert v-if="about && !about.embeddingReady" variant="destructive">
        <AlertDescription class="flex flex-wrap items-center justify-between gap-2">
          <span>该频道的向量已过期，暂不可用于检索{{ about.pendingEmbeddingChunks ? `（待处理 ${about.pendingEmbeddingChunks} 个片段）` : '' }}。</span>
          <Button v-if="canManage" size="sm" variant="outline" :disabled="reembedLocked" @click="reembed">
            <Spinner v-if="reembedLocked" class="mr-1 size-3" />{{ reembedLocked ? '正在重新向量化' : '重新向量化' }}
          </Button>
        </AlertDescription>
      </Alert>

      <div v-if="uploading" class="rounded-xl border bg-card p-4">
        <div class="mb-2 flex items-center justify-between text-sm">
          <span class="truncate">{{ uploadingName }}</span>
          <span class="text-muted-foreground">{{ uploadDoneCount }}/{{ uploadItems.length }} · {{ uploadProgress }}%</span>
        </div>
        <div class="h-1.5 overflow-hidden rounded-full bg-muted">
          <div class="h-full rounded-full bg-primary transition-all" :style="{ width: `${uploadProgress}%` }" />
        </div>
      </div>

      <div v-if="moveBuffer.length" class="flex flex-wrap items-center justify-between gap-2 rounded-xl border bg-muted/30 px-4 py-3 text-sm">
        <span>已记录 {{ moveBuffer.length }} 个文件。进入目标文件夹后点击“移动到这里”。</span>
        <Button variant="ghost" size="sm" @click="moveBuffer = []">取消</Button>
      </div>

      <section class="min-w-0 overflow-hidden rounded-xl border bg-card">
        <header class="flex flex-col gap-3 border-b px-4 py-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex min-w-0 items-center gap-1">
              <Button variant="ghost" size="icon" class="size-8" title="后退" @click="router.back()">
                <IconArrowLeft class="size-4" />
              </Button>
              <Button variant="ghost" size="icon" class="size-8" title="前进" @click="router.forward()">
                <IconArrowRight class="size-4" />
              </Button>
              <Button variant="ghost" size="icon" class="size-8" title="上一级" :disabled="!currentFolder" @click="goUp">
                <IconArrowUp class="size-4" />
              </Button>
              <nav class="ml-1 flex min-w-0 items-center gap-1 text-sm" aria-label="目录导航">
                <button type="button" class="truncate rounded px-1 py-0.5 hover:bg-muted" @click="goFolder(null)">
                  {{ channel.name }}
                </button>
                <template v-for="folder in folderTrail" :key="folder.id">
                  <IconChevronRight class="size-3.5 shrink-0 text-muted-foreground" />
                  <button type="button" class="truncate rounded px-1 py-0.5 hover:bg-muted" @click="goFolder(folder)">
                    {{ folder.name }}
                  </button>
                </template>
              </nav>
            </div>

            <div class="flex flex-wrap items-center gap-1.5">
              <div class="mr-1 flex items-center rounded-md border p-0.5" role="group" aria-label="显示方式">
                <Button variant="ghost" size="icon" class="size-7" :class="viewMode === 'grid' ? 'bg-muted' : ''" title="平铺" @click="setViewMode('grid')">
                  <IconLayoutGrid class="size-4" />
                </Button>
                <Button variant="ghost" size="icon" class="size-7" :class="viewMode === 'list' ? 'bg-muted' : ''" title="列表" @click="setViewMode('list')">
                  <IconList class="size-4" />
                </Button>
              </div>
              <Button variant="outline" size="sm" :disabled="loading" @click="load">
                <IconRefresh class="mr-1 size-4" />刷新
              </Button>
              <Button v-if="canUpload && !channel.archived" variant="outline" size="sm" :disabled="uploading" @click="openCreateFolder">
                <IconFolderPlus class="mr-1 size-4" />新建文件夹
              </Button>
              <DropdownMenu v-if="canUpload && !channel.archived">
                <DropdownMenuTrigger as-child>
                  <Button size="sm" :disabled="uploading">
                    <IconPlus class="mr-1 size-4" />添加<IconChevronDown class="ml-1 size-3.5" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-40">
                  <DropdownMenuItem @select="triggerUpload">
                    <IconFileText class="mr-2 size-4" />上传文件
                  </DropdownMenuItem>
                  <DropdownMenuItem @select="triggerDirectoryUpload">
                    <IconFolder class="mr-2 size-4" />上传文件夹
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
              <Button v-if="canUpload && !channel.archived && selectedDocuments.length" variant="outline" size="sm" @click="stageMove">
                <IconFolder class="mr-1 size-4" />移动（{{ selectedDocuments.length }}）
              </Button>
              <Button v-if="canUpload && !channel.archived && moveBuffer.length" size="sm" :disabled="moving" @click="moveBufferHere">
                <Spinner v-if="moving" class="mr-1 size-3" />
                <IconFolder v-else class="mr-1 size-4" />移动到这里（{{ moveBuffer.length }}）
              </Button>
              <Button v-if="moveBuffer.length" variant="ghost" size="sm" @click="moveBuffer = []">取消移动</Button>
            </div>
          </div>
          <div class="flex flex-wrap items-center justify-between gap-1 text-xs text-muted-foreground">
            <span>{{ folderCount }} 个文件夹 · {{ documentCount }} 个文件</span>
            <span class="hidden sm:inline">双击文件夹进入，双击文件预览</span>
          </div>
        </header>

        <div v-if="!visibleFolders.length && !currentDocuments.length" class="flex flex-col items-center gap-3 px-4 py-16 text-center">
          <div class="flex size-12 items-center justify-center rounded-full bg-muted">
            <IconFolder class="size-6 text-muted-foreground" />
          </div>
          <div>
            <p class="text-sm font-medium">{{ currentFolder ? '这个文件夹还是空的' : '这里还没有文件' }}</p>
            <p class="mt-1 text-xs text-muted-foreground">上传 PDF、Markdown、TXT 等文件，或新建文件夹整理内容。</p>
          </div>
          <div v-if="canUpload && !channel.archived" class="flex gap-2">
            <Button variant="outline" size="sm" @click="openCreateFolder">
              <IconFolderPlus class="mr-1 size-4" />新建文件夹
            </Button>
            <Button v-if="canUpload" variant="outline" size="sm" @click="triggerUpload">
              <IconCloudUpload class="mr-1 size-4" />上传文件
            </Button>
          </div>
        </div>

        <div v-else-if="viewMode === 'grid'" class="grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          <div v-for="folder in visibleFolders" :key="`folder-${folder.id}`"
            class="group relative flex cursor-default items-center gap-3 rounded-lg border p-3 transition hover:border-primary/40 hover:bg-muted/40"
            @dblclick="goFolder(folder)">
            <div class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <IconFolder class="size-4" />
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium">{{ folder.name }}</p>
              <p class="text-xs text-muted-foreground">{{ folderDocumentCount(folder) }} 个文件</p>
            </div>
            <Button v-if="canUpload" variant="ghost" size="icon"
              class="absolute right-1 top-1 size-7 opacity-100 text-muted-foreground hover:text-destructive sm:opacity-0 sm:group-hover:opacity-100"
              title="删除文件夹" @click.stop="deleteFolder(folder)">
              <IconTrash class="size-3.5" />
            </Button>
          </div>

          <div v-for="document in currentDocuments" :key="`doc-${document.id}`"
            class="group relative flex cursor-default flex-col gap-2 rounded-lg border p-3 transition hover:border-primary/40 hover:bg-muted/40"
            @dblclick="openDocument(document)">
            <div class="flex min-w-0 items-center gap-2 pr-12">
              <Checkbox v-if="canUpload && !channel.archived" :model-value="isSelected(document.id)"
                class="shrink-0" @click.stop
                @update:model-value="(value) => toggleDocumentSelection(document.id, Boolean(value))" />
              <IconFileText class="size-4 shrink-0 text-muted-foreground" />
              <span class="truncate text-sm font-medium" :title="document.name">{{ document.name }}</span>
            </div>
            <div class="flex items-center justify-between gap-2 text-xs text-muted-foreground">
              <span>{{ formatBytes(document.size) }}</span>
              <span>{{ document.createTime || '' }}</span>
            </div>
            <div class="absolute right-1 top-1 flex items-center gap-0.5">
              <Button v-if="canManage" variant="ghost" size="icon" class="size-7 text-muted-foreground" title="查看分片" @click.stop="openChunks(document)">
                <IconList class="size-3.5" />
              </Button>
              <Button v-if="canUpload" variant="ghost" size="icon"
                class="size-7 text-muted-foreground hover:text-destructive" title="删除" @click.stop="askDelete(document)">
                <IconTrash class="size-3.5" />
              </Button>
            </div>
          </div>
        </div>

        <div v-else class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead class="border-b bg-muted/40 text-xs text-muted-foreground">
              <tr>
                <th class="w-10 px-3 py-2.5 text-left font-medium">
                  <Checkbox v-if="canWrite && !channel.archived"
                    :model-value="currentDocuments.length > 0 && currentDocuments.every(d => isSelected(d.id))"
                    @update:model-value="(value) => selectedDocuments = value ? currentDocuments.map(d => d.id) : []" />
                </th>
                <th class="px-4 py-2.5 text-left font-medium">名称</th>
                <th class="px-3 py-2.5 text-left font-medium">类型</th>
                <th class="px-3 py-2.5 text-left font-medium">大小</th>
                <th class="px-3 py-2.5 text-left font-medium">上传时间</th>
                <th class="px-3 py-2.5 text-right font-medium">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y">
              <tr v-for="folder in visibleFolders" :key="`folder-${folder.id}`" class="cursor-default hover:bg-muted/30" @dblclick="goFolder(folder)">
                <td class="w-10 px-3 py-3"></td>
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <IconFolder class="size-4 shrink-0 text-primary" />
                    <span class="truncate font-medium">{{ folder.name }}</span>
                  </div>
                </td>
                <td class="px-3 py-3 text-xs text-muted-foreground">文件夹</td>
                <td class="px-3 py-3 text-muted-foreground">{{ folderDocumentCount(folder) }} 个文件</td>
                <td class="px-3 py-3 text-xs text-muted-foreground">-</td>
                <td class="whitespace-nowrap px-3 py-3">
                  <div class="flex justify-end">
                    <Button v-if="canUpload" variant="ghost" size="icon" class="size-8 text-muted-foreground hover:text-destructive"
                      title="删除文件夹" @click.stop="deleteFolder(folder)">
                      <IconTrash class="size-4" />
                    </Button>
                  </div>
                </td>
              </tr>
              <tr v-for="document in currentDocuments" :key="document.id" class="cursor-default hover:bg-muted/30" @dblclick="openDocument(document)">
                <td class="w-10 px-3 py-3">
                  <Checkbox v-if="canUpload && !channel.archived" :model-value="isSelected(document.id)"
                    class="shrink-0" @click.stop
                    @update:model-value="(value) => toggleDocumentSelection(document.id, Boolean(value))" />
                </td>
                <td class="max-w-[320px] px-4 py-3">
                  <div class="flex items-center gap-2">
                    <IconFileText class="size-4 shrink-0 text-muted-foreground" />
                    <span class="truncate font-medium">{{ document.name }}</span>
                  </div>
                  <p v-if="document.errorMessage" class="mt-1 truncate text-xs text-destructive" :title="document.errorMessage">
                    {{ document.errorMessage }}
                  </p>
                </td>
                <td class="whitespace-nowrap px-3 py-3 text-xs text-muted-foreground">{{ document.documentType || '文件' }}</td>
                <td class="whitespace-nowrap px-3 py-3 text-muted-foreground">{{ formatBytes(document.size) }}</td>
                <td class="whitespace-nowrap px-3 py-3 text-xs text-muted-foreground">{{ document.createTime || '-' }}</td>
                <td class="whitespace-nowrap px-3 py-3">
                  <div class="flex justify-end gap-1">
                    <Button v-if="canManage" variant="ghost" size="icon" class="size-8 text-muted-foreground"
                      title="查看分片" @click.stop="openChunks(document)">
                      <IconList class="size-4" />
                    </Button>
                    <Button v-if="canUpload" variant="ghost" size="icon" class="size-8 text-muted-foreground hover:text-destructive"
                      title="删除" @click.stop="askDelete(document)">
                      <IconTrash class="size-4" />
                    </Button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="readmeContent" class="rounded-xl border bg-card">
        <div class="p-4">
          <Markdown :text="readmeContent" />
        </div>
      </section>
      </div>

      <aside class="flex flex-col gap-4 xl:sticky xl:top-4">
        <section class="rounded-xl p-5">
          <h2 class="text-base font-semibold">关于</h2>
          <p class="mt-3 whitespace-pre-line text-sm leading-relaxed" data-i18n-skip>{{ about?.description || '这个频道还没有简介。' }}</p>

          <div class="mt-5 flex flex-col gap-3 text-sm">
            <button type="button" class="flex items-center gap-2.5 text-left transition"
              :class="about?.hasReadme ? 'text-foreground hover:text-primary' : 'text-muted-foreground'"
              :disabled="!about?.hasReadme" @click="openReadme">
              <IconBook class="size-4 shrink-0" />
              <span>{{ about?.hasReadme ? 'Readme' : 'Not Readme' }}</span>
            </button>
            <div class="flex items-center gap-2.5 text-muted-foreground">
              <IconActivity class="size-4 shrink-0" />
              <span>{{ about?.archived ? '已存档' : '活跃的频道' }}</span>
            </div>
            <div class="flex items-center gap-2.5 text-muted-foreground">
              <IconStar class="size-4 shrink-0" />
              <span>{{ formatDetailed(about?.subscriberCount ?? 0) }}个订阅</span>
            </div>
            <div class="flex items-center gap-2.5 text-muted-foreground">
              <IconEye class="size-4 shrink-0" />
              <span>{{ formatDetailed(about?.notifyCount ?? 0) }}个通知订阅</span>
            </div>
            <button type="button" class="mt-1 text-left text-sm text-muted-foreground transition hover:text-foreground" @click="toast.info('举报功能暂未上线')">
              举报频道
            </button>
          </div>

          <div class="my-5 border-t" />

          <div class="flex items-center gap-2">
            <h3 class="text-sm font-semibold">贡献者</h3>
            <Badge variant="secondary" class="h-5 min-w-5 rounded-full px-1.5 text-[10px] tabular-nums">{{ about?.contributors.length ?? 0 }}</Badge>
          </div>
          <div v-if="(about?.contributors.length ?? 0) >= 5" class="mt-4 flex flex-wrap gap-2">
            <router-link v-for="person in about?.contributors" :key="person.id" :to="contributorLink(person.username)"
              class="rounded-full transition hover:opacity-80" :title="person.nickname">
              <Avatar class="size-9">
                <AvatarImage :src="person.avatar || '/logo.png'" :alt="person.nickname" />
                <AvatarFallback>{{ person.nickname.slice(0, 1).toUpperCase() }}</AvatarFallback>
              </Avatar>
            </router-link>
          </div>
          <div v-else-if="about?.contributors.length" class="mt-4 flex flex-col gap-3">
            <router-link v-for="person in about.contributors" :key="person.id" :to="contributorLink(person.username)"
              class="flex items-center gap-2.5 rounded-md px-0.5 py-0.5 transition hover:bg-muted/50">
              <Avatar class="size-8">
                <AvatarImage :src="person.avatar || '/logo.png'" :alt="person.nickname" />
                <AvatarFallback>{{ person.nickname.slice(0, 1).toUpperCase() }}</AvatarFallback>
              </Avatar>
              <span class="min-w-0 truncate text-sm font-medium" data-i18n-skip>{{ person.nickname }}</span>
              <span v-if="person.username && person.username !== person.nickname" class="min-w-0 truncate text-xs text-muted-foreground" data-i18n-skip>@{{ person.username }}</span>
            </router-link>
          </div>
          <p v-else class="mt-4 text-xs text-muted-foreground">暂无贡献者。</p>

          <div class="my-5 border-t" />

          <h3 class="text-sm font-semibold">文档构成</h3>
          <div v-if="fileTypeSegments.length" class="mt-3 flex flex-col gap-3">
            <div class="flex h-2 w-full overflow-hidden rounded-full bg-muted">
              <div v-for="item in fileTypeSegments" :key="item.type" class="h-full" :style="{ width: `${item.percent}%`, backgroundColor: item.color }" :title="`${item.label} ${item.percent.toFixed(1)}%`" />
            </div>
            <div class="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[11px]">
              <span v-for="item in fileTypeSegments" :key="`legend-${item.type}`" class="inline-flex items-center gap-1.5">
                <span class="size-2 rounded-full" :style="{ backgroundColor: item.color }" />
                <span class="font-medium">{{ item.label }}</span>
                <span class="tabular-nums text-muted-foreground">{{ item.percent.toFixed(1) }}%</span>
              </span>
            </div>
          </div>
          <p v-else class="mt-3 text-xs text-muted-foreground">暂无文件。</p>
        </section>
      </aside>
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

      <div v-else-if="activeView === 'embedding'" class="flex flex-col gap-4">
        <section class="rounded-xl border bg-card">
          <header class="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-3">
            <div class="flex flex-col gap-1">
              <h2 class="text-sm font-semibold">处理记录</h2>
              <p class="text-xs text-muted-foreground">按文件记录上传、解析/OCR、分片与向量化状态。文件处理完成后才会出现在文件管理器中。</p>
            </div>
            <div class="flex items-center gap-2">
              <Button variant="outline" size="sm" :disabled="embeddingLoading" @click="openEmbedding">
                <IconRefresh class="mr-1 size-4" />刷新
              </Button>
              <Button size="sm" :disabled="reembedLocked" @click="reembed">
                <Spinner v-if="reembedLocked" class="mr-1 size-3" />{{ reembedLocked ? '正在重新向量化' : '全部重新向量化' }}
              </Button>
              <Button v-if="embeddingStatus?.reembedding" variant="outline" size="sm" :disabled="reembedding" @click="cancelEmbedding">
                取消向量化
              </Button>
            </div>
          </header>
          <div v-if="embeddingLoading" class="flex h-40 items-center justify-center"><Spinner class="size-5" /></div>
          <div v-else class="flex flex-col gap-4 p-4">
            <Alert v-if="embeddingStatus?.reembedding">
              <IconActivity class="size-4" />
              <AlertDescription>频道正在重新向量化，完成后会自动恢复检索并发送通知。</AlertDescription>
            </Alert>
            <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div class="rounded-lg border p-3">
                <p class="text-xs text-muted-foreground">检索状态</p>
                <p class="mt-1 text-sm font-semibold">{{ embeddingStatus?.embeddingReady ? '可用于检索' : '暂不可用于检索' }}</p>
              </div>
              <div class="rounded-lg border p-3">
                <p class="text-xs text-muted-foreground">文件进度</p>
                <p class="mt-1 text-sm font-semibold">{{ embeddingStatus?.completedDocuments ?? 0 }} / {{ embeddingStatus?.totalDocuments ?? 0 }}</p>
              </div>
              <div class="rounded-lg border p-3">
                <p class="text-xs text-muted-foreground">片段进度</p>
                <p class="mt-1 text-sm font-semibold">{{ embeddingStatus?.embeddedChunks ?? 0 }} / {{ embeddingStatus?.totalChunks ?? 0 }}</p>
              </div>
              <div class="rounded-lg border p-3">
                <p class="text-xs text-muted-foreground">待处理片段</p>
                <p class="mt-1 text-sm font-semibold">{{ embeddingStatus?.pendingChunks ?? 0 }}</p>
              </div>
            </div>
            <div v-if="(embeddingStatus?.totalChunks ?? 0) > 0" class="flex flex-col gap-1.5">
              <div class="flex justify-between text-xs text-muted-foreground">
                <span>向量化进度</span>
                <span>{{ Math.round(((embeddingStatus?.embeddedChunks ?? 0) / Math.max(1, embeddingStatus?.totalChunks ?? 1)) * 100) }}%</span>
              </div>
              <div class="h-2 overflow-hidden rounded-full bg-muted">
                <div class="h-full rounded-full bg-primary transition-all" :style="{ width: `${Math.round(((embeddingStatus?.embeddedChunks ?? 0) / Math.max(1, embeddingStatus?.totalChunks ?? 1)) * 100)}%` }" />
              </div>
            </div>
          </div>
        </section>
        <section class="rounded-xl border bg-card">
          <header class="flex items-center justify-between border-b px-4 py-3">
            <h2 class="text-sm font-semibold">文件处理记录</h2>
            <div class="flex items-center gap-2">
              <Button v-if="completedRecords.length" variant="ghost" size="sm" @click="showCompletedRecords = !showCompletedRecords">
                {{ showCompletedRecords ? '隐藏已完成' : `显示已完成（${completedRecords.length}）` }}
              </Button>
              <Badge variant="secondary">{{ embeddingStatus?.records.length ?? 0 }} 个文件</Badge>
            </div>
          </header>
          <div v-if="!pagedRecords.length" class="flex h-32 items-center justify-center text-sm text-muted-foreground">
            {{ pendingRecords.length ? '当前页暂无记录' : (showCompletedRecords ? '暂无处理记录' : '没有待处理记录，已完成项已折叠') }}
          </div>
          <div v-else class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead class="border-b bg-muted/40 text-xs text-muted-foreground">
                <tr>
                  <th class="px-4 py-2.5 text-left font-medium">文件</th>
                  <th class="px-3 py-2.5 text-left font-medium">阶段</th>
                  <th class="px-3 py-2.5 text-left font-medium">片段</th>
                  <th class="px-3 py-2.5 text-left font-medium">尝试</th>
                  <th class="px-3 py-2.5 text-left font-medium">状态</th>
                  <th class="px-3 py-2.5 text-left font-medium">上传时间</th>
                  <th class="px-3 py-2.5 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y">
                <tr v-for="record in pagedRecords" :key="record.documentId">
                  <td class="max-w-[280px] px-4 py-3">
                    <div class="flex items-center gap-2">
                      <IconFileText class="size-4 shrink-0 text-muted-foreground" />
                      <span class="truncate font-medium" :title="record.name">{{ record.name }}</span>
                    </div>
                    <p v-if="record.errorMessage" class="mt-0.5 truncate text-xs text-destructive" :title="record.errorMessage">{{ record.errorMessage }}</p>
                  </td>
                  <td class="px-3 py-3 text-xs">
                    <div>{{ processingStageLabel(record.stage) }}</div>
                    <div class="mt-1 h-1.5 w-24 overflow-hidden rounded-full bg-muted">
                      <div class="h-full rounded-full bg-primary" :style="{ width: `${recordProgress(record)}%` }" />
                    </div>
                  </td>
                  <td class="whitespace-nowrap px-3 py-3 text-xs text-muted-foreground">{{ record.chunkDone }} / {{ record.chunkTotal }}</td>
                  <td class="whitespace-nowrap px-3 py-3 text-xs text-muted-foreground">{{ record.attempts }}</td>
                  <td class="whitespace-nowrap px-3 py-3">
                    <Badge :variant="processingStatusVariant(record.status)">{{ processingStatusLabel(record.status) }}</Badge>
                  </td>
                  <td class="whitespace-nowrap px-3 py-3 text-xs text-muted-foreground">{{ record.uploadedAt || '-' }}</td>
                  <td class="whitespace-nowrap px-3 py-3 text-right">
                    <Button v-if="record.status !== 'SUCCESS'" variant="outline" size="sm" @click="retryRecord(record)">重试</Button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div class="flex items-center justify-between border-t px-4 py-3 text-xs text-muted-foreground">
              <span>第 {{ Math.min(recordsPage, recordsTotalPages) }} / {{ recordsTotalPages }} 页 · 共 {{ filteredRecords.length }} 条</span>
              <div class="flex gap-2">
                <Button variant="outline" size="sm" :disabled="recordsPage <= 1" @click="recordsPage--">上一页</Button>
                <Button variant="outline" size="sm" :disabled="recordsPage >= recordsTotalPages" @click="recordsPage++">下一页</Button>
              </div>
            </div>
          </div>
        </section>
      </div>

      <ChannelIssues v-else-if="activeView === 'issues' && route.name === 'ChannelIssues'" embedded />
      <IssueDetail v-else-if="activeView === 'issues' && route.name === 'IssueDetail'" embedded />
      <IssueCreate v-else-if="activeView === 'issues' && route.name === 'ChannelIssueCreate'" embedded />
      <div v-else-if="activeView === 'settings'">
        <ChannelSettings embedded />
      </div>

      <div v-else-if="activeView === 'recycle'" class="flex flex-col gap-4">
        <section class="rounded-xl border bg-card">
          <header class="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-3">
            <div class="flex flex-col gap-1">
              <h2 class="text-sm font-semibold">回收站</h2>
              <p class="text-xs text-muted-foreground">删除的文件会保留在回收站，超过保留期后自动永久删除。管理员可在此恢复文件。</p>
            </div>
            <Button variant="outline" size="sm" :disabled="recycleLoading" @click="loadRecycle">
              <IconRefresh class="mr-1 size-4" />刷新
            </Button>
          </header>
          <div v-if="recycleLoading" class="flex h-40 items-center justify-center"><Spinner class="size-5" /></div>
          <div v-else-if="!recycleEntries.length" class="flex h-40 flex-col items-center justify-center gap-2 text-muted-foreground">
            <IconTrash class="size-7" />
            <p class="text-sm">回收站是空的</p>
          </div>
          <div v-else class="divide-y">
            <div v-for="entry in recycleEntries" :key="entry.id" class="flex flex-wrap items-center gap-3 px-4 py-3">
              <div class="flex min-w-0 flex-1 items-center gap-3">
                <div class="flex size-9 shrink-0 items-center justify-center rounded-md bg-muted"><IconFileText class="size-4 text-muted-foreground" /></div>
                <div class="min-w-0">
                  <div class="truncate text-sm font-medium">{{ entry.name }}</div>
                  <div class="mt-0.5 text-xs text-muted-foreground">{{ formatBytes(entry.size) }} · 删除于 {{ entry.deleteTime }} · {{ entry.expiresAt ? `保留至 ${entry.expiresAt}` : `${entry.retentionDays} 天` }}</div>
                </div>
              </div>
              <Button variant="outline" size="sm" :disabled="restoringId === entry.id" @click="restoreRecycleEntry(entry)">
                <Spinner v-if="restoringId === entry.id" class="mr-1 size-3.5" />
                <IconRefresh v-else class="mr-1 size-3.5" />
                恢复
              </Button>
            </div>
          </div>
        </section>
      </div>
    </template>

    <CitationPreviewDialog v-model:open="previewOpen" :source="previewSource" :show-index="false" />

    <Dialog :open="uploadOpen" @update:open="onUploadOpenChange">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>上传 {{ uploadItems.length }} 个文件</DialogTitle>
          <DialogDescription>上传到 {{ uploadTargetLabel }} · 可多选文件。</DialogDescription>
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
          <DialogDescription>最多支持 5 层目录。</DialogDescription>
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

    <ChunkInspectorDialog
      v-model:open="chunkOpen"
      :channel-id="channel?.id ?? null"
      :document-id="chunkDocument?.id ?? null"
      :document-name="chunkDocument?.name"
    />
  </div>
</template>
