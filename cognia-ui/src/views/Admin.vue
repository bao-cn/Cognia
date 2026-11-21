<script setup lang="ts">
import AppSidebar from "@/components/AppSidebar.vue"
import NotificationBell from "@/components/NotificationBell.vue"
import { ref, watch } from "vue"
import { useRoute } from "vue-router"
import { useSiteStore } from "@/stores/site"
import {
  SidebarInset,
  SidebarProvider,
} from "@/components/ui/sidebar"
import {
  IconUsers,
  IconBooks,
  IconCpu,
  IconShield,
  IconSubtask,
  IconSettings2,
  IconDashboard,
  IconMail,
  IconThumbUp,
  IconCreditCard,
} from "@tabler/icons-vue"
const data = {
  navMain: [
    {
      title: "运营看板",
      url: "/console/dashboard",
      icon: IconDashboard,
    },
    {
      title: "组织管理",
      url: "#",
      icon: IconUsers,
      isActive: true,
      items: [
        {
          title: "用户管理",
          url: "/console/org/user",
        },
        {
          title: "角色管理",
          url: "/console/org/role",
        },
        {
          title: "团队管理",
          url: "/console/org/team",
        },
      ],
    },
    {
      title: "知识库管理",
      url: "#",
      icon: IconBooks,
      items: [
        {
          title: "知识库列表",
          url: "http://localhost:9001/",
        },
        {
          title: "文档回收站",
          url: "/console/kb/recycle",
        },
      ],
    },
    {
      title: "系统配置",
      url: "#",
      icon: IconCpu,
      items: [
        {
          title: "对象存储",
          url: "/console/system/object-storage",
        },
        {
          title: "登录方式",
          url: "/console/system/oauth",
        },
        {
          title: "模型配置",
          url: "/console/system/model",
        },
        {
          title: "工具配置",
          url: "/console/system/tool",
        },
        {
          title: "检索参数配置",
          url: "/console/system/retrieval",
        },
        {
          title: "文档处理",
          url: "/console/system/ingestion",
        },
        {
          title: "Prompt 配置",
          url: "/console/system/prompt",
        },
        {
          title: "开发模式",
          url: "/console/system/dev",
        },
      ],
    },
    // {
    //   title: "API 配置",
    //   url: "#",
    //   icon: IconApi,
    //   items: [
    //     {
    //       title: "API 密钥",
    //       url: "/console/api/key",
    //     },
    //     {
    //       title: "Webhook",
    //       url: "/console/api/webhook",
    //     },
    //     {
    //       title: "限流策略",
    //       url: "/console/api/rate-limit",
    //     },
    //   ],
    // },
    {
      title: "安全审计",
      url: "/console/security/audit",
      icon: IconShield,
    },
    {
      title: "质量评估",
      url: "/console/quality",
      icon: IconThumbUp,
    },
    {
      title: "计划任务",
      url: "/console/task",
      icon: IconSubtask,
    },
    {
      title: "订阅与计费",
      url: "/console/billing",
      icon: IconCreditCard,
    },
    {
      title: "邮件服务",
      url: "/console/mail",
      icon: IconMail,
    },
    {
      title: "系统设置",
      url: "/console/setting",
      icon: IconSettings2,
    },
  ]
}
const siteStore = useSiteStore()
const route = useRoute()
const contentRef = ref<HTMLElement | null>(null)
const pageScrolled = ref(false)

function handleContentScroll(event: Event): void {
  const target = event.target
  if (!(target instanceof HTMLElement))
    return
  if (target.scrollHeight <= target.clientHeight + 1)
    return
  pageScrolled.value = target.scrollTop > 2
}

watch(() => route.fullPath, () => {
  pageScrolled.value = false
})
</script>

<template>
  <SidebarProvider class="h-full min-h-0 overflow-hidden" :style="{
    '--sidebar-width': 'calc(var(--spacing) * 72)',
    '--header-height': 'calc(var(--spacing) * 12)',
  }">
    <AppSidebar variant="inset" :isAdmin="true" :data="data" :logo="siteStore.logo"
      :title="siteStore.siteName + ' Console'" :badge_status="true" :badge_value="'Beta'" />
    <SidebarInset class="flex h-full min-h-0 flex-1 flex-col overflow-hidden">
      <!-- <SiteHeader class="z-99 shrink-0 rounded-t-xl bg-background" /> -->
      <div class="relative flex h-12 shrink-0 items-center justify-end px-4">
        <NotificationBell />
        <div
          aria-hidden="true"
          class="pointer-events-none absolute inset-x-0 top-full z-20 h-5 bg-linear-to-b from-background via-background/60 to-transparent transition-opacity duration-200"
          :class="pageScrolled ? 'opacity-100' : 'opacity-0'"
        />
      </div>
      <div ref="contentRef" class="@container/main route-transition-content flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto" @scroll.capture="handleContentScroll">
        <router-view v-slot="{ Component }">
          <KeepAlive include="ChannelDetail">
            <component :is="Component" />
          </KeepAlive>
        </router-view>
      </div>
    </SidebarInset>
  </SidebarProvider>
</template>
