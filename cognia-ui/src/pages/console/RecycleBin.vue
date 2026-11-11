<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconTrash class="size-5 text-muted-foreground" />
          文档回收站
        </h1>
        <p class="text-sm text-muted-foreground">
          回收站中的文档会保留原文件，超过保留期后自动永久删除；也可以在此立即永久删除。
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Badge variant="outline" class="h-8 px-3">{{ entries.length }} 条记录</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="loadEntries">
          <IconRefresh class="mr-1 size-4" />
          刷新
        </Button>
      </div>
    </div>

    <section class="rounded-xl border bg-card">
      <header class="flex flex-col gap-3 border-b px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div class="flex flex-col gap-1">
          <h2 class="text-sm font-semibold">回收站记录</h2>
          <p class="text-xs text-muted-foreground">永久删除后，文档原文、索引与相关记录都将被删除，且无法恢复。</p>
        </div>
        <div class="w-full sm:w-60">
          <Select v-model="ownerValue" @update:model-value="loadEntries">
            <SelectTrigger>
              <SelectValue placeholder="选择归属" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="USER">个人</SelectItem>
              <SelectItem v-for="team in teams" :key="team.id" :value="`TEAM:${team.id}`">
                {{ team.name }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
      </header>

      <div v-if="loading" class="flex h-48 items-center justify-center">
        <Spinner class="size-5" />
      </div>
      <Alert v-else-if="error" variant="destructive" class="m-4">
        <AlertDescription>{{ error }}</AlertDescription>
      </Alert>
      <div v-else-if="entries.length === 0" class="flex h-48 flex-col items-center justify-center gap-2 text-muted-foreground">
        <IconTrash class="size-8" />
        <p class="text-sm">回收站是空的</p>
      </div>
      <Table v-else>
        <TableHeader>
          <TableRow>
            <TableHead>文件</TableHead>
            <TableHead>大小</TableHead>
            <TableHead>删除时间</TableHead>
            <TableHead>保留</TableHead>
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
            <TableCell class="text-sm text-muted-foreground">{{ formatBytes(entry.size) }}</TableCell>
            <TableCell class="text-sm text-muted-foreground">{{ entry.deleteTime }}</TableCell>
            <TableCell><Badge variant="secondary">{{ entry.retentionDays }} 天</Badge></TableCell>
            <TableCell class="text-sm text-muted-foreground">{{ entry.expiresAt || '-' }}</TableCell>
            <TableCell class="text-right">
              <Button variant="ghost" size="sm" class="text-destructive" @click="askPurge(entry)">
                <IconTrash class="mr-1 size-4" />
                永久删除
              </Button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </section>

    <Dialog v-model:open="purgeDialogOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>永久删除文档</DialogTitle>
          <DialogDescription>
            确定要永久删除「{{ purgeTarget?.name }}」吗？文档原文、索引与相关记录都将被删除，且无法恢复。
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
import { IconRefresh, IconTrash } from '@tabler/icons-vue'
import { recycleApi, profileApi } from '@/api'
import type { OwnerProfile, RecycleBinEntry } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'

const userStore = useUserStore()
const loading = ref(false)
const error = ref('')
const entries = ref<RecycleBinEntry[]>([])
const profile = ref<OwnerProfile | null>(null)
const ownerValue = ref('USER')
const purgeDialogOpen = ref(false)
const purgeTarget = ref<RecycleBinEntry | null>(null)
const purging = ref(false)

const teams = computed(() => profile.value?.teams ?? [])
const owner = computed(() => {
  if (ownerValue.value === 'USER') {
    return { type: 'USER' as const, id: userStore.profile?.id ?? '' }
  }
  const id = ownerValue.value.replace('TEAM:', '')
  return { type: 'TEAM' as const, id }
})

async function loadProfile() {
  try {
    profile.value = await profileApi.me()
  } catch {
    profile.value = null
  }
}

async function loadEntries() {
  if (!owner.value.id) return
  loading.value = true
  error.value = ''
  try {
    entries.value = await recycleApi.list(owner.value.type, owner.value.id)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载回收站失败'
  } finally {
    loading.value = false
  }
}

function askPurge(entry: RecycleBinEntry) {
  purgeTarget.value = entry
  purgeDialogOpen.value = true
}

async function confirmPurge() {
  if (!purgeTarget.value) return
  purging.value = true
  try {
    await recycleApi.purge(purgeTarget.value.id)
    toast.success('文档已永久删除')
    purgeDialogOpen.value = false
    purgeTarget.value = null
    await loadEntries()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '删除失败')
  } finally {
    purging.value = false
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
  await loadProfile()
  await loadEntries()
})
</script>
