<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  IconActivity,
  IconBook,
  IconChevronDown,
  IconFilePlus,
  IconFolderPlus,
  IconMessageCircle,
  IconTrash,
  IconInfoCircle,
} from '@tabler/icons-vue'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import type { UserContribution } from '@/types/api'

const props = defineProps<{
  startYear?: number
  data: UserContribution
  self: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  'toggle-visibility': [value: boolean]
  'load-more': []
  'year-change': [year: number]
}>()

const rulesOpen = ref(false)

const countMap = computed(() => {
  const map = new Map<string, number>()
  for (const item of props.data.daily) map.set(item.date, item.count)
  return map
})

function dayLabel(key: string) {
  const [year, month, day] = key.split('-').map(Number)
  return `${year}年${month}月${day}日`
}

function isoDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const weeks = computed(() => {
  const year = props.data.year
  const start = new Date(year, 0, 1)
  const end = new Date(year, 11, 31)
  const first = new Date(start)
  first.setDate(first.getDate() - first.getDay())
  const result: { date: Date; key: string; count: number; level: number; inYear: boolean }[][] = []
  const cursor = new Date(first)
  while (cursor <= end) {
    const week: { date: Date; key: string; count: number; level: number; inYear: boolean }[] = []
    for (let i = 0; i < 7; i++) {
      const key = isoDate(cursor)
      const count = countMap.value.get(key) || 0
      const level = count === 0 ? 0 : count < 3 ? 1 : count < 6 ? 2 : count < 10 ? 3 : 4
      week.push({ date: new Date(cursor), key, count, level, inYear: cursor.getFullYear() === year })
      cursor.setDate(cursor.getDate() + 1)
    }
    result.push(week)
  }
  return result
})

const monthLabels = computed(() => {
  const labels: { label: string; index: number }[] = []
  const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  let last = -1
  weeks.value.forEach((week, index) => {
    const day = week.find((cell) => cell.inYear && cell.date.getDate() <= 7)
    if (day) {
      const month = day.date.getMonth()
      if (month !== last) {
        labels.push({ label: monthNames[month], index })
        last = month
      }
    }
  })
  return labels
})

const totalCount = computed(() => props.data.total)
const radar = computed(() => {
  const values = [props.data.radar.create, props.data.radar.update, props.data.radar.delete, props.data.radar.ticket]
  const sum = values.reduce((a, b) => a + b, 0) || 1
  const labels = ['新增文档', '修订文档', '删除文档', '回复工单']
  const percentages = values.map((v) => Math.round((v / sum) * 100))
  return { values, percentages, labels }
})

const radarPoints = computed(() => {
  const center = { x: 140, y: 112 }
  const radius = 78
  const angles = [-90, 0, 90, 180]
  return radar.value.percentages.map((pct, i) => {
    const angle = (angles[i] * Math.PI) / 180
    const r = (Math.max(pct, 2) / 100) * radius
    return `${center.x + Math.cos(angle) * r},${center.y + Math.sin(angle) * r}`
  }).join(' ')
})

const axisPoints = computed(() => {
  const center = { x: 140, y: 112 }
  const radius = 78
  return [-90, 0, 90, 180].map((deg) => {
    const angle = (deg * Math.PI) / 180
    return { x: center.x + Math.cos(angle) * radius, y: center.y + Math.sin(angle) * radius }
  })
})

const years = computed(() => {
  const current = new Date().getFullYear()
  const start = Math.min(props.startYear ?? current, current)
  const list = Array.from({ length: current - start + 1 }, (_, i) => current - i)
  if (!list.includes(props.data.year)) list.unshift(props.data.year)
  return list
})

const topChannelsText = computed(() => {
  const names = props.data.topChannels
  if (!names.length) return '还没有频道贡献记录'
  const shown = names.slice(0, 3)
  const rest = Math.max(0, props.data.channelCount - shown.length)
  return `为频道${shown.join('、')}${rest > 0 ? ` 及另外${rest}个频道` : ''}做出了贡献`
})

