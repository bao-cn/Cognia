<script setup lang="ts">
import {
  IconCirclePlusFilled,
  IconSpy,
  IconBrandSafari,
  IconSearch,
  IconThinkingHigh,
  IconHash,
} from "@tabler/icons-vue"
import { Kbd, KbdGroup } from '@/components/ui/kbd'
import { Button } from '@/components/ui/button'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/ui/sidebar'
</script>

<template>
  <SidebarGroup>
    <SidebarGroupContent class="flex flex-col gap-2">
      <SidebarMenu>
        <SidebarMenuItem class="flex items-center gap-2">
          <SidebarMenuButton
            class="bg-primary text-primary-foreground hover:bg-primary/90 hover:text-primary-foreground active:bg-primary/90 active:text-primary-foreground min-w-8 duration-200 ease-linear cursor-pointer">
            <IconCirclePlusFilled />
            <span>新建对话</span>
          </SidebarMenuButton>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger>
                <Button size="icon" class="size-8 group-data-[collapsible=icon]:opacity-0 cursor-pointer"
                  variant="outline">
                  <IconSpy />
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>进入临时对话</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </SidebarMenuItem>
      </SidebarMenu>
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton>
            <IconSearch />
            <span>搜索</span>
            <div class="flex-1"></div>
            <KbdGroup>
              <Kbd>⌘/Ctrl + K</Kbd>
            </KbdGroup>
          </SidebarMenuButton>
        </SidebarMenuItem>
        <SidebarMenuItem>
          <SidebarMenuButton>
            <IconThinkingHigh />
            <span>知识库</span>
          </SidebarMenuButton>
        </SidebarMenuItem>
        <SidebarMenuItem>
          <SidebarMenuButton>
            <IconBrandSafari />
            <span>发现广场</span>
          </SidebarMenuButton>
        </SidebarMenuItem>
        <SidebarMenuItem>
          <SidebarMenuButton>
            <IconHash />
            <span>我的频道</span>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarGroupContent>
  </SidebarGroup>

</template>
