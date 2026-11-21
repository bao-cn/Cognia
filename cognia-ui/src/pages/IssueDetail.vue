<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { profileApi, channelApi, issueApi } from '@/api'
import type { ChannelCard, ChannelDetail, IssueComment, IssueDetail, IssueLabel, IssueUser, OwnerProfile } from '@/types/api'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Spinner } from '@/components/ui/spinner'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import IssueLabelChip from '@/components/IssueLabelChip.vue'
import Markdown from '@/components/Markdown.vue'
import { IconArrowLeft, IconCircleCheck, IconCircleDot, IconMessageCircle, IconQuote, IconSend } from '@tabler/icons-vue'

const props = defineProps<{ embedded?: boolean }>()
const route = useRoute()
const router = useRouter()
const owner = computed(() => String(route.params.owner ?? ''))
const slug = computed(() => String(route.params.channelSlug ?? ''))
const number = computed(() => Number(route.params.number))

const profile = ref<OwnerProfile | null>(null)
const channel = ref<ChannelCard | null>(null)
const detail = ref<ChannelDetail | null>(null)
const issue = ref<IssueDetail | null>(null)
const labels = ref<IssueLabel[]>([])
const assignees = ref<IssueUser[]>([])
const loading = ref(true)
const error = ref('')
const body = ref('')
const quote = ref<IssueComment | null>(null)
const submitting = ref(false)
const canManage = computed(() => Boolean(detail.value?.canManage))

async function resolveChannel() {
  const p = await profileApi.get(owner.value)
  profile.value = p
  const channels = await channelApi.list(p.type, p.id)
  channel.value = channels.find((item) => item.slug === slug.value) ?? null
  if (!channel.value) throw new Error('频道不存在')
  detail.value = await channelApi.detail(channel.value.id)
}

async function loadIssue() {
  if (!channel.value) return
  issue.value = await issueApi.detail(Number(channel.value.id), number.value)
}

async function loadMeta() {
  if (!channel.value) return
  const [ls, users] = await Promise.all([
    issueApi.labels(Number(channel.value.id)),
    issueApi.assignees(Number(channel.value.id)),
  ])
  labels.value = ls
  assignees.value = users
}

