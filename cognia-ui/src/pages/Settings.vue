<template>
  <div class="flex flex-col items-center justify-center gap-6 p-6 md:p-10">
    <!-- 账号设置 -->
    <div class="flex w-full max-w-lg flex-col gap-2">
      <h3 class="text-lg font-bold">{{ $t('settings.account.title') }}</h3>
      <div class="bg-muted rounded-2xl overflow-hidden">
        <button type="button" class="flex h-12 w-full items-center gap-2 px-3 text-left transition hover:bg-muted/70" @click="router.push(`/${userStore.profile?.username}`)">
          <ColorfulIcon :icon="IconUserCircle" color="#6366F1" class="size-6" />
          <span class="text-sm font-medium">个人主页</span>
          <div class="flex-1"></div>
          <span class="max-w-[50%] truncate text-xs text-gray-500 dark:text-gray-300">@{{ userStore.profile?.username }}</span>
          <IconChevronRight class="size-4 text-muted-foreground" />
        </button>
        <div class="border-t border-border"></div>
        <div class="flex items-center px-3 h-12 gap-2">
          <ColorfulIcon :icon="IconPhone" color="#EC4899" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.account.phone') }}</span>
          <div class="flex-1"></div>
          <span class="text-xs text-gray-500 dark:text-gray-300">{{ userStore.profile?.phone || '未绑定' }}</span>
          <Button variant="ghost" class="h-8 text-blue-500 hover:text-blue-600" @click="toast.info('手机号绑定功能暂未开放')">绑定</Button>
        </div>
        <template v-for="account in oauthAccounts" :key="account.code">
          <div class="border-t border-border"></div>
          <div class="flex h-12 items-center gap-2 px-3">
            <ColorfulIcon :icon="oauthIcon(account.code)" :color="oauthColor(account.code)" class="size-6" />
            <span class="text-sm font-medium">{{ account.name }}</span>
            <div class="flex-1"></div>
            <span class="text-xs text-gray-500 dark:text-gray-300">
              {{ account.linked ? '已关联' : account.enabled ? '未关联' : '未启用' }}
            </span>
            <Button
              v-if="account.linked"
              variant="ghost"
              class="h-8 text-red-600 hover:text-red-700"
              :disabled="Boolean(oauthBusy)"
              @click="askUnlink(account)"
            >解绑</Button>
            <Button
              v-else
              variant="ghost"
              class="h-8 text-blue-500 hover:text-blue-600"
              :disabled="Boolean(oauthBusy)"
              @click="account.enabled ? startOAuthBind(account) : toast.info('该登录方式未启用')"
            >
              <Spinner v-if="oauthBusy === account.code" class="mr-1 size-3.5" />
              绑定
            </Button>
          </div>
        </template>
        <div class="border-t border-border"></div>
        <div class="flex items-center px-3 h-12 gap-2">
          <ColorfulIcon :icon="IconMail" color="#0EA5E9" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.account.email') }}</span>
          <div class="flex-1"></div>
          <span class="text-xs text-gray-500 dark:text-gray-300">{{ userStore.profile?.email || '未绑定' }}</span>
          <Button variant="ghost" class="h-8 text-blue-500 hover:text-blue-600" @click="openSecurity('email')">{{ $t('settings.other.change') }}</Button>
        </div>
        <div class="border-t border-border"></div>
        <div class="flex items-center px-3 h-12 gap-2">
          <ColorfulIcon :icon="IconKey" color="#F97316" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.account.password') }}</span>
          <div class="flex-1"></div>
          <Button variant="ghost" class="h-8 text-blue-500 hover:text-blue-600" @click="openSecurity('password')">{{ userStore.profile?.hasPassword ? '修改密码' : '设置密码' }}</Button>
        </div>
        <div class="border-t border-border"></div>
        <div class="flex items-center px-3 h-12 gap-2">
          <ColorfulIcon :icon="IconShieldCheck" color="#22C55E" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.account.2fa') }}</span>
          <div class="flex-1"></div>
          <Button variant="ghost" class="h-8 text-blue-500 hover:text-blue-600" @click="openSecurity('twofa')">{{ userStore.profile?.twoFactorEnabled ? '管理' : '开启' }}</Button>
        </div>
        <div class="border-t border-border"></div>
        <button type="button" class="flex h-12 w-full items-center gap-2 px-3 text-left text-red-600 transition hover:bg-red-50 dark:hover:bg-red-950/20" @click="openSecurity('delete')">
          <ColorfulIcon :icon="IconUserX" color="#EF4444" class="size-6" />
          <span class="text-sm font-medium">注销账户</span>
          <div class="flex-1"></div>
          <IconChevronRight class="size-4" />
        </button>
      </div>
    </div>
    <!-- 团队设置 -->
    <div class="flex w-full max-w-lg flex-col gap-2">
      <h3 class="text-lg font-bold">{{ $t('settings.teams.title') }}</h3>
      <div class="bg-muted rounded-2xl overflow-hidden">
        <div class="flex items-center px-3 h-12 gap-2 cursor-pointer" @click="handleMyTeams">
          <ColorfulIcon :icon="IconUsers" color="#0EA5E9" class="size-6" />
          <span class="text-sm font-medium">我的团队</span>
          <div class="flex-1"></div>
          <IconChevronRight class="size-4 text-muted-foreground" />
        </div>
        <div class="border-t border-border"></div>
        <div class="flex items-center px-3 h-12 gap-2 cursor-pointer" @click="handleCreateTeam">
          <ColorfulIcon :icon="IconUsers" color="#6366F1" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.teams.create') }}</span>
          <div class="flex-1"></div>
          <IconChevronRight class="size-4 text-muted-foreground" />
        </div>
      </div>
    </div>
    <!-- 基础设置 -->
    <div class="flex w-full max-w-lg flex-col gap-2">
      <h3 class="text-lg font-bold">{{ $t('settings.preferences.title') }}</h3>
      <div class="bg-muted rounded-2xl overflow-hidden">
        <div class="flex items-center px-3 h-12 gap-2">
          <ColorfulIcon :icon="IconBrightness" color="#8B5CF6" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.preferences.theme') }}</span>
          <div class="flex-1"></div>
          <Select @update:modelValue="handleChangeTheme" v-model="configStore.configInfo.theme">
            <SelectTrigger class="h-8 w-36">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="system">{{ $t('settings.preferences.theme.system') }}</SelectItem>
              <SelectItem value="light">{{ $t('settings.preferences.theme.light') }}</SelectItem>
              <SelectItem value="dark">{{ $t('settings.preferences.theme.dark') }}</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div class="border-t border-border"></div>
        <div class="flex items-center px-3 h-12 gap-2">
          <ColorfulIcon :icon="IconLanguage" color="#10B981" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.preferences.language') }}</span>
          <div class="flex-1"></div>
          <Select @update:modelValue="handleChangeLanguage" v-model="configStore.configInfo.language">
            <SelectTrigger class="h-8 w-36">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="zhCn">简体中文</SelectItem>
              <SelectItem value="enUs">English</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div class="border-t border-border"></div>
        <div class="flex items-center px-3 h-12 gap-2">
          <ColorfulIcon :icon="IconWorldSearch" color="#F59E0B" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.preferences.auto_websearch') }}</span>
          <div class="flex-1"></div>
          <Switch :model-value="autoWebSearch" :disabled="savingPreference" @update:model-value="changeAutoWebSearch" />
        </div>
      </div>
    </div>
    <!-- 模型 -->
    <div class="flex w-full max-w-lg flex-col gap-2">
      <h3 class="text-lg font-bold">{{ $t('settings.models.title') }}</h3>
      <div class="bg-muted rounded-2xl overflow-hidden">
        <div class="flex items-center px-3 h-12 gap-2 cursor-pointer" @click="handleModelList">
          <ColorfulIcon :icon="IconBrain" color="#7C3AED" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.models.list') }}</span>
          <div class="flex-1"></div>
          <IconChevronRight class="size-4 text-muted-foreground" />
        </div>
      </div>
    </div>
    <!-- 帮助与反馈 -->
    <div class="flex w-full max-w-lg flex-col gap-2">
      <h3 class="text-lg font-bold">{{ $t('settings.help_feedback.title') }}</h3>
      <div class="bg-muted rounded-2xl overflow-hidden">
        <div class="flex items-center px-3 h-12 gap-2 cursor-pointer" @click="handleHelp">
          <ColorfulIcon :icon="IconHelp" color="#3B82F6" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.help_feedback.help') }}</span>
          <div class="flex-1"></div>
          <IconChevronRight class="size-4 text-muted-foreground" />
        </div>
        <div class="border-t border-border"></div>
        <div class="flex items-center px-3 h-12 gap-2 cursor-pointer" @click="handleFeedback">
          <ColorfulIcon :icon="IconMessageDots" color="#14B8A6" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.help_feedback.feedback') }}</span>
          <div class="flex-1"></div>
          <IconChevronRight class="size-4 text-muted-foreground" />
        </div>
      </div>
    </div>
    <!-- 关于 -->
    <div class="flex w-full max-w-lg flex-col gap-2">
      <h3 class="text-lg font-bold">{{ $t('settings.about.title') }}</h3>
      <div class="bg-muted rounded-2xl overflow-hidden">
        <div class="flex items-center px-3 h-12 gap-2 cursor-pointer" @click="handleAgreement">
          <ColorfulIcon :icon="IconFileText" color="#64748B" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.about.service') }}</span>
          <div class="flex-1"></div>
          <IconChevronRight class="size-4 text-muted-foreground" />
        </div>
        <div class="border-t border-border"></div>
        <div class="flex items-center px-3 h-12 gap-2 cursor-pointer" @click="handlePrivacy">
          <ColorfulIcon :icon="IconShieldLock" color="#475569" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.about.privacy') }}</span>
          <div class="flex-1"></div>
          <IconChevronRight class="size-4 text-muted-foreground" />
        </div>
        <div class="border-t border-border"></div>
        <div class="flex items-center px-3 h-12 gap-2 cursor-pointer" @click="handleLicense">
          <ColorfulIcon :icon="IconCode" color="#0284C7" class="size-6" />
          <span class="text-sm font-medium">{{ $t('settings.about.opensource') }}</span>
          <div class="flex-1"></div>
          <IconChevronRight class="size-4 text-muted-foreground" />
        </div>
      </div>
    </div>
    <!-- 退出登录 -->
    <div class="flex w-full max-w-lg pt-2">
      <Button variant="outline" class="w-full h-11 border-red-200 text-red-600 hover:bg-red-50" @click="handleLogout">
        <IconLogout class="mr-2 size-5" />
        退出登录
      </Button>
    </div>

    <Dialog v-model:open="editorOpen">
      <DialogContent class="max-h-[90vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>修改头像</DialogTitle>
          <DialogDescription>选择一个新头像，保存后立即生效。手机号需通过短信验证码绑定，暂不支持在此修改。</DialogDescription>
        </DialogHeader>

        <section>
          <div class="flex items-center justify-between">
            <Label>选择头像</Label>
            <Button variant="ghost" size="sm" class="rounded-full text-indigo-600 dark:text-indigo-300" @click="randomAvatar">
              <IconArrowsShuffle class="mr-1.5 size-4" />
              随机一个
            </Button>
          </div>
          <div class="mt-3 flex flex-wrap items-center gap-2">
            <input
              ref="avatarFileInput"
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              class="hidden"
              @change="handleAvatarFile"
            />
            <Button
              variant="outline"
              size="sm"
              class="rounded-full"
              :disabled="avatarUploading"
              @click="avatarFileInput?.click()"
            >
              <Spinner v-if="avatarUploading" class="mr-1.5 size-4" />
              <IconUpload v-else class="mr-1.5 size-4" />
              上传自定义头像
            </Button>
            <p class="text-xs text-muted-foreground">支持 JPG / PNG / WebP，自动压缩为 512 × 512 WebP。</p>
          </div>
          <div class="mt-3 grid grid-cols-5 gap-2.5 sm:grid-cols-6">
            <button
              v-for="option in avatarOptions"
              :key="option.file"
              type="button"
              class="group relative aspect-square rounded-2xl border-2 p-1 transition duration-200 hover:scale-105"
              :class="draft.avatar === avatarPath(option.file)
                ? 'border-indigo-500 bg-indigo-50 shadow-md dark:border-indigo-300 dark:bg-indigo-950/40'
                : 'border-transparent bg-muted/50 hover:border-border'"
              :aria-label="option.label"
              :aria-pressed="draft.avatar === avatarPath(option.file)"
              @click="chooseAvatar(option.file)"
            >
              <img :src="avatarThumbPath(option.file)" :alt="option.label" loading="lazy" decoding="async" class="h-full w-full rounded-xl object-cover" />
              <span
                v-if="draft.avatar === avatarPath(option.file)"
                class="absolute -right-1.5 -top-1.5 inline-flex size-5 items-center justify-center rounded-full bg-indigo-500 text-white shadow ring-2 ring-background"
              >
                <IconCheck class="size-3" />
              </span>
            </button>
          </div>
        </section>

        <div class="mt-4 flex items-center gap-3 rounded-2xl border bg-muted/40 p-3">
          <Avatar class="size-12 ring-2 ring-background">
            <AvatarImage :src="draftPreview" :alt="displayName" class="object-cover" />
            <AvatarFallback>{{ initials }}</AvatarFallback>
          </Avatar>
          <div>
            <p class="text-sm font-medium">{{ displayName }}</p>
            <p class="text-xs text-muted-foreground">选择后点击保存头像即可生效</p>
          </div>
        </div>

        <p v-if="editorError" class="mt-3 rounded-xl bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ editorError }}</p>

        <DialogFooter>
          <Button variant="outline" class="rounded-full" :disabled="saving" @click="editorOpen = false">取消</Button>
          <Button class="rounded-full" :disabled="saving" @click="saveAvatar">
            <Spinner v-if="saving" class="mr-2" />
            保存头像
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="nicknameEditorOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>修改昵称</DialogTitle>
          <DialogDescription>昵称会显示在侧边栏和对话中。手机号需通过短信验证码绑定，不在此处修改。</DialogDescription>
        </DialogHeader>

        <div class="grid gap-2">
          <Label for="settings-nickname">昵称</Label>
          <Input
            id="settings-nickname"
            v-model="nicknameDraft"
            maxlength="32"
            placeholder="给自己起个名字"
            @keyup.enter="saveNickname"
          />
          <p class="text-xs text-muted-foreground">{{ nicknameDraft.length }}/32</p>
        </div>

        <p v-if="nicknameError" class="rounded-xl bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ nicknameError }}</p>

        <DialogFooter>
          <Button variant="outline" class="rounded-full" :disabled="nicknameSaving" @click="nicknameEditorOpen = false">取消</Button>
          <Button class="rounded-full" :disabled="nicknameSaving" @click="saveNickname">
            <Spinner v-if="nicknameSaving" class="mr-2" />
            保存昵称
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    <AccountSecurityDialog v-model:open="securityOpen" :mode="securityMode" @updated="onProfileUpdated" />
  </div>

    <Dialog v-model:open="oauthUnlinkOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>解绑账号</DialogTitle>
          <DialogDescription>
            解绑后，将无法使用 {{ oauthUnlinkTarget?.name }} 登录此账户。确定解绑吗？
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" :disabled="Boolean(oauthBusy)" @click="oauthUnlinkOpen = false">取消</Button>
          <Button variant="destructive" :disabled="Boolean(oauthBusy)" @click="confirmUnlink">
            <Spinner v-if="oauthBusy" class="mr-2 size-4" />
            解绑
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import ColorfulIcon from '@/components/ui/colorful-icon/ColorfulIcon.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  IconUsers,
  IconBrightness,
  IconLanguage,
  IconWorldSearch,
  IconPhone,
  IconBrandAppleFilled,
  IconBrandGoogleFilled,
  IconBrandGithub,
  IconLink,
  IconMail,
  IconKey,
  IconShieldCheck,
  IconBrain,
  IconHelp,
  IconMessageDots,
  IconFileText,
  IconShieldLock,
  IconCode,
  IconLogout,
  IconChevronRight,
  IconUserCircle,
  IconArrowsShuffle,
  IconCheck,
  IconUpload,
  IconUserX
} from "@tabler/icons-vue"
import AccountSecurityDialog from '@/components/AccountSecurityDialog.vue'
import { useConfigStore, THEMES, LocaleType } from '@/stores/config'
import { useRouter } from 'vue-router'
import type { AcceptableValue } from 'reka-ui'
import { toast } from 'vue-sonner'
import { useUserStore } from '@/stores/user'
import { AVATAR_OPTIONS, avatarPath, avatarThumbnailFromUrl, avatarThumbPath } from '@/lib/avatars'
import { userApi } from '@/api'
import { oauthApi, type UserOAuthAccount } from '@/api/oauth'

