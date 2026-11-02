<template>
  <div class="mx-auto flex w-full max-w-5xl flex-col gap-6 p-4 pb-12 md:p-8">
    <section class="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
      <div class="flex flex-col gap-2">
        <div class="flex items-center gap-2">
          <Badge variant="outline" class="rounded-full">团队空间</Badge>
          <span class="text-xs text-muted-foreground">邀请制 · 每个团队都是独立的知识库</span>
        </div>
        <h1 class="text-2xl font-semibold tracking-tight md:text-3xl">我的团队</h1>
        <p class="max-w-2xl text-sm leading-6 text-muted-foreground">
          团队是知识库的归属单位。即使只有你一个人，也从一个自己的团队开始。
        </p>
      </div>
      <Button class="rounded-full" @click="handleCreateClick">
        <IconPlus class="mr-1.5 size-4" />
        创建团队
      </Button>
    </section>

    <section v-if="invitations.length" class="flex flex-col gap-3 rounded-2xl border border-primary/20 bg-primary/[0.03] p-4 md:p-5">
      <div class="flex items-center gap-2">
        <IconMail class="size-4 text-primary" />
        <h2 class="text-sm font-semibold">待处理邀请</h2>
        <Badge variant="secondary" class="rounded-full text-[10px]">{{ invitations.length }}</Badge>
      </div>
      <div class="grid gap-3">
        <div v-for="invitation in invitations" :key="invitation.id" class="flex flex-col gap-3 rounded-xl border bg-background p-3 sm:flex-row sm:items-center">
          <img :src="invitation.teamLogoUrl || '/logo.png'" :alt="invitation.teamName" class="size-11 rounded-xl object-cover ring-1 ring-border" />
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium">{{ invitation.teamName }}</p>
            <p class="mt-0.5 truncate text-xs text-muted-foreground">{{ invitation.inviterName }} 邀请你加入 · {{ invitation.teamDescription || '团队邀请' }}</p>
          </div>
          <div class="flex shrink-0 items-center gap-2">
            <Button variant="outline" size="sm" class="rounded-full" :disabled="invitationAction === invitation.id" @click="declineInvitation(invitation)">
              拒绝
            </Button>
            <Button size="sm" class="rounded-full" :disabled="invitationAction === invitation.id" @click="acceptInvitation(invitation)">
              <Spinner v-if="invitationAction === invitation.id" class="mr-1.5 size-3.5" />
              接受邀请
            </Button>
          </div>
        </div>
      </div>
    </section>

    <div v-if="loading" class="flex h-52 items-center justify-center rounded-2xl border border-dashed">
      <Spinner class="size-6" />
    </div>

    <div v-else-if="!mine.length" class="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed px-6 py-16 text-center">
      <div class="flex size-12 items-center justify-center rounded-2xl bg-muted">
        <IconUsers class="size-6 text-muted-foreground" />
      </div>
      <div>
        <p class="text-sm font-medium">还没有团队</p>
        <p class="mt-1 text-xs text-muted-foreground">创建你的第一个团队，开始沉淀团队知识。团队只有你一个人也没关系。</p>
      </div>
      <Button size="sm" class="rounded-full" @click="handleCreateClick">创建团队</Button>
    </div>

    <div v-else class="grid gap-4 md:grid-cols-2">
      <router-link
        v-for="team in mine"
        :key="team.id"
        :to="`/${team.slug}`"
        class="group flex gap-4 rounded-2xl border bg-card p-4 transition hover:-translate-y-0.5 hover:border-foreground/20 hover:shadow-md"
      >
        <img :src="team.logoUrl || '/logo.png'" :alt="team.name" class="size-12 shrink-0 rounded-xl object-cover ring-1 ring-border" />
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <h2 class="truncate text-sm font-semibold">{{ team.name }}</h2>
            <Badge v-if="team.role === 'OWNER'" variant="secondary" class="rounded-full text-[10px]">创建者</Badge>
            <Badge v-else variant="outline" class="rounded-full text-[10px]">成员</Badge>
          </div>
          <p class="mt-1 truncate text-xs text-muted-foreground">团队 ID：{{ team.id }} · {{ team.memberCount }} 位成员</p>
          <p class="mt-2 line-clamp-2 text-xs leading-5 text-muted-foreground">{{ team.description || '这个团队还没有介绍。' }}</p>
        </div>
        <IconChevronRight class="mt-1 size-4 shrink-0 text-muted-foreground transition group-hover:translate-x-0.5" />
      </router-link>
    </div>

    <Dialog v-model:open="createOpen">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>创建团队</DialogTitle>
          <DialogDescription>创建后你将成为团队创建者，可以在团队设置中邀请成员。</DialogDescription>
        </DialogHeader>

        <div class="flex flex-col gap-4">
          <div class="flex items-center gap-3 rounded-xl border bg-muted/30 p-3">
            <img :src="previewLogo" alt="团队 Logo 预览" class="size-12 rounded-xl object-cover ring-1 ring-border" />
            <div class="min-w-0">
              <p class="text-sm font-medium">{{ form.name || '团队名称' }}</p>
              <p class="truncate text-xs text-muted-foreground">{{ form.slug || '团队标识' }}</p>
            </div>
          </div>
          <div class="grid gap-2">
            <Label for="team-name">团队名称</Label>
            <Input id="team-name" v-model="form.name" maxlength="64" placeholder="例如：产品设计团队" />
          </div>
          <div class="grid gap-2">
            <Label for="team-slug">团队标识</Label>
            <Input id="team-slug" v-model="form.slug" maxlength="64" placeholder="例如：product-design（唯一，创建后不可修改）" />
            <p class="text-xs text-muted-foreground">用于团队展示，团队详情页通过系统生成的团队 ID 访问。</p>
          </div>
          <div class="grid gap-2">
            <Label for="team-description">团队介绍</Label>
            <Textarea id="team-description" v-model="form.description" :rows="3" maxlength="512" placeholder="介绍一下团队在做什么、沉淀什么知识" />
          </div>
          <div class="grid gap-2">
            <Label for="team-logo">团队 Logo</Label>
            <Input id="team-logo" v-model="form.logoUrl" maxlength="512" placeholder="/logo.png 或 https://..." />
            <p class="text-xs text-muted-foreground">支持站内路径或 HTTPS 图片地址，留空使用默认 Logo。</p>
          </div>
          <p v-if="createError" class="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ createError }}</p>
        </div>

        <DialogFooter>
          <Button variant="outline" class="rounded-full" :disabled="creating" @click="createOpen = false">取消</Button>
          <Button class="rounded-full" :disabled="creating" @click="createTeam">
            <Spinner v-if="creating" class="mr-2" />
            创建团队
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { IconChevronRight, IconMail, IconPlus, IconUsers } from '@tabler/icons-vue'
import { teamApi } from '@/api'
import type { TeamInvitation, TeamLimits, TeamSummary } from '@/types/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const mine = ref<TeamSummary[]>([])
const limits = ref<TeamLimits | null>(null)
const invitations = ref<TeamInvitation[]>([])
const invitationAction = ref<string | null>(null)
const createOpen = ref(false)
const creating = ref(false)
const createError = ref('')
const form = reactive({ name: '', slug: '', description: '', logoUrl: '' })

