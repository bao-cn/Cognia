<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { IconCopy, IconShieldCheck } from '@tabler/icons-vue'
import { chatApi, userApi } from '@/api'
import type { PublicChatModel, UserProfile } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import QrCode from '@/components/QrCode.vue'

type Mode = 'password' | 'twofa' | 'email' | 'delete' | 'models'

const props = defineProps<{ open: boolean; mode: Mode }>()
const emit = defineEmits<{ 'update:open': [value: boolean]; updated: [profile: UserProfile] }>()

const router = useRouter()
const userStore = useUserStore()
const saving = ref(false)
const error = ref('')
const setup = ref<{ secret: string; otpauthUri: string } | null>(null)
const models = ref<PublicChatModel[]>([])
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '', email: '', code: '', twoFactorCode: '' })
const sendingEmailCode = ref(false)
const emailCountdown = ref(0)
let emailTimer: ReturnType<typeof setInterval> | null = null

const hasPassword = computed(() => Boolean(userStore.profile?.hasPassword))
const twoFactorEnabled = computed(() => Boolean(userStore.profile?.twoFactorEnabled))
const title = computed(() => ({
  password: hasPassword.value ? '修改密码' : '设置密码',
  twofa: twoFactorEnabled.value ? '两步验证' : '开启两步验证',
  email: '修改邮箱',
  delete: '注销账户',
  models: '可用模型',
}[props.mode]))

function reset() {
  error.value = ''
  saving.value = false
  setup.value = null
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  form.email = userStore.profile?.email || ''
  form.code = ''
  form.twoFactorCode = ''
  emailCountdown.value = 0
  if (emailTimer) { clearInterval(emailTimer); emailTimer = null }
}

watch(() => props.open, async (open) => {
  if (!open) return
  reset()
  if (props.mode === 'twofa' && !twoFactorEnabled.value) {
    try {
      setup.value = await userApi.setupTwoFactor()
    } catch (err: any) {
      error.value = err?.message || '生成两步验证密钥失败'
    }
  }
  if (props.mode === 'models') {
    try {
      models.value = await chatApi.models()
    } catch (err: any) {
      error.value = err?.message || '加载模型失败'
    }
  }
})

function close() {
  emit('update:open', false)
}

async function savePassword() {
  if (!form.newPassword || form.newPassword !== form.confirmPassword) {
    error.value = '两次输入的新密码不一致'
    return
  }
  saving.value = true
  error.value = ''
  try {
    if (hasPassword.value)
      await userApi.changeMyPassword({ oldPassword: form.oldPassword, newPassword: form.newPassword, twoFactorCode: form.code || undefined })
    else
      await userApi.setMyPassword({ newPassword: form.newPassword, twoFactorCode: form.code || undefined })
    toast.success(hasPassword.value ? '密码已修改' : '密码已设置')
    await userStore.fetchProfile()
    close()
  } catch (err: any) {
    error.value = err?.message || '保存密码失败'
  } finally {
    saving.value = false
  }
}

async function saveTwoFactor() {
  saving.value = true
  error.value = ''
  const wasEnabled = twoFactorEnabled.value
  try {
    const profile = wasEnabled
      ? await userApi.disableTwoFactor(form.code)
      : await userApi.enableTwoFactor(form.code)
    userStore.setProfile(profile)
    emit('updated', profile)
    toast.success(wasEnabled ? '已关闭两步验证' : '已开启两步验证')
    close()
  } catch (err: any) {
    error.value = err?.message || '两步验证操作失败'
  } finally {
    saving.value = false
  }
}

async function saveEmail() {
  if (!form.email.trim()) {
    error.value = '请输入邮箱'
    return
  }
  if (form.code.length !== 6) {
    error.value = '请输入新邮箱收到的 6 位验证码'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const profile = await userApi.updateEmail({ email: form.email.trim(), code: form.code, twoFactorCode: form.twoFactorCode || undefined })
    userStore.setProfile(profile)
    emit('updated', profile)
    toast.success('邮箱已更新')
    close()
  } catch (err: any) {
    error.value = err?.message || '修改邮箱失败'
  } finally {
    saving.value = false
  }
}