const router = useRouter()
const userStore = useUserStore()
const configStore = useConfigStore()

const displayName = computed(() => userStore.displayName)
const avatar = computed(() => userStore.avatar)
const initials = computed(() => displayName.value.slice(0, 2).toUpperCase())
const avatarOptions = AVATAR_OPTIONS

const editorOpen = ref(false)
const saving = ref(false)
const editorError = ref('')
const draft = reactive({ avatar: '' })
const avatarFileInput = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)

function chooseAvatar(file: string): void {
  draft.avatar = avatarPath(file)
  editorError.value = ''
}

function randomAvatar(): void {
  const current = draft.avatar
  const alternatives = avatarOptions.filter(option => avatarPath(option.file) !== current)
  const next = alternatives[Math.floor(Math.random() * alternatives.length)]
  if (next) chooseAvatar(next.file)
}

const draftPreview = computed(() => avatarThumbnailFromUrl(draft.avatar || avatar.value))

async function toSquareWebp(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  try {
    const side = Math.min(bitmap.width, bitmap.height)
    const size = Math.min(512, side)
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const context = canvas.getContext('2d')
    if (!context) throw new Error('当前浏览器不支持图片处理')
    context.drawImage(
      bitmap,
      (bitmap.width - side) / 2,
      (bitmap.height - side) / 2,
      side,
      side,
      0,
      0,
      size,
      size,
    )
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/webp', 0.9))
    if (!blob) throw new Error('头像转换失败，请更换图片重试')
    return blob
  } finally {
    bitmap.close()
  }
}

