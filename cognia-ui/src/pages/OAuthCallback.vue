<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { Spinner } from '@/components/ui/spinner'
import { useUserStore } from '@/stores/user'
import { oauthApi } from '@/api/oauth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const error = ref('')

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
    userStore.applyTokens(result.accessToken, result.refreshToken)
    userStore.setProfile(result.user)
    userStore.initialized = true
    toast.success('登录成功')
    await router.replace('/chat')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '第三方登录失败'
  }
})
</script>

<template>
  <div class="flex min-h-svh items-center justify-center p-6 text-center">
    <div v-if="!error" class="flex flex-col items-center gap-3">
      <Spinner class="size-6" />
      <p class="text-sm text-muted-foreground">正在完成登录…</p>
    </div>
    <div v-else class="flex flex-col items-center gap-3">
      <p class="text-sm text-destructive">{{ error }}</p>
      <RouterLink to="/sign/sign-in" class="text-sm underline">返回登录</RouterLink>
    </div>
  </div>
</template>