async function init() {
  loading.value = true
  error.value = ''
  try {
    await resolveChannel()
    await Promise.all([loadIssue(), loadMeta()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  if (!issue.value || !channel.value) return
  if (!body.value.trim()) return toast.error('请输入评论内容')
  submitting.value = true
  try {
    await issueApi.comment(Number(channel.value.id), issue.value.number, {
      body: body.value.trim(), quoteCommentId: quote.value?.id ?? null,
    })
    body.value = ''
    quote.value = null
    await loadIssue()
    toast.success('评论已发送')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '发送失败')
  } finally {
    submitting.value = false
  }
}

function startQuote(comment: IssueComment) {
  quote.value = comment
  document.getElementById('issue-comment-box')?.focus()
}

async function toggleStatus() {
  if (!issue.value || !channel.value) return
  try {
    issue.value = await issueApi.setStatus(Number(channel.value.id), issue.value.number,
      issue.value.status === 'OPEN' ? 'CLOSED' : 'OPEN')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function updateAssignee(value: string) {
  if (!issue.value || !channel.value) return
  try {
    issue.value = await issueApi.setAssignee(Number(channel.value.id), issue.value.number,
      value === 'none' ? null : Number(value))
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '分配失败')
  }
}

async function toggleLabel(label: IssueLabel) {
  if (!issue.value || !channel.value) return
  const ids = issue.value.labels.map((item) => item.id)
  const next = ids.includes(label.id) ? ids.filter((id) => id !== label.id) : [...ids, label.id]
  try {
    issue.value = await issueApi.setLabels(Number(channel.value.id), issue.value.number, next)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '设置失败')
  }
}

onMounted(init)
</script>

<template>
  <div :class="props.embedded ? 'flex flex-col gap-4' : 'mx-auto flex w-full max-w-6xl flex-col gap-4 p-4 md:p-6'">
    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border"><Spinner class="size-6" /></div>
    <div v-else-if="error" class="rounded-xl border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
      {{ error }}<Button variant="outline" size="sm" class="ml-3" @click="init">重新加载</Button>
    </div>
    <template v-else-if="issue && channel">
      <div v-if="!props.embedded" class="flex items-center gap-2">
        <Button variant="ghost" size="icon" class="size-8" @click="router.push(`/${owner}/${slug}/issues`)">
          <IconArrowLeft class="size-4" />
        </Button>
        <span class="text-sm text-muted-foreground">{{ channel.name }}</span>
      </div>

      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <h1 class="text-2xl font-semibold">{{ issue.title }}</h1>
            <span class="text-xl text-muted-foreground">#{{ issue.number }}</span>
          </div>
          <div class="mt-2 flex flex-wrap items-center gap-2">
            <Badge :variant="issue.status === 'OPEN' ? 'default' : 'secondary'" class="gap-1">
              <IconCircleDot v-if="issue.status === 'OPEN'" class="size-3" />
              <IconCircleCheck v-else class="size-3" />
              {{ issue.status === 'OPEN' ? '打开' : '已关闭' }}
            </Badge>
            <IssueLabelChip v-for="label in issue.labels" :key="label.id" :name="label.name" :color="label.color" />
          </div>
        </div>
        <Button v-if="issue.canClose" variant="outline" size="sm" @click="toggleStatus">
          {{ issue.status === 'OPEN' ? '关闭工单' : '重新打开' }}
        </Button>
      </div>

      <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_280px]">
        <div class="flex flex-col gap-4">
          <article class="rounded-xl border bg-card">
            <div class="flex items-center gap-2 border-b bg-muted/30 px-4 py-3 text-sm">
              <span class="font-medium">{{ issue.author.nickname }}</span>
              <span class="text-muted-foreground">创建于 {{ issue.createTime }}</span>
            </div>
            <div class="px-4 py-4"><Markdown :text="issue.body" /></div>
          </article>

          <article v-for="comment in issue.comments" :key="comment.id" class="rounded-xl border bg-card">
            <div class="flex items-center gap-2 border-b bg-muted/30 px-4 py-2.5 text-sm">
              <span class="font-medium">{{ comment.author.nickname }}</span>
              <span class="text-muted-foreground">{{ comment.createTime }}</span>
              <Button variant="ghost" size="sm" class="ml-auto h-7 text-xs" @click="startQuote(comment)">
                <IconQuote class="mr-1 size-3.5" />引用
              </Button>
            </div>
            <div class="px-4 py-3">
              <blockquote v-if="comment.quoteBody" class="mb-3 rounded-r border-l-2 bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
                <span class="font-medium">{{ comment.quoteAuthor?.nickname }}</span>
                <div class="line-clamp-4 whitespace-pre-wrap">{{ comment.quoteBody }}</div>
              </blockquote>
              <Markdown :text="comment.body" />
            </div>
          </article>

          <div class="rounded-xl border bg-card">
            <div class="border-b px-4 py-3">
              <h2 class="text-sm font-semibold">添加评论</h2>
              <p class="text-xs text-muted-foreground">支持 Markdown；使用 @用户名 可提醒他人。</p>
            </div>
            <div v-if="issue.status === 'CLOSED'" class="p-4 text-sm text-muted-foreground">工单已关闭，无法继续评论。</div>
            <div v-else class="flex flex-col gap-3 p-4">
              <div v-if="quote" class="flex items-start gap-2 rounded-lg border bg-muted/40 p-2 text-xs">
                <IconQuote class="mt-0.5 size-3.5 shrink-0" />
                <div class="min-w-0 flex-1"><span class="font-medium">{{ quote.author.nickname }}</span><p class="line-clamp-2">{{ quote.body }}</p></div>
                <Button variant="ghost" size="sm" class="h-6" @click="quote = null">取消</Button>
              </div>
              <Textarea id="issue-comment-box" v-model="body" rows="6" placeholder="写下你的回复…" />
              <div class="flex justify-end"><Button :disabled="submitting" @click="submitComment">
                <Spinner v-if="submitting" class="mr-2" /><IconSend v-else class="mr-2 size-4" />评论
              </Button></div>
            </div>
          </div>
        </div>

        <aside class="flex h-fit flex-col gap-4 text-sm">
          <div class="rounded-xl border bg-card p-3">
            <h3 class="mb-2 text-xs font-medium text-muted-foreground">指派给</h3>
            <Select v-if="canManage" :model-value="issue.assignee?.id ? String(issue.assignee.id) : 'none'" @update:model-value="(value) => updateAssignee(String(value ?? 'none'))">
              <SelectTrigger class="h-9"><SelectValue placeholder="未指派" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="none">未指派</SelectItem>
                <SelectItem v-for="user in assignees" :key="user.id ?? user.username" :value="String(user.id)">{{ user.nickname }}</SelectItem>
              </SelectContent>
            </Select>
            <p v-else>{{ issue.assignee?.nickname ?? '未指派' }}</p>
          </div>
          <div v-if="canManage" class="rounded-xl border bg-card p-3">
            <h3 class="mb-2 text-xs font-medium text-muted-foreground">标签</h3>
            <div class="flex flex-col gap-1.5">
              <button v-for="label in labels" :key="label.id" class="flex items-center gap-2 rounded px-1 py-1 text-left hover:bg-muted"
                      @click="toggleLabel(label)">
                <span class="flex size-4 items-center justify-center rounded border" :class="issue.labels.some(l => l.id === label.id) ? 'bg-primary text-primary-foreground' : ''">
                  <span v-if="issue.labels.some(l => l.id === label.id)" class="text-[10px]">✓</span>
                </span>
                <IssueLabelChip :name="label.name" :color="label.color" />
              </button>
              <p v-if="!labels.length" class="text-xs text-muted-foreground">暂无标签，可在工单列表管理。</p>
            </div>
          </div>
          <div v-else class="rounded-xl border bg-card p-3">
            <h3 class="mb-2 text-xs font-medium text-muted-foreground">标签</h3>
            <div class="flex flex-wrap gap-1.5">
              <IssueLabelChip v-for="label in issue.labels" :key="label.id" :name="label.name" :color="label.color" />
              <span v-if="!issue.labels.length" class="text-muted-foreground">暂无</span>
            </div>
          </div>
          <div class="rounded-xl border bg-card p-3 text-xs text-muted-foreground">
            <p><IconMessageCircle class="mr-1 inline size-3.5" />{{ issue.commentCount }} 条评论</p>
            <p class="mt-1">提出者默认收到该工单的所有回复。</p>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>
