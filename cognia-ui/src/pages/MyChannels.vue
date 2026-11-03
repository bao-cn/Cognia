<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { IconBookmark, IconCompass, IconHash, IconUsers } from '@tabler/icons-vue'
import { channelApi, discoverApi, profileApi } from '@/api'
import type { ChannelCard, DiscoverChannel } from '@/types/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'

interface OwnedChannel extends ChannelCard { ownerLabel: string; url: string }

const loading = ref(true)
const owned = ref<OwnedChannel[]>([])
const subscribed = ref<DiscoverChannel[]>([])

const teamGroups = computed(() => {
  const groups = new Map<string, OwnedChannel[]>()
  for (const channel of owned.value) {
    if (!channel.ownerLabel.startsWith('团队 · ')) continue
    const key = channel.ownerLabel.slice(4)
    const list = groups.get(key) ?? []
    list.push(channel)
    groups.set(key, list)
  }
  return [...groups.entries()]
})
const personal = computed(() => owned.value.filter(channel => channel.ownerLabel === '个人'))

async function load() {
  loading.value = true
  try {
    const [profile, subscriptions] = await Promise.all([profileApi.me(), discoverApi.subscriptions().catch(() => [])])
    const next: OwnedChannel[] = profile.channels.map(channel => ({
      ...channel, ownerLabel: '个人', url: `/${profile.username}/${channel.slug}`,
    }))
    for (const team of profile.teams) {
      try {
        const list = await channelApi.list('TEAM', team.id)
        next.push(...list.map(channel => ({
          ...channel, ownerLabel: `团队 · ${team.name || team.slug}`, url: `/${team.slug}/${channel.slug}`,
        })))
      } catch { /* ignore inaccessible team */ }
    }
    owned.value = next
    subscribed.value = subscriptions
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold"><IconHash class="size-5 text-muted-foreground" />我的频道</h1>
        <p class="text-sm text-muted-foreground">自己创建、团队共享以及订阅的公开频道。</p>
      </div>
      <Button variant="outline" size="sm" as-child>
        <router-link to="/discover"><IconCompass class="mr-1 size-4" />发现广场</router-link>
      </Button>
    </div>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border"><Spinner class="size-6" /></div>
    <template v-else>
      <section class="rounded-xl border bg-card">
        <header class="flex items-center gap-2 border-b px-4 py-3">
          <h2 class="text-sm font-semibold">个人频道</h2>
          <Badge variant="secondary">{{ personal.length }}</Badge>
        </header>
        <div v-if="personal.length === 0" class="px-4 py-8 text-center text-sm text-muted-foreground">还没有个人频道</div>
        <div v-else class="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-3">
          <router-link v-for="channel in personal" :key="channel.id" :to="channel.url"
            class="rounded-lg border p-3 transition-colors hover:bg-muted/50">
            <p class="truncate text-sm font-medium">{{ channel.name }}</p>
            <p class="mt-1 line-clamp-2 text-xs text-muted-foreground">{{ channel.description || '暂无简介' }}</p>
            <p class="mt-2 text-[11px] text-muted-foreground">{{ channel.documentCount }} 个文档</p>
          </router-link>
        </div>
      </section>

      <section v-for="[team, list] in teamGroups" :key="team" class="rounded-xl border bg-card">
        <header class="flex items-center gap-2 border-b px-4 py-3">
          <IconUsers class="size-4 text-muted-foreground" />
          <h2 class="text-sm font-semibold">{{ team }}</h2>
          <Badge variant="secondary">{{ list.length }}</Badge>
        </header>
        <div class="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-3">
          <router-link v-for="channel in list" :key="channel.id" :to="channel.url"
            class="rounded-lg border p-3 transition-colors hover:bg-muted/50">
            <p class="truncate text-sm font-medium">{{ channel.name }}</p>
            <p class="mt-1 line-clamp-2 text-xs text-muted-foreground">{{ channel.description || '暂无简介' }}</p>
            <p class="mt-2 text-[11px] text-muted-foreground">{{ channel.documentCount }} 个文档</p>
          </router-link>
        </div>
      </section>

      <section class="rounded-xl border bg-card">
        <header class="flex items-center gap-2 border-b px-4 py-3">
          <IconBookmark class="size-4 text-muted-foreground" />
          <h2 class="text-sm font-semibold">已订阅</h2>
          <Badge variant="secondary">{{ subscribed.length }}</Badge>
        </header>
        <div v-if="subscribed.length === 0" class="px-4 py-8 text-center text-sm text-muted-foreground">
          还没有订阅频道，去<router-link to="/discover" class="text-primary hover:underline">发现广场</router-link>看看吧
        </div>
        <div v-else class="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-3">
          <router-link v-for="channel in subscribed" :key="channel.id" :to="`/${channel.ownerSlug}/${channel.slug}`"
            class="rounded-lg border p-3 transition-colors hover:bg-muted/50">
            <div class="flex items-center gap-2">
              <p class="truncate text-sm font-medium">{{ channel.name }}</p>
              <Badge v-if="channel.featured" variant="secondary" class="shrink-0">精选</Badge>
            </div>
            <p class="mt-0.5 truncate text-xs text-muted-foreground">@{{ channel.ownerSlug }}</p>
            <p class="mt-1 line-clamp-2 text-xs text-muted-foreground">{{ channel.description || '暂无简介' }}</p>
          </router-link>
        </div>
      </section>
    </template>
  </div>
</template>
