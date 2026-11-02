<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const canvasRef = ref<HTMLCanvasElement | null>(null)
let frame = 0
let lastFrame = 0
let resizeObserver: ResizeObserver | null = null
let width = 0
let height = 0
let dpr = 1
const reduceMotion = typeof window !== 'undefined'
  && window.matchMedia('(prefers-reduced-motion: reduce)').matches

function resize(): void {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  width = Math.max(1, rect.width)
  height = Math.max(1, rect.height)
  dpr = Math.min(1.5, window.devicePixelRatio || 1)
  canvas.width = Math.round(width * dpr)
  canvas.height = Math.round(height * dpr)
}

function draw(time: number): void {
  if (!reduceMotion && time - lastFrame < 32) {
    frame = requestAnimationFrame(draw)
    return
  }
  lastFrame = time
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  if (!canvas || !ctx) return

  const t = time * 0.00008
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)

  ctx.fillStyle = '#0c1b2e'
  ctx.fillRect(0, 0, width, height)

  ctx.globalCompositeOperation = 'lighter'

  const blobs = [
    { x: 0.26 + Math.sin(t * 1.1) * 0.09, y: 0.10 + Math.cos(t * 0.8) * 0.05, r: 0.74, color: 'rgba(52, 105, 164, 0.62)' },
    { x: 0.66 + Math.cos(t * 0.9) * 0.08, y: 0.20 + Math.sin(t * 1.25) * 0.07, r: 0.68, color: 'rgba(62, 91, 145, 0.50)' },
    { x: 0.43 + Math.sin(t * 0.65) * 0.12, y: 0.46 + Math.cos(t * 0.7) * 0.06, r: 0.62, color: 'rgba(40, 96, 118, 0.32)' },
    { x: 0.82 + Math.cos(t * 1.4) * 0.05, y: 0.06 + Math.sin(t * 1.05) * 0.04, r: 0.50, color: 'rgba(70, 120, 190, 0.28)' },
  ]

  for (const blob of blobs) {
    const x = blob.x * width
    const y = blob.y * height
    const radius = blob.r * Math.max(width, height)
    const gradient = ctx.createRadialGradient(x, y, 0, x, y, radius)
    gradient.addColorStop(0, blob.color)
    gradient.addColorStop(0.5, blob.color.replace(/[\d.]+\)$/, '0.18)'))
    gradient.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = gradient
    ctx.fillRect(0, 0, width, height)
  }

  // Slow silk-like light bands crossing the upper half.
  for (let band = 0; band < 3; band += 1) {
    ctx.beginPath()
    for (let x = -40; x <= width + 40; x += 18) {
      const p = x / width
      const y = height * (0.14 + band * 0.075)
        + Math.sin(p * 6.2 + t * (1.4 + band * 0.3) + band) * height * 0.06
        + Math.cos(p * 3.4 - t * 0.9 + band * 0.8) * height * 0.03
      if (x === -40) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    }
    ctx.strokeStyle = band === 0 ? 'rgba(118, 181, 255, 0.16)' : 'rgba(88, 139, 210, 0.10)'
    ctx.lineWidth = height * (0.06 + band * 0.025)
    ctx.stroke()
  }

  const vignette = ctx.createRadialGradient(width * 0.5, height * 0.18, height * 0.1, width * 0.5, height * 0.25, height * 0.95)
  vignette.addColorStop(0, 'rgba(0,0,0,0)')
  vignette.addColorStop(0.62, 'rgba(5,7,11,0.12)')
  vignette.addColorStop(1, 'rgba(10,10,10,0.88)')
  ctx.globalCompositeOperation = 'source-over'
  ctx.fillStyle = vignette
  ctx.fillRect(0, 0, width, height)

  if (!reduceMotion) {
    frame = requestAnimationFrame(draw)
  }
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  resize()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      resize()
      if (reduceMotion) draw(0)
    })
    resizeObserver.observe(canvas)
  } else {
    window.addEventListener('resize', resize)
  }
  draw(0)
})

onUnmounted(() => {
  cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
  window.removeEventListener('resize', resize)
})
</script>

<template>
  <canvas ref="canvasRef" class="size-full" aria-hidden="true" />
</template>