const previewLogo = computed(() => form.logoUrl.trim() || '/logo.png')

async function load(): Promise<void> {
  loading.value = true
  try {
    const [teams, teamLimits, pending] = await Promise.all([
      teamApi.mine(),
      teamApi.limits(),
      teamApi.incomingInvitations(),
    ])
    mine.value = teams
    limits.value = teamLimits
    invitations.value = pending
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '团队加载失败')
  } finally {
    loading.value = false
  }
}

function handleCreateClick(): void {
  if (limits.value && limits.value.createdCount >= limits.value.creationLimit) {
    toast.error(`最多只能创建 ${limits.value.creationLimit} 个团队`)
    return
  }
  createOpen.value = true
}

async function createTeam(): Promise<void> {
  if (!form.name.trim()) {
    createError.value = '请输入团队名称'
    return
  }
  if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(form.slug.trim()) || form.slug.trim().length < 2) {
    createError.value = '团队标识需为 2-63 位小写字母、数字或短横线'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    const team = await teamApi.create({
      name: form.name.trim(),
      slug: form.slug.trim(),
      description: form.description.trim() || undefined,
      logoUrl: form.logoUrl.trim() || undefined,
    })
    createOpen.value = false
    toast.success('团队创建成功')
    await router.push(`/${team.slug}`)
  } catch (error) {
    createError.value = error instanceof Error ? error.message : '团队创建失败'
  } finally {
    creating.value = false
  }
}

async function acceptInvitation(invitation: TeamInvitation): Promise<void> {
  invitationAction.value = invitation.id
  try {
    const team = await teamApi.acceptInvitation(invitation.id)
    toast.success(`已加入 ${invitation.teamName}`)
    await router.push(`/${team.slug}`)
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '接受邀请失败')
  } finally {
    invitationAction.value = null
  }
}

async function declineInvitation(invitation: TeamInvitation): Promise<void> {
  invitationAction.value = invitation.id
  try {
    await teamApi.declineInvitation(invitation.id)
    invitations.value = invitations.value.filter(item => item.id !== invitation.id)
    toast.success('已拒绝邀请')
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '拒绝邀请失败')
  } finally {
    invitationAction.value = null
  }
}

watch(
  () => route.query.create,
  value => {
    if (value === '1') {
      handleCreateClick()
    }
  },
)

onMounted(async () => {
  await load()
  if (route.query.create === '1') {
    handleCreateClick()
  }
})
</script>
