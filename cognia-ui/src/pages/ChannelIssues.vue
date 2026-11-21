<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { profileApi, channelApi, issueApi } from '@/api'
import type { ChannelCard, ChannelDetail, IssueLabel, IssueSummary, PageResult, OwnerProfile } from '@/types/api'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import IssueLabelChip from '@/components/IssueLabelChip.vue'
import { issueLabelClass } from '@/lib/issue-colors'
import { IconPlus, IconMessageCircle, IconCircleDot, IconCircleCheck, IconChevronLeft } from '@tabler/icons-vue'

const props = defineProps<{ embedded?: boolean }>()
const route = useRoute()
const router = useRouter()
const owner = computed(() => String(route.params.owner ?? ''))
const slug = computed(() => String(route.params.channelSlug ?? ''))

const profile = ref<OwnerProfile | null>(null)
const channel = ref<ChannelCard | null>(null)
const detail = ref<ChannelDetail | null>(null)
const issues = ref<IssueSummary[]>([])
const result = ref<PageResult<IssueSummary> | null>(null)
const labels = ref<IssueLabel[]>([])
const status = ref<'ALL' | 'OPEN' | 'CLOSED'>('OPEN')
const labelId = ref<number | undefined>(undefined)
const page = ref(1)
const size = 20
const loading = ref(true)
const error = ref('')


async function resolveChannel() {
  const p = await profileApi.get(owner.value)
  profile.value = p
  const channels = await channelApi.list(p.type, p.id)
  channel.value = channels.find((item) => item.slug === slug.value) ?? null
  if (!channel.value) throw new Error('频道不存在')
  detail.value = await channelApi.detail(channel.value.id)
}

async function loadIssues() {
  if (!channel.value) return
  const res = await issueApi.list(Number(channel.value.id), {
    status: status.value, labelId: labelId.value, page: page.value, size,
  })
  result.value = res
  issues.value = res.records
}

async function loadLabels() {
  if (!channel.value) return
  labels.value = await issueApi.labels(Number(channel.value.id))
}

async function init() {
  loading.value = true
  error.value = ''
  try {
    await resolveChannel()
    await Promise.all([loadIssues(), loadLabels()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function setStatus(next: 'ALL' | 'OPEN' | 'CLOSED') {
  status.value = next
  page.value = 1
  await loadIssues()
}

async function filterLabel(id?: number) {
  labelId.value = id
  page.value = 1
  await loadIssues()
}

async function goPage(next: number) {
  const pages = result.value?.pages ?? 1
  page.value = Math.min(pages, Math.max(1, next))
  await loadIssues()
}

function openIssue(row: IssueSummary) {
  void router.push(`/${owner.value}/${slug.value}/issues/${row.number}`)
}

onMounted(init)
</script>

<template>
  <div :class="props.embedded ? 'flex flex-col gap-4' : 'mx-auto flex w-full max-w-6xl flex-col gap-4 p-4 md:p-6'">
    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>
    <div v-else-if="error" class="rounded-xl border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
      {{ error }}
      <Button variant="outline" size="sm" class="ml-3" @click="init">重新加载</Button>
    </div>
    <template v-else-if="channel">
      <div v-if="!props.embedded" class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex min-w-0 items-center gap-2">
          <Button variant="ghost" size="icon" class="size-8" @click="router.push(`/${owner}/${slug}`)">
            <IconChevronLeft class="size-4" />
          </Button>
          <div class="min-w-0">
            <h1 class="truncate text-xl font-semibold">{{ channel.name }} · 工单</h1>
            <p class="text-sm text-muted-foreground">公开频道的工单与讨论</p>
          </div>
        </div>
        <Button size="sm" @click="router.push(`/${owner}/${slug}/issues/new`)"><IconPlus class="mr-1 size-4" />新建工单</Button>
      </div>
      <div v-else class="flex justify-end">
        <Button size="sm" @click="router.push(`/${owner}/${slug}/issues/new`)"><IconPlus class="mr-1 size-4" />新建工单</Button>
      </div>

      <div class="overflow-hidden rounded-xl border bg-card">
        <div class="flex flex-wrap items-center gap-2 border-b bg-muted/30 p-3">
          <Button v-for="tab in ([['OPEN','打开'],['CLOSED','已关闭'],['ALL','全部']] as const)" :key="tab[0]"
                  :variant="status === tab[0] ? 'secondary' : 'ghost'" size="sm" class="h-8"
                  @click="setStatus(tab[0])">{{ tab[1] }}</Button>
          <div class="mx-2 h-4 w-px bg-border" />
          <Button variant="ghost" size="sm" class="h-8" :class="!labelId ? 'bg-muted' : ''" @click="filterLabel(undefined)">全部标签</Button>
          <button v-for="label in labels" :key="label.id" class="rounded-full border px-2 py-0.5 text-[11px]"
                  :class="issueLabelClass(label.color)" @click="filterLabel(label.id)">{{ label.name }}</button>
          <span class="ml-auto text-xs text-muted-foreground">{{ result?.total ?? 0 }} 个工单</span>
        </div>

        <div v-if="!issues.length" class="flex flex-col items-center gap-2 py-16 text-sm text-muted-foreground">
          <IconCircleDot class="size-8" />
          当前没有符合条件的工单
        </div>
        <div v-else class="divide-y">
          <button v-for="issue in issues" :key="issue.id" class="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-muted/40"
                  @click="openIssue(issue)">
            <IconCircleCheck v-if="issue.status === 'CLOSED'" class="mt-0.5 size-4 text-purple-500" />
            <IconCircleDot v-else class="mt-0.5 size-4 text-green-500" />
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <span class="truncate text-sm font-medium">{{ issue.title }}</span>
                <IssueLabelChip v-for="label in issue.labels" :key="label.id" :name="label.name" :color="label.color" />
              </div>
              <p class="mt-1 text-xs text-muted-foreground">
                #{{ issue.number }} · {{ issue.author.nickname }} 创建于 {{ issue.createTime }}
              </p>
            </div>
            <div class="flex shrink-0 items-center gap-1 text-xs text-muted-foreground">
              <IconMessageCircle class="size-4" />{{ issue.commentCount }}
            </div>
          </button>
        </div>
        <div v-if="result && result.pages > 1" class="flex items-center justify-end gap-2 border-t p-3">
          <Button variant="outline" size="sm" :disabled="page <= 1" @click="goPage(page - 1)">上一页</Button>
          <span class="text-xs text-muted-foreground">{{ page }} / {{ result.pages }}</span>
          <Button variant="outline" size="sm" :disabled="page >= result.pages" @click="goPage(page + 1)">下一页</Button>
        </div>
      </div>
    </template>

  </div>
</template>