async function deleteAccount() {
  saving.value = true
  error.value = ''
  try {
    await userApi.deleteMe({ password: form.oldPassword || undefined, twoFactorCode: form.code || undefined })
    userStore.clearAuth()
    toast.success('账户已注销')
    await router.replace('/sign')
  } catch (err: any) {
    error.value = err?.message || '注销账户失败'
  } finally {
    saving.value = false
  }
}

async function sendEmailCode() {
  const email = form.email.trim()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    error.value = '请输入有效邮箱'
    return
  }
  sendingEmailCode.value = true
  error.value = ''
  try {
    await userApi.sendEmailChangeCode(email)
    emailCountdown.value = 60
    if (emailTimer) clearInterval(emailTimer)
    emailTimer = setInterval(() => {
      emailCountdown.value -= 1
      if (emailCountdown.value <= 0 && emailTimer) { clearInterval(emailTimer); emailTimer = null }
    }, 1000)
    toast.success('验证码已发送，请查收新邮箱')
  } catch (err: any) {
    error.value = err?.message || '验证码发送失败'
  } finally {
    sendingEmailCode.value = false
  }
}

onUnmounted(() => { if (emailTimer) clearInterval(emailTimer) })

async function copy(value: string) {
  await navigator.clipboard.writeText(value)
  toast.success('已复制')
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="max-h-[90vh] overflow-y-auto sm:max-w-md">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2">
          <IconShieldCheck v-if="mode === 'twofa'" class="size-4 text-emerald-500" />
          {{ title }}
        </DialogTitle>
        <DialogDescription>
          <template v-if="mode === 'password'">密码用于账户登录和高危操作验证。</template>
          <template v-else-if="mode === 'twofa'">
            {{ twoFactorEnabled ? '输入当前验证器生成的 6 位验证码以关闭两步验证。' : '使用任意 TOTP 客户端（如 1Password、Google Authenticator）扫描或手动添加密钥。' }}
          </template>
          <template v-else-if="mode === 'email'">修改邮箱后需要重新登录。</template>
          <template v-else-if="mode === 'delete'">注销后账户将无法登录，对话与文件会被永久删除，该操作不可撤销。</template>
          <template v-else>当前账户可用的模型。</template>
        </DialogDescription>
      </DialogHeader>

      <div v-if="mode === 'models'" class="max-h-80 space-y-2 overflow-y-auto">
        <div v-for="model in models" :key="model.id" class="flex items-center justify-between rounded-xl border px-3 py-2">
          <div>
            <p class="text-sm font-medium">{{ model.name }}</p>
            <p class="text-xs text-muted-foreground">{{ model.provider }} · {{ model.model }}</p>
          </div>
          <div class="flex gap-1">
            <span v-if="model.supportsVision" class="rounded bg-muted px-1.5 py-0.5 text-[10px]">图片</span>
            <span v-if="model.supportsTools" class="rounded bg-muted px-1.5 py-0.5 text-[10px]">工具</span>
          </div>
        </div>
      </div>

      <template v-else-if="mode === 'twofa'">
        <div v-if="!twoFactorEnabled && setup" class="space-y-4">
          <div class="flex justify-center">
            <QrCode :value="setup.otpauthUri" />
          </div>
          <div class="rounded-xl border bg-muted/40 p-3">
            <p class="text-xs text-muted-foreground">无法扫码？手动输入密钥</p>
            <div class="mt-1 flex items-center gap-2">
              <code class="min-w-0 flex-1 break-all text-sm">{{ setup.secret }}</code>
              <Button variant="ghost" size="icon-sm" @click="copy(setup.secret)"><IconCopy class="size-4" /></Button>
            </div>
            <Button variant="ghost" size="sm" class="mt-2 h-7 px-0 text-xs text-muted-foreground" @click="copy(setup.otpauthUri)">
              <IconCopy class="mr-1 size-3.5" />复制 otpauth 链接
            </Button>
          </div>
          <div class="grid gap-2">
            <Label>输入客户端生成的 6 位验证码</Label>
            <Input v-model="form.code" maxlength="6" inputmode="numeric" autocomplete="one-time-code" placeholder="000000" class="text-center text-lg tracking-[0.4em]" />
          </div>
        </div>
        <div v-else class="grid gap-2">
          <Label>输入当前验证码以关闭两步验证</Label>
          <Input v-model="form.code" maxlength="6" inputmode="numeric" placeholder="000000" />
        </div>
      </template>

      <template v-else-if="mode === 'password'">
        <div class="grid gap-3">
          <div v-if="hasPassword" class="grid gap-2">
            <Label>当前密码</Label>
            <Input v-model="form.oldPassword" type="password" autocomplete="current-password" />
          </div>
          <div class="grid gap-2">
            <Label>新密码</Label>
            <Input v-model="form.newPassword" type="password" autocomplete="new-password" placeholder="8-32 位，包含字母和数字" />
          </div>
          <div class="grid gap-2">
            <Label>确认新密码</Label>
            <Input v-model="form.confirmPassword" type="password" autocomplete="new-password" />
          </div>
          <div v-if="twoFactorEnabled" class="grid gap-2">
            <Label>两步验证码</Label>
            <Input v-model="form.code" maxlength="6" inputmode="numeric" placeholder="000000" />
          </div>
        </div>
      </template>

      <template v-else-if="mode === 'email'">
        <div class="grid gap-3">
          <div class="grid gap-2">
            <Label>新邮箱</Label>
            <div class="flex gap-2">
              <Input v-model="form.email" type="email" placeholder="name@example.com" />
              <Button variant="outline" class="shrink-0" :disabled="sendingEmailCode || emailCountdown > 0" @click="sendEmailCode">
                <Spinner v-if="sendingEmailCode" class="mr-1 size-3.5" />
                {{ emailCountdown > 0 ? `${emailCountdown}s` : '发送验证码' }}
              </Button>
            </div>
            <p class="text-xs text-muted-foreground">验证码会发送到新邮箱，验证通过后才会更新。</p>
          </div>
          <div class="grid gap-2">
            <Label>邮箱验证码</Label>
            <Input v-model="form.code" maxlength="6" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位验证码" class="text-center tracking-[0.4em]" />
          </div>
          <div v-if="twoFactorEnabled" class="grid gap-2">
            <Label>两步验证码</Label>
            <Input v-model="form.twoFactorCode" maxlength="6" inputmode="numeric" placeholder="000000" />
          </div>
        </div>
      </template>

      <template v-else>
        <div class="space-y-3">
          <p class="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-xs text-destructive">
            账户注销后将无法登录，已归档对话和文件将被永久删除，且无法恢复。
          </p>
          <div v-if="hasPassword" class="grid gap-2">
            <Label>当前密码</Label>
            <Input v-model="form.oldPassword" type="password" />
          </div>
          <div v-if="twoFactorEnabled" class="grid gap-2">
            <Label>两步验证码</Label>
            <Input v-model="form.code" maxlength="6" inputmode="numeric" placeholder="000000" />
          </div>
        </div>
      </template>

      <p v-if="error" class="rounded-xl bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ error }}</p>

      <DialogFooter>
        <Button variant="outline" @click="close">取消</Button>
        <Button v-if="mode === 'password'" :disabled="saving" @click="savePassword">
          <Spinner v-if="saving" class="mr-2" />保存
        </Button>
        <Button v-else-if="mode === 'twofa'" :disabled="saving" @click="saveTwoFactor">
          <Spinner v-if="saving" class="mr-2" />{{ twoFactorEnabled ? '关闭' : '开启' }}
        </Button>
        <Button v-else-if="mode === 'email'" :disabled="saving" @click="saveEmail">
          <Spinner v-if="saving" class="mr-2" />保存
        </Button>
        <Button v-else-if="mode === 'delete'" variant="destructive" :disabled="saving" @click="deleteAccount">
          <Spinner v-if="saving" class="mr-2" />确认注销
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
