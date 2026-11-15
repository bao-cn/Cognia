<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { computed, onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import { toast } from "vue-sonner"
import { cn } from "cn"
import { authApi } from '@/api'
import type { LoginOptions } from '@/types/api'
import { useUserStore } from "@/stores/user"
import { oauthApi, type PublicOAuthProvider } from '@/api/oauth'
import { useSiteStore } from "@/stores/site"
import TurnstileWidget from '@/components/TurnstileWidget.vue'
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
import { Checkbox } from '@/components/ui/checkbox'
import { Spinner } from '@/components/ui/spinner'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'

const props = defineProps<{
  class?: HTMLAttributes["class"]
}>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const siteStore = useSiteStore()

const form = reactive({
  account: typeof route.query.account === 'string' ? route.query.account : '',
  password: '',
  rememberMe: false,
  twoFactorCode: '',
})

const errors = reactive<{ account?: string; password?: string; twoFactorCode?: string }>({})
const loginStep = ref<1 | 2>(1)
const loginMode = ref<'password' | 'twofa'>('password')
const loginOptions = ref<LoginOptions | null>(null)
const checkingAccount = ref(false)
const submitting = ref(false)
const turnstileToken = ref('')
const turnstileError = ref('')
const turnstileRef = ref<InstanceType<typeof TurnstileWidget> | null>(null)

const oauthProviders = ref<PublicOAuthProvider[]>([])

onMounted(async () => {
  try { oauthProviders.value = await oauthApi.providers() } catch { oauthProviders.value = [] }
})

async function startOAuth(provider: PublicOAuthProvider): Promise<void> {
  try {
    const redirectUri = `${window.location.origin}/oauth/callback?provider=${provider.code}`
    const url = await oauthApi.authorizeUrl(provider.code, redirectUri)
    window.location.href = url
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '无法启动第三方登录')
  }
}

const loginCaptchaRequired = computed(() => siteStore.loginCaptchaEnabled
  && siteStore.turnstileConfigured
  && Boolean(siteStore.turnstileSiteKey))

const canSubmit = computed(() => {
  if (loginStep.value !== 2) return false
  if (loginMode.value === 'password' && !form.password) return false
  if (loginMode.value === 'twofa' && form.twoFactorCode.length !== 6) return false
  return !submitting.value && (!loginCaptchaRequired.value || Boolean(turnstileToken.value))
})

async function nextFromAccount(): Promise<void> {
  errors.account = undefined
  if (!form.account.trim()) {
    errors.account = '请输入用户名或邮箱'
    return
  }
  checkingAccount.value = true
  try {
    const options = await authApi.loginOptions(form.account.trim())
    loginOptions.value = options
    if (!options.hasPassword && !options.twoFactorEnabled) {
      errors.account = '该账户未设置密码，请使用第三方登录或联系管理员'
      return
    }
    loginMode.value = options.hasPassword ? 'password' : 'twofa'
    form.twoFactorCode = ''
    form.password = ''
    loginStep.value = 2
  } catch (error) {
    errors.account = error instanceof Error ? error.message : '无法检查账户登录方式'
  } finally {
    checkingAccount.value = false
  }
}

function backToAccount(): void {
  loginStep.value = 1
  loginMode.value = 'password'
  errors.password = undefined
  errors.twoFactorCode = undefined
  form.password = ''
  form.twoFactorCode = ''
}

function validate(): boolean {
  errors.password = undefined
  errors.twoFactorCode = undefined
  if (loginMode.value === 'password' && !form.password) {
    errors.password = '请输入密码'
    return false
  }
  if (loginMode.value === 'twofa' && !/^\d{6}$/.test(form.twoFactorCode)) {
    errors.twoFactorCode = '请输入 6 位验证码'
    return false
  }
  return true
}

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

function resetTurnstile(): void {
  turnstileToken.value = ''
  turnstileRef.value?.reset()
}

function resolveRedirect(): string {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/chat'
}

