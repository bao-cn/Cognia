<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { computed, onMounted, onUnmounted, reactive, ref } from "vue"
import { useRouter } from "vue-router"
import { toast } from "vue-sonner"
import { cn } from "cn"
import { authApi } from "@/api"
import { oauthApi, type PublicOAuthProvider } from "@/api/oauth"
import { useUserStore } from "@/stores/user"
import { useSiteStore } from "@/stores/site"
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldSeparator,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import TurnstileWidget from '@/components/TurnstileWidget.vue'

const props = defineProps<{
  class?: HTMLAttributes["class"]
}>()

const router = useRouter()
const userStore = useUserStore()
const siteStore = useSiteStore()

const step = ref<1 | 2 | 3>(1)
const form = reactive({
  email: '',
  password: '',
  code: '',
  username: '',
})
const errors = reactive<Record<string, string | undefined>>({})
const emailChecking = ref(false)
const usernameChecking = ref(false)
const submitting = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const devCode = ref('')
const turnstileToken = ref('')
const turnstileError = ref('')
const turnstileRef = ref<InstanceType<typeof TurnstileWidget> | null>(null)
const suggestions = ref<string[]>([])
const oauthProviders = ref<PublicOAuthProvider[]>([])
const oauthRegistration = ref<{ token: string; email: string; provider: string; suggestions: string[] } | null>(null)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const turnstileRequired = computed(() => siteStore.emailVerificationRequired
  && siteStore.registerEmailCaptchaEnabled
  && siteStore.turnstileConfigured
  && Boolean(siteStore.turnstileSiteKey))

const emailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()))
const passwordState = computed(() => {
  const value = form.password
  const valid = /^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(value)
  if (!valid) {
    return { level: 'invalid', text: '密码需为 8-32 位，且同时包含字母和数字', className: 'text-red-500' }
  }
  const strong = value.length >= 12 && /[a-z]/.test(value) && /[A-Z]/.test(value) && /\d/.test(value)
  return strong
    ? { level: 'strong', text: '高强度密码', className: 'text-green-600 dark:text-green-400' }
    : { level: 'weak', text: '低强度密码，建议增加长度或混合大小写', className: 'text-amber-600 dark:text-amber-400' }
})

const stepTitle = computed(() => {
  if (oauthRegistration.value) return '选择唯一标识'
  return step.value === 1 ? '输入邮箱' : step.value === 2 ? '设置密码' : '选择唯一标识'
})

function stopCountdown(): void {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function startCountdown(seconds: number): void {
  stopCountdown()
  countdown.value = Math.max(1, seconds)
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      stopCountdown()
    }
  }, 1000)
}

onUnmounted(() => {
  stopCountdown()
  sessionStorage.removeItem('cognia:oauth-registration')
})

function handleTurnstileVerified(token: string): void {
  turnstileToken.value = token
  turnstileError.value = ''
}

function handleTurnstileExpired(): void {
  turnstileToken.value = ''
}

function handleTurnstileError(message: string): void {
  turnstileToken.value = ''
  turnstileError.value = message
}

async function nextFromEmail(): Promise<void> {
  errors.email = undefined
  if (!form.email.trim()) {
    errors.email = '请输入邮箱'
    return
  }
  if (!emailValid.value) {
    errors.email = '邮箱格式不正确'
    return
  }
  emailChecking.value = true
  try {
    const result = await authApi.checkEmail(form.email.trim())
    if (!result.available) {
      errors.email = result.message || '该邮箱不可用'
      return
    }
    step.value = 2
  } catch (error) {
    errors.email = error instanceof Error ? error.message : '邮箱检查失败'
  } finally {
    emailChecking.value = false
  }
}

async function sendCode(): Promise<void> {
  if (sendingCode.value || countdown.value > 0) return
  if (turnstileRequired.value && !turnstileToken.value) {
    toast.warning('请先完成人机验证')
    return
  }
  sendingCode.value = true
  try {
    const result = await userStore.sendRegisterCode(
      form.email.trim(),
      turnstileRequired.value ? turnstileToken.value : undefined,
    )
    startCountdown(result.resendAfterSeconds || 60)
    toast.success(result.message || '验证码已发送，请查收邮箱')
    if (result.devCode) {
      devCode.value = result.devCode
      toast.info(`开发环境验证码：${result.devCode}`, { duration: 15000 })
    }
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '验证码发送失败')
  } finally {
    sendingCode.value = false
    turnstileToken.value = ''
    turnstileRef.value?.reset()
  }
}

async function nextFromPassword(): Promise<void> {
  errors.password = undefined
  errors.code = undefined
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(form.password)) {
    errors.password = '密码需为 8-32 位，且同时包含字母和数字'
    return
  }
  if (siteStore.emailVerificationRequired && !/^\d{6}$/.test(form.code.trim())) {
    errors.code = '请输入 6 位数字验证码'
    return
  }
  step.value = 3
  await loadSuggestions()
}

