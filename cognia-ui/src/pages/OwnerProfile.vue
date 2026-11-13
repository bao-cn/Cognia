<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import Markdown from '@/components/Markdown.vue'
import TeamDetail from '@/pages/TeamDetail.vue'
import { profileApi, channelApi, userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { ChannelCard, OwnerProfile, ProfileLink } from '@/types/api'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import {
  IconPlus, IconPencil, IconLink, IconBrandGithub, IconMail, IconCamera,
  IconMapPin, IconBuilding, IconClock, IconUsers, IconPin,
  IconFolder, IconArrowUpRight, IconShieldCheck, IconId,
} from '@tabler/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(true)
const error = ref('')
const profile = ref<OwnerProfile | null>(null)

const identity = computed(() => String(route.params.identifier ?? ''))
const isTeam = computed(() => profile.value?.type === 'TEAM')
const channelBase = computed(() => (profile.value ? `/${profile.value.username}` : ''))

const displayName = computed(() => profile.value?.nickname || profile.value?.username || '')
const initials = computed(() => displayName.value.slice(0, 1).toUpperCase() || '?')

async function load() {
  loading.value = true
  error.value = ''
  try {
    profile.value = await profileApi.get(identity.value)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载主页失败'
    profile.value = null
  } finally {
    loading.value = false
  }
}

function channelLink(channel: ChannelCard) {
  return `${channelBase.value}/${channel.slug}`
}

function goChannel(channel: ChannelCard) {
  void router.push(channelLink(channel))
}

function goTeam(slug: string) {
  void router.push(`/${slug}`)
}

const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
  username: '', nickname: '', gender: '', bio: '', company: '', hometown: '', timezone: '', readme: '',
})
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)

function triggerAvatar() {
  avatarInput.value?.click()
}

async function toSquareWebp(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  const side = Math.min(bitmap.width, bitmap.height)
  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 512
  const context = canvas.getContext('2d')
  if (!context) throw new Error('无法处理图片')
  context.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, 512, 512)
  bitmap.close()
  return new Promise((resolve, reject) => {
    canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('无法生成头像')), 'image/webp', 0.9)
  })
}

async function handleAvatarFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    toast.error('请选择 JPG、PNG、WebP 或 GIF 图片')
    return
  }
  avatarUploading.value = true
  try {
    const updated = await userApi.uploadAvatar(await toSquareWebp(file))
    userStore.setProfile(updated)
    if (profile.value) profile.value.avatar = updated.avatar
    toast.success('头像已更新')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '头像上传失败')
  } finally {
    avatarUploading.value = false
  }
}
const editLinks = ref<ProfileLink[]>([])

function openEdit() {
  if (!profile.value) return
  editForm.username = profile.value.username ?? ''
  editForm.nickname = profile.value.nickname ?? ''
  editForm.gender = profile.value.gender ?? ''
  editForm.bio = profile.value.bio ?? ''
  editForm.company = profile.value.company ?? ''
  editForm.hometown = profile.value.hometown ?? ''
  editForm.timezone = profile.value.timezone ?? ''
  editForm.readme = profile.value.readme ?? ''
  editLinks.value = (profile.value.links ?? []).map(link => ({ ...link }))
  editOpen.value = true
}

function addLink() {
  if (editLinks.value.length >= 4) return
  editLinks.value.push({ label: '', url: '' })
}

function removeLink(index: number) {
  editLinks.value.splice(index, 1)
}

async function saveProfile() {
  if (!editForm.nickname.trim()) {
    toast.error('昵称不能为空')
    return
  }
  if (!/^[a-zA-Z][a-zA-Z0-9_]{2,31}$/.test(editForm.username.trim())) {
    toast.error('用户名需以字母开头，3-32 位字母、数字或下划线')
    return
  }
  saving.value = true
  try {
    const links = editLinks.value
      .map(link => ({ label: link.label.trim(), url: link.url.trim() }))
      .filter(link => link.label && link.url)
    const previousUsername = profile.value?.username
    profile.value = await profileApi.update({
      username: editForm.username.trim(),
      nickname: editForm.nickname.trim(),
      gender: editForm.gender.trim(),
      bio: editForm.bio.trim(),
      company: editForm.company.trim(),
      hometown: editForm.hometown.trim(),
      timezone: editForm.timezone.trim(),
      readme: editForm.readme,
      links,
    })
    editOpen.value = false
    if (previousUsername && profile.value?.username && profile.value.username !== previousUsername && profile.value?.self) {
      await router.replace(`/${profile.value.username}`)
    }
    toast.success('资料已更新')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    saving.value = false
  }
}