async function handleSubmit(): Promise<void> {
  if (!validate()) return
  if (loginCaptchaRequired.value && !turnstileToken.value) {
    toast.warning('请先完成人机验证')
    return
  }
  submitting.value = true
  try {
    const user = loginMode.value === 'twofa'
      ? await userStore.loginTwoFactor({
          account: form.account.trim(),
          code: form.twoFactorCode,
          turnstileToken: loginCaptchaRequired.value ? turnstileToken.value : undefined,
          rememberMe: form.rememberMe,
        })
      : await userStore.login({
          account: form.account.trim(),
          password: form.password,
          turnstileToken: loginCaptchaRequired.value ? turnstileToken.value : undefined,
          rememberMe: form.rememberMe,
        })
    toast.success(`欢迎回来，${user.nickname || user.username}`)
    await router.replace(resolveRedirect())
  } catch (error) {
    const message = error instanceof Error ? error.message : '登录失败，请稍后重试'
    toast.error(message)
    // A Turnstile token is single-use; require a fresh challenge on retry.
    resetTurnstile()
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
          欢迎回来
        </CardTitle>
        <CardDescription>
          登录 {{ siteStore.siteName }}，继续您的工作
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="handleSubmit">
          <FieldGroup class="gap-2">
            <Field v-if="loginStep === 1 && oauthProviders.length">
              <div class="grid gap-2">
                <Button v-for="provider in oauthProviders" :key="provider.code" variant="outline" type="button" class="cursor-pointer" @click="startOAuth(provider)">
                  使用 {{ provider.name }} 登录
                </Button>
              </div>
            </Field>
            <FieldSeparator v-if="loginStep === 1 && oauthProviders.length" class="*:data-[slot=field-separator-content]:bg-card">
              或
            </FieldSeparator>

            <Field v-if="loginStep === 1" :data-invalid="Boolean(errors.account)">
              <FieldLabel for="account">
                用户名或邮箱
              </FieldLabel>
              <Input
                id="account"
                v-model="form.account"
                type="text"
                autocomplete="username"
                placeholder="admin 或 m@example.com"
                :aria-invalid="Boolean(errors.account)"
                required
              />
              <FieldError :errors="[errors.account]" />
              <Button type="button" class="w-full" :disabled="checkingAccount" @click="nextFromAccount">
                <Spinner v-if="checkingAccount" class="mr-2" />
                {{ checkingAccount ? '检查中...' : '下一步' }}
              </Button>
            </Field>
            <Field v-if="loginStep === 2">
              <div class="flex items-center justify-between rounded-lg border bg-muted/30 px-3 py-2">
                <div class="min-w-0">
                  <p class="truncate text-sm font-medium">{{ form.account.trim() }}</p>
                  <p class="text-xs text-muted-foreground">选择登录方式</p>
                </div>
                <Button type="button" variant="ghost" size="sm" @click="backToAccount">更换账号</Button>
              </div>
              <div v-if="loginOptions?.twoFactorEnabled" class="grid grid-cols-2 gap-2">
                <Button type="button" :variant="loginMode === 'password' ? 'default' : 'outline'" size="sm" @click="loginMode = 'password'">密码登录</Button>
                <Button type="button" :variant="loginMode === 'twofa' ? 'default' : 'outline'" size="sm" @click="loginMode = 'twofa'">两步验证登录</Button>
              </div>
            </Field>
            <Field v-if="loginStep === 2 && loginMode === 'password'">
              <div class="flex items-center justify-between">
                <FieldLabel for="password">
                  密码
                </FieldLabel>

                <TooltipProvider class="ml-auto">
                  <Tooltip>
                    <TooltipTrigger as-child>
                      <a href="#" class="text-sm underline-offset-4 hover:underline" @click.prevent="toast.info('请联系管理员重置密码')">
                        忘记了您的密码?
                      </a>
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>您的组织禁用了该功能，请联系管理员</p>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              </div>
              <Input
                id="password"
                v-model="form.password"
                type="password"
                autocomplete="current-password"
                :aria-invalid="Boolean(errors.password)"
                required
              />
              <FieldError :errors="[errors.password]" />
            </Field>
            <Field v-if="loginStep === 2 && loginMode === 'twofa'">
              <FieldLabel for="two-factor-code">两步验证码</FieldLabel>
              <Input id="two-factor-code" v-model="form.twoFactorCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="6 位验证码" />
              <FieldError :errors="[errors.twoFactorCode]" />
              <FieldDescription>{{ loginMode === 'twofa' ? '输入验证器中的 6 位验证码完成登录。' : '该账户已开启两步验证，请输入当前验证码。' }}</FieldDescription>
            </Field>
            <Field v-if="loginStep === 2" orientation="horizontal" class="items-center">
              <Checkbox id="remember-me" v-model="form.rememberMe" />
              <FieldLabel for="remember-me" class="font-normal">
                30 天内记住我
              </FieldLabel>
            </Field>
            <div v-if="loginStep === 2 && loginCaptchaRequired" class="flex flex-col gap-2 rounded-lg border bg-muted/20 p-3">
              <TurnstileWidget
                ref="turnstileRef"
                :site-key="siteStore.turnstileSiteKey"
                action="login"
                @verified="handleTurnstileVerified"
                @expired="handleTurnstileExpired"
                @error="handleTurnstileError"
              />
              <p v-if="turnstileError" class="text-xs text-destructive">{{ turnstileError }}</p>
              <p v-else class="text-xs text-muted-foreground">
                请先完成 Cloudflare 人机验证后登录。
              </p>
            </div>
            <Field v-if="loginStep === 2">
              <Button type="submit" :disabled="!canSubmit">
                <Spinner v-if="submitting" class="mr-2" />
                {{ submitting ? '登录中...' : '登录' }}
              </Button>
              <Button type="button" variant="ghost" class="w-full" @click="backToAccount">返回上一步</Button>
              <FieldDescription v-if="siteStore.registrationEnabled" class="text-center">
                没有账户?
                <router-link to="/sign/sign-up" class="ml-1 underline-offset-4 hover:underline">
                  注册
                </router-link>
              </FieldDescription>
              <FieldDescription v-else class="text-center">
                注册功能已关闭，请联系管理员开通账户。
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
