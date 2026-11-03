<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'

interface OutlineRound {
  id: string
  index: number
  user: string
  assistant: string
}

const props = defineProps<{
  rounds: OutlineRound[]
  activeId?: string
}>()

const emit = defineEmits<{ jump: [id: string] }>()

const hoveredId = ref<string | null>(null)
const previewId = ref<string | null>(null)
const tooltip = ref({ top: 0, left: 0 })
let enterTimer: ReturnType<typeof setTimeout> | undefined
let leaveTimer: ReturnType<typeof setTimeout> | undefined

const BASE = 18
const HOVER = 96
const NEIGHBOR = 74

const hoveredIndex = computed(() => props.rounds.findIndex(item => item.id === hoveredId.value))
const previewRound = computed(() => props.rounds.find(item => item.id === previewId.value))

function enter(round: OutlineRound, event: MouseEvent | FocusEvent) {
  if (enterTimer)
    clearTimeout(enterTimer)
  if (leaveTimer)
    clearTimeout(leaveTimer)
  const target = event.currentTarget as HTMLElement
  enterTimer = setTimeout(() => {
    const rect = target.getBoundingClientRect()
    tooltip.value = { top: rect.top + rect.height / 2, left: rect.left - 10 }
    hoveredId.value = round.id
    previewId.value = round.id
  }, 40)
}

function leave() {
  if (enterTimer)
    clearTimeout(enterTimer)
  if (leaveTimer)
    clearTimeout(leaveTimer)
  leaveTimer = setTimeout(() => {
    hoveredId.value = null
    previewId.value = null
  }, 120)
}

function clearPreview() {
  previewId.value = null
}

function barWidth(index: number) {
  if (hoveredIndex.value < 0)
    return `${BASE}px`
  if (index === hoveredIndex.value)
    return `${HOVER}px`
  const distance = Math.abs(index - hoveredIndex.value)
  const falloff = Math.exp(-(distance * distance) / (2 * 1.45 * 1.45))
  return `${Math.round(BASE + (NEIGHBOR - BASE) * falloff)}px`
}

function barTone(index: number) {
  if (index === hoveredIndex.value)
    return 'bg-primary shadow-[0_0_16px_-4px_var(--primary)]'
  if (props.rounds[index]?.id === props.activeId)
    return 'bg-primary/65'
  return 'bg-muted-foreground/35 group-hover:bg-muted-foreground/50'
}

function jump(round: OutlineRound) {
  previewId.value = null
  emit('jump', round.id)
}

onBeforeUnmount(() => {
  if (enterTimer)
    clearTimeout(enterTimer)
  if (leaveTimer)
    clearTimeout(leaveTimer)
})
</script>

<template>
  <nav class="w-28 select-none" aria-label="对话大纲" @mouseleave="leave">
    <div class="flex max-h-[58vh] flex-col gap-2 overflow-y-auto py-2 pr-2 scrollbar-thin" @scroll="clearPreview">
      <div
        v-for="round in rounds"
        :key="round.id"
        class="group flex items-center justify-end gap-2"
      >
        
        <button
          type="button"
          class="flex h-1.5 items-center justify-end rounded-full outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
          :aria-label="`第 ${round.index + 1} 轮对话`"
          :aria-current="round.id === activeId ? 'true' : undefined"
          @mouseenter="enter(round, $event)"
          @focus="enter(round, $event)"
          @mouseleave="leave"
          @blur="leave"
          @click="jump(round)"
        >
          <span
            class="block h-1 rounded-full transition-[width,background-color,box-shadow] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]"
            :class="barTone(round.index)"
            :style="{ width: barWidth(round.index) }"
          />
        </button>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="outline-pop">
        <div
          v-if="previewRound"
          class="pointer-events-none fixed z-[80] w-64 -translate-x-full -translate-y-1/2 rounded-xl border bg-popover p-3 text-popover-foreground shadow-xl"
          :style="{ top: `${tooltip.top}px`, left: `${tooltip.left}px` }"
        >
          <div class="mb-1 flex items-center justify-between text-[11px] text-muted-foreground">
            <span>第 {{ previewRound.index + 1 }} 轮</span>
            <span v-if="previewRound.id === activeId">当前</span>
          </div>
          <p class="line-clamp-3 text-xs font-medium leading-5">
            {{ previewRound.user }}
          </p>
          <p v-if="previewRound.assistant" class="mt-1 line-clamp-4 text-xs leading-5 text-muted-foreground">
            {{ previewRound.assistant }}
          </p>
        </div>
      </Transition>
    </Teleport>
  </nav>
</template>

<style scoped>
.outline-pop-enter-active,
.outline-pop-leave-active {
  transition: opacity 140ms ease, transform 140ms ease;
}
.outline-pop-enter-from,
.outline-pop-leave-to {
  opacity: 0;
  transform: translate(-100%, -50%) translateX(4px);
}
</style>
