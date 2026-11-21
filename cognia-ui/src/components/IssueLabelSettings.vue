<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { issueApi } from '@/api'
import type { IssueLabel } from '@/types/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import IssueLabelChip from '@/components/IssueLabelChip.vue'
import { ISSUE_LABEL_COLORS, issueLabelDot } from '@/lib/issue-colors'
import { IconPlus, IconTrash } from '@tabler/icons-vue'

const props = defineProps<{ channelId: string | number }>()
const labels = ref<IssueLabel[]>([])
const loading = ref(true)
const name = ref('')
const color = ref('blue')
const creating = ref(false)

async function load() {
  loading.value = true
  try {
    labels.value = await issueApi.labels(Number(props.channelId))
  } finally {
    loading.value = false
  }
}

async function add() {
  if (!name.value.trim()) return toast.error('请输入标签名称')
  creating.value = true
  try {
    const label = await issueApi.createLabel(Number(props.channelId), { name: name.value.trim(), color: color.value })
    labels.value.push(label)
    name.value = ''
    toast.success('标签已添加')
  } catch (error: any) {
    toast.error(error?.message || '添加标签失败')
  } finally {
    creating.value = false
  }
}

async function remove(label: IssueLabel) {
  try {
    await issueApi.deleteLabel(Number(props.channelId), label.id)
    labels.value = labels.value.filter((item) => item.id !== label.id)
    toast.success('标签已删除')
  } catch (error: any) {
    toast.error(error?.message || '删除标签失败')
  }
}

onMounted(load)
</script>

<template>
  <section class="rounded-xl border bg-card">
    <header class="flex flex-col gap-1 border-b px-4 py-3">
      <h2 class="text-sm font-semibold">工单标签</h2>
      <p class="text-xs text-muted-foreground">管理频道工单可用的标签和颜色。</p>
    </header>
    <div class="flex flex-col gap-3 p-4">
      <div v-if="loading" class="flex h-12 items-center justify-center"><Spinner class="size-4" /></div>
      <div v-else class="flex flex-wrap gap-2">
        <div v-for="label in labels" :key="label.id" class="flex items-center gap-1">
          <IssueLabelChip :name="label.name" :color="label.color" />
          <Button variant="ghost" size="icon" class="size-6 text-muted-foreground hover:text-destructive" @click="remove(label)">
            <IconTrash class="size-3.5" />
          </Button>
        </div>
        <p v-if="!labels.length" class="text-sm text-muted-foreground">还没有标签。</p>
      </div>
      <div class="grid gap-2 border-t pt-3">
        <div class="flex gap-2">
          <Input v-model="name" maxlength="32" placeholder="例如：计划中" />
          <Button :disabled="creating" @click="add"><IconPlus class="mr-1 size-4" />添加</Button>
        </div>
        <div class="flex flex-wrap gap-2">
          <button v-for="item in ISSUE_LABEL_COLORS" :key="item" type="button" class="size-7 rounded-full border-2"
                  :class="[issueLabelDot(item), color === item ? 'border-foreground' : 'border-transparent']"
                  @click="color = item" />
        </div>
      </div>
    </div>
  </section>
</template>
