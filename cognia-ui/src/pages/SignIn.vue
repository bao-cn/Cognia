<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { computed, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import { toast } from "vue-sonner"
import { cn } from "cn"
import { useUserStore } from "@/stores/user"
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
})

const errors = reactive<{ account?: string; password?: string }>({})
const submitting = ref(false)
const turnstileToken = ref('')
const turnstileError = ref('')
const turnstileRef = ref<InstanceType<typeof TurnstileWidget> | null>(null)

const loginCaptchaRequired = computed(() => siteStore.loginCaptchaEnabled
  && siteStore.turnstileConfigured
  && Boolean(siteStore.turnstileSiteKey))

const canSubmit = computed(() => Boolean(form.account.trim())
  && Boolean(form.password)
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
    })
    toast.success(`欢迎回来，${user.nickname || user.username}`)
    await router.replace(resolveRedirect())
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '登录失败，请稍后重试')
    // A Turnstile token is single-use; require a fresh challenge on retry.
    resetTurnstile()
  } finally {
    submitting.value = false
  }
}

function notImplemented(provider: string): void {
  toast.info(`${provider} 登录尚未配置，请使用账号密码登录`)
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
            <Field v-if="siteStore.appleLoginEnabled || siteStore.googleLoginEnabled">
              <Button
                v-if="siteStore.appleLoginEnabled"
                variant="outline"
                type="button"
                class="cursor-pointer"
                @click="notImplemented('Apple')"
              >
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                  <path
                    d="M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701"
                    fill="currentColor" />
                </svg>
                使用 Apple 登录
              </Button>
              <Button
                v-if="siteStore.googleLoginEnabled"
                variant="outline"
                type="button"
                class="cursor-pointer"
                @click="notImplemented('Google')"
              >
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                  <path
                    d="M12.48 10.92v3.28h7.84c-.24 1.84-.853 3.187-1.787 4.133-1.147 1.147-2.933 2.4-6.053 2.4-4.827 0-8.6-3.893-8.6-8.72s3.773-8.72 8.6-8.72c2.6 0 4.507 1.027 5.907 2.347l2.307-2.307C18.747 1.44 16.133 0 12.48 0 5.867 0 .307 5.387.307 12s5.56 12 12.173 12c3.573 0 6.267-1.173 8.373-3.36 2.16-2.16 2.84-5.213 2.84-7.667 0-.76-.053-1.467-.173-2.053H12.48z"
                    fill="currentColor" />
                </svg>
                使用 Google 登录
              </Button>
            </Field>
            <FieldSeparator
              v-if="siteStore.appleLoginEnabled || siteStore.googleLoginEnabled"
              class="*:data-[slot=field-separator-content]:bg-card"
            >
              Or
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
