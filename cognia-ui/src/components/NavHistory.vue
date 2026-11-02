<script setup lang="ts">
import { ref } from "vue"
import { toast } from "vue-sonner"
import {
  IconDots,
  IconTrash,
  IconMessagePlus,
  IconChevronDown,
} from "@tabler/icons-vue"

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
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
interface HistoryItem {
  id: string
  name: string
  url: string
}

defineProps<{
  items: HistoryItem[]
}>()

const emit = defineEmits<{ 'new': []; 'delete': [item: HistoryItem] }>()

const { isMobile } = useSidebar()
const isMenuCollapsed = ref(false)
</script>

<template>
  <SidebarGroup class="group-data-[collapsible=icon]:hidden">
    <SidebarGroupLabel class="group/label flex h-auto min-h-6 w-full gap-1 p-0">
      <span class="shrink-0">最近</span>

      <Button variant="ghost" size="icon"
        class="size-6 shrink-0 opacity-0 pointer-events-none transition-opacity duration-200 group-hover/label:opacity-100 group-hover/label:pointer-events-auto focus-visible:opacity-100"
        :aria-expanded="!isMenuCollapsed" aria-label="折叠或展开最近会话" @click="isMenuCollapsed = !isMenuCollapsed">
        <IconChevronDown class="size-4 transition-transform duration-300" :class="{ 'rotate-180': isMenuCollapsed }" />
      </Button>

      <div class="flex-1" />

      <Button variant="ghost" size="icon"
        class="size-6 shrink-0 opacity-0 pointer-events-none transition-opacity duration-200 group-hover/label:opacity-100 group-hover/label:pointer-events-auto focus-visible:opacity-100"
        aria-label="更多操作" @click="toast('暂不可用')">
        <IconDots class="size-4" />
      </Button>

      <Button variant="ghost" size="icon"
        class="size-6 shrink-0 opacity-0 pointer-events-none transition-opacity duration-200 group-hover/label:opacity-100 group-hover/label:pointer-events-auto focus-visible:opacity-100"
        aria-label="新建会话" @click="emit('new')">
        <IconMessagePlus class="size-4" />
      </Button>
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
                <span class="truncate">{{ item.name }}</span>
              </router-link>
            </SidebarMenuButton>
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <SidebarMenuAction show-on-hover class="data-[state=open]:bg-accent rounded-sm">
                  <IconDots />
                </SidebarMenuAction>
              </DropdownMenuTrigger>
              <DropdownMenuContent class="w-24 rounded-lg" :side="isMobile ? 'bottom' : 'right'"
                :align="isMobile ? 'end' : 'start'">
                <DropdownMenuItem variant="destructive" @click="emit('delete', item)">
                  <IconTrash />
                  <span>删除</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </div>
    </div>

  </SidebarGroup>
</template>
