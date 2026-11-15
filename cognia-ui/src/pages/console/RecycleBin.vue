<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconTrash class="size-5 text-muted-foreground" />
          文档回收站
        </h1>
        <p class="text-sm text-muted-foreground">
          查看全站已删除文件，可按频道筛选、恢复、下载或永久删除。超过保留期的文件会自动永久删除。
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Badge variant="outline" class="h-8 px-3">{{ total }} 条记录</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="load">
          <IconRefresh class="mr-1 size-4" />
          刷新
        </Button>
      </div>
    </div>

    <Alert v-if="!canRead" variant="destructive">
      <AlertDescription>当前账户没有查看文档回收站的权限。</AlertDescription>
    </Alert>

    <section v-else class="rounded-xl border bg-card">
      <header class="flex flex-col gap-3 border-b px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
        <div class="flex flex-col gap-1">
          <h2 class="text-sm font-semibold">回收站记录</h2>
          <p class="text-xs text-muted-foreground">永久删除后，文件原文、索引与相关记录都将被删除，且无法恢复。</p>
        </div>
        <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
          <div class="flex w-full flex-wrap items-center gap-2 sm:w-auto">
            <Input v-model="channelKeyword" placeholder="搜索频道名称或标识" class="sm:w-48" @keydown.enter="applyFilters" />
            <Input v-model="keyword" placeholder="搜索文件名" class="sm:w-44" @keydown.enter="applyFilters" />
            <Button variant="outline" size="sm" @click="applyFilters">筛选</Button>
            <Button variant="ghost" size="sm" @click="resetFilters">重置</Button>
          </div>
        </div>
      </header>

      <div v-if="loading" class="flex h-48 items-center justify-center"><Spinner class="size-5" /></div>
      <Alert v-else-if="error" variant="destructive" class="m-4"><AlertDescription>{{ error }}</AlertDescription></Alert>
      <div v-else-if="entries.length === 0" class="flex h-48 flex-col items-center justify-center gap-2 text-muted-foreground">
        <IconTrash class="size-8" />
        <p class="text-sm">回收站是空的</p>
      </div>
      <Table v-else>
        <TableHeader>
          <TableRow>
            <TableHead>文件</TableHead>
            <TableHead>频道</TableHead>
            <TableHead>归属</TableHead>
            <TableHead>大小</TableHead>
            <TableHead>删除时间</TableHead>
            <TableHead>到期时间</TableHead>
            <TableHead class="text-right">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="entry in entries" :key="entry.id">
            <TableCell>
              <div class="flex flex-col gap-0.5">
                <span class="max-w-64 truncate font-medium">{{ entry.name }}</span>
                <span class="max-w-64 truncate text-xs text-muted-foreground">{{ entry.contentType || '未知类型' }}</span>
              </div>
            </TableCell>
            <TableCell>
              <div class="flex flex-col gap-0.5">
                <span class="max-w-40 truncate text-sm">{{ entry.channelName || '-' }}</span>
                <span v-if="entry.channelSlug" class="text-xs text-muted-foreground">@{{ entry.channelSlug }}</span>
              </div>
            </TableCell>
            <TableCell class="text-sm text-muted-foreground">{{ entry.ownerName || entry.ownerType }}</TableCell>
            <TableCell class="text-sm text-muted-foreground">{{ formatBytes(entry.size) }}</TableCell>
            <TableCell class="text-sm text-muted-foreground">{{ entry.deleteTime }}</TableCell>
            <TableCell class="text-sm text-muted-foreground">{{ entry.expiresAt || '-' }}</TableCell>
            <TableCell class="text-right">
              <div class="flex justify-end gap-1">
                <Button variant="ghost" size="icon" class="size-8" title="下载" :disabled="downloadingId === entry.id" @click="download(entry)">
                  <Spinner v-if="downloadingId === entry.id" class="size-4" />
                  <IconDownload v-else class="size-4" />
                </Button>
                <Button v-if="canUpdate" variant="ghost" size="sm" :disabled="restoringId === entry.id" @click="restore(entry)">
                  <Spinner v-if="restoringId === entry.id" class="mr-1 size-3.5" />
                  <IconRestore v-else class="mr-1 size-4" />
                  恢复
                </Button>
                <Button v-if="canUpdate" variant="ghost" size="sm" class="text-destructive" @click="askPurge(entry)">
                  <IconTrash class="mr-1 size-4" />
                  永久删除
                </Button>
              </div>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>

      <div v-if="pages > 1" class="flex items-center justify-between border-t px-4 py-3 text-sm text-muted-foreground">
        <span>第 {{ page }} / {{ pages }} 页 · 共 {{ total }} 条</span>
        <div class="flex gap-1">
          <Button variant="outline" size="icon" class="size-8" :disabled="page <= 1" @click="goToPage(page - 1)"><IconChevronLeft class="size-4" /></Button>
          <Button variant="outline" size="icon" class="size-8" :disabled="page >= pages" @click="goToPage(page + 1)"><IconChevronRight class="size-4" /></Button>
        </div>
      </div>
    </section>

    <Dialog v-model:open="purgeDialogOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>永久删除文件</DialogTitle>
          <DialogDescription>
            确定要永久删除「{{ purgeTarget?.name }}」吗？文件原文、索引与相关记录都将被删除，且无法恢复。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" :disabled="purging" @click="purgeDialogOpen = false">取消</Button>
          <Button variant="destructive" :disabled="purging" @click="confirmPurge">
            <Spinner v-if="purging" class="mr-2" />
            永久删除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { IconChevronLeft, IconChevronRight, IconDownload, IconRefresh, IconRestore, IconTrash } from '@tabler/icons-vue'
