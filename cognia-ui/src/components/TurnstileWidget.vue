<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  siteKey: string
  theme?: 'auto' | 'light' | 'dark'
  language?: string
  action?: string
}>(), {
  theme: 'auto',
  language: 'zh-cn',
  action: 'register',
})

const emit = defineEmits<{
  (event: 'verified', token: string): void
  (event: 'expired'): void
  (event: 'error', message: string): void
}>()

const container = ref<HTMLElement | null>(null)
let widgetId: string | null = null
let scriptPromise: Promise<void> | null = null

function loadScript(): Promise<void> {
  if (window.turnstile) {
    return Promise.resolve()
  }
  if (scriptPromise) {
    return scriptPromise
  }
  scriptPromise = new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>('script[data-cognia-turnstile]')
    if (existing) {
      if (window.turnstile) {
        resolve()
        return
      }
      existing.addEventListener('error', () => reject(new Error('Turnstile script failed')), { once: true })
      const startedAt = Date.now()
      const poll = (): void => {
        if (window.turnstile) {
          resolve()
        } else if (Date.now() - startedAt > 10000) {
          reject(new Error('Turnstile script timeout'))
        } else {
          window.setTimeout(poll, 100)
        }
      }
      poll()
      return
    }
    const script = document.createElement('script')
    script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
    script.async = true
    script.defer = true
    script.dataset.cogniaTurnstile = 'true'
    const timeout = window.setTimeout(() => {
      script.remove()
      scriptPromise = null
      reject(new Error('Turnstile script timeout'))
    }, 15000)
    script.onload = () => {
      window.clearTimeout(timeout)
      resolve()
    }
    script.onerror = () => {
      window.clearTimeout(timeout)
      script.remove()
      scriptPromise = null
      reject(new Error('Turnstile script failed'))
    }
    document.head.appendChild(script)
  })
  return scriptPromise
}

function renderWidget(): void {
  if (!container.value || !window.turnstile || !props.siteKey) {
    return
  }
  if (widgetId && window.turnstile) {
    window.turnstile.remove(widgetId)
    widgetId = null
  }
  widgetId = window.turnstile.render(container.value, {
    sitekey: props.siteKey,
    theme: props.theme,
    language: props.language,
    action: props.action,
    callback: token => emit('verified', token),
    'expired-callback': () => emit('expired'),
    'timeout-callback': () => emit('expired'),
    'error-callback': code => emit('error', `人机验证失败（错误码 ${code}），请刷新后重试`),
  })
}

function reset(): void {
  if (widgetId && window.turnstile) {
    window.turnstile.reset(widgetId)
  }
  emit('expired')
}

defineExpose({ reset })

onMounted(async () => {
  try {
    await loadScript()
    renderWidget()
  } catch {
    emit('error', '人机验证组件加载失败，请刷新页面重试')
  }
})

watch(
  () => [props.siteKey, props.theme, props.language],
  () => renderWidget(),
)

onBeforeUnmount(() => {
  if (widgetId && window.turnstile) {
    window.turnstile.remove(widgetId)
  }
  widgetId = null
})
</script>

<template>
  <div ref="container" class="flex min-h-[65px] w-full items-center justify-center" />
</template>