const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', slug: '', description: '', isPublic: true })

function openCreate() {
  createForm.name = ''
  createForm.slug = ''
  createForm.description = ''
  createForm.isPublic = true
  createOpen.value = true
}

function slugify(value: string) {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .replace(/-{2,}/g, '-')
    .slice(0, 63)
}

function onChannelNameInput() {
  if (!createForm.slug) createForm.slug = slugify(createForm.name)
}

async function createChannel() {
  if (!profile.value) return
  if (!createForm.name.trim()) {
    toast.error('请填写频道名称')
    return
  }
  if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(createForm.slug) || createForm.slug.length < 2) {
    toast.error('频道标识需为 2-63 位小写字母、数字或短横线')
    return
  }
  creating.value = true
  try {
    const channel = await channelApi.create({
      ownerType: profile.value.type,
      ownerId: profile.value.id,
      name: createForm.name.trim(),
      slug: createForm.slug,
      description: createForm.description.trim() || undefined,
      isPublic: createForm.isPublic,
    })
    createOpen.value = false
    toast.success('频道已创建')
    await router.push(`${channelBase.value}/${channel.slug}`)
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '创建频道失败')
  } finally {
    creating.value = false
  }
}

const linkIcon = (link: ProfileLink) => {
  const url = link.url.toLowerCase()
  if (url.includes('github.com')) return IconBrandGithub
  return IconLink
}

const teamMemberRoleLabel: Record<string, string> = {
  OWNER: '所有者',
  MANAGER: '维护者',
  EDITOR: '编辑者',
  MEMBER: '成员',
}

const genderLabel: Record<string, string> = { male: '男', female: '女', other: '其他' }

onMounted(load)
watch(identity, load)
</script>

