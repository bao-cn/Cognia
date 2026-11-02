<template>
  <div class="flex min-h-svh flex-col items-center justify-center gap-4 p-6">
    <div class="flex size-20 items-center justify-center rounded-full bg-muted text-4xl font-bold text-muted-foreground">
      404
    </div>
    <h1 class="text-2xl font-semibold">页面不存在</h1>
    <p class="text-sm text-muted-foreground">您访问的页面可能已被移动或删除。</p>
    <Button @click="router.push('/chat')">回到首页</Button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'

const router = useRouter()
</script>
