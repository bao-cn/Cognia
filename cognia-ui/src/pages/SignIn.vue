<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { computed, onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import { toast } from "vue-sonner"
import { cn } from "cn"
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

const errors = reactive<{ account?: string; password?: string }>({})
const submitting = ref(false)
const turnstileToken = ref('')
const turnstileError = ref('')
const turnstileRef = ref<InstanceType<typeof TurnstileWidget> | null>(null)
const twoFactorRequired = ref(false)
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

const canSubmit = computed(() => Boolean(form.account.trim())
  && Boolean(form.password)
  && (!twoFactorRequired.value || form.twoFactorCode.length === 6)
  && !submitting.value
  && (!loginCaptchaRequired.value || Boolean(turnstileToken.value)))

function validate(): boolean {
  errors.account = form.account.trim() ? undefined : '请输入用户名或邮箱'
  errors.password = form.password ? undefined : '请输入密码'
  return !errors.account && !errors.password
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
    const user = await userStore.login({
      account: form.account.trim(),
      password: form.password,
      turnstileToken: loginCaptchaRequired.value ? turnstileToken.value : undefined,
      rememberMe: form.rememberMe,
      twoFactorCode: form.twoFactorCode || undefined,
    })
    toast.success(`欢迎回来，${user.nickname || user.username}`)
    await router.replace(resolveRedirect())
  } catch (error) {
    const message = error instanceof Error ? error.message : '登录失败，请稍后重试'
    if (message.includes('两步验证码')) twoFactorRequired.value = true
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
          登录 {{ siteStore.siteName }}，继续您的知识库之旅
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="handleSubmit">
          <FieldGroup class="gap-2">
            <Field v-if="oauthProviders.length">
              <div class="grid gap-2">
                <Button v-for="provider in oauthProviders" :key="provider.code" variant="outline" type="button" class="cursor-pointer" @click="startOAuth(provider)">
                  使用 {{ provider.name }} 登录
                </Button>
              </div>
            </Field>
            <FieldSeparator v-if="oauthProviders.length" class="*:data-[slot=field-separator-content]:bg-card">
              或
            </FieldSeparator>

            <Field :data-invalid="Boolean(errors.account)">
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
            </Field>
            <Field>
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
            <Field v-if="twoFactorRequired">
              <FieldLabel for="two-factor-code">两步验证码</FieldLabel>
              <Input id="two-factor-code" v-model="form.twoFactorCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="6 位验证码" />
              <FieldDescription>打开你的 TOTP 验证器并输入当前验证码。</FieldDescription>
            </Field>
            <Field orientation="horizontal" class="items-center">
              <Checkbox id="remember-me" v-model="form.rememberMe" />
              <FieldLabel for="remember-me" class="font-normal">
                30 天内记住我
              </FieldLabel>
            </Field>
            <div v-if="loginCaptchaRequired" class="flex flex-col gap-2 rounded-lg border bg-muted/20 p-3">
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
            <Field>
              <Button type="submit" :disabled="!canSubmit">
                <Spinner v-if="submitting" class="mr-2" />
                {{ submitting ? '登录中...' : '登录' }}
              </Button>
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