<template>
  <TeamDetail v-if="isTeam" />

  <div v-else class="mx-auto flex w-full max-w-6xl flex-col gap-4 p-4 md:p-6">
    <div v-if="loading" class="flex h-72 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <div v-else-if="error" class="flex flex-col items-start gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-6">
      <p class="text-sm text-destructive">{{ error }}</p>
      <Button variant="outline" size="sm" @click="load">重新加载</Button>
    </div>

    <template v-else-if="profile">
      <input ref="avatarInput" type="file" accept="image/jpeg,image/png,image/webp,image/gif" class="hidden" @change="handleAvatarFile" />
      <div class="grid items-start gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">
        <aside class="flex flex-col gap-4 lg:sticky lg:top-4">
          <div class="relative aspect-square w-full">
              <Avatar class="size-full rounded-full">
                <AvatarImage :src="profile?.avatar || '/logo.png'" :alt="displayName" class="object-cover" />
                <AvatarFallback class="rounded-full text-4xl">{{ initials }}</AvatarFallback>
              </Avatar>
              <Button v-if="profile.canEdit && !isTeam" size="icon" class="absolute bottom-7 right-7 rounded-full shadow-lg" :disabled="avatarUploading" title="修改头像" @click="triggerAvatar">
                <Spinner v-if="avatarUploading" class="size-4" />
                <IconCamera v-else class="size-4" />
              </Button>
            </div>
          <section class="overflow-hidden rounded-xl">
            <div class="px-4 pb-4">
              <div class="mt-3 flex flex-col gap-1">
                <div class="flex flex-wrap items-center gap-2">
                  <h1 data-i18n-skip class="text-xl font-semibold">{{ displayName }}</h1>
                  <Badge variant="outline" class="gap-1">
                    <IconUsers v-if="isTeam" class="size-3" />
                    <IconId v-else class="size-3" />
                    {{ isTeam ? '团队' : '个人' }}
                  </Badge>
                </div>
                <p class="text-sm text-muted-foreground">@{{ profile.username }}</p>
              </div>
              <p v-if="profile.bio" class="mt-3 text-sm leading-relaxed">{{ profile.bio }}</p>
              <p v-else class="mt-3 text-sm text-muted-foreground">还没有填写个人简介。</p>

              <div class="mt-3 flex flex-col gap-2">
                <Button v-if="profile.canEdit && !isTeam" variant="outline" size="sm" class="w-full" @click="openEdit">
                  <IconPencil class="mr-1 size-4" />编辑资料
                </Button>
                <!-- 
                <Button v-if="profile.canEdit" size="sm" class="w-full" @click="openCreate">
                  <IconPlus class="mr-1 size-4" />新建频道
                </Button>
                 -->
              </div>

              <div class="mt-4 flex flex-col gap-2 pt-4 text-sm">
                <div v-if="profile.company" class="flex items-center gap-2 text-muted-foreground">
                  <IconBuilding class="size-4" /><span>{{ profile.company }}</span>
                </div>
                <div v-if="profile.hometown" class="flex items-center gap-2 text-muted-foreground">
                  <IconMapPin class="size-4" /><span>{{ profile.hometown }}</span>
                </div>
                <div v-if="profile.timezone" class="flex items-center gap-2 text-muted-foreground">
                  <IconClock class="size-4" /><span>{{ profile.timezone }}</span>
                </div>
                <div v-if="profile.gender" class="flex items-center gap-2 text-muted-foreground">
                  <IconId class="size-4" /><span>{{ genderLabel[profile.gender] || profile.gender }}</span>
                </div>
                <div v-if="profile.email" class="flex items-center gap-2 text-muted-foreground">
                  <IconMail class="size-4" /><span class="truncate">{{ profile.email }}</span>
                </div>
              </div>

              <div v-if="profile.links.length" class="mt-4 flex flex-col gap-1 pt-3">
                <a v-for="link in profile.links" :key="link.url" :href="link.url" target="_blank"
                  rel="noopener noreferrer"
                  class="flex items-center gap-2 rounded-md px-1 py-1 text-sm text-muted-foreground hover:text-foreground">
                  <component :is="linkIcon(link)" class="size-4" />
                  <span class="truncate">{{ link.label }}</span>
                  <IconArrowUpRight class="ml-auto size-3.5" />
                </a>
              </div>
            </div>
          </section>

          <section v-if="profile.badges.length" class="rounded-xl border bg-card">
            <header class="border-b px-4 py-3"><h2 class="text-sm font-semibold">勋章</h2></header>
            <div class="flex flex-wrap gap-2 p-4">
              <Badge v-for="badge in profile.badges" :key="badge" variant="secondary" class="gap-1">
                <IconShieldCheck class="size-3" />{{ badge }}
              </Badge>
            </div>
          </section>

          <section v-if="profile.teams.length" class="rounded-xl">
            <header class="border-t px-4 py-3"><h2 class="text-sm font-semibold">团队</h2></header>
            <div class="flex flex-col divide-y">
              <button v-for="team in profile.teams" :key="team.id" type="button"
                class="flex items-center gap-3 px-4 py-3 text-left hover:bg-muted/50 hover:rounded-md"
                @click="goTeam(team.slug)">
                <Avatar class="size-8"><AvatarImage :src="team.logoUrl || '/logo.png'" /><AvatarFallback>{{ team.name.slice(0, 1) }}</AvatarFallback></Avatar>
                <div class="min-w-0 flex-1">
                  <p data-i18n-skip class="truncate text-sm font-medium">{{ team.name }}</p>
                  <p class="truncate text-xs text-muted-foreground"><span>{{ teamMemberRoleLabel[team.role] || team.role }}</span> · <span>{{ team.memberCount }} 位成员</span></p>
                </div>
              </button>
            </div>
          </section>
        </aside>

        <div class="flex min-w-0 flex-col gap-4">
          <section v-if="profile.readme" class="rounded-xl border bg-card">
            <div class="p-4">
              <Markdown :text="profile.readme" />
            </div>
          </section>

          <section v-if="profile.pinnedChannels.length" class="rounded-xl border bg-card">
            <header class="border-b px-4 py-3">
              <h2 class="flex items-center gap-2 text-sm font-semibold"><IconPin class="size-4" />置顶频道</h2>
            </header>
            <div class="grid gap-3 p-4 md:grid-cols-2">
              <button v-for="channel in profile.pinnedChannels" :key="channel.id" type="button"
                class="flex flex-col gap-2 rounded-lg border p-3 text-left transition hover:border-primary/40 hover:bg-muted/40"
                @click="goChannel(channel)">
                <div class="flex items-center gap-2">
                  <IconFolder class="size-4 text-muted-foreground" />
                  <span data-i18n-skip class="truncate text-sm font-medium">{{ channel.name }}</span>
                  <Badge variant="secondary" class="ml-auto shrink-0">{{ channel.isPublic ? '公开' : '私密' }}</Badge>
                </div>
                <p class="line-clamp-2 text-xs text-muted-foreground">{{ channel.description || '暂无简介' }}</p>
                <p class="text-xs text-muted-foreground">{{ channel.documentCount }} 个文档</p>
              </button>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex items-center justify-between border-b px-4 py-3">
              <div>
                <h2 class="flex items-center gap-2 text-sm font-semibold"><IconFolder class="size-4" />全部频道</h2>
                <p class="mt-0.5 text-xs text-muted-foreground">{{ profile.channels.length }} 个频道</p>
              </div>
              <Button v-if="profile.canEdit" variant="ghost" size="sm" @click="openCreate">
                <IconPlus class="mr-1 size-4" />新建
              </Button>
            </header>
            <div v-if="profile.channels.length" class="flex flex-col divide-y">
              <button v-for="channel in profile.channels" :key="channel.id" type="button"
                class="flex items-center gap-3 px-4 py-3 text-left hover:bg-muted/50"
                @click="goChannel(channel)">
                <div class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted">
                  <IconFolder class="size-4 text-muted-foreground" />
                </div>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2">
                    <p data-i18n-skip class="truncate text-sm font-medium">{{ channel.name }}</p>
                    <Badge variant="outline" class="shrink-0">{{ channel.isPublic ? '公开' : '私密' }}</Badge>
                  </div>
                  <p class="truncate text-xs text-muted-foreground"><span data-i18n-skip>{{ channel.description || '暂无简介' }}</span> · <span>{{ channel.documentCount }} 个文档</span></p>
                </div>
                <IconArrowUpRight class="size-4 shrink-0 text-muted-foreground" />
              </button>
            </div>
            <div v-else class="flex flex-col items-center gap-2 px-4 py-10 text-center">
              <IconFolder class="size-8 text-muted-foreground/50" />
              <p class="text-sm text-muted-foreground">还没有频道</p>
              <Button v-if="profile.canEdit" size="sm" variant="outline" @click="openCreate">
                <IconPlus class="mr-1 size-4" />创建第一个频道
              </Button>
            </div>
          </section>
        </div>
      </div>
    </template>

    <Dialog v-model:open="editOpen">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>编辑资料</DialogTitle>
          <DialogDescription>这些信息会展示在你的公开主页上。</DialogDescription>
        </DialogHeader>
        <div class="grid max-h-[70vh] gap-4 overflow-y-auto py-1 md:grid-cols-2">
          <div class="flex items-center gap-3 md:col-span-2">
            <Avatar class="size-14">
              <AvatarImage :src="profile?.avatar || '/logo.png'" :alt="displayName" class="object-cover" />
              <AvatarFallback>{{ initials }}</AvatarFallback>
            </Avatar>
            <Button variant="outline" size="sm" :disabled="avatarUploading" @click="triggerAvatar">
              <Spinner v-if="avatarUploading" class="mr-1 size-3.5" />
              <IconCamera v-else class="mr-1 size-3.5" />
              修改头像
            </Button>
            <span class="text-xs text-muted-foreground">JPG / PNG / WebP / GIF，自动裁切为 512×512 WebP</span>
          </div>
          <div class="grid gap-2">
            <Label for="profile-username">Username</Label>
            <Input id="profile-username" v-model="editForm.username" maxlength="32" placeholder="用户名，作为个人主页地址" />
          </div>
          <div class="grid gap-2">
            <Label for="profile-nickname">Nickname</Label>
            <Input id="profile-nickname" v-model="editForm.nickname" maxlength="32" />
          </div>
          <div class="grid gap-2">
            <Label for="profile-gender">性别</Label>
            <Select
              :model-value="editForm.gender || 'none'"
              @update:model-value="(value) => editForm.gender = value === 'none' ? '' : String(value)"
            >
              <SelectTrigger id="profile-gender" class="w-full">
                <SelectValue placeholder="不公开" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none">不公开</SelectItem>
                <SelectItem value="male">男</SelectItem>
                <SelectItem value="female">女</SelectItem>
                <SelectItem value="other">其他</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="grid gap-2 md:col-span-2">
            <Label for="profile-bio">个人简介</Label>
            <Textarea id="profile-bio" v-model="editForm.bio" rows="3" maxlength="512" />
          </div>
          <div class="grid gap-2">
            <Label for="profile-company">企业</Label>
            <Input id="profile-company" v-model="editForm.company" maxlength="128" />
          </div>
          <div class="grid gap-2">
            <Label for="profile-hometown">家乡</Label>
            <Input id="profile-hometown" v-model="editForm.hometown" maxlength="128" />
          </div>
          <div class="grid gap-2">
            <Label for="profile-timezone">时区</Label>
            <Input id="profile-timezone" v-model="editForm.timezone" placeholder="Asia/Shanghai" maxlength="64" />
          </div>
          <div class="grid gap-2 md:col-span-2">
            <Label for="profile-readme">README</Label>
            <Textarea id="profile-readme" v-model="editForm.readme" rows="8" placeholder="支持 Markdown" />
          </div>
          <div class="grid gap-2 md:col-span-2">
            <div class="flex items-center justify-between">
              <Label>自定义链接</Label>
              <Button type="button" variant="outline" size="sm" :disabled="editLinks.length >= 4" @click="addLink">
                <IconPlus class="mr-1 size-3.5" />添加
              </Button>
            </div>
            <div v-for="(link, index) in editLinks" :key="index" class="flex gap-2">
              <Input v-model="link.label" placeholder="名称" class="w-32" maxlength="32" />
              <Input v-model="link.url" placeholder="https://..." class="flex-1" />
              <Button type="button" variant="ghost" size="icon" @click="removeLink(index)">×</Button>
            </div>
            <p class="text-xs text-muted-foreground">最多 4 个链接，需为 http(s) 地址。</p>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="saving" @click="editOpen = false">取消</Button>
          <Button :disabled="saving" @click="saveProfile">
            <Spinner v-if="saving" class="mr-2" />保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="createOpen">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>新建频道</DialogTitle>
          <DialogDescription>频道类似于仓库，用来组织文档、README 和 AGENT.md。</DialogDescription>
        </DialogHeader>
        <div class="grid gap-4 py-1">
          <div class="grid gap-2">
            <Label for="channel-name">频道名称</Label>
            <Input id="channel-name" v-model="createForm.name" placeholder="例如：产品知识库" maxlength="64" @input="onChannelNameInput" />
          </div>
          <div class="grid gap-2">
            <Label for="channel-slug">频道标识</Label>
            <Input id="channel-slug" v-model="createForm.slug" placeholder="product-kb" maxlength="63" />
            <p class="text-xs text-muted-foreground">小写字母、数字和短横线，创建后作为频道地址。</p>
          </div>
          <div class="grid gap-2">
            <Label for="channel-description">频道简介</Label>
            <Textarea id="channel-description" v-model="createForm.description" rows="3" maxlength="512" />
          </div>
          <div class="flex items-center gap-2">
            <Checkbox id="channel-public" v-model="createForm.isPublic" />
            <Label for="channel-public" class="text-sm font-normal">公开频道（所有人可访问）</Label>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="creating" @click="createOpen = false">取消</Button>
          <Button :disabled="creating" @click="createChannel">
            <Spinner v-if="creating" class="mr-2" />创建
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
