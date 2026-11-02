<template>
    <div class="flex min-h-svh flex-col items-center justify-center gap-6 p-6 md:p-10">
        <div class="flex w-full max-w-sm flex-col gap-6">
            <router-link to="/" class="flex items-center gap-2 self-center font-medium">
                <div class="bg-primary text-primary-foreground flex size-8 items-center justify-center rounded-xl">
                    <img :src="siteStore.logo" :alt="`${siteStore.siteName} Logo`" class="rounded-xl" />
                </div>
                {{ siteStore.siteName }}
            </router-link>
            <router-view />
        </div>
        <div class="absolute top-0 left-0 h-screen w-full overflow-hidden z-[-1]">
            <LiquidBackground />
        </div>
    </div>
</template>
<script setup lang="ts">
import LiquidBackground from "@/components/ui/liquid-background/LiquidBackground.vue"
import { useSiteStore } from '@/stores/site'

const siteStore = useSiteStore()
</script>
