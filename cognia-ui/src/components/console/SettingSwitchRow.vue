<script setup lang="ts">
import { Switch } from '@/components/ui/switch'

defineProps<{
  title: string
  description?: string
  modelValue: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()
</script>

<template>
  <div data-setting-row class="flex items-start justify-between gap-4 px-4 py-3">
    <div class="flex min-w-0 items-start gap-3">
      <div
        v-if="$slots.icon"
        class="flex size-8 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground"
      >
        <slot name="icon" />
      </div>
      <div class="flex flex-col gap-0.5">
        <span class="text-sm font-medium">{{ title }}</span>
        <span v-if="description" class="text-xs text-muted-foreground">{{ description }}</span>
      </div>
    </div>
    <Switch
      :model-value="modelValue"
      :disabled="disabled"
      @update:model-value="value => emit('update:modelValue', value)"
    />
  </div>
</template>
