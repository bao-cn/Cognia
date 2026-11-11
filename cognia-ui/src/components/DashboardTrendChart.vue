<script setup lang="ts">
import { computed, useId } from 'vue'
import { VisArea, VisAxis, VisLine, VisXYContainer } from '@unovis/vue'
import type { ChartConfig } from '@/components/ui/chart'
import {
  ChartContainer,
  ChartCrosshair,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  componentToString,
} from '@/components/ui/chart'

interface TrendPoint {
  date: string
  qa: number
  retrieval: number
  llm: number
}

interface SeriesConfig {
  key: keyof Omit<TrendPoint, 'date'>
  label: string
  color?: string
}

const uid = useId().replace(/:/g, '')

const props = withDefaults(defineProps<{
  data: TrendPoint[]
  series?: SeriesConfig[]
  height?: number
}>(), {
  series: () => [
    { key: 'qa', label: '问答请求' },
    { key: 'retrieval', label: '检索请求' },
    { key: 'llm', label: 'LLM 生成' },
  ],
  height: 240,
})

const palette = [
  'var(--primary)',
  'color-mix(in oklab, var(--primary) 62%, transparent)',
  'color-mix(in oklab, var(--primary) 34%, transparent)',
]

const colors = computed(() => props.series.map((item, index) => item.color ?? palette[index % palette.length]))

const chartConfig = computed<ChartConfig>(() => {
  const config: ChartConfig = {}
  props.series.forEach((item, index) => {
    config[item.key] = { label: item.label, color: colors.value[index] }
  })
  return config
})

const chartData = computed(() => props.data.map(item => ({
  ...item,
  dateValue: new Date(`${item.date}T00:00:00`),
})))

const yDomain = computed<[number, number]>(() => {
  const max = Math.max(1, ...props.data.flatMap(item => props.series.map(series => item[series.key])))
  return [0, Math.max(4, Math.ceil(max * 1.15))]
})

const svgDefs = computed(() => props.series.map((item, index) => `
  <linearGradient id="fill-${uid}-${item.key}" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0%" stop-color="${colors.value[index]}" stop-opacity="0.55" />
    <stop offset="100%" stop-color="${colors.value[index]}" stop-opacity="0.02" />
  </linearGradient>
`).join(''))

const areaColors = computed(() => props.series.map(item => `url(#fill-${uid}-${item.key})`))
const lineColors = computed(() => colors.value)
const x = (d: any) => d.dateValue
const yAccessors = computed(() => props.series.map(item => (d: any) => Number(d[item.key] ?? 0)))

function formatDate(value: number | Date) {
  const date = new Date(value)
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}
</script>

<template>
  <ChartContainer :config="chartConfig" class="w-full" :cursor="true" :style="{ height: `${height}px` }">
    <VisXYContainer
      :data="chartData"
      :svg-defs="svgDefs"
      :margin="{ left: -20, right: 12, top: 12, bottom: 0 }"
      :y-domain="yDomain"
    >
      <VisArea
        :x="x"
        :y="yAccessors"
        :color="areaColors"
        :opacity="1"
      />
      <VisLine
        :x="x"
        :y="yAccessors"
        :color="lineColors"
        :line-width="1.5"
      />
      <VisAxis
        type="x"
        :x="x"
        :tick-line="false"
        :domain-line="false"
        :grid-line="false"
        :num-ticks="6"
        :tick-format="formatDate"
      />
      <VisAxis
        type="y"
        :num-ticks="4"
        :tick-line="false"
        :domain-line="false"
        :tick-format="(value: number) => Math.round(value).toString()"
      />
      <ChartTooltip />
      <ChartCrosshair
        :template="componentToString(chartConfig, ChartTooltipContent, {
          labelFormatter: formatDate,
        })"
        :color="lineColors"
      />
    </VisXYContainer>
    <ChartLegendContent />
  </ChartContainer>
</template>