function activityText(item: UserContribution['activities'][number]) {
  const channel = item.channelName || '频道'
  switch (item.eventType) {
    case 'CHANNEL_CREATE':
      return `创建了频道「${channel}」`
    case 'CHANNEL_DELETE':
      return `解散了频道「${channel}」`
    case 'DOC_CREATE':
      return `为「${channel}」贡献了文档`
    case 'DOC_DELETE':
      return `删除了「${channel}」中的文档`
    case 'DOC_UPDATE':
      return `修订了「${channel}」中的文档`
    case 'TICKET_REPLY':
      return `回复了「${channel}」的工单`
    default:
      return `在「${channel}」产生了贡献`
  }
}

function activityIcon(item: UserContribution['activities'][number]) {
  switch (item.eventType) {
    case 'CHANNEL_CREATE':
      return IconFolderPlus
    case 'CHANNEL_DELETE':
      return IconTrash
    case 'DOC_CREATE':
      return IconFilePlus
    case 'DOC_UPDATE':
      return IconBook
    default:
      return IconMessageCircle
  }
}

function formatDay(value: string) {
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? value : `${date.getMonth() + 1}月${date.getDate()}日`
}
</script>

<template>
  <section class="rounded-xl border bg-card">
    <header class="flex flex-wrap items-start justify-between gap-3 border-b px-4 py-3">
      <div>
        <h2 class="flex items-center gap-2 text-sm font-semibold">
          <IconActivity class="size-4" />
          {{ data.year === new Date().getFullYear() ? '去年' : `${data.year}年` }}你的贡献记录有{{ totalCount }}次
        </h2>
        <p class="mt-0.5 text-xs text-muted-foreground">按日统计的公开贡献记录</p>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-xs text-muted-foreground">公开可见</span>
        <Switch :model-value="data.publicVisible" :disabled="!self || loading" @update:model-value="(value) => emit('toggle-visibility', Boolean(value))" />
      </div>
    </header>

    <div class="grid gap-4 p-4 lg:grid-cols-[minmax(0,1fr)_auto]">
      <div class="min-w-0">
        <div class="overflow-x-auto pb-1">
          <div class="min-w-0">
            <div class="relative ml-9 h-4 text-[10px] text-muted-foreground">
              <span v-for="month in monthLabels" :key="month.label" class="absolute -translate-x-1/2" :style="{ left: `${(month.index / Math.max(weeks.length - 1, 1)) * 100}%` }">{{ month.label }}</span>
            </div>
            <div class="flex gap-1">
              <div class="mt-1 flex w-8 shrink-0 flex-col justify-between py-1 text-[10px] text-muted-foreground">
                <span>Mon</span><span>Wed</span><span>Fri</span>
              </div>
              <TooltipProvider :delay-duration="80">
                <div class="grid w-full grid-flow-col grid-rows-7 gap-[3px]" :style="{ gridTemplateColumns: `repeat(${weeks.length}, minmax(0, 1fr))` }">
                  <Tooltip v-for="cell in weeks.flat()" :key="cell.key">
                    <TooltipTrigger as-child>
                      <span
                        class="aspect-square w-full min-w-0 cursor-default rounded-[2px] transition-colors"
                        :class="cell.inYear ? 'ring-0' : 'opacity-30'"
                        :style="{ backgroundColor: cell.level === 0 ? 'color-mix(in oklab, var(--muted) 75%, transparent)' : `color-mix(in oklab, var(--primary) ${18 + cell.level * 18}%, transparent)` }"
                      />
                    </TooltipTrigger>
                    <TooltipContent side="top" class="text-xs">
                      {{ dayLabel(cell.key) }} · {{ cell.count }} 次贡献
                    </TooltipContent>
                  </Tooltip>
                </div>
              </TooltipProvider>
            </div>
            <div class="ml-9 mt-2 flex items-center gap-2 text-[10px] text-muted-foreground">
              <span>少</span>
              <span v-for="level in 4" :key="level" class="size-3 rounded-[3px]" :style="{ backgroundColor: `color-mix(in oklab, var(--primary) ${18 + level * 18}%, transparent)` }" />
              <span>多</span>
              <button type="button" class="ml-2 inline-flex items-center gap-1 hover:text-foreground" @click="rulesOpen = true">
                <IconInfoCircle class="size-3" />了解我们如何计算贡献?
              </button>
            </div>
          </div>
        </div>

        <div v-if="data.teams.length" class="mt-4 flex flex-wrap gap-2">
          <RouterLink
            v-for="team in data.teams"
            :key="team.id"
            :to="team.slug ? `/${team.slug}` : '/teams'"
            class="inline-flex items-center gap-1.5 rounded-full border bg-background px-3 py-1 text-xs font-medium hover:bg-muted"
          >
            <IconFolderPlus class="size-3.5 text-muted-foreground" />
            {{ team.name }} <span class="text-muted-foreground">{{ team.count }}</span>
          </RouterLink>
        </div>

        <div class="mt-5 grid gap-4 md:grid-cols-[minmax(0,1fr)_260px]">
          <div class="rounded-lg bg-muted/15 p-4">
            <p class="text-sm leading-7">{{ topChannelsText }}</p>
            <p class="mt-2 text-xs text-muted-foreground">共 {{ data.channelCount }} 个频道 · {{ data.total }} 次贡献</p>
          </div>
          <div class="rounded-lg bg-muted/15 p-2">
            <svg viewBox="0 0 280 240" class="h-56 w-full overflow-visible" role="img" aria-label="四维贡献分布：上为新增文档，右为修订文档，下为删除文档，左为回复工单">
              <line v-for="axis in axisPoints" :key="axis.x" :x1="140" :y1="112" :x2="axis.x" :y2="axis.y" class="stroke-border" stroke-width="1" />
              <polygon :points="`140,34 218,112 140,190 62,112`" fill="none" class="stroke-border" stroke-width="1" />
              <polygon :points="`140,73 179,112 140,151 101,112`" fill="none" class="stroke-border" stroke-width="1" />
              <polygon :points="radarPoints" fill="color-mix(in oklab, var(--primary) 25%, transparent)" stroke="var(--primary)" stroke-width="1.5" />
              <circle :cx="140" :cy="112" r="2.5" fill="var(--primary)" />
              <text x="140" y="23" text-anchor="middle" class="fill-foreground/70" font-size="11" font-weight="600">新增文档</text>
              <text x="226" y="116" text-anchor="start" class="fill-foreground/70" font-size="11" font-weight="600">修订文档</text>
              <text x="140" y="205" text-anchor="middle" class="fill-foreground/70" font-size="11" font-weight="600">删除文档</text>
              <text x="54" y="116" text-anchor="end" class="fill-foreground/70" font-size="11" font-weight="600">回复工单</text>
            </svg>
          </div>
        </div>

        <div class="mt-5">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-muted-foreground">活动贡献</h3>
          <div v-if="data.activities.length" class="mt-3 flex flex-col divide-y">
            <div v-for="item in data.activities" :key="item.id" class="flex items-start gap-3 py-3">
              <div class="flex size-8 shrink-0 items-center justify-center rounded-full bg-muted">
                <component :is="activityIcon(item)" class="size-4 text-muted-foreground" />
              </div>
              <div class="min-w-0 flex-1">
                <p class="text-sm">{{ activityText(item) }}</p>
                <p class="mt-0.5 text-xs text-muted-foreground">{{ formatDay(item.createTime) }}</p>
              </div>
            </div>
          </div>
          <p v-else class="mt-3 text-xs text-muted-foreground">最近三个月还没有活动记录。</p>
          <Button v-if="data.hasMore" variant="outline" size="sm" class="mt-3 w-full" :disabled="loading" @click="emit('load-more')">
            <IconChevronDown class="mr-1 size-3.5" />{{ loading ? '加载中…' : '显示更多活动' }}
          </Button>
        </div>
      </div>

      <div class="flex flex-row gap-1 self-start lg:flex-col">
        <button
          v-for="year in years"
          :key="year"
          type="button"
          class="rounded-md px-3 py-1.5 text-xs transition-colors"
          :class="year === data.year ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'"
          @click="emit('year-change', year)"
        >{{ year }}</button>
      </div>
    </div>

    <Dialog v-model:open="rulesOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>如何计算贡献</DialogTitle>
          <DialogDescription>贡献统计用于展示你在公开频道中的参与记录。</DialogDescription>
        </DialogHeader>
        <div class="space-y-2 text-sm text-muted-foreground">
          <p>新增文档、修订文档、删除文档和回复工单都会计入活动记录。</p>
          <p>团队标签仅展示所选年份内有贡献的团队；历史记录不会因频道或团队后续被删除而消失。</p>
          <p>贡献面板默认公开，你可以随时关闭公开可见性；关闭后仅自己可见。</p>
        </div>
      </DialogContent>
    </Dialog>
  </section>
</template>