async function handleAvatarFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    editorError.value = '请选择图片文件'
    return
  }
  avatarUploading.value = true
  editorError.value = ''
  try {
    const blob = await toSquareWebp(file)
    const updated = await userApi.uploadAvatar(blob)
    userStore.setProfile(updated)
    draft.avatar = updated.avatar || ''
    toast.success('头像已上传')
  } catch (error) {
    editorError.value = error instanceof Error ? error.message : '头像上传失败'
  } finally {
    avatarUploading.value = false
  }
}

async function saveAvatar(): Promise<void> {
  saving.value = true
  editorError.value = ''
  try {
    const updated = await userApi.updateMe({ avatar: draft.avatar || undefined })
    userStore.setProfile(updated)
    editorOpen.value = false
    toast.success('头像已更新')
  } catch (error) {
    editorError.value = error instanceof Error ? error.message : '头像保存失败'
  } finally {
    saving.value = false
  }
}

const nicknameEditorOpen = ref(false)
const nicknameSaving = ref(false)
const nicknameError = ref('')
const nicknameDraft = ref('')

async function saveNickname(): Promise<void> {
  const nickname = nicknameDraft.value.trim()
  if (!nickname) {
    nicknameError.value = '昵称不能为空'
    return
  }
  nicknameSaving.value = true
  nicknameError.value = ''
  try {
    const updated = await userApi.updateMe({ nickname })
    userStore.setProfile(updated)
    nicknameEditorOpen.value = false
    toast.success('昵称已更新')
  } catch (error) {
    nicknameError.value = error instanceof Error ? error.message : '昵称保存失败'
  } finally {
    nicknameSaving.value = false
  }
}

