<script setup lang="ts">
import NavHistory from '@/components/NavHistory.vue'
import NavChannel from '@/components/NavChannel.vue'
import NavMain from '@/components/NavMain.vue'
import ConsoleNavMain from '@/components/console/NavMain.vue'
import NavUser from '@/components/NavUser.vue'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/ui/sidebar'
import { Badge } from '@/components/ui/badge'
import { defineEmits, defineProps } from 'vue'

const emit = defineEmits<{ 'newChat': []; 'temporaryChat': []; 'deleteConversation': [item: { id: string, name: string }] }>()

defineProps({
  logo: {
    type: String,
    default: '/logo.png',
  },
  title: {
    type: String,
    default: 'Cognia',
  },
  badge_status: {
    type: Boolean,
    default: false,
  },
  badge_value: {
    type: String,
    default: 'Beta',
  },
  data: {
    type: Object,
  },
  isAdmin: {
    type: Boolean,
    default: false,
  }
})



</script>

<template>
  <Sidebar collapsible="offcanvas">
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem class="flex items-center gap-2">
          <SidebarMenuButton as-child class="data-[slot=sidebar-menu-button]:p-1.5! hover:bg-sidebar active:bg-sidebar">
            <router-link to="/">
              <img :src="logo" alt="Cognia Logo" class="h-8! w-8! rounded-xl" />
              <span class="text-base font-semibold">{{ title }}</span>
            </router-link>
          </SidebarMenuButton>
          <div class="flex-1"></div>
          <Badge v-if="badge_status" variant="outline" class="bg-primary text-primary-foreground">{{ badge_value }}</Badge>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
    <SidebarContent class="overflow-hidden">
      <NavMain v-if="!isAdmin" @new-chat="emit('newChat')" @temporary-chat="emit('temporaryChat')"/>
      <ConsoleNavMain v-if="isAdmin" :items="data?.navMain" />
      <NavChannel v-if="!isAdmin" :items="data?.channels" />
      <NavHistory v-if="!isAdmin" :items="data?.documents" @new="emit('newChat')"
        @delete="emit('deleteConversation', $event)" />
    </SidebarContent>
    <SidebarFooter>
      <NavUser />
    </SidebarFooter>
  </Sidebar>
</template>
