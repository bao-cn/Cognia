<script setup lang="ts">
import { computed } from 'vue'
import { IconActivity } from '@tabler/icons-vue'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { useDebugStore } from '@/stores/debug'
import { useUserStore } from '@/stores/user'

const debugStore = useDebugStore()
const userStore = useUserStore()

const visible = computed(() => debugStore.isActive && userStore.isAdmin)
</script>

<template>
  <TooltipProvider v-if="visible" :delay-duration="200">
    <Tooltip>
      <TooltipTrigger as-child>
        <Button
          type="button"
          class="fixed bottom-6 right-6 z-40 h-11 gap-2 rounded-full border-primary/30 bg-primary px-4 text-primary-foreground shadow-lg shadow-primary/25 transition-transform hover:scale-[1.03]"
          aria-label="打开开发链路"
          @click="debugStore.toggleDrawer()"
        >
          <span class="relative flex size-2">
            <span class="absolute inline-flex size-full animate-ping rounded-full bg-emerald-300 opacity-70"></span>
            <span class="relative inline-flex size-2 rounded-full bg-emerald-300"></span>
          </span>
          <IconActivity class="size-4" />
          <span class="text-xs font-medium">开发链路</span>
        </Button>
      </TooltipTrigger>
      <TooltipContent side="left" class="text-xs">
        查看 {{ debugStore.hardcodedUsername }} 的全部对话链路记录
      </TooltipContent>
    </Tooltip>
  </TooltipProvider>
</template>
