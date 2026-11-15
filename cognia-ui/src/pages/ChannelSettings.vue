<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { channelApi, profileApi } from '@/api'
import { useUserStore } from '@/stores/user'
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
  IconAlertTriangle, IconArrowLeft, IconLock, IconRefresh,
  IconSettings, IconTrash,
} from '@tabler/icons-vue'

const props = defineProps<{ embedded?: boolean }>()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(true)
const error = ref('')
const detail = ref<ChannelDetail | null>(null)
const saving = ref(false)
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

const form = reactive({ name: '', description: '', webSearchEnabled: true })

function fill(value: ChannelDetail) {
  detail.value = value
  form.name = value.channel.name
  form.description = value.channel.description ?? ''
  form.webSearchEnabled = value.channel.webSearchEnabled ?? true
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

type DangerAction = 'make-public' | 'make-private' | 'archive' | 'unarchive'
const dangerOpen = ref(false)
const dangerAction = ref<DangerAction>('make-public')
const verificationCode = ref('')
const dangerBusy = ref(false)
const needsVerification = computed(() => dangerAction.value !== 'make-private')
const dangerTitle = computed(() => ({
  'make-public': '设为公开频道',
  'make-private': '转为私密频道',
  archive: '存档频道',
  unarchive: '取消存档',
}[dangerAction.value]))
const dangerDescription = computed(() => ({
  'make-public': '公开后所有访问者都能查看和检索频道内容。为确认身份，请输入登录密码或两步验证码。',
  'make-private': '转为私密后，已订阅用户会保留订阅状态，但无法再访问或检索该频道，直到频道再次公开。',
  archive: '存档后，频道将暂停文件上传、删除和整理，订阅者仍可查看和检索。请输入登录密码或两步验证码。',
  unarchive: '取消存档后，频道将恢复文件管理能力。请输入登录密码或两步验证码。',
}[dangerAction.value]))

function openDanger(action: DangerAction) {
  dangerAction.value = action
  verificationCode.value = ''
  dangerOpen.value = true
}

async function confirmDanger() {
  if (!channel.value) return
  dangerBusy.value = true
  try {
    if (dangerAction.value === 'make-public' || dangerAction.value === 'make-private') {
      const updated = await channelApi.setVisibility(
        channel.value.id,
        dangerAction.value === 'make-public',
        needsVerification.value ? verificationCode.value.trim() : undefined,
      )
      detail.value = { ...detail.value!, channel: { ...detail.value!.channel, ...updated } }
      toast.success(updated.isPublic ? '频道已设为公开' : '频道已转为私密')
    } else {
      const updated = await channelApi.setArchived(
        channel.value.id,
        dangerAction.value === 'archive',
        verificationCode.value.trim(),
      )
      detail.value = { ...detail.value!, channel: { ...detail.value!.channel, ...updated } }
      toast.success(updated.archived ? '频道已存档' : '频道已取消存档')
    }
    dangerOpen.value = false
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '操作失败')
  } finally {
    dangerBusy.value = false
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
          <p class="text-sm text-muted-foreground">管理频道基本信息、展示与删除操作。</p>
        </div>
        <div class="flex items-center gap-2">
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
              <p class="text-xs text-muted-foreground">用于 /{{ ownerParam }}/{{ channel.slug }}，创建后不可修改</p>
            </div>
            <div class="grid gap-2 md:col-span-2">
              <Label for="channel-description">频道简介</Label>
              <Textarea id="channel-description" v-model="form.description" rows="3" maxlength="512" />
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
            <p class="text-xs text-muted-foreground">这些操作会影响所有访问者，请谨慎确认。删除后频道不可恢复；频道中的文档会移入文档回收站，可在保留期内恢复或永久删除。</p>
          </header>
          <div class="flex flex-col divide-y">
            <div class="flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <p class="text-sm font-medium">频道可见性</p>
                <p class="text-xs text-muted-foreground">
                  当前{{ channel.isPublic ? '公开' : '私密' }}。转为私密后，订阅者保留订阅但无法访问或检索；再次公开需要验证身份。
                </p>
              </div>
              <Button variant="outline" size="sm" @click="openDanger(channel.isPublic ? 'make-private' : 'make-public')">
                <IconLock class="mr-1 size-4" />{{ channel.isPublic ? '转为私密' : '设为公开' }}
              </Button>
            </div>
            <div class="flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <p class="text-sm font-medium">频道存档</p>
                <p class="text-xs text-muted-foreground">
                  当前{{ channel.archived ? '已存档' : '活跃' }}。存档期间暂停文件上传与管理，订阅者仍可查看和检索。
                </p>
              </div>
              <Button variant="outline" size="sm" @click="openDanger(channel.archived ? 'unarchive' : 'archive')">
                {{ channel.archived ? '取消存档' : '存档频道' }}
              </Button>
            </div>
            <div class="flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <p class="text-sm font-medium">删除频道</p>
                <p class="text-xs text-muted-foreground">请确认频道中的文档已备份。</p>
              </div>
              <Button variant="destructive" size="sm" @click="deleteOpen = true">
                <IconTrash class="mr-1 size-4" />删除频道
              </Button>
            </div>
          </div>
        </section>
      </fieldset>

      <Dialog v-model:open="dangerOpen">
        <DialogContent class="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{{ dangerTitle }}</DialogTitle>
            <DialogDescription>{{ dangerDescription }}</DialogDescription>
          </DialogHeader>
          <div v-if="needsVerification" class="grid gap-2 py-2">
            <Label for="channel-verification">
              {{ userStore.profile?.twoFactorEnabled ? '两步验证码' : '登录密码' }}
            </Label>
            <Input id="channel-verification" v-model="verificationCode"
              :type="userStore.profile?.twoFactorEnabled ? 'text' : 'password'"
              :placeholder="userStore.profile?.twoFactorEnabled ? '6 位验证码' : '请输入当前账号密码'"
              autocomplete="off" @keyup.enter="confirmDanger" />
          </div>
          <DialogFooter>
            <Button variant="outline" :disabled="dangerBusy" @click="dangerOpen = false">取消</Button>
            <Button :disabled="dangerBusy || (needsVerification && !verificationCode.trim())" @click="confirmDanger">
              <Spinner v-if="dangerBusy" class="mr-2" />确认
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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
