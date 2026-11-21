
<script setup lang="ts">
import type { LucideIcon } from "@lucide/vue"
import { ChevronRight } from "@lucide/vue"
import { RouterLink, useRoute } from "vue-router"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import {
  SidebarGroup,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from "@/components/ui/sidebar"

const route = useRoute()

function isActive(url: string) {
  if (!url || url.startsWith("http")) return false
  return route.path === url || route.path.startsWith(url + "/")
}

function groupActive(item: { url: string; items?: { url: string }[] }) {
  return isActive(item.url) || (item.items ?? []).some((sub) => isActive(sub.url))
}

defineProps<{
  items: {
    title: string
    url: string
    icon?: LucideIcon
    isActive?: boolean
    items?: {
      title: string
      url: string
      icon?: LucideIcon
    }[]
  }[]
}>()
</script>

<template>
  <SidebarGroup>
    <SidebarMenu>
      <template v-for="item in items" :key="item.title">
        <!-- 有子项：可折叠的一级菜单 -->
        <Collapsible
          v-if="item.items?.length"
          as-child
          :default-open="groupActive(item)"
          class="group/collapsible"
        >
          <SidebarMenuItem>
            <CollapsibleTrigger as-child>
              <SidebarMenuButton :tooltip="item.title" :is-active="groupActive(item)">
                <component :is="item.icon" v-if="item.icon" />
                <span>{{ item.title }}</span>
                <ChevronRight
                  class="ml-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90"
                />
              </SidebarMenuButton>
            </CollapsibleTrigger>

            <CollapsibleContent>
              <SidebarMenuSub>
                <SidebarMenuSubItem
                  v-for="subItem in item.items"
                  :key="subItem.title"
                >
                  <SidebarMenuSubButton as-child :is-active="isActive(subItem.url)">
                    <RouterLink
                      v-if="!subItem.url.startsWith('http')"
                      :to="subItem.url"
                    >
                      <component
                        :is="subItem.icon"
                        v-if="subItem.icon"
                      />
                      <span>{{ subItem.title }}</span>
                    </RouterLink>
                    <a v-else :href="subItem.url" target="_blank">
                      <component
                        :is="subItem.icon"
                        v-if="subItem.icon"
                      />
                      <span>{{ subItem.title }}</span>
                    </a>
                  </SidebarMenuSubButton>
                </SidebarMenuSubItem>
              </SidebarMenuSub>
            </CollapsibleContent>
          </SidebarMenuItem>
        </Collapsible>

        <!-- 没有子项：直接进行路由跳转 -->
        <SidebarMenuItem v-else>
          <SidebarMenuButton as-child :tooltip="item.title" :is-active="isActive(item.url)">
            <RouterLink :to="item.url">
              <component :is="item.icon" v-if="item.icon" />
              <span>{{ item.title }}</span>
            </RouterLink>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </template>
    </SidebarMenu>
  </SidebarGroup>
</template>
