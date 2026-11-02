<template>
  <div class="flex min-h-svh flex-col items-center justify-center gap-4 p-6">
    <div class="flex size-20 items-center justify-center rounded-full bg-muted text-4xl font-bold text-muted-foreground">
      403
    </div>
    <h1 class="text-2xl font-semibold">无访问权限</h1>
    <p class="text-sm text-muted-foreground">当前账户没有访问该页面的权限，请联系管理员开通。</p>
    <div class="flex gap-2">
      <Button variant="outline" @click="router.back()">返回上一页</Button>
      <Button @click="router.push('/chat')">回到对话</Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'

const router = useRouter()
</script>
