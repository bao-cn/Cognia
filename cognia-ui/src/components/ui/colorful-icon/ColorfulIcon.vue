<script setup lang="ts">
import type { HTMLAttributes, Component } from "vue"
import { cn } from "cn"

const props = defineProps<{
  icon: Component
  color: string
  /** 内边距，tailwind class，默认 p-1 */
  padding?: string
  class?: HTMLAttributes["class"]
}>()

// Hex -> HSL，加深明度 12% 作为底部渐变颜色，保证同色相
function getDarkerShade(hex: string): string {
  const r = parseInt(hex.slice(1, 3), 16) / 255
  const g = parseInt(hex.slice(3, 5), 16) / 255
  const b = parseInt(hex.slice(5, 7), 16) / 255
  let h = 0, s = 0, l = 0
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  l = (max + min) / 2
  if (max !== min) {
    s = max > min ? (max - min) / (2 - max - min) : (max - min) / (max + min)
    if (max === r) h = ((g - b) / (max - min)) % 6
    else if (max === g) h = ((b - r) / (max - min)) + 2
    else h = ((r - g) / (max - min)) + 4
  }
  h *= 60
  // 降低明度 12%，最低不低于 8%，防止变黑
  const darkerL = Math.max(l - 0.12, 0.08)
  return `hsl(${h.toFixed(0)}, ${(s * 100).toFixed(0)}%, ${(darkerL * 100).toFixed(0)}%)`
}

const bottomColor = getDarkerShade(props.color)
const gradientStyle = {
  // 从上到下，顶部=传入色，底部=加深色
  background: `linear-gradient(180deg, ${props.color}, ${bottomColor})`
}
</script>

<template>
  <div
    :class="cn('inline-flex items-center justify-center rounded-sm', props.padding ?? 'p-1', props.class)"
    :style="gradientStyle"
  >
    <component :is="icon" class="w-full h-full text-white" />
  </div>
</template>
