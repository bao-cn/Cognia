<template>
  <div class="mx-auto flex w-full max-w-5xl flex-col gap-5 p-4 pb-12 md:p-8">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <Button variant="ghost" size="sm" class="mb-2 rounded-full px-0 text-muted-foreground" @click="router.push(`/teams/${teamId}`)">
          <IconArrowLeft class="mr-1.5 size-4" />
          返回团队
        </Button>
        <h1 class="text-2xl font-semibold tracking-tight">频道管理</h1>
        <p class="mt-1 text-sm text-muted-foreground">创建、编辑、删除和置顶团队频道。文档上传与频道参数配置将在后续版本开放。</p>
      </div>
      <Button class="rounded-full" @click="openCreate">
        <IconPlus class="mr-1.5 size-4" />
        创建频道
      </Button>
    </div>

    <div v-if="loading" class="flex h-52 items-center justify-center rounded-2xl border border-dashed">
      <Spinner class="size-6" />
    </div>
    <div v-else-if="error" class="rounded-2xl border border-dashed p-10 text-center text-sm text-muted-foreground">{{ error }}</div>
    <div v-else-if="team && !team.canCreateChannel" class="rounded-2xl border border-dashed p-10 text-center text-sm text-muted-foreground">
      你没有频道管理权限。
    </div>
    <template v-else-if="team">
      <div v-if="!team.channels.length" class="flex flex-col items-center gap-2 rounded-2xl border border-dashed px-4 py-14 text-center">
        <IconHash class="size-8 text-muted-foreground" />
        <p class="text-sm font-medium">还没有频道</p>
        <p class="text-xs text-muted-foreground">创建一个频道，开始聚合团队知识。</p>
      </div>
      <div v-else class="grid gap-3 md:grid-cols-2">
        <article v-for="channel in team.channels" :key="channel.id" class="flex flex-col gap-3 rounded-2xl border bg-card p-4">
          <div class="flex items-start gap-3">
            <div class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-muted">
              <IconHash class="size-4 text-muted-foreground" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <h2 class="truncate text-sm font-semibold">{{ channel.name }}</h2>
                <Badge variant="outline" class="rounded-full text-[10px]">{{ channel.isPublic ? '公开' : '私有' }}</Badge>
                <Badge v-if="channel.pinned" variant="secondary" class="rounded-full text-[10px]">置顶 {{ channel.pinnedOrder }}</Badge>
              </div>
              <p class="mt-1 line-clamp-2 text-xs leading-5 text-muted-foreground">{{ channel.description || '暂无介绍' }}</p>
              <p class="mt-2 text-[10px] text-muted-foreground">由 {{ channel.creatorName }} 创建</p>
            </div>
          </div>
          <div class="flex flex-wrap items-center gap-2 border-t pt-3">
            <Button v-if="team.canPinChannel" variant="ghost" size="sm" class="h-8 rounded-full text-xs" @click="togglePin(channel)">
              <IconPin class="mr-1 size-3.5" />
              {{ channel.pinned ? '取消置顶' : '置顶' }}
            </Button>
            <Button variant="ghost" size="sm" class="h-8 rounded-full text-xs" @click="openEdit(channel)">
              <IconPencil class="mr-1 size-3.5" />
              编辑
            </Button>
            <Button v-if="canDeleteChannel(channel)" variant="ghost" size="sm" class="h-8 rounded-full text-xs text-destructive hover:text-destructive" @click="deleteTarget = channel">
              <IconTrash class="mr-1 size-3.5" />
              删除
            </Button>
          </div>
        </article>
      </div>
    </template>

    <Dialog v-model:open="dialogOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{{ editing ? '编辑频道' : '创建频道' }}</DialogTitle>
          <DialogDescription>频道用于聚集团队文档与问答。</DialogDescription>
        </DialogHeader>
        <div class="flex flex-col gap-4">
          <div class="grid gap-2">
            <Label>频道名称</Label>
            <Input v-model="form.name" maxlength="64" placeholder="例如：产品文档" />
          </div>
          <div class="grid gap-2">
            <Label>频道介绍</Label>
            <Textarea v-model="form.description" :rows="3" maxlength="512" placeholder="这个频道用来沉淀什么内容" />
          </div>
          <label class="flex items-center gap-2 text-sm">
            <input v-model="form.isPublic" type="checkbox" class="size-4 rounded border-border" />
            公开频道
          </label>
          <p v-if="formError" class="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ formError }}</p>
        </div>
        <DialogFooter>
          <Button variant="outline" class="rounded-full" :disabled="saving" @click="dialogOpen = false">取消</Button>
          <Button class="rounded-full" :disabled="saving" @click="saveChannel">
            <Spinner v-if="saving" class="mr-2" />
            {{ editing ? '保存频道' : '创建频道' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="Boolean(deleteTarget)" @update:open="value => { if (!value) deleteTarget = null }">
      <DialogContent class="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>删除频道</DialogTitle>
          <DialogDescription>确定删除「{{ deleteTarget?.name }}」吗？此操作不可撤销。</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" class="rounded-full" @click="deleteTarget = null">取消</Button>
          <Button variant="destructive" class="rounded-full" :disabled="deleting" @click="removeChannel">
            <Spinner v-if="deleting" class="mr-2" />
            删除频道
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { IconArrowLeft, IconHash, IconPencil, IconPin, IconPlus, IconTrash } from '@tabler/icons-vue'
import { teamApi } from '@/api'
import type { TeamChannel, TeamDetail } from '@/types/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const teamId = String(route.params.id || '')

const team = ref<TeamDetail | null>(null)
const loading = ref(true)
const error = ref('')
const dialogOpen = ref(false)
const editing = ref<TeamChannel | null>(null)
const form = reactive({ name: '', description: '', isPublic: true })
const formError = ref('')
const saving = ref(false)
const deleteTarget = ref<TeamChannel | null>(null)
const deleting = ref(false)

function canDeleteChannel(channel: TeamChannel): boolean {
  if (!team.value) return false
  if (team.value.canDeleteAnyChannel) return true
  return team.value.viewerRole === 'EDITOR' && channel.createdBy === userStore.profile?.id
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    team.value = await teamApi.detail(teamId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '团队加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  form.name = ''
  form.description = ''
  form.isPublic = true
  formError.value = ''
  dialogOpen.value = true
}

function openEdit(channel: TeamChannel): void {
  editing.value = channel
  form.name = channel.name
  form.description = channel.description || ''
  form.isPublic = channel.isPublic
  formError.value = ''
  dialogOpen.value = true
}

async function saveChannel(): Promise<void> {
  if (!form.name.trim()) {
    formError.value = '请输入频道名称'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    if (editing.value) {
      await teamApi.updateChannel(teamId, editing.value.id, {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        isPublic: form.isPublic,
      })
      toast.success('频道已更新')
    } else {
      await teamApi.createChannel(teamId, {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        isPublic: form.isPublic,
      })
      toast.success('频道已创建')
    }
    dialogOpen.value = false
    team.value = await teamApi.detail(teamId)
  } catch (err) {
    formError.value = err instanceof Error ? err.message : '频道保存失败'
  } finally {
    saving.value = false
  }
}

async function togglePin(channel: TeamChannel): Promise<void> {
  try {
    await teamApi.pinChannel(teamId, channel.id, !channel.pinned)
    team.value = await teamApi.detail(teamId)
    toast.success(channel.pinned ? '已取消置顶' : '频道已置顶')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '置顶失败')
  }
}

async function removeChannel(): Promise<void> {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await teamApi.deleteChannel(teamId, deleteTarget.value.id)
    toast.success('频道已删除')
    deleteTarget.value = null
    team.value = await teamApi.detail(teamId)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '删除频道失败')
  } finally {
    deleting.value = false
  }
}

onMounted(load)
</script>
