<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const props = withDefaults(defineProps<{
  delay?: number
  y?: number
}>(), {
  delay: 0,
  y: 28,
})

const el = ref<HTMLElement | null>(null)
const shown = ref(false)
let observer: IntersectionObserver | null = null

onMounted(() => {
  if (!el.value) return
  if (!('IntersectionObserver' in window)) {
    shown.value = true
    return
  }
  observer = new IntersectionObserver(([entry]) => {
    if (entry?.isIntersecting) {
      shown.value = true
      observer?.disconnect()
    }
  }, { threshold: 0.08, rootMargin: '0px 0px -48px 0px' })
  observer.observe(el.value)
})

onUnmounted(() => observer?.disconnect())
</script>

<template>
  <div
    ref="el"
    class="transition-all duration-700 ease-out will-change-transform"
    :class="shown ? 'translate-y-0 opacity-100 blur-0' : 'opacity-0 blur-[2px]'"
    :style="{
      transform: shown ? 'translateY(0)' : `translateY(${props.y}px)`,
      transitionDelay: `${props.delay}ms`,
    }"
  >
    <slot />
  </div>
</template>
