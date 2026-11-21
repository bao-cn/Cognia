<script setup lang="ts">
import { issueLabelClass } from '@/lib/issue-colors'
defineProps<{ name: string; color: string }>()
</script>

<template>
  <span class="inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-medium leading-4" :class="issueLabelClass(color)">
    {{ name }}
  </span>
</template>
