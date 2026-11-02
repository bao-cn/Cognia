<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  IconDots,
} from "@tabler/icons-vue"
import { Button } from '@/components/ui/button'
import { SidebarTrigger } from '@/components/ui/sidebar'
import NotificationBell from '@/components/NotificationBell.vue'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? 'Cognia'))
const subtitle = computed(() => {
  switch (route.name) {
    case 'Favorites':
      return '把重要内容留在手边'
    case 'Teams':
      return '管理团队、成员与公开频道'
    case 'TeamDetail':
      return '团队知识空间'
    case 'TeamChannelManage':
      return '管理团队频道'
    case 'Download':
      return '选择适合你的客户端'
    case 'Settings':
      return '个性化你的使用体验'
    case 'Chat':
      return 'Cognia 可能会出错，请核查重要信息'
    default:
      return '欢迎回来'
  }
})
</script>

<template>
  <header class="flex h-(--header-height) shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)">
    <div class="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
      <SidebarTrigger class="-ml-1" />
      <div class="flex-1"></div>
      <div class="flex flex-col gap-1 text-center">
        <h1 class="text-base font-medium">
        {{ title }}
      </h1>
      <p class="text-xs text-muted-foreground">
        {{ subtitle }}
      </p>
      </div>
      <div class="flex-1"></div>
      <div class="ml-auto flex items-center gap-2">
        <NotificationBell />
        <Button variant="ghost" as-child size="sm" class="hidden sm:flex">
          <a
            href="#"
            class="dark:text-foreground"
          >
            <IconDots />
          </a>
        </Button>
      </div>
    </div>
  </header>
</template>
