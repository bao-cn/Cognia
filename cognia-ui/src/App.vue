<template>
  <div :class="isStandalonePage ? 'min-h-dvh' : 'h-dvh overflow-hidden'">
    <router-view />
    <Toaster />
    <GlobalSearch />
    <TraceDrawer />

    <!-- Global re-consent dialog: cannot be dismissed until the user agrees
         or explicitly declines (which logs out and closes the window). -->
    <Dialog :open="showConsent">
      <DialogContent
        class="sm:max-w-2xl"
        :show-close-button="false"
        @escape-key-down.prevent
        @pointer-down-outside.prevent
      >
        <DialogHeader>
          <DialogTitle>隐私政策与用户协议</DialogTitle>
          <DialogDescription>
            <template v-if="legalStore.isRevision">
              相关文档已修订，请重新阅读并同意后继续使用 {{ siteStore.siteName }}。
            </template>
            <template v-else>
              首次使用前，请阅读并同意以下文档。
            </template>
          </DialogDescription>
        </DialogHeader>

        <div v-if="legalStore.loading" class="flex h-40 items-center justify-center">
          <Spinner class="size-6" />
        </div>

        <template v-else-if="legalStore.status">
          <div class="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <Badge variant="outline">隐私政策 v{{ legalStore.privacy?.version }}</Badge>
            <Badge variant="outline">用户协议 v{{ legalStore.terms?.version }}</Badge>
            <span v-if="legalStore.privacy?.updatedAt">最近更新：{{ legalStore.privacy.updatedAt }}</span>
          </div>

          <Tabs default-value="PRIVACY" class="gap-3">
            <TabsList class="grid w-full grid-cols-2">
              <TabsTrigger value="PRIVACY">隐私政策</TabsTrigger>
              <TabsTrigger value="TERMS">用户协议</TabsTrigger>
            </TabsList>
            <TabsContent value="PRIVACY">
              <div class="h-[38vh] overflow-y-auto rounded-lg border bg-muted/20 p-4 text-sm">
                <Markdown :text="legalStore.privacy?.content ?? ''" />
              </div>
            </TabsContent>
            <TabsContent value="TERMS">
              <div class="h-[38vh] overflow-y-auto rounded-lg border bg-muted/20 p-4 text-sm">
                <Markdown :text="legalStore.terms?.content ?? ''" />
              </div>
            </TabsContent>
          </Tabs>

          <div class="flex items-start gap-2 rounded-lg border p-3">
            <Checkbox id="legal-consent" v-model="consentChecked" class="mt-0.5" />
            <label for="legal-consent" class="cursor-pointer text-sm leading-5">
              我已阅读并同意《隐私政策》和《用户协议》，并理解不同意将无法继续使用本服务。
            </label>
          </div>
        </template>

        <DialogFooter>
          <Button variant="outline" :disabled="legalStore.submitting" @click="declineConsent">
            不同意并退出
          </Button>
          <Button
            :disabled="!consentChecked || legalStore.submitting || !legalStore.status"
            @click="agreeConsent"
          >
            <Spinner v-if="legalStore.submitting" class="mr-2" />
            同意并继续
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import 'vue-sonner/style.css'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { Toaster } from '@/components/ui/sonner'
import GlobalSearch from '@/components/GlobalSearch.vue'
import TraceDrawer from '@/components/TraceDrawer.vue'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Badge } from '@/components/ui/badge'
import { Spinner } from '@/components/ui/spinner'
import Markdown from '@/components/Markdown.vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs'
import { useConfigStore } from '@/stores/config'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import { useLegalStore } from '@/stores/legal'
import { useNotificationStore } from '@/stores/notification'
import { useDebugStore } from '@/stores/debug'
import i18n from '@/locales/index'

const router = useRouter()
const route = useRoute()
const configStore = useConfigStore()
const userStore = useUserStore()
const siteStore = useSiteStore()
const legalStore = useLegalStore()
const notificationStore = useNotificationStore()
const debugStore = useDebugStore()
const isStandalonePage = computed(() => Boolean(route.meta.standalone))

const consentChecked = ref(false)
const showConsent = computed(() => userStore.isLoggedIn && legalStore.requiresConsent)
let consentTimer: ReturnType<typeof setInterval> | null = null

watch(showConsent, (visible) => {
  if (visible) {
    consentChecked.value = false
  }
})

watch(
  () => userStore.isLoggedIn,
  async (loggedIn) => {
    if (loggedIn) {
      await legalStore.ensureStatus(true)
      notificationStore.start()
      void debugStore.load()
    } else {
      legalStore.reset()
      notificationStore.stop()
    }
  },
)

onMounted(async () => {
  configStore.applyTheme()

  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    configStore.applyTheme()
  })

  i18n.global.locale.value = configStore.actualLanguage
  document.documentElement.lang = configStore.actualLanguage === 'enUs' ? 'en' : 'zh-CN'

  await siteStore.load()
  if (userStore.isLoggedIn) {
    await legalStore.ensureStatus(true)
    notificationStore.start()
    void debugStore.load()
  }

  // Revisions are picked up while the app stays open (at most one minute lag).
  consentTimer = setInterval(() => {
    if (userStore.isLoggedIn && !legalStore.requiresConsent && !legalStore.loading) {
      void legalStore.ensureStatus(true)
    }
  }, 60_000)
  window.addEventListener('focus', refreshConsentStatus)
})

onUnmounted(() => {
  if (consentTimer) {
    clearInterval(consentTimer)
    consentTimer = null
  }
  window.removeEventListener('focus', refreshConsentStatus)
  notificationStore.stop()
})

function refreshConsentStatus(): void {
  if (userStore.isLoggedIn && !legalStore.requiresConsent && !legalStore.loading) {
    void legalStore.ensureStatus(true)
  }
}

async function agreeConsent(): Promise<void> {
  if (!consentChecked.value) return
  try {
    await legalStore.agree()
    consentChecked.value = false
    toast.success('感谢您的确认')
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '提交失败，请稍后重试')
  }
}

async function declineConsent(): Promise<void> {
  await userStore.logout()
  legalStore.reset()
  toast.info('您已退出登录')
  // Browser policy only allows closing tabs opened by script; when blocked we
  // fall back to the sign-in page after logging the user out.
  window.close()
  await router.replace('/sign')
}
</script>
