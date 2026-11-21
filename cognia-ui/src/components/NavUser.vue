<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import {
  IconLogout,
  IconBookmark,
  IconSettings,
  IconDashboard,
  IconDownload,
  IconArrowUpRight,
  IconUser,
  IconUsers,
  IconArchive,
} from "@tabler/icons-vue"

import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'
import { useUserStore } from '@/stores/user'
import { useConfigStore, THEMES } from '@/stores/config'
import { isElectronClient } from '@/lib/client-env'

const router = useRouter()
const userStore = useUserStore()
const configStore = useConfigStore()
const { isMobile } = useSidebar()
const isElectron = isElectronClient()

const displayName = computed(() => userStore.displayName)
const email = computed(() => userStore.profile?.email || '')
const avatar = computed(() => userStore.avatar)
const initials = computed(() => displayName.value.slice(0, 2).toUpperCase())

const themeOptions = [
  { value: THEMES.SYSTEM, label: '设备' },
  { value: THEMES.LIGHT, label: '明亮' },
  { value: THEMES.DARK, label: '黑暗' },
] as const

const themeIndex = computed(() => {
  const index = themeOptions.findIndex(option => option.value === configStore.configInfo.theme)
  return index < 0 ? 0 : index
})

function selectTheme(theme: THEMES): void {
  configStore.setTheme(theme)
}

async function handleLogout(): Promise<void> {
  await userStore.logout()
  toast.success('已退出登录')
  await router.replace('/sign')
}
</script>

<template>
  <SidebarMenu>
    <SidebarMenuItem v-if="!isElectron" class="mb-2">
      <SidebarMenuButton as-child class="h-9 rounded-lg border border-sidebar-border/70 bg-sidebar-accent/40 text-sidebar-foreground hover:bg-sidebar-accent">
        <router-link to="/download" class="flex w-full items-center gap-2">
          <IconDownload class="size-4" />
          <span class="text-sm font-medium">下载客户端</span>
          <IconArrowUpRight class="ml-auto size-3.5 opacity-50" />
        </router-link>
      </SidebarMenuButton>
    </SidebarMenuItem>
    <SidebarMenuItem>
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <SidebarMenuButton size="lg"
            class="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground">
            <Avatar class="h-8 w-8 rounded-lg">
              <AvatarImage :src="avatar" :alt="displayName" />
              <AvatarFallback class="rounded-lg">
                {{ initials }}
              </AvatarFallback>
            </Avatar>
            <div class="grid flex-1 text-left text-sm leading-tight">
              <span class="truncate font-medium">{{ displayName }}</span>
              <span class="text-muted-foreground truncate text-xs">
                {{ email }}
              </span>
            </div>
          </SidebarMenuButton>
        </DropdownMenuTrigger>
        <DropdownMenuContent class="w-(--reka-dropdown-menu-trigger-width) min-w-56 rounded-lg"
          :side="isMobile ? 'bottom' : 'right'" :side-offset="4" align="end">
          <DropdownMenuLabel class="p-0 font-normal">
            <div class="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
              <Avatar class="h-8 w-8 rounded-lg">
                <AvatarImage :src="avatar" :alt="displayName" />
                <AvatarFallback class="rounded-lg">
                  {{ initials }}
                </AvatarFallback>
              </Avatar>
              <div class="grid flex-1 text-left text-sm leading-tight">
                <span class="truncate font-medium">{{ displayName }}</span>
                <span class="text-muted-foreground truncate text-xs">
                  {{ email }}
                </span>
              </div>
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <DropdownMenuItem v-if="userStore.profile" @click="router.push(`/${userStore.profile.username}`)">
              <IconUser />
              我的主页
            </DropdownMenuItem>
            <DropdownMenuItem @click="router.push('/teams')">
              <IconUsers />
              我的团队
            </DropdownMenuItem>
            <DropdownMenuItem @click="router.push('/favorites')">
              <IconBookmark />
              我的收藏
            </DropdownMenuItem>
            <DropdownMenuItem @click="router.push('/archive')">
              <IconArchive />
              归档对话
            </DropdownMenuItem>
            <!-- 
            <DropdownMenuItem @click="toast.info('帮助中心即将上线')">
              <IconHelp />
              帮助
            </DropdownMenuItem>
             -->
            <DropdownMenuItem @click="router.push('/settings')">
              <IconSettings />
              设置
            </DropdownMenuItem>
            <DropdownMenuItem v-if="userStore.hasPermission('console:access')" @click="router.push('/console')">
              <IconDashboard />
              控制台
            </DropdownMenuItem>
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          <div class="px-2 py-2">
            <p class="mb-2 text-xs font-medium text-muted-foreground">外观</p>
            <div class="relative">
              <div class="grid grid-cols-3 rounded-lg bg-muted" role="radiogroup" aria-label="外观">
                <button
                  v-for="(option, index) in themeOptions"
                  :key="option.value"
                  type="button"
                  role="radio"
                  :aria-checked="themeIndex === index"
                  class="relative z-10 h-7 rounded-md text-xs transition-colors"
                  :class="themeIndex === index ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'"
                  @pointerdown.stop
                  @click.stop="selectTheme(option.value)"
                >
                  {{ option.label }}
                </button>
              </div>
              <span
                aria-hidden="true"
                class="pointer-events-none absolute inset-y-0 left-0 w-1/3 rounded-md bg-background shadow-sm transition-transform duration-200"
                :style="{ transform: `translateX(${themeIndex * 100}%)` }"
              />
            </div>
          </div>
          <DropdownMenuSeparator />
          <DropdownMenuItem variant="destructive" @click="handleLogout">
            <IconLogout />
            登出账户
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </SidebarMenuItem>
  </SidebarMenu>
</template>
