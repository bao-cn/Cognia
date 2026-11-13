<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { Spinner } from '@/components/ui/spinner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useUserStore } from '@/stores/user'
import { oauthApi, type OAuthCallbackResult } from '@/api/oauth'
import type { LoginResult } from '@/types/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const error = ref('')
const linkRequest = ref<OAuthCallbackResult | null>(null)
const linking = ref(false)

function finishLogin(login: LoginResult, message: string) {
  userStore.applyTokens(login.accessToken, login.refreshToken)
  userStore.setProfile(login.user)
  userStore.initialized = true
  toast.success(message)
  void router.replace('/chat')
}

onMounted(async () => {
  const provider = typeof route.query.provider === 'string' ? route.query.provider : ''
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  const state = typeof route.query.state === 'string' ? route.query.state : ''
  if (!provider || !code || !state) {
    error.value = '登录回调参数不完整'
    return
  }
  try {
    const result = await oauthApi.callback(provider, code, state)
    if (result.linked) {
      toast.success('第三方账号已关联')
      await router.replace('/settings')
      return
    }
    if (result.registrationRequired && result.registrationToken) {
      sessionStorage.setItem('cognia:oauth-registration', JSON.stringify({
        token: result.registrationToken,
        email: result.email || '',
        provider: result.provider || '',
        suggestions: result.usernameSuggestions || [],
      }))
      await router.replace('/sign/sign-up?oauth=1')
      return
    }
    if (result.linkRequired) {
      linkRequest.value = result
      return
    }
    if (result.login) finishLogin(result.login, '登录成功')
    else error.value = '第三方登录失败'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '第三方登录失败'
  }
})

async function confirmLink() {
  if (!linkRequest.value?.linkToken) return
  linking.value = true
  try {
    const result = await oauthApi.link(linkRequest.value.linkToken)
    if (!result.login) throw new Error('关联失败，请重试')
    linkRequest.value = null
    finishLogin(result.login, '账户已关联并登录')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '关联失败，请重试')
  } finally {
    linking.value = false
  }
}

function cancelLink() {
  linkRequest.value = null
  toast.info('已取消关联，请使用原有方式登录')
  void router.replace('/sign/sign-in')
}
</script>

<template>
  <div class="flex min-h-svh items-center justify-center p-6 text-center">
    <div v-if="!error && !linkRequest" class="flex flex-col items-center gap-3">
      <Spinner class="size-6" />
      <p class="text-sm text-muted-foreground">正在完成登录…</p>
    </div>
    <div v-else-if="error" class="flex flex-col items-center gap-3">
      <p class="text-sm text-destructive">{{ error }}</p>
      <RouterLink to="/sign/sign-in" class="text-sm underline">返回登录</RouterLink>
    </div>

    <Dialog :open="Boolean(linkRequest)" @update:open="value => { if (!value) cancelLink() }">
      <DialogContent class="sm:max-w-md" :show-close-button="false">
        <DialogHeader>
          <DialogTitle>关联现有账户</DialogTitle>
          <DialogDescription>
            第三方账户的邮箱 {{ linkRequest?.email }} 已在系统中注册。是否将
            {{ linkRequest?.provider }} 账户与现有账户关联？
          </DialogDescription>
        </DialogHeader>
        <p class="text-sm text-muted-foreground">
          关联后将直接登录现有账户，并保留原有数据与权限；取消则返回登录页。
        </p>
        <DialogFooter>
          <Button variant="outline" :disabled="linking" @click="cancelLink">不关联</Button>
          <Button :disabled="linking" @click="confirmLink">
            <Spinner v-if="linking" class="mr-2 size-4" />
            关联并登录
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
