<script setup lang="ts">
import { ref } from "vue"
import { toast } from "vue-sonner"
import {
  IconDots,
  IconArchive,
  IconMessagePlus,
  IconChevronDown,
  IconPin,
  IconPinned,
  IconPinFilled,
  IconStar,
  IconStarFilled,
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
  pinned?: boolean
  favorited?: boolean
}

defineProps<{
  items: HistoryItem[]
}>()

const emit = defineEmits<{
  'new': []
  'archive': [item: HistoryItem]
  'pin': [item: HistoryItem]
  'favorite': [item: HistoryItem]
}>()

const { isMobile } = useSidebar()
const isMenuCollapsed = ref(false)
</script>

<template>
  <SidebarGroup class="group-data-[collapsible=icon]:hidden flex min-h-20 flex-1 flex-col overflow-hidden">
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
    <div class="grid min-h-0 flex-1 transition-[grid-template-rows,opacity] duration-300 ease-in-out" :class="isMenuCollapsed
        ? 'grid-rows-[0fr] opacity-0'
        : 'grid-rows-[1fr] opacity-100'
      ">
      <div class="min-h-0 overflow-y-auto pr-1">
        <SidebarMenu>
          <SidebarMenuItem v-for="item in items" :key="item.id">
            <SidebarMenuButton as-child>
              <router-link :to="item.url" class="min-w-0">
                <IconPinFilled v-if="item.pinned" class="size-3 shrink-0 text-primary" />
                <span data-i18n-skip class="truncate">{{ item.name }}</span>
                <IconStarFilled v-if="item.favorited" class="size-3 shrink-0 text-amber-500" />
              </router-link>
            </SidebarMenuButton>
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <SidebarMenuAction show-on-hover class="data-[state=open]:bg-accent rounded-sm">
                  <IconDots />
                </SidebarMenuAction>
              </DropdownMenuTrigger>
              <DropdownMenuContent class="w-40 rounded-lg" :side="isMobile ? 'bottom' : 'right'"
                :align="isMobile ? 'end' : 'start'">
                <DropdownMenuItem @click="emit('pin', item)">
                  <IconPinned v-if="item.pinned" />
                  <IconPin v-else />
                  <span>{{ item.pinned ? '取消置顶' : '置顶' }}</span>
                </DropdownMenuItem>
                <DropdownMenuItem @click="emit('favorite', item)">
                  <IconStarFilled v-if="item.favorited" class="text-amber-500" />
                  <IconStar v-else />
                  <span>{{ item.favorited ? '取消收藏' : '收藏' }}</span>
                </DropdownMenuItem>
                <DropdownMenuItem @click="emit('archive', item)">
                  <IconArchive />
                  <span>归档</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </div>
    </div>

  </SidebarGroup>
</template>
