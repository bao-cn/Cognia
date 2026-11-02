<template>
  <div class="min-h-svh bg-muted/20">
    <header class="border-b bg-background">
      <div class="mx-auto flex h-14 w-full max-w-4xl items-center gap-3 px-4">
        <router-link to="/" class="flex items-center gap-2 font-medium">
          <img :src="siteStore.logo" :alt="siteStore.siteName" class="size-8 rounded-lg" />
          <span>{{ siteStore.siteName }}</span>
        </router-link>
        <div class="flex-1"></div>
        <Button variant="ghost" size="sm" @click="goBack">
          返回
        </Button>
      </div>
    </header>

    <main class="mx-auto flex w-full max-w-4xl flex-col gap-4 px-4 py-8">
      <div v-if="loading" class="flex h-48 items-center justify-center">
        <Spinner class="size-6" />
      </div>
      <template v-else-if="document">
        <div class="flex flex-col gap-2">
          <div class="flex flex-wrap items-center gap-2">
            <h1 class="text-2xl font-semibold">{{ document.title }}</h1>
            <Badge variant="outline">v{{ document.version }}</Badge>
          </div>
          <p class="text-xs text-muted-foreground">
            <template v-if="document.updatedAt">最近更新：{{ document.updatedAt }}</template>
            <template v-else-if="document.publishedAt">发布于：{{ document.publishedAt }}</template>
          </p>
        </div>
        <article class="min-h-[50vh] rounded-xl border bg-background p-6 text-sm">
          <Markdown :text="document.content" />
        </article>
        <div class="flex flex-wrap items-center justify-between gap-3 text-sm">
          <div v-if="otherDocuments.length" class="flex flex-wrap gap-x-4 gap-y-1">
            <router-link
              v-for="other in otherDocuments"
              :key="other.type"
              :to="legalPath(other.type)"
              class="text-primary underline-offset-4 hover:underline"
            >
              查看《{{ other.title }}》
            </router-link>
          </div>
          <span v-else></span>
          <router-link to="/" class="text-muted-foreground underline-offset-4 hover:underline">
            返回 {{ siteStore.siteName }}
          </router-link>
        </div>
      </template>
      <div v-else class="flex flex-col items-center gap-3 py-16">
        <p class="text-sm text-muted-foreground">文档不存在或尚未发布。</p>
        <Button variant="outline" @click="goBack">返回</Button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { legalApi } from '@/api'
import type { LegalDocument, LegalDocType } from '@/types/api'
import { useSiteStore } from '@/stores/site'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import Markdown from '@/components/Markdown.vue'

const route = useRoute()
const router = useRouter()
const siteStore = useSiteStore()

const loading = ref(true)
const documents = ref<LegalDocument[]>([])

const docType = computed<LegalDocType>(() => (route.meta.docType as LegalDocType | undefined) ?? 'PRIVACY')
const document = computed(() => documents.value.find(item => item.type === docType.value) ?? null)
const otherDocuments = computed(() => documents.value.filter(item => item.type !== docType.value))

const legalPaths: Record<LegalDocType, string> = {
  PRIVACY: '/legal/privacy',
  TERMS: '/legal/terms',
  OPEN_SOURCE: '/legal/licenses',
}

function legalPath(type: LegalDocType): string {
  return legalPaths[type]
}

function goBack(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.push('/')
  }
}

onMounted(async () => {
  try {
    documents.value = await legalApi.documents()
  } catch {
    documents.value = []
  } finally {
    loading.value = false
  }
})
</script>