const securityOpen = ref(false)
const securityMode = ref<'password' | 'twofa' | 'email' | 'delete' | 'models'>('password')
const oauthAccounts = ref<UserOAuthAccount[]>([])
const oauthLoading = ref(false)
const oauthBusy = ref('')
const oauthUnlinkOpen = ref(false)
const oauthUnlinkTarget = ref<UserOAuthAccount | null>(null)
const oauthIcons: Record<string, any> = {
  GOOGLE: IconBrandGoogleFilled,
  APPLE: IconBrandAppleFilled,
  GITHUB: IconBrandGithub,
  OIDC: IconLink,
}
const oauthColors: Record<string, string> = {
  GOOGLE: '#EA4335',
  APPLE: '#111827',
  GITHUB: '#6B7280',
  OIDC: '#6366F1',
}
function oauthIcon(code: string) {
  return oauthIcons[code] || IconLink
}
function oauthColor(code: string) {
  return oauthColors[code] || '#6366F1'
}
async function loadOAuthAccounts() {
  oauthLoading.value = true
  try {
    oauthAccounts.value = await oauthApi.userAccounts()
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '加载第三方账号失败')
  } finally {
    oauthLoading.value = false
  }
}
async function startOAuthBind(account: UserOAuthAccount) {
  if (!account.enabled || oauthBusy.value) return
  oauthBusy.value = account.code
  try {
    const redirectUri = `${window.location.origin}/oauth/callback?provider=${account.code}`
    const url = await oauthApi.userAuthorizeUrl(account.code, redirectUri)
    window.location.href = url
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '无法发起关联')
    oauthBusy.value = ''
  }
}
function askUnlink(account: UserOAuthAccount) {
  oauthUnlinkTarget.value = account
  oauthUnlinkOpen.value = true
}
async function confirmUnlink() {
  const account = oauthUnlinkTarget.value
  if (!account) return
  oauthBusy.value = account.code
  try {
    await oauthApi.unlinkAccount(account.code)
    toast.success(`已解绑 ${account.name} 账号`)
    oauthUnlinkOpen.value = false
    await loadOAuthAccounts()
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '解绑失败')
  } finally {
    oauthBusy.value = ''
  }
}
onMounted(loadOAuthAccounts)
const autoWebSearch = computed(() => Boolean(userStore.profile?.autoWebSearch))
const savingPreference = ref(false)

