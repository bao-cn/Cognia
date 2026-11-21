<script setup lang="ts">
import { ref } from 'vue'
import TurnstileWidget from '@/components/TurnstileWidget.vue'
import GeeTestWidget from '@/components/GeeTestWidget.vue'
import type { CaptchaProvider, CaptchaSolution } from '@/types/api'

const props = withDefaults(defineProps<{
  provider: CaptchaProvider
  turnstileSiteKey?: string
  geetestCaptchaId?: string
  theme?: 'auto' | 'light' | 'dark'
  action?: string
}>(), {
  turnstileSiteKey: '',
  geetestCaptchaId: '',
  theme: 'auto',
  action: 'register',
})

const emit = defineEmits<{
  (event: 'verified', solution: CaptchaSolution): void
  (event: 'expired'): void
  (event: 'error', message: string): void
}>()

const turnstileRef = ref<InstanceType<typeof TurnstileWidget> | null>(null)
const geetestRef = ref<InstanceType<typeof GeeTestWidget> | null>(null)

function reset(): void {
  if (props.provider === 'TURNSTILE') {
    turnstileRef.value?.reset()
  } else if (props.provider === 'GEETEST') {
    geetestRef.value?.reset()
  }
}

defineExpose({ reset })
</script>

<template>
  <TurnstileWidget
    v-if="provider === 'TURNSTILE'"
    ref="turnstileRef"
    :site-key="turnstileSiteKey"
    :theme="theme"
    :action="action"
    @verified="token => emit('verified', { provider: 'TURNSTILE', token })"
    @expired="emit('expired')"
    @error="message => emit('error', message)"
  />
  <GeeTestWidget
    v-else-if="provider === 'GEETEST'"
    ref="geetestRef"
    :captcha-id="geetestCaptchaId"
    @verified="result => emit('verified', { provider: 'GEETEST', ...result })"
    @expired="emit('expired')"
    @error="message => emit('error', message)"
  />
</template>
