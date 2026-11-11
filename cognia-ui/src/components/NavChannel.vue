<script setup lang="ts">
import { ref } from "vue"
import {
  IconDots,
  IconShare3,
  IconTrash,
  IconMessageCirclePlus,
  IconPinned,
  IconChevronDown,
  IconChevronRight,
  IconRss,
} from "@tabler/icons-vue"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuAction,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'
import { Button } from '@/components/ui/button'
interface ChannelItem {
  id: string
  name: string
  url: string
}

defineProps<{
  items: ChannelItem[]
}>()

const { isMobile } = useSidebar()

const isMenuCollapsed = ref(false)
</script>

<template>
  <SidebarGroup class="group-data-[collapsible=icon]:hidden">
    <SidebarGroupLabel class="group/label flex h-auto min-h-6 w-full gap-1 p-0">
      <span class="shrink-0">频道</span>

      <Button variant="ghost" size="icon"
        class="size-6 shrink-0 opacity-0 pointer-events-none transition-opacity duration-200 group-hover/label:opacity-100 group-hover/label:pointer-events-auto focus-visible:opacity-100"
        @click="isMenuCollapsed = !isMenuCollapsed" :aria-expanded="!isMenuCollapsed" aria-label="折叠或展开频道">
        <IconChevronDown class="size-4 transition-transform duration-300" :class="{ 'rotate-180': isMenuCollapsed }" />
      </Button>

      <div class="flex-1" />

    </SidebarGroupLabel>
    <div class="grid transition-[grid-template-rows,opacity] duration-300 ease-in-out" :class="isMenuCollapsed
        ? 'grid-rows-[0fr] opacity-0'
        : 'grid-rows-[1fr] opacity-100'
      ">
      <div class="min-h-0 overflow-hidden">
        <SidebarMenu>
          <SidebarMenuItem v-for="item in items" :key="item.id">
            <SidebarMenuButton as-child>
              <router-link :to="item.url" class="min-w-0">
                <IconRss />
                <span data-i18n-skip class="truncate">{{ item.name }}</span>
              </router-link>
            </SidebarMenuButton>
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <SidebarMenuAction show-on-hover class="data-[state=open]:bg-accent rounded-sm">
                  <IconDots />
                  <span class="sr-only">更多</span>
                </SidebarMenuAction>
              </DropdownMenuTrigger>
              <DropdownMenuContent class="w-24 rounded-lg" :side="isMobile ? 'bottom' : 'right'"
                :align="isMobile ? 'end' : 'start'">
                <DropdownMenuItem>
                  <IconMessageCirclePlus />
                  <span>新建对话</span>
                </DropdownMenuItem>
                <DropdownMenuItem>
                  <IconPinned />
                  <span>置顶</span>
                </DropdownMenuItem>
                <DropdownMenuItem>
                  <IconShare3 />
                  <span>分享</span>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem variant="destructive">
                  <IconTrash />
                  <span>删除</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
          <SidebarMenuItem v-if="items.length">
            <SidebarMenuButton as-child>
              <router-link to="/channels" class="min-w-0">
                <IconChevronRight />
                <span class="truncate">更多</span>
              </router-link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </div>
    </div>

  </SidebarGroup>
</template>
