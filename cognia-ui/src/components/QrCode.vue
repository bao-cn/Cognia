<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{ value: string; size?: number }>(), { size: 188 })
const host = ref<HTMLDivElement | null>(null)
let loading: Promise<void> | null = null

function loadScript(): Promise<void> {
  if ((window as any).qrcode) return Promise.resolve()
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = '/vendor/qrcode.js'
      script.onload = () => resolve()
      script.onerror = () => reject(new Error('二维码组件加载失败'))
      document.head.appendChild(script)
    })
  }
  return loading
}

async function render(): Promise<void> {
  if (!props.value || !host.value) return
  await loadScript()
  const factory = (window as any).qrcode
  if (!factory) return
  const qr = factory(0, 'M')
  qr.addData(props.value)
  qr.make()
  host.value.innerHTML = qr.createSvgTag({ cellSize: 4, margin: 0, scalable: true })
}

onMounted(render)
watch(() => props.value, render)
</script>

<template>
  <div ref="host" class="qr-host rounded-xl bg-white p-3" :style="{ width: `${size}px`, height: `${size}px` }" />
</template>

<style scoped>
.qr-host :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