async function loadSuggestions(): Promise<void> {
  const base = form.email.trim().split('@')[0] || 'user'
  try {
    suggestions.value = await authApi.usernameSuggestions(base)
  } catch {
    suggestions.value = []
  }
}

function useSuggestion(value: string): void {
  form.username = value
  errors.username = undefined
}

async function ensureUsernameAvailable(): Promise<boolean> {
  const value = form.username.trim()
  if (!/^[a-zA-Z][a-zA-Z0-9_]{2,31}$/.test(value)) {
    errors.username = '唯一标识需以字母开头，只能包含字母、数字、下划线，长度 3-32'
    return false
  }
  usernameChecking.value = true
  try {
    const result = await authApi.checkUsername(value)
    if (!result.available) {
      errors.username = result.message || '该唯一标识已被占用'
      suggestions.value = await authApi.usernameSuggestions(value)
      return false
    }
    return true
  } catch (error) {
    errors.username = error instanceof Error ? error.message : '唯一标识检查失败'
    return false
  } finally {
    usernameChecking.value = false
  }
}

async function finishRegistration(): Promise<void> {
  if (submitting.value) return
  if (!await ensureUsernameAvailable()) return
  submitting.value = true
  try {
    if (oauthRegistration.value) {
      const result = await oauthApi.completeRegistration(oauthRegistration.value.token, form.username.trim())
      if (!result.login) throw new Error('注册失败，请重试')
      userStore.applyTokens(result.login.accessToken, result.login.refreshToken)
      userStore.setProfile(result.login.user)
      userStore.initialized = true
      sessionStorage.removeItem('cognia:oauth-registration')
      toast.success('注册成功，正在为您登录...')
      await router.replace('/chat')
      return
    }
    await userStore.register({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
      code: siteStore.emailVerificationRequired ? form.code.trim() : undefined,
    })
    toast.success('注册成功，正在为您登录...')
    try {
      await userStore.login({ account: form.username.trim(), password: form.password })
      await router.replace('/chat')
    } catch {
      toast.warning('注册成功，请手动登录')
      await router.replace({ name: 'SignIn', query: { account: form.username.trim() } })
    }
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '注册失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function startOAuthRegister(provider: PublicOAuthProvider): Promise<void> {
  try {
    const redirectUri = `${window.location.origin}/oauth/callback?provider=${provider.code}`
    const url = await oauthApi.authorizeUrl(provider.code, redirectUri, 'register')
    window.location.href = url
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '无法发起第三方注册')
  }
}

onMounted(async () => {
  try {
    oauthProviders.value = await oauthApi.providers()
  } catch {
    oauthProviders.value = []
  }
  const raw = sessionStorage.getItem('cognia:oauth-registration')
  if (raw) {
    try {
      oauthRegistration.value = JSON.parse(raw)
      step.value = 3
      suggestions.value = oauthRegistration.value?.suggestions || []
      form.username = suggestions.value[0] || ''
    } catch {
      sessionStorage.removeItem('cognia:oauth-registration')
    }
  }
})
</script>