function openSecurity(mode: typeof securityMode.value) {
  securityMode.value = mode
  securityOpen.value = true
}

function onProfileUpdated(profile: any) {
  userStore.setProfile(profile)
}

async function changeAutoWebSearch(value: boolean) {
  savingPreference.value = true
  try {
    const profile = await userApi.updatePreferences({ autoWebSearch: value })
    userStore.setProfile(profile)
    toast.success(value ? '已开启默认联网搜索' : '已关闭默认联网搜索')
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '保存设置失败')
  } finally {
    savingPreference.value = false
  }
}

// 切换外观
function handleChangeTheme(val: AcceptableValue) {
  configStore.setTheme(val as THEMES)
}

// 切换语言
function handleChangeLanguage(val: AcceptableValue) {
  configStore.setLanguage(val as LocaleType)
}

// 点击事件
function handleCreateTeam() {
  void router.push('/settings/teams?create=1')
}
function handleMyTeams() {
  void router.push('/settings/teams')
}
function handleModelList() {
  openSecurity('models')
}
function handleHelp() {
  toast.warning('暂不可用，管理员未配置使用帮助功能')
}
function handleFeedback() {
  toast.warning('暂不可用，管理员未配置意见反馈功能')
}
function handleAgreement() {
  router.push('/legal/terms')
}
function handlePrivacy() {
  router.push('/legal/privacy')
}
function handleLicense() {
  router.push('/legal/licenses')
}
async function handleLogout() {
  await userStore.logout()
  toast.success('已退出登录')
  await router.replace('/sign')
}
</script>

<style scoped></style>
