<template>
  <div class="relative h-full w-full overflow-hidden rounded-[14px] border border-white/10 bg-[#0d0f13] text-white shadow-2xl shadow-black/40">
    <div class="flex h-9 items-center gap-2 border-b border-white/[0.07] bg-[#14171d] px-3">
      <span class="size-2 rounded-full bg-[#ff5f57]" />
      <span class="size-2 rounded-full bg-[#febc2e]" />
      <span class="size-2 rounded-full bg-[#28c840]" />
      <div class="flex flex-1 items-center justify-center gap-2">
        <img :src="siteStore.logo" :alt="siteStore.siteName" class="size-3.5 rounded" />
        <span class="text-[10px] font-medium text-white/55">Cognia · 产品团队</span>
      </div>
      <IconLayoutSidebar class="size-3.5 text-white/30" />
      <IconDots class="size-3.5 text-white/30" />
    </div>

    <div class="flex h-[calc(100%-2.25rem)]">
      <aside class="hidden w-[208px] shrink-0 flex-col border-r border-white/[0.07] bg-[#0a0c10] p-3 sm:flex">
        <div class="flex items-center gap-2 px-1.5">
          <img :src="siteStore.logo" :alt="siteStore.siteName" class="size-5 rounded-md" />
          <span class="text-[12px] font-semibold">{{ siteStore.siteName }}</span>
          <span class="ml-auto rounded-full border border-white/10 px-1.5 py-0.5 text-[8px] text-white/35">Beta</span>
        </div>

        <button class="mt-4 flex h-8 w-full items-center gap-1.5 rounded-lg border border-white/10 bg-white/[0.04] px-2.5 text-[10px] font-medium text-white/80">
          <IconPlus class="size-3.5" />
          新会话
          <span class="ml-auto text-white/25">⌘ N</span>
        </button>

        <nav class="mt-3 space-y-0.5">
          <div v-for="item in navItems" :key="item.label" class="flex h-7 items-center gap-2 rounded-md px-2 text-[10px]" :class="item.active ? 'bg-white/[0.06] text-white/90' : 'text-white/45'">
            <component :is="item.icon" class="size-3.5" />
            {{ item.label }}
          </div>
        </nav>

        <div class="mt-5 px-2 text-[8px] font-medium uppercase tracking-[0.16em] text-white/25">我的频道</div>
        <div class="mt-1.5 space-y-0.5">
          <div class="flex h-7 items-center gap-2 rounded-md px-2 text-[10px] text-white/50">
            <IconHash class="size-3" />
            产品文档
          </div>
          <div class="flex h-7 items-center gap-2 rounded-md px-2 text-[10px] text-white/50">
            <IconHash class="size-3" />
            用户研究
          </div>
          <div class="flex h-7 items-center gap-2 rounded-md px-2 text-[10px] text-white/50">
            <IconHash class="size-3" />
            发布计划 · 私有
          </div>
        </div>

        <div class="mt-auto rounded-xl border border-white/[0.08] bg-white/[0.03] p-2.5">
          <div class="flex items-center gap-1.5 text-[9px] text-white/55">
            <IconDatabase class="size-3" />
            产品团队
          </div>
          <div class="mt-1 text-[8px] text-white/25">6 个频道 · 3 个公开</div>
        </div>
      </aside>

      <main class="flex min-w-0 flex-1 flex-col bg-[#0d0f13]">
        <div class="flex h-10 shrink-0 items-center gap-2 border-b border-white/[0.06] px-4">
          <span class="text-[11px] font-medium text-white/85">认识 Cognia</span>
          <span class="rounded-full border border-white/[0.08] px-2 py-0.5 text-[8px] text-white/35">RAG 知识库</span>
          <div class="flex-1" />
          <div class="flex items-center gap-1.5 rounded-full border border-white/[0.08] px-2 py-1 text-[8px] text-white/50">
            <span class="size-1.5 rounded-full bg-emerald-400" />
            Cognia V4 · High
            <IconChevronDown class="size-2.5" />
          </div>
        </div>

        <div class="min-h-0 flex-1 overflow-hidden px-7 py-4">
          <div class="flex justify-end">
            <div class="max-w-[76%] rounded-2xl rounded-br-md bg-white/[0.08] px-3 py-2 text-[10px] leading-5 text-white/80">
              可以介绍一下你自己吗？
            </div>
          </div>

          <div class="mt-4 flex items-start gap-2.5">
            <div class="flex size-6 shrink-0 items-center justify-center rounded-full bg-indigo-500/90">
              <IconSparkles class="size-3" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2 text-[9px] text-white/30">
                <span class="rounded-full bg-white/[0.06] px-1.5 py-0.5">已思考</span>
                <span>1.2s</span>
              </div>
              <p class="mt-1.5 text-[11px] leading-5 text-white/75">
                我是 Cognia，一个为团队而生的 RAG 知识系统。知识库归团队所有，频道聚合内容，公开频道还可以被其他团队订阅。
              </p>
              <p class="mt-2 text-[10px] text-white/40">我可以帮你：</p>
              <div class="mt-1.5 grid grid-cols-2 gap-x-4 gap-y-1.5">
                <div v-for="capability in capabilities" :key="capability" class="flex items-center gap-1.5 text-[9px] text-white/55">
                  <IconCheck class="size-2.5 shrink-0 text-emerald-400" />
                  {{ capability }}
                </div>
              </div>

              <div class="mt-3 flex flex-wrap items-center gap-1.5">
                <span class="flex items-center gap-1 rounded-md border border-white/[0.08] bg-white/[0.03] px-1.5 py-1 text-[8px] text-white/40">
                  <IconFileText class="size-2.5 text-sky-400" />
                  产品手册.pdf
                  <IconCheck class="size-2 text-emerald-400" />
                </span>
                <span class="flex items-center gap-1 rounded-md border border-white/[0.08] bg-white/[0.03] px-1.5 py-1 text-[8px] text-white/40">
                  <IconFileText class="size-2.5 text-amber-400" />
                  Q3 数据.xlsx
                  <IconCheck class="size-2 text-emerald-400" />
                </span>
                <span class="text-[8px] text-white/25">引用 2 个来源</span>
              </div>
            </div>
          </div>
        </div>

        <div class="shrink-0 px-4 pb-3">
          <div class="rounded-xl border border-white/10 bg-[#13161c] p-2.5">
            <div class="text-[9px] text-white/30">描述你想了解的内容，/ 调用指令，@ 文件或对话</div>
            <div class="mt-2 flex items-center gap-1.5">
              <div class="flex size-5 items-center justify-center rounded-full border border-white/10 text-white/40">
                <IconPaperclip class="size-2.5" />
              </div>
              <div class="flex items-center gap-1 rounded-full border border-white/10 px-1.5 py-0.5 text-[8px] text-white/35">
                <IconWorld class="size-2.5" />
                联网
              </div>
              <div class="flex-1" />
              <div class="flex size-5 items-center justify-center rounded-full bg-white text-black">
                <IconArrowUp class="size-3" />
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  IconArrowUp,
  IconBookmark,
  IconCheck,
  IconChevronDown,
  IconDatabase,
  IconDots,
  IconFileText,
  IconHash,
  IconLayoutSidebar,
  IconPaperclip,
  IconPlus,
  IconSearch,
  IconSettings,
  IconSparkles,
  IconUsers,
  IconWorld,
} from '@tabler/icons-vue'
import { useSiteStore } from '@/stores/site'

const siteStore = useSiteStore()

const navItems = [
  { label: '团队空间', icon: IconUsers, active: true },
  { label: '发现广场', icon: IconWorld, active: false },
  { label: '我的订阅', icon: IconBookmark, active: false },
  { label: '搜索', icon: IconSearch, active: false },
  { label: '设置', icon: IconSettings, active: false },
]

const capabilities = [
  '团队空间即知识库',
  '频道聚合文档与问答',
  '公开频道可被订阅',
  '私有频道仅成员可见',
]
</script>
