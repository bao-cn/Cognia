<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { profileApi, channelApi, issueApi } from '@/api'
import type { ChannelCard, ChannelDetail, IssueLabel, IssueUser, OwnerProfile } from '@/types/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Spinner } from '@/components/ui/spinner'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import IssueLabelChip from '@/components/IssueLabelChip.vue'
import Markdown from '@/components/Markdown.vue'
import { IconArrowLeft, IconSend } from '@tabler/icons-vue'

const props = defineProps<{ embedded?: boolean }>()
const route = useRoute()
const router = useRouter()
const owner = computed(() => String(route.params.owner ?? ''))
const slug = computed(() => String(route.params.channelSlug ?? ''))

const profile = ref<OwnerProfile | null>(null)
const channel = ref<ChannelCard | null>(null)
const detail = ref<ChannelDetail | null>(null)
const labels = ref<IssueLabel[]>([])
const assignees = ref<IssueUser[]>([])
const loading = ref(true)
const error = ref('')
const submitting = ref(false)
const tab = ref<'write' | 'preview'>('write')
const form = reactive({ title: '', body: '' })
const assigneeId = ref<string>('none')
const selectedLabelIds = ref<number[]>([])
const canManage = computed(() => Boolean(detail.value?.canManage))

async function init() {
  loading.value = true
  error.value = ''
  try {
    const p = await profileApi.get(owner.value)
    profile.value = p
    const channels = await channelApi.list(p.type, p.id)
    channel.value = channels.find((item) => item.slug === slug.value) ?? null
    if (!channel.value) throw new Error('频道不存在')
    detail.value = await channelApi.detail(channel.value.id)
    if (canManage.value) {
      const [ls, users] = await Promise.all([
        issueApi.labels(Number(channel.value.id)),
        issueApi.assignees(Number(channel.value.id)),
      ])
      labels.value = ls
      assignees.value = users
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function toggleLabel(id: number) {
  const index = selectedLabelIds.value.indexOf(id)
  if (index >= 0) selectedLabelIds.value.splice(index, 1)
  else selectedLabelIds.value.push(id)
}

async function submit() {
  if (!channel.value) return
  if (!form.title.trim()) return toast.error('请输入工单标题')
  if (!form.body.trim()) return toast.error('请输入工单内容')
  submitting.value = true
  try {
    const issue = await issueApi.create(Number(channel.value.id), {
      title: form.title.trim(),
      body: form.body.trim(),
    })
    if (canManage.value) {
      if (assigneeId.value !== 'none') {
        await issueApi.setAssignee(Number(channel.value.id), issue.number, Number(assigneeId.value))
      }
      if (selectedLabelIds.value.length) {
        await issueApi.setLabels(Number(channel.value.id), issue.number, selectedLabelIds.value)
      }
    }
    toast.success(`工单 #${issue.number} 已创建`)
    await router.replace(`/${owner.value}/${slug.value}/issues/${issue.number}`)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    submitting.value = false
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
    <template v-else-if="channel">
      <div v-if="!props.embedded" class="flex items-center gap-2">
        <Button variant="ghost" size="icon" class="size-8" @click="router.push(`/${owner}/${slug}/issues`)">
          <IconArrowLeft class="size-4" />
        </Button>
        <div>
          <h1 class="text-xl font-semibold">新建工单</h1>
          <p class="text-sm text-muted-foreground">{{ channel.name }} · 公开频道的工单</p>
        </div>
      </div>

      <div class="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_280px]">
        <div class="flex flex-col gap-4 rounded-xl border bg-card p-4 md:p-5">
          <div class="grid gap-2">
            <Label for="issue-title">标题 <span class="text-destructive">*</span></Label>
            <Input id="issue-title" v-model="form.title" maxlength="200" placeholder="简要描述问题或建议" />
          </div>

          <div class="grid gap-2">
            <div class="flex items-center justify-between">
              <Label>描述 <span class="text-destructive">*</span></Label>
              <div class="flex rounded-md border p-0.5 text-xs">
                <button type="button" class="rounded px-2.5 py-1" :class="tab === 'write' ? 'bg-muted font-medium' : 'text-muted-foreground'" @click="tab = 'write'">编写</button>
                <button type="button" class="rounded px-2.5 py-1" :class="tab === 'preview' ? 'bg-muted font-medium' : 'text-muted-foreground'" @click="tab = 'preview'">预览</button>
              </div>
            </div>
            <Textarea v-if="tab === 'write'" v-model="form.body" rows="16" class="min-h-[360px] font-mono text-sm"
                      placeholder="详细描述问题、复现步骤、期望结果…&#10;&#10;支持 Markdown，使用 @用户名 可以提醒他人。" />
            <div v-else class="min-h-[360px] rounded-md border p-4">
              <Markdown v-if="form.body.trim()" :text="form.body" />
              <p v-else class="text-sm text-muted-foreground">没有可预览的内容。</p>
            </div>
            <p class="text-xs text-muted-foreground">支持 Markdown；使用 @用户名 可以提醒他人。创建后提出者会默认收到该工单的所有回复。</p>
          </div>

          <div class="flex justify-end gap-2 border-t pt-4">
            <Button variant="outline" @click="router.push(`/${owner}/${slug}/issues`)">取消</Button>
            <Button :disabled="submitting" @click="submit">
              <Spinner v-if="submitting" class="mr-2" /><IconSend v-else class="mr-2 size-4" />创建工单
            </Button>
          </div>
        </div>

        <aside class="flex h-fit flex-col gap-4 text-sm">
          <div class="rounded-xl border bg-card p-3">
            <h3 class="mb-2 text-xs font-medium text-muted-foreground">指派给</h3>
            <Select v-if="canManage" v-model="assigneeId">
              <SelectTrigger class="h-9"><SelectValue placeholder="未指派" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="none">未指派</SelectItem>
                <SelectItem v-for="user in assignees" :key="user.id ?? user.username" :value="String(user.id)">{{ user.nickname }}</SelectItem>
              </SelectContent>
            </Select>
            <p v-else class="text-muted-foreground">创建后由频道管理员指派</p>
          </div>
          <div v-if="canManage" class="rounded-xl border bg-card p-3">
            <h3 class="mb-2 text-xs font-medium text-muted-foreground">标签</h3>
            <div class="flex flex-col gap-1.5">
              <button v-for="label in labels" :key="label.id" type="button" class="flex items-center gap-2 rounded px-1 py-1 text-left hover:bg-muted" @click="toggleLabel(label.id)">
                <span class="flex size-4 items-center justify-center rounded border" :class="selectedLabelIds.includes(label.id) ? 'bg-primary text-primary-foreground' : ''">
                  <span v-if="selectedLabelIds.includes(label.id)" class="text-[10px]">✓</span>
                </span>
                <IssueLabelChip :name="label.name" :color="label.color" />
              </button>
              <p v-if="!labels.length" class="text-xs text-muted-foreground">暂无标签，可在工单列表管理。</p>
            </div>
          </div>
          <div class="rounded-xl border bg-card p-3 text-xs text-muted-foreground">
            工单只有打开和关闭两种状态，创建后不会提供删除操作。
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>
