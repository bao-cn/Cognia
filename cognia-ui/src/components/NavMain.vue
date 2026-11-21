<script setup lang="ts">
import {
  IconCirclePlusFilled,
  IconSpy,
  IconBrandSafari,
  IconHash,
  IconSearch,
  IconCreditCard,
} from "@tabler/icons-vue"
import { Button } from '@/components/ui/button'
import { Kbd, KbdGroup } from '@/components/ui/kbd'
import { useRouter } from 'vue-router'
import { useSearchStore } from '@/stores/search'
import { useSiteStore } from '@/stores/site'
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/ui/sidebar'

const router = useRouter()
const searchStore = useSearchStore()
const siteStore = useSiteStore()
const emit = defineEmits<{ 'newChat': []; 'temporaryChat': [] }>()
</script>

<template>
  <SidebarGroup>
    <SidebarGroupContent class="flex flex-col gap-2">
      <SidebarMenu>
        <SidebarMenuItem class="flex items-center gap-2">
          <SidebarMenuButton @click="emit('newChat')"
            class="bg-primary text-primary-foreground hover:bg-primary/90 hover:text-primary-foreground active:bg-primary/90 active:text-primary-foreground min-w-8 duration-200 ease-linear cursor-pointer">
            <IconCirclePlusFilled />
            <span>新建对话</span>
          </SidebarMenuButton>
          <Button size="icon" class="size-8 group-data-[collapsible=icon]:opacity-0 cursor-pointer"
            variant="outline" aria-label="隐私模式" @click="emit('temporaryChat')">
            <IconSpy />
          </Button>
        </SidebarMenuItem>
      </SidebarMenu>

      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton class="border border-sidebar-border/70 text-sidebar-foreground/70 cursor-pointer" @click="searchStore.openSearch()">
            <IconSearch />
            <span>搜索</span>
            <KbdGroup class="ml-auto">
              <Kbd>⌘</Kbd>
              <Kbd>K</Kbd>
            </KbdGroup>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>

      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton @click="router.push('/discover')">
            <IconBrandSafari />
            <span>发现广场</span>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>

      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton @click="router.push('/channels')">
            <IconHash />
            <span>我的频道</span>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>

      <SidebarMenu>
        <SidebarMenuItem v-if="siteStore.billingEnabled">
          <SidebarMenuButton @click="router.push('/subscribe')">
            <IconCreditCard />
            <span>Platform +</span>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarGroupContent>
  </SidebarGroup>
</template>
