<script setup lang="ts">
import AppSidebar from "@/components/AppSidebar.vue"
import NotificationBell from "@/components/NotificationBell.vue"
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
} from "@tabler/icons-vue"
const data = {
  navMain: [
    //   {
    //   title: "仪表盘",
    //   url: "/console/dashboard",
    //   icon: IconDashboard,
    // },
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
          title: "Prompt 配置",
          url: "/console/system/prompt",
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
      title: "计划任务",
      url: "/console/task",
      icon: IconSubtask,
    },
    {
      title: "系统设置",
      url: "/console/setting",
      icon: IconSettings2,
    },
  ]
}
const siteStore = useSiteStore()
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
      <div class="flex h-12 shrink-0 items-center justify-end border-b px-4">
        <NotificationBell />
      </div>
      <div class="@container/main flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto">
        <router-view />
      </div>
    </SidebarInset>
  </SidebarProvider>
</template>
