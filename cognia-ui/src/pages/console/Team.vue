<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { teamApi } from '@/api'
import type { AdminTeam } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import {
  IconChevronLeft, IconChevronRight, IconRefresh, IconSearch, IconTrash, IconUsers,
} from '@tabler/icons-vue'

const userStore = useUserStore()
const loading = ref(false)
const rows = ref<AdminTeam[]>([])
const total = ref(0)
const pages = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const deleteOpen = ref(false)
const deleteTarget = ref<AdminTeam | null>(null)
const deleting = ref(false)

const canUpdate = computed(() => userStore.hasPermission('team:update') || userStore.hasPermission('user:update'))
const rangeStart = computed(() => (total.value === 0 ? 0 : (page.value - 1) * size.value + 1))
const rangeEnd = computed(() => Math.min(page.value * size.value, total.value))

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function fetchTeams() {
  loading.value = true
  try {
    const result = await teamApi.adminList({ page: page.value, size: size.value, keyword: keyword.value.trim() || undefined })
    rows.value = result.records
    total.value = result.total
    pages.value = result.pages
  } catch (error) {
    toast.error(messageOf(error))
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void fetchTeams()
}

function goToPage(next: number) {
  if (next < 1 || next > pages.value || next === page.value) return
  page.value = next
  void fetchTeams()
}

function askDelete(team: AdminTeam) {
  deleteTarget.value = team
  deleteOpen.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await teamApi.adminDelete(deleteTarget.value.id)
    toast.success('团队已解散')
    deleteOpen.value = false
    deleteTarget.value = null
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await fetchTeams()
  } catch (error) {
    toast.error(messageOf(error))
  } finally {
    deleting.value = false
  }
}

onMounted(fetchTeams)
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconUsers class="size-5 text-muted-foreground" />团队管理
        </h1>
        <p class="text-sm text-muted-foreground">查看系统内全部团队，管理员可以解散违规或废弃团队。</p>
      </div>
      <div class="flex items-center gap-2">
        <Badge variant="outline" class="h-8 px-3">共 {{ total }} 个团队</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="fetchTeams">
          <IconRefresh class="mr-1 size-4" />重新加载
        </Button>
      </div>
    </div>

    <Alert v-if="!canUpdate">
      <AlertDescription>当前账户只有查看团队的权限，解散团队需要 team:update 权限。</AlertDescription>
    </Alert>

    <section class="rounded-xl border bg-card">
      <header class="flex flex-wrap items-center gap-2 border-b px-4 py-3">
        <div class="relative min-w-64 flex-1">
          <IconSearch class="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input v-model="keyword" class="pl-9" placeholder="搜索团队名称或标识" @keyup.enter="search" />
        </div>
        <Button size="sm" @click="search">查询</Button>
        <Button variant="outline" size="sm" @click="keyword = ''; search()">重置</Button>
      </header>

      <div v-if="loading" class="flex h-56 items-center justify-center">
        <Spinner class="size-6" />
      </div>
      <Table v-else>
        <TableHeader>
          <TableRow>
            <TableHead>团队</TableHead>
            <TableHead>标识</TableHead>
            <TableHead>创建者</TableHead>
            <TableHead>成员</TableHead>
            <TableHead>频道</TableHead>
            <TableHead>创建时间</TableHead>
            <TableHead class="text-right">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="team in rows" :key="team.id">
            <TableCell>
              <div class="flex items-center gap-2">
                <img :src="team.logoUrl || '/logo.png'" :alt="team.name" class="size-8 rounded-lg border object-cover" />
                <router-link :to="`/${team.slug}`" class="font-medium hover:underline">{{ team.name }}</router-link>
              </div>
            </TableCell>
            <TableCell><code class="rounded bg-muted px-1.5 py-0.5 text-xs">@{{ team.slug }}</code></TableCell>
            <TableCell>
              <div class="flex flex-col text-xs">
                <span>{{ team.ownerName || '未知' }}</span>
                <span class="text-muted-foreground">@{{ team.ownerUsername || '-' }}</span>
              </div>
            </TableCell>
            <TableCell>{{ team.memberCount }}</TableCell>
            <TableCell>{{ team.channelCount }}</TableCell>
            <TableCell class="text-xs text-muted-foreground">{{ team.createTime || '-' }}</TableCell>
            <TableCell class="text-right">
              <Button v-if="canUpdate" variant="ghost" size="sm" class="text-destructive" @click="askDelete(team)">
                <IconTrash class="mr-1 size-4" />解散
              </Button>
            </TableCell>
          </TableRow>
          <TableRow v-if="!rows.length">
            <TableCell :colspan="7" class="h-32 text-center text-sm text-muted-foreground">没有找到团队</TableCell>
          </TableRow>
        </TableBody>
      </Table>

      <footer class="flex flex-wrap items-center justify-between gap-2 border-t px-4 py-3 text-xs text-muted-foreground">
        <span>{{ rangeStart }}-{{ rangeEnd }} / {{ total }}</span>
        <div class="flex items-center gap-1">
          <Button variant="outline" size="icon" class="size-7" :disabled="page <= 1" @click="goToPage(page - 1)">
            <IconChevronLeft class="size-4" />
          </Button>
          <span class="px-2">第 {{ page }} / {{ pages || 1 }} 页</span>
          <Button variant="outline" size="icon" class="size-7" :disabled="page >= pages" @click="goToPage(page + 1)">
            <IconChevronRight class="size-4" />
          </Button>
        </div>
      </footer>
    </section>

    <Dialog v-model:open="deleteOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>解散团队</DialogTitle>
          <DialogDescription>
            确定解散「{{ deleteTarget?.name }}」？团队频道和成员关系会被移除，文件将由计划任务清理。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" :disabled="deleting" @click="deleteOpen = false">取消</Button>
          <Button variant="destructive" :disabled="deleting" @click="confirmDelete">
            <Spinner v-if="deleting" class="mr-2" />解散团队
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
