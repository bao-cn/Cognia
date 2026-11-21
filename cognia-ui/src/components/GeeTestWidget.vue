<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  captchaId: string
}>()

const emit = defineEmits<{
  (event: 'verified', result: {
    lotNumber: string
    captchaOutput: string
    passToken: string
    genTime: string
  }): void
  (event: 'expired'): void
  (event: 'error', message: string): void
}>()

const { locale } = useI18n()
const container = ref<HTMLElement | null>(null)
let captcha: GeeTestCaptchaInstance | null = null
let scriptPromise: Promise<void> | null = null

function loadScript(): Promise<void> {
  if (window.initGeetest4) {
    return Promise.resolve()
  }
  if (scriptPromise) {
    return scriptPromise
  }
  scriptPromise = new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>('script[data-cognia-geetest]')
    if (existing) {
      if (window.initGeetest4) {
        resolve()
        return
      }
      existing.addEventListener('error', () => reject(new Error('GeeTest script failed')), { once: true })
      const startedAt = Date.now()
      const poll = (): void => {
        if (window.initGeetest4) {
          resolve()
        } else if (Date.now() - startedAt > 10000) {
          reject(new Error('GeeTest script timeout'))
        } else {
          window.setTimeout(poll, 100)
        }
      }
      poll()
      return
    }
    const script = document.createElement('script')
    script.src = 'https://static.geetest.com/v4/gt4.js'
    script.async = true
    script.defer = true
    script.dataset.cogniaGeetest = 'true'
    const timeout = window.setTimeout(() => {
      script.remove()
      scriptPromise = null
      reject(new Error('GeeTest script timeout'))
    }, 15000)
    script.onload = () => {
      window.clearTimeout(timeout)
      resolve()
    }
    script.onerror = () => {
      window.clearTimeout(timeout)
      script.remove()
      scriptPromise = null
      reject(new Error('GeeTest script failed'))
    }
    document.head.appendChild(script)
  })
  return scriptPromise
}

function destroyWidget(): void {
  if (captcha) {
    try {
      captcha.destroy()
    } catch {
      // The instance may already be gone after a failed initialization.
    }
    captcha = null
  }
  if (container.value) {
    container.value.innerHTML = ''
  }
}

function language(): string {
  return locale.value === 'enUs' ? 'eng' : 'zho'
}

function renderWidget(): void {
  if (!container.value || !window.initGeetest4 || !props.captchaId) {
    return
  }
  destroyWidget()
  window.initGeetest4({
    captchaId: props.captchaId,
    product: 'float',
    language: language(),
    timeout: 15000,
    // Stretch the inline button to the host form width instead of GeeTest's
    // default 260px so it lines up with the input fields.
    nativeButton: { width: '100%', height: '50px' },
  }, (instance) => {
    // The component can unmount while gt4.js is still initializing.
    if (!container.value) {
      try {
        instance.destroy()
      } catch {
        // Nothing to clean up.
      }
      return
    }
    captcha = instance
    instance.appendTo(container.value)
    instance.onSuccess(() => {
      const result = instance.getValidate()
      if (!result) {
        emit('expired')
        return
      }
      emit('verified', {
        lotNumber: result.lot_number,
        captchaOutput: result.captcha_output,
        passToken: result.pass_token,
        genTime: result.gen_time,
      })
    })
    instance.onClose(() => emit('expired'))
    instance.onError(() => {
      emit('error', '人机验证组件加载失败，请刷新页面重试')
    })
  })
}

function reset(): void {
  if (captcha) {
    try {
      captcha.reset()
    } catch {
      // Ignore resets of a destroyed instance.
    }
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
  () => [props.captchaId, locale.value],
  () => renderWidget(),
)

onBeforeUnmount(() => {
  destroyWidget()
})
</script>

<template>
  <div ref="container" class="flex min-h-[50px] w-full items-center justify-center" />
</template>