import { recycleApi } from '@/api'
import type { AdminRecycleBinEntry } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'

const userStore = useUserStore()
const canRead = computed(() => userStore.hasPermission('recycle:read') || userStore.hasPermission('settings:read'))
const canUpdate = computed(() => userStore.hasPermission('recycle:update') || userStore.hasPermission('settings:update'))

const loading = ref(false)
const error = ref('')
const entries = ref<AdminRecycleBinEntry[]>([])
const keyword = ref('')
const channelKeyword = ref('')
const page = ref(1)
const size = 20
const total = ref(0)
const pages = ref(0)
const downloadingId = ref<string | null>(null)
const restoringId = ref<string | null>(null)
const purgeDialogOpen = ref(false)
const purgeTarget = ref<AdminRecycleBinEntry | null>(null)
const purging = ref(false)

async function load() {
  if (!canRead.value) return
  loading.value = true
  error.value = ''
  try {
    const result = await recycleApi.adminList({
      page: page.value,
      size,
      keyword: keyword.value.trim() || undefined,
      channelKeyword: channelKeyword.value.trim() || undefined,
    })
    entries.value = result.records
    total.value = result.total
    pages.value = result.pages
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载回收站失败'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  void load()
}

function resetFilters() {
  channelKeyword.value = ''
  keyword.value = ''
  page.value = 1
  void load()
}

function goToPage(next: number) {
  page.value = next
  void load()
}

async function restore(entry: AdminRecycleBinEntry) {
  restoringId.value = entry.id
  try {
    await recycleApi.adminRestore(entry.id)
    toast.success('文件已恢复')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '恢复失败')
  } finally {
    restoringId.value = null
  }
}

function askPurge(entry: AdminRecycleBinEntry) {
  purgeTarget.value = entry
  purgeDialogOpen.value = true
}

async function confirmPurge() {
  if (!purgeTarget.value) return
  purging.value = true
  try {
    await recycleApi.adminPurge(purgeTarget.value.id)
    toast.success('文件已永久删除')
    purgeDialogOpen.value = false
    purgeTarget.value = null
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '删除失败')
  } finally {
    purging.value = false
  }
}

async function download(entry: AdminRecycleBinEntry) {
  downloadingId.value = entry.id
  try {
    const blob = await recycleApi.adminDownload(entry.id)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = entry.name || 'file'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '下载失败')
  } finally {
    downloadingId.value = null
  }
}

function formatBytes(size: number) {
  if (!size) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = size
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index++
  }
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

onMounted(async () => {
  if (!canRead.value) return
  await load()
})
</script>