<template>
  <div :class="cn('flex flex-col gap-6', props.class)">
    <Card>
      <CardHeader class="text-center">
        <CardTitle class="text-xl">
          创建您的 {{ siteStore.siteName }} 账户
        </CardTitle>
        <CardDescription>
          {{ siteStore.registrationEnabled ? (oauthRegistration ? '使用第三方账号完成注册' : `第 ${step} 步，共 3 步 · ${stepTitle}`) : '当前站点已关闭自助注册' }}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div v-if="!siteStore.registrationEnabled" class="flex flex-col gap-3 py-4 text-center">
          <p class="text-sm text-muted-foreground">注册功能已关闭，请联系管理员开通账户。</p>
          <Button as-child variant="outline">
            <router-link to="/sign">返回登录</router-link>
          </Button>
        </div>
        <form v-else @submit.prevent="step === 1 ? nextFromEmail() : step === 2 ? nextFromPassword() : finishRegistration()">
          <FieldGroup class="gap-4">
            <template v-if="step === 1">
              <Field>
                <FieldLabel for="email">邮箱</FieldLabel>
                <Input
                  id="email"
                  v-model="form.email"
                  type="email"
                  autocomplete="email"
                  placeholder="m@example.com"
                  :aria-invalid="Boolean(errors.email)"
                  required
                />
                <FieldError :errors="[errors.email]" />
                <FieldDescription v-if="siteStore.registrationEmailWhitelistEnabled">
                  仅允许指定邮箱或域名注册。
                </FieldDescription>
              </Field>
              <Field>
                <Button type="submit" class="w-full" :disabled="emailChecking">
                  <Spinner v-if="emailChecking" class="mr-2" />
                  {{ emailChecking ? '检查中...' : '下一步' }}
                </Button>
                <FieldDescription class="text-center">
                  已有账户?
                  <router-link to="/sign" class="ml-1 underline-offset-4 hover:underline">登录</router-link>
                </FieldDescription>
              </Field>
              <template v-if="oauthProviders.length">
                <FieldSeparator class="bg-card">或使用第三方账号注册</FieldSeparator>
                <div class="grid gap-2">
                  <Button
                    v-for="provider in oauthProviders"
                    :key="provider.code"
                    variant="outline"
                    type="button"
                    @click="startOAuthRegister(provider)"
                  >
                    使用 {{ provider.name }} 注册
                  </Button>
                </div>
              </template>
            </template>

            <template v-else-if="step === 2">
              <Field>
                <FieldLabel for="password">密码</FieldLabel>
                <Input
                  id="password"
                  v-model="form.password"
                  type="password"
                  autocomplete="new-password"
                  :aria-invalid="passwordState.level === 'invalid'"
                  required
                />
                <p class="text-xs" :class="passwordState.className">{{ passwordState.text }}</p>
                <FieldError :errors="[errors.password]" />
              </Field>
              <template v-if="siteStore.emailVerificationRequired">
                <Field>
                  <FieldLabel for="code">邮箱验证码</FieldLabel>
                  <div v-if="turnstileRequired" class="flex flex-col gap-2 rounded-lg border bg-muted/20 p-3">
                    <TurnstileWidget
                      ref="turnstileRef"
                      :site-key="siteStore.turnstileSiteKey"
                      @verified="handleTurnstileVerified"
                      @expired="handleTurnstileExpired"
                      @error="handleTurnstileError"
                    />
                    <p v-if="turnstileError" class="text-xs text-destructive">{{ turnstileError }}</p>
                  </div>
                  <div class="flex gap-2">
                    <Input
                      id="code"
                      v-model="form.code"
                      inputmode="numeric"
                      maxlength="6"
                      placeholder="6 位数字"
                      class="flex-1"
                      :aria-invalid="Boolean(errors.code)"
                      required
                    />
                    <Button
                      type="button"
                      variant="outline"
                      class="w-32 shrink-0"
                      :disabled="sendingCode || countdown > 0 || (turnstileRequired && !turnstileToken)"
                      @click="sendCode"
                    >
                      <Spinner v-if="sendingCode" class="mr-2" />
                      {{ sendingCode ? '发送中' : countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
                    </Button>
                  </div>
                  <FieldError :errors="[errors.code]" />
                  <FieldDescription v-if="devCode">
                    开发环境验证码：<span class="font-mono font-semibold">{{ devCode }}</span>
                  </FieldDescription>
                </Field>
              </template>
              <Field>
                <Button type="submit" class="w-full">
                  下一步
                </Button>
                <Button type="button" variant="ghost" class="w-full" @click="step = 1">返回修改邮箱</Button>
              </Field>
            </template>

            <template v-else>
              <div v-if="oauthRegistration" class="rounded-lg border bg-muted/30 px-3 py-2 text-sm">
                <p>使用 {{ oauthRegistration.provider }} 注册</p>
                <p v-if="oauthRegistration.email" class="text-xs text-muted-foreground">{{ oauthRegistration.email }}</p>
                <p class="mt-1 text-xs text-muted-foreground">该第三方账号的用户标识已被占用，请选择一个唯一标识完成注册。</p>
              </div>
              <Field>
                <FieldLabel for="username">唯一标识</FieldLabel>
                <Input
                  id="username"
                  v-model="form.username"
                  type="text"
                  autocomplete="username"
                  placeholder="gabriel"
                  :aria-invalid="Boolean(errors.username)"
                  required
                  @input="errors.username = undefined"
                />
                <FieldError :errors="[errors.username]" />
                <FieldDescription>
                  您的个人主页地址为 /{{ form.username.trim() || 'username' }}，创建后不可修改。
                </FieldDescription>
              </Field>
              <Field v-if="suggestions.length">
                <FieldLabel>推荐标识</FieldLabel>
                <div class="flex flex-wrap gap-2">
                  <Button
                    v-for="suggestion in suggestions"
                    :key="suggestion"
                    type="button"
                    variant="outline"
                    size="sm"
                    class="rounded-full"
                    @click="useSuggestion(suggestion)"
                  >
                    {{ suggestion }}
                  </Button>
                </div>
              </Field>
              <Field>
                <Button type="submit" class="w-full" :disabled="submitting || usernameChecking">
                  <Spinner v-if="submitting || usernameChecking" class="mr-2" />
                  {{ submitting ? '创建中...' : usernameChecking ? '检查中...' : '完成注册' }}
                </Button>
                <Button v-if="!oauthRegistration" type="button" variant="ghost" class="w-full" @click="step = 2">
                  返回修改密码
                </Button>
              </Field>
            </template>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
    <FieldDescription class="px-6 text-center">
      继续则代表您同意
      <router-link to="/legal/terms" class="underline underline-offset-4">用户协议</router-link>
      和
      <router-link to="/legal/privacy" class="underline underline-offset-4">隐私政策</router-link>.
    </FieldDescription>
  </div>
</template>
