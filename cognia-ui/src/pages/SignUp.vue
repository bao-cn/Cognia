<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { computed, onUnmounted, reactive, ref } from "vue"
import { useRouter } from "vue-router"
import { toast } from "vue-sonner"
import { cn } from "cn"
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

const form = reactive({
  username: '',
  nickname: '',
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
})

const errors = reactive<Record<string, string | undefined>>({})
const submitting = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const devCode = ref('')
const turnstileToken = ref('')
const turnstileError = ref('')
const turnstileRef = ref<InstanceType<typeof TurnstileWidget> | null>(null)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const turnstileRequired = computed(() => siteStore.emailVerificationRequired
  && siteStore.registerEmailCaptchaEnabled
  && siteStore.turnstileConfigured
  && Boolean(siteStore.turnstileSiteKey))

const canSubmit = computed(() => !submitting.value)

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

onUnmounted(stopCountdown)

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

function validateEmail(): boolean {
  if (!form.email.trim()) {
    errors.email = '请输入邮箱'
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = '邮箱格式不正确'
  } else {
    errors.email = undefined
  }
  return !errors.email
}

function validate(): boolean {
  errors.username = undefined
  errors.password = undefined
  errors.confirmPassword = undefined
  errors.code = undefined
  validateEmail()

  if (!/^[a-zA-Z][a-zA-Z0-9_]{2,31}$/.test(form.username.trim())) {
    errors.username = '用户名需以字母开头，只能包含字母、数字、下划线，长度 3-32'
  }
  if (siteStore.emailVerificationRequired && !/^\d{6}$/.test(form.code.trim())) {
    errors.code = '请输入 6 位数字验证码'
  } else {
    errors.code = undefined
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(form.password)) {
    errors.password = '密码长度需为 8-32 位，且同时包含字母和数字'
  }
  if (form.confirmPassword !== form.password) {
    errors.confirmPassword = '两次输入的密码不一致'
  }
  return !errors.username && !errors.email && !errors.code && !errors.password && !errors.confirmPassword
}

async function handleSendCode(): Promise<void> {
  if (!siteStore.registrationEnabled || !siteStore.emailVerificationRequired) return
  if (sendingCode.value || countdown.value > 0) return
  if (!validateEmail()) return
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

async function handleSubmit(): Promise<void> {
  if (!siteStore.registrationEnabled) {
    toast.error('注册功能已关闭，请联系管理员开通账户')
    return
  }
  if (!validate()) return
  submitting.value = true
  const username = form.username.trim()
  const password = form.password
  try {
    await userStore.register({
      username,
      nickname: form.nickname.trim() || undefined,
      email: form.email.trim(),
      password,
      code: siteStore.emailVerificationRequired ? form.code.trim() : undefined,
    })
    toast.success('注册成功，正在为您登录...')
    try {
      await userStore.login({ account: username, password })
      await router.replace('/chat')
    } catch {
      toast.warning('注册成功，请手动登录')
      await router.replace({ name: 'SignIn', query: { account: username } })
    }
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '注册失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div :class="cn('flex flex-col gap-6', props.class)">
    <Card>
      <CardHeader class="text-center">
        <CardTitle class="text-xl">
          创建您的 {{ siteStore.siteName }} 账户
        </CardTitle>
        <CardDescription>
          {{ siteStore.registrationEnabled ? '输入您的信息以创建账户' : '当前站点已关闭自助注册' }}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div v-if="!siteStore.registrationEnabled" class="flex flex-col gap-3 py-4 text-center">
          <p class="text-sm text-muted-foreground">注册功能已关闭，请联系管理员开通账户。</p>
          <Button as-child variant="outline">
            <router-link to="/sign">返回登录</router-link>
          </Button>
        </div>
        <form v-else @submit.prevent="handleSubmit">
          <FieldGroup class="gap-2">

            <Field>
              <FieldLabel for="username">
                用户名
              </FieldLabel>
              <Input
                id="username"
                v-model="form.username"
                type="text"
                autocomplete="username"
                placeholder="gabriel"
                :aria-invalid="Boolean(errors.username)"
                required
              />
              <FieldError :errors="[errors.username]" />
            </Field>
            <Field>
              <FieldLabel for="nickname">
                昵称（可选）
              </FieldLabel>
              <Input id="nickname" v-model="form.nickname" type="text" placeholder="Gabriel" />
            </Field>
            <Field>
              <FieldLabel for="email">
                邮箱
              </FieldLabel>
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
            <Field v-if="siteStore.emailVerificationRequired">
              <FieldLabel for="code">
                邮箱验证码
              </FieldLabel>
              <div v-if="turnstileRequired" class="flex flex-col gap-2 rounded-lg border bg-muted/20 p-3">
                <TurnstileWidget
                  ref="turnstileRef"
                  :site-key="siteStore.turnstileSiteKey"
                  @verified="handleTurnstileVerified"
                  @expired="handleTurnstileExpired"
                  @error="handleTurnstileError"
                />
                <p v-if="turnstileError" class="text-xs text-destructive">{{ turnstileError }}</p>
                <p v-else class="text-xs text-muted-foreground">
                  请先完成 Cloudflare 人机验证，验证通过后即可发送邮箱验证码。
                </p>
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
                  @click="handleSendCode"
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
            <p v-else class="text-xs text-muted-foreground">
              当前未开启邮箱验证，注册成功后可直接登录。
            </p>
            <Field>
              <Field class="grid grid-cols-2 gap-4">
                <Field>
                  <FieldLabel for="password">
                    密码
                  </FieldLabel>
                  <Input
                    id="password"
                    v-model="form.password"
                    type="password"
                    autocomplete="new-password"
                    :aria-invalid="Boolean(errors.password)"
                    required
                  />
                  <FieldError :errors="[errors.password]" />
                </Field>
                <Field>
                  <FieldLabel for="confirm-password">
                    确认密码
                  </FieldLabel>
                  <Input
                    id="confirm-password"
                    v-model="form.confirmPassword"
                    type="password"
                    autocomplete="new-password"
                    :aria-invalid="Boolean(errors.confirmPassword)"
                    required
                  />
                  <FieldError :errors="[errors.confirmPassword]" />
                </Field>
              </Field>
              <FieldDescription>
                不得少于 8 个字符，且同时包含字母和数字
              </FieldDescription>
            </Field>
            <Field>
              <Button type="submit" :disabled="!canSubmit">
                <Spinner v-if="submitting" class="mr-2" />
                {{ submitting ? '创建中...' : '创建账户' }}
              </Button>
              <FieldDescription class="text-center">
                已有账户?
                <router-link to="/sign" class="ml-1 underline-offset-4 hover:underline">
                  登录
                </router-link>
              </FieldDescription>
            </Field>
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
