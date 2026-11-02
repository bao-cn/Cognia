<template>
  <div class="h-full min-h-0 overflow-y-auto overflow-x-hidden bg-[#0a0a0a] text-white">
    <!-- Header -->
    <header class="fixed inset-x-0 top-0 z-50">
      <div class="mx-auto flex h-[50px] w-full max-w-[1188px] items-center gap-4 px-5 pt-2 md:px-6">
        <router-link to="/" class="flex min-w-0 items-center gap-2 text-white">
          <img :src="siteStore.logo" :alt="siteStore.siteName" class="size-6 rounded-md object-cover" />
          <span class="truncate text-[17px] font-semibold tracking-[-0.02em]">{{ siteStore.siteName }}</span>
        </router-link>
        <div class="flex-1" />
        <span v-if="inClient" class="hidden items-center gap-1.5 rounded-full border border-white/[0.08] px-2.5 py-1 text-[10px] text-white/45 sm:flex">
          <span class="size-1.5 rounded-full bg-emerald-400" />
          客户端{{ clientVersion ? ` v${clientVersion}` : '' }}
        </span>

      </div>
    </header>

    <main>
      <!-- Hero -->
      <section class="relative flex min-h-[1240px] w-full flex-col overflow-hidden">
        <div class="pointer-events-none absolute inset-0 opacity-90">
          <HarnessBackdrop />
        </div>
        <div class="pointer-events-none absolute inset-0 bg-[#0a0a0a]/32" />
        <div class="pointer-events-none absolute inset-x-0 bottom-0 h-72 bg-[#0a0a0a] [mask-image:linear-gradient(to_top,black,transparent)]" />

        <div class="relative z-10 mx-auto flex w-full max-w-[1188px] flex-col items-center px-5 pt-[160px] md:px-6">
          <div class="flex w-full max-w-[800px] flex-col items-center gap-8 pt-4 text-center">
            <div class="hero-enter flex flex-col items-center gap-4">
              <span class="inline-flex items-center gap-2 font-mono text-[12px] font-medium uppercase leading-none tracking-[0.22em] text-white/45">
                <svg viewBox="0 0 24 24" class="size-3 -translate-y-px" aria-hidden="true">
                  <path d="M9 6C10.53 9.82 13.68 12.97 17.5 14.5C13.68 16.03 10.53 19.18 9 23C7.47 19.18 4.32 16.03 0.5 14.5C4.32 12.97 7.47 9.82 9 6Z" fill="#c7d7ff" />
                  <path d="M19.5 1C20.22 2.8 21.7 4.28 23.5 5C21.7 5.72 20.22 7.2 19.5 9C18.78 7.2 17.3 5.72 15.5 5C17.3 4.28 18.78 2.8 19.5 1Z" fill="#a6c2ff" opacity=".7" />
                </svg>
                预览版
              </span>
              <h1 class="text-[34px] font-normal leading-[1.45] tracking-[-0.02em] text-white sm:text-[40px] sm:leading-[60px]">
                <span class="font-medium">{{ siteStore.siteName }}</span><br />
                现在，开箱即用
              </h1>
            </div>

            <div class="hero-enter flex w-full max-w-[720px] flex-col gap-1.5" style="animation-delay: 0.15s">
              <p class="text-[16px] leading-[1.6] text-white/50 sm:text-[16px]">
                为年轻团队、高校组织与新兴企业而生的 RAG 知识系统
              </p>
              <p class="text-[16px] leading-[1.6] text-white/50 sm:text-[16px]">
                没有组织树，加入团队，频道自然汇聚知识；公开频道还会被更多伙伴看见和订阅
              </p>
            </div>

            <div class="hero-enter relative z-30 mt-2 flex w-full max-w-[800px] flex-col items-center gap-3">
              <div class="flex flex-wrap items-center justify-center gap-2.5">
                <div
                  v-for="platformButton in platformButtons"
                  :key="platformButton.key"
                  class="relative"
                  @mouseenter="hoveredPlatform = platformButton.key"
                  @mouseleave="hoveredPlatform = null"
                >
                  <button
                    type="button"
                    class="inline-flex h-[42px] items-center justify-center gap-2 rounded-full border border-white/[0.12] bg-white/[0.06] px-4 text-[14px] font-medium text-white transition hover:bg-white/[0.1]"
                    :class="platformButton.key === platform ? 'border-white/[0.22]' : ''"
                    :aria-expanded="visiblePlatform === platformButton.key"
                    @click.stop="togglePlatform(platformButton.key)"
                  >
                    <component :is="platformButton.icon" class="size-4 text-white/70" />
                    {{ platformButton.label }}
                    <Badge
                      v-if="platformButton.key === platform"
                      variant="secondary"
                      class="rounded-full bg-white/15 px-1.5 py-0 text-[9px] text-white"
                    >
                      推荐{{ architectureLabel ? ` · ${architectureLabel}` : '' }}
                    </Badge>
                    <IconChevronDown class="size-3.5 text-white/40 transition" :class="visiblePlatform === platformButton.key ? 'rotate-180' : ''" />
                  </button>

                  <Transition name="platform-select">
                    <div
                      v-if="visiblePlatform === platformButton.key"
                      class="absolute left-0 top-full z-40 w-[280px] origin-top-left pt-1.5"
                      @mouseenter="hoveredPlatform = platformButton.key"
                      @mouseleave="hoveredPlatform = null"
                      @click.stop
                    >
                      <div class="overflow-hidden rounded-md border border-white/10 bg-[#17191f] p-1 text-white shadow-md shadow-black/40">
                        <button
                          v-for="asset in assetsFor(platformButton.key)"
                          :key="asset.code"
                          type="button"
                          role="option"
                          :aria-selected="isRecommendedItem(asset, platformButton.key)"
                          class="relative flex w-full cursor-default select-none items-center gap-2 rounded-sm py-1.5 pl-2 pr-8 text-left text-[12px] outline-hidden transition-colors hover:bg-white/[0.07] focus:bg-white/[0.07]"
                          @click="startDownload(asset)"
                        >
                          <component :is="assetIcon(asset)" class="size-3.5 text-white/45" />
                          <span class="min-w-0 flex-1 truncate text-white/75">{{ formatLabel(asset) }} · {{ archLabel(asset) }}</span>
                          <Badge
                            v-if="isRecommendedItem(asset, platformButton.key)"
                            variant="secondary"
                            class="shrink-0 rounded-full bg-emerald-400/15 px-1.5 py-0 text-[8px] text-emerald-300"
                          >
                            推荐
                          </Badge>
                          <span class="shrink-0 font-mono text-[9px] text-white/25">v{{ asset.version }}</span>
                        </button>
                        <p v-if="assetsFor(platformButton.key).length === 0" class="px-2 py-3 text-xs text-white/30">暂无可下载版本</p>
                      </div>
                    </div>
                  </Transition>
                </div>
              </div>
            </div>
          </div>

          <div class="hero-preview-enter relative mt-[27px] aspect-[1020/612] min-h-[420px] w-full max-w-[1020px] sm:min-h-0">
            <CogniaPreview />
          </div>
        </div>
      </section>

      <!-- Capabilities -->
      <section class="mx-auto w-full max-w-[1188px] px-5 py-28 md:px-6 md:pb-40 md:pt-[180px]">
        <Reveal>
          <div class="mx-auto flex max-w-[820px] flex-col items-center text-center">
            <h2 class="text-[30px] font-normal leading-tight tracking-[-0.02em] text-white sm:text-[36px]">知识，以团队的方式生长</h2>
          </div>
        </Reveal>

        <div class="mt-16 grid grid-cols-1 gap-8 md:mt-[72px] lg:grid-cols-2">
          <!-- Grouped knowledge spaces -->
          <Reveal :delay="80">
            <article class="group flex h-[525px] flex-col overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.02] p-8 transition-colors hover:border-white/[0.11] md:p-10 md:pb-0">
              <h3 class="text-[20px] font-normal leading-[30px] text-white">团队，就是知识库</h3>
              <p class="mt-2 max-w-[472px] text-[16px] leading-[1.6] text-white/50">知识库归团队所有，用频道而不是组织树来组织内容。像 Discord 一样自然地协作，但这里沉淀的是知识，不是社交动态。</p>

              <div class="mt-10 flex flex-1 flex-col overflow-hidden rounded-t-xl border border-b-0 border-white/[0.08] bg-[#111318]">
                <div class="flex h-10 shrink-0 items-center gap-2 border-b border-white/[0.06] px-3">
                  <IconUsers class="size-3.5 text-indigo-300" />
                  <span class="text-[10px] font-medium text-white/70">产品团队 · 知识空间</span>
                  <span class="rounded-full bg-white/[0.06] px-2 py-0.5 text-[8px] text-white/35">团队 owner</span>
                  <div class="flex-1" />
                  <span class="flex items-center gap-1 text-[8px] text-white/30"><IconRefresh class="size-2.5" />刚刚</span>
                </div>
                <div class="flex flex-wrap gap-1.5 border-b border-white/[0.05] px-3 py-2">
                  <span class="rounded-full bg-indigo-400/15 px-2 py-0.5 text-[8px] text-indigo-300"># 产品文档 · 公开</span>
                  <span class="rounded-full bg-white/[0.06] px-2 py-0.5 text-[8px] text-white/35"># 用户研究</span>
                  <span class="rounded-full bg-white/[0.06] px-2 py-0.5 text-[8px] text-white/35"># 发布计划 · 私有</span>
                </div>
                <div class="flex min-h-0 flex-1 flex-col gap-2 p-3">
                  <div v-for="source in knowledgeSources" :key="source.name" class="flex items-center gap-2 rounded-lg border border-white/[0.05] bg-white/[0.02] px-2.5 py-2">
                    <div class="flex size-6 shrink-0 items-center justify-center rounded-md text-[7px] font-bold" :class="source.bg">{{ source.ext }}</div>
                    <span class="min-w-0 flex-1 truncate text-[9px] text-white/55">{{ source.name }}</span>
                    <span class="text-[8px] text-white/25">{{ source.chunks }} 个片段</span>
                    <IconCheck class="size-3 text-emerald-400" />
                  </div>
                  <div class="mt-1 rounded-lg border border-white/[0.07] bg-white/[0.035] p-3">
                    <div class="flex items-center gap-2 text-[8px] text-white/30"><IconSearch class="size-3" />频道内检索</div>
                    <p class="mt-1.5 text-[10px] leading-4 text-white/70">Q3 客户流失的主要原因是什么？</p>
                    <div class="mt-2 flex flex-wrap items-center gap-1.5 text-[8px]">
                      <span class="rounded-full bg-indigo-400/10 px-2 py-0.5 text-indigo-300">召回 6 个片段</span>
                      <span class="rounded-full bg-white/[0.06] px-2 py-0.5 text-white/35">引用 3 个来源</span>
                      <span class="ml-auto text-white/25">1.2s</span>
                    </div>
                  </div>
                </div>
              </div>
            </article>
          </Reveal>

          <!-- Public discovery -->
          <Reveal :delay="160">
            <article class="group flex h-[525px] flex-col overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.02] p-8 transition-colors hover:border-white/[0.11] md:p-10 md:pb-0">
              <h3 class="text-[20px] font-normal leading-[30px] text-white">值得被看见的公开频道</h3>
              <p class="mt-2 max-w-[472px] text-[16px] leading-[1.6] text-white/50">公开频道会出现在发现广场，其他团队可以订阅；私有频道只对成员开放，安静地沉淀内部知识。</p>

              <div class="mt-10 flex flex-1 flex-col overflow-hidden rounded-t-xl border border-b-0 border-white/[0.08] bg-[#111318] p-4">
                <div class="flex items-center gap-2 border-b border-white/[0.06] pb-2 text-[8px] text-white/30">
                  <IconWorld class="size-3 text-sky-300" />
                  发现广场
                  <span class="rounded-full bg-white/[0.06] px-2 py-0.5">公开频道</span>
                  <span class="ml-auto text-white/25">按订阅热度</span>
                </div>
                <div class="mt-3 space-y-2">
                  <div v-for="channel in discoveryChannels" :key="channel.name" class="rounded-lg border border-white/[0.07] bg-white/[0.025] p-2.5">
                    <div class="flex items-center gap-2">
                      <div class="flex size-6 items-center justify-center rounded-md" :class="channel.bg"><IconHash class="size-3" /></div>
                      <div class="min-w-0 flex-1">
                        <p class="truncate text-[9px] text-white/65">{{ channel.name }}</p>
                        <p class="text-[7px] text-white/25">{{ channel.subscribers }} 个订阅 · {{ channel.description }}</p>
                      </div>
                      <span class="rounded-full border px-2 py-0.5 text-[7px]" :class="channel.subscribed ? 'border-emerald-400/20 bg-emerald-400/10 text-emerald-300' : 'border-white/[0.08] text-white/35'">{{ channel.subscribed ? '已订阅' : '订阅' }}</span>
                    </div>
                  </div>
                </div>
                <div class="mt-auto flex items-center gap-2 pt-3 text-[8px] text-white/25">
                  <IconWorld class="size-3 text-sky-400" />
                  订阅后，频道会出现在你的「我的订阅」中
                </div>
              </div>
            </article>
          </Reveal>

          <!-- Team ownership / private channels -->
          <Reveal :delay="80">
            <article class="group flex h-[466px] flex-col overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.02] p-8 transition-colors hover:border-white/[0.11] md:p-10 md:pb-0">
              <h3 class="text-[20px] font-normal leading-[30px] text-white">边界清晰，协作自由</h3>
              <p class="mt-2 max-w-[472px] text-[16px] leading-[1.6] text-white/50">知识库归团队所有，频道权限由团队掌握。公开内容可以被订阅，私有内容只在团队内流动。</p>

              <div class="mt-10 flex flex-1 flex-col overflow-hidden rounded-t-xl border border-b-0 border-white/[0.08] bg-[#111318] p-4">
                <div class="grid grid-cols-3 gap-2">
                  <div v-for="space in knowledgeSpaces" :key="space.name" class="rounded-lg border border-white/[0.06] bg-white/[0.025] p-2.5">
                    <div class="flex items-center justify-between">
                      <span class="flex size-6 items-center justify-center rounded-md" :class="space.bg"><component :is="space.icon" class="size-3" /></span>
                      <span class="rounded-full px-1.5 py-0.5 text-[7px]" :class="space.private ? 'bg-white/[0.06] text-white/35' : 'bg-emerald-400/10 text-emerald-300'">{{ space.private ? '私有' : '公开' }}</span>
                    </div>
                    <p class="mt-2 text-[9px] font-medium text-white/65">{{ space.name }}</p>
                    <p class="mt-0.5 text-[7px] text-white/25">{{ space.members }} 位成员</p>
                  </div>
                </div>

                <div class="mt-3 rounded-lg border border-white/[0.07] bg-white/[0.025] p-3">
                  <div class="flex items-center gap-2">
                    <IconLock class="size-3.5 text-emerald-400" />
                    <span class="text-[9px] text-white/60">频道权限</span>
                    <span class="ml-auto rounded-full bg-emerald-400/10 px-2 py-0.5 text-[7px] text-emerald-300">已启用</span>
                  </div>
                  <div class="mt-2 space-y-1.5 font-mono text-[8px] text-white/35">
                    <div class="flex items-center justify-between"><span>团队成员</span><span class="text-white/55">公开 + 私有频道</span></div>
                    <div class="flex items-center justify-between"><span>订阅者</span><span class="text-white/55">仅公开频道</span></div>
                    <div class="flex items-center justify-between"><span>外部团队</span><span class="text-rose-300/70">无私有权限</span></div>
                  </div>
                </div>
              </div>
            </article>
          </Reveal>

          <!-- Citations -->
          <Reveal :delay="160">
            <article class="group flex h-[466px] flex-col overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.02] p-8 transition-colors hover:border-white/[0.11] md:p-10 md:pb-0">
              <h3 class="text-[20px] font-normal leading-[30px] text-white">每个答案，都有出处</h3>
              <p class="mt-2 max-w-[472px] text-[16px] leading-[1.6] text-white/50">RAG 会回到团队频道里找原文，回答附带引用、页码与更新时间，让每个结论都经得起追问。</p>

              <div class="mt-10 flex flex-1 flex-col overflow-hidden rounded-t-xl border border-b-0 border-white/[0.08] bg-[#111318] p-4">
                <div class="flex items-center gap-2 border-b border-white/[0.06] pb-2 text-[8px] text-white/30">
                  <IconQuote class="size-3 text-sky-300" />
                  回答 · 已生成
                  <span class="rounded-full bg-white/[0.06] px-2 py-0.5">3 个引用</span>
                  <span class="ml-auto text-emerald-300">已核对</span>
                </div>
                <p class="mt-3 text-[9px] text-white/35">为什么 Q3 客户流失率上升？</p>
                <p class="mt-1.5 text-[10px] leading-4 text-white/70">主要原因是新客户在 onboarding 第 2 周的激活断点，以及知识库导入失败后缺少及时跟进。</p>
                <div class="mt-3 space-y-2">
                  <div v-for="citation in citations" :key="citation.source" class="rounded-lg border border-white/[0.07] bg-white/[0.025] p-2.5">
                    <div class="flex items-center gap-2">
                      <IconFileText class="size-3" :class="citation.color" />
                      <span class="text-[8px] text-white/50">{{ citation.source }}</span>
                      <span class="ml-auto rounded bg-emerald-400/10 px-1.5 py-0.5 text-[7px] text-emerald-300">{{ citation.score }}</span>
                    </div>
                    <p class="mt-1.5 line-clamp-2 text-[8px] leading-3.5 text-white/35">{{ citation.quote }}</p>
                  </div>
                </div>
                <div class="mt-auto flex items-center gap-1.5 pt-3 text-[8px] text-white/25">
                  <IconShieldCheck class="size-3 text-emerald-400" />
                  每个结论均可回溯到原文
                </div>
              </div>
            </article>
          </Reveal>
        </div>
      </section>

      <!-- CTA -->
      <section class="relative mx-auto flex w-full max-w-[1188px] flex-col items-center px-5 pb-40 pt-32 text-center md:px-6 md:pb-40 md:pt-40">
        <div class="pointer-events-none absolute inset-0 overflow-hidden">
          <div class="absolute -inset-x-[30%] -top-[80%] h-[900px] opacity-20">
            <HarnessBackdrop />
          </div>
          <div class="absolute inset-0 bg-[#0a0a0a]/55" />
        </div>
        <Reveal class="relative z-10">
          <div class="flex flex-col items-center">
            <h2 class="text-[30px] font-normal leading-tight tracking-[-0.02em] text-white sm:text-[36px]">
              把知识，<span class="font-medium">放进对的频道</span>
            </h2>
            <p class="mt-5 max-w-[652px] text-[15px] leading-[1.7] text-white/50 sm:text-[16px]">
              Cognia 让团队像经营频道一样经营知识：公开的会被看见，私有的被妥善保护，每个答案都能回到原文。
            </p>
            <div class="mt-7 flex flex-wrap items-center justify-center gap-3">
              <Button class="h-[42px] rounded-full bg-white px-5 text-[15px] font-medium text-[#0a0a0a] hover:bg-white/90" @click="router.push('/chat')">
                开始使用
                <IconArrowUpRight class="ml-1 size-4" />
              </Button>
              <Button variant="outline" class="h-[42px] rounded-full border-white/[0.12] bg-white/[0.06] px-5 text-[15px] font-medium text-white hover:bg-white/[0.1] hover:text-white" @click="comingSoon('API 文档')">
                开发者文档
              </Button>
              <Button variant="outline" class="h-[42px] rounded-full border-white/[0.12] bg-white/[0.06] px-5 text-[15px] font-medium text-white hover:bg-white/[0.1] hover:text-white" @click="router.push('/legal/privacy')">
                隐私政策
              </Button>
              <Button variant="outline" class="h-[42px] rounded-full border-white/[0.12] bg-white/[0.06] px-5 text-[15px] font-medium text-white hover:bg-white/[0.1] hover:text-white" @click="router.push('/legal/terms')">
                用户协议
              </Button>
              <Button variant="outline" class="h-[42px] rounded-full border-white/[0.12] bg-white/[0.06] px-5 text-[15px] font-medium text-white hover:bg-white/[0.1] hover:text-white" @click="router.push('/legal/licenses')">
                开源许可
              </Button>
            </div>
          </div>
        </Reveal>
      </section>
    </main>

    <Dialog v-model:open="downloadDialogOpen">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>下载已开始</DialogTitle>
          <DialogDescription v-if="selectedDownload">
            {{ selectedDownload.name }} · v{{ selectedDownload.version }}
          </DialogDescription>
        </DialogHeader>

        <div v-if="selectedDownload" class="flex flex-col gap-4">
          <div class="overflow-hidden rounded-2xl border bg-muted/25">
            <div class="flex items-center justify-between gap-3 border-b px-4 py-2.5 text-sm">
              <span class="text-muted-foreground">平台</span>
              <span class="font-medium">{{ platformName(selectedDownload.platform) }} / {{ archLabel(selectedDownload) }} / {{ formatLabel(selectedDownload) }}</span>
            </div>
            <div class="flex items-center justify-between gap-3 border-b px-4 py-2.5 text-sm">
              <span class="text-muted-foreground">版本</span>
              <span class="font-mono font-medium">v{{ selectedDownload.version }}</span>
            </div>
            <div class="flex items-center justify-between gap-3 px-4 py-2.5 text-sm">
              <span class="text-muted-foreground">文件大小</span>
              <span class="font-mono">{{ selectedDownload.fileSize ? formatFileSize(selectedDownload.fileSize) : '未提供' }}</span>
            </div>
          </div>

          <div class="flex flex-col gap-2">
            <Label>SHA256 校验值</Label>
            <div class="break-all rounded-xl border bg-muted/35 px-3 py-2.5 font-mono text-[11px] leading-5 text-muted-foreground">
              {{ selectedDownload.sha256 || '该安装包尚未配置 SHA256 校验值' }}
            </div>
            <p class="text-xs leading-5 text-muted-foreground">下载完成后，请使用 SHA256 校验文件完整性，确保安装包未被篡改。</p>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" class="rounded-full" @click="downloadDialogOpen = false">关闭</Button>
          <Button class="rounded-full" :disabled="!selectedDownload?.sha256" @click="copySha256">
            <IconCopy class="mr-1.5 size-4" />
            复制 SHA256
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import {
  IconArrowUpRight,
  IconBook,
  IconBrandApple,
  IconBrandWindows,
  IconCheck,
  IconChevronDown,
  IconCopy,
  IconFileText,
  IconHash,
  IconHeadset,
  IconLock,
  IconQuote,
  IconRefresh,
  IconSearch,
  IconShieldCheck,
  IconUsers,
  IconWorld,
} from '@tabler/icons-vue'
import LinuxIcon from '@/components/icons/linux.vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import HarnessBackdrop from '@/components/landing/HarnessBackdrop.vue'
import CogniaPreview from '@/components/landing/CogniaPreview.vue'
import Reveal from '@/components/landing/Reveal.vue'
import { settingsApi } from '@/api'
import type { DownloadAsset } from '@/types/api'
import { useSiteStore } from '@/stores/site'
import {
  getClientArchitecture,
  getClientPlatform,
  getClientVersion,
  isCogniaClient,
} from '@/lib/client-env'

const router = useRouter()
const siteStore = useSiteStore()
const inClient = isCogniaClient()
const clientVersion = getClientVersion()
const platform = getClientPlatform()

const fallbackAssets: DownloadAsset[] = [
  { code: 'WINDOWS_X64', name: 'Windows exe（x64）', platform: 'windows', arch: 'x64', packageType: 'exe', version: '1.0.0', enabled: true },
  { code: 'WINDOWS_ARM64', name: 'Windows exe（ARM64）', platform: 'windows', arch: 'arm64', packageType: 'exe', version: '1.0.0', enabled: true },
  { code: 'MACOS_ARM64', name: 'macOS dmg（Apple 芯片）', platform: 'macos', arch: 'arm64', packageType: 'dmg', version: '1.0.0', enabled: true },
  { code: 'MACOS_X64', name: 'macOS dmg（Intel）', platform: 'macos', arch: 'x64', packageType: 'dmg', version: '1.0.0', enabled: true },
  { code: 'LINUX_X64', name: 'Linux AppImage（amd64）', platform: 'linux', arch: 'amd64', packageType: 'appimage', version: '1.0.0', enabled: true },
  { code: 'LINUX_APPIMAGE_ARM64', name: 'Linux AppImage（arm64）', platform: 'linux', arch: 'arm64', packageType: 'appimage', version: '1.0.0', enabled: true },
  { code: 'LINUX_DEB_AMD64', name: 'Linux deb（amd64）', platform: 'linux', arch: 'amd64', packageType: 'deb', version: '1.0.0', enabled: true },
  { code: 'LINUX_DEB_ARM64', name: 'Linux deb（arm64）', platform: 'linux', arch: 'arm64', packageType: 'deb', version: '1.0.0', enabled: true },
  { code: 'LINUX_RPM_AMD64', name: 'Linux rpm（amd64）', platform: 'linux', arch: 'amd64', packageType: 'rpm', version: '1.0.0', enabled: true },
  { code: 'LINUX_RPM_ARM64', name: 'Linux rpm（arm64）', platform: 'linux', arch: 'arm64', packageType: 'rpm', version: '1.0.0', enabled: true },
]

const downloadAssets = ref<DownloadAsset[]>([...fallbackAssets])
const detectedArch = ref<'x64' | 'arm64' | null>(null)
const hoveredPlatform = ref<string | null>(null)
const openPlatform = ref<string | null>(null)
const selectedDownload = ref<DownloadAsset | null>(null)
const downloadDialogOpen = ref(false)

const platformButtons = [
  { key: 'windows', label: 'Windows', icon: IconBrandWindows },
  { key: 'macos', label: 'macOS', icon: IconBrandApple },
  { key: 'linux', label: 'Linux', icon: LinuxIcon },
] as const

const visiblePlatform = computed(() => openPlatform.value ?? hoveredPlatform.value)

const architectureLabel = computed(() => {
  if (!detectedArch.value) return ''
  if (platform === 'macos') return detectedArch.value === 'arm64' ? 'Apple 芯片' : 'Intel'
  if (platform === 'linux') return detectedArch.value === 'x64' ? 'amd64' : 'arm64'
  return detectedArch.value === 'x64' ? 'x64' : 'ARM64'
})

const knowledgeSources = [
  { name: '产品手册.pdf', ext: 'PDF', chunks: 128, bg: 'bg-rose-400/15 text-rose-300' },
  { name: 'Q3 客户数据.xlsx', ext: 'X', chunks: 42, bg: 'bg-emerald-400/15 text-emerald-300' },
  { name: '客户成功手册.docx', ext: 'W', chunks: 316, bg: 'bg-sky-400/15 text-sky-300' },
  { name: '帮助中心 · Confluence', ext: 'C', chunks: 762, bg: 'bg-indigo-400/15 text-indigo-300' },
]

const citations = [
  {
    source: '客户成功手册 · p.18',
    score: '0.92',
    quote: '新客户在第 14 天仍未完成首个知识库导入时，次月流失概率会显著上升。',
    color: 'text-sky-300',
  },
  {
    source: 'Q3 客户数据.xlsx · Sheet2',
    score: '0.88',
    quote: '9 月流失客户中，有 64% 在 onboarding 第 2 周没有完成关键激活动作。',
    color: 'text-emerald-300',
  },
  {
    source: '产品手册.pdf · p.7',
    score: '0.81',
    quote: '当导入任务失败时，系统会保留原始文件并提示重新解析，管理员可在任务中心查看。',
    color: 'text-amber-300',
  },
]

const knowledgeSpaces = [
  { name: '产品团队', members: 12, icon: IconBook, bg: 'bg-indigo-400/15 text-indigo-300', private: false },
  { name: '高校实验室', members: 8, icon: IconUsers, bg: 'bg-amber-400/15 text-amber-300', private: true },
  { name: '初创团队', members: 6, icon: IconHeadset, bg: 'bg-emerald-400/15 text-emerald-300', private: false },
]

const discoveryChannels = [
  { name: '产品团队 · 用户研究', subscribers: '1.2k', description: '访谈洞察与需求沉淀', subscribed: true, bg: 'bg-sky-400/15 text-sky-300' },
  { name: '高校实验室 · RAG 论文', subscribers: '860', description: '检索增强生成研究', subscribed: false, bg: 'bg-indigo-400/15 text-indigo-300' },
  { name: '初创团队 · 增长复盘', subscribers: '420', description: '冷启动与增长实验', subscribed: false, bg: 'bg-emerald-400/15 text-emerald-300' },
]

function assetsFor(key: string): DownloadAsset[] {
  return downloadAssets.value.filter(asset => asset.enabled && asset.platform === key)
}

function isRecommendedItem(asset: DownloadAsset, platformKey: string): boolean {
  return platformKey === platform && asset.code === recommendedCodeFor(platformKey)
}

function recommendedCodeFor(key: string): string {
  if (key === 'windows') {
    return detectedArch.value === 'arm64' ? 'WINDOWS_ARM64' : 'WINDOWS_X64'
  }
  if (key === 'macos') {
    return detectedArch.value === 'x64' ? 'MACOS_X64' : 'MACOS_ARM64'
  }
  return detectedArch.value === 'arm64' ? 'LINUX_APPIMAGE_ARM64' : 'LINUX_X64'
}

function assetIcon(asset: DownloadAsset) {
  if (asset.platform === 'macos') return IconBrandApple
  if (asset.platform === 'windows') return IconBrandWindows
  return LinuxIcon
}

function archLabel(asset: DownloadAsset): string {
  if (asset.platform === 'macos') return asset.arch === 'arm64' ? 'Apple 芯片' : 'Intel'
  if (asset.arch === 'arm64') return 'ARM64'
  if (asset.arch === 'amd64') return 'amd64'
  return 'x64'
}

function formatLabel(asset: DownloadAsset): string {
  if (asset.packageType === 'appimage') return 'AppImage'
  return asset.packageType || asset.name
}

function platformName(value: string): string {
  if (value === 'windows') return 'Windows'
  if (value === 'macos') return 'macOS'
  if (value === 'linux') return 'Linux'
  return value
}

function formatFileSize(bytes?: number): string {
  if (!bytes || bytes <= 0) return ''
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
  return `${(bytes / 1024 / 1024).toFixed(0)} MB`
}

function togglePlatform(key: string): void {
  openPlatform.value = openPlatform.value === key ? null : key
}

function startDownload(asset: DownloadAsset): void {
  hoveredPlatform.value = null
  openPlatform.value = null
  if (!asset.downloadUrl) {
    toast.info(`${asset.name} 下载链接尚未配置，请联系管理员`)
    return
  }
  window.open(asset.downloadUrl, '_blank', 'noopener,noreferrer')
  selectedDownload.value = asset
  downloadDialogOpen.value = true
}

async function copySha256(): Promise<void> {
  if (!selectedDownload.value?.sha256) return
  try {
    await navigator.clipboard.writeText(selectedDownload.value.sha256)
    toast.success('SHA256 已复制')
  } catch {
    toast.error('复制失败，请手动选择校验值')
  }
}

function comingSoon(feature: string): void {
  toast.info(`${feature} 即将上线`)
}

function closePlatforms(event: MouseEvent): void {
  if (!(event.target as HTMLElement).closest('[data-download-platform]')) {
    openPlatform.value = null
  }
}

onMounted(async () => {
  document.addEventListener('click', closePlatforms)
  const [assets, arch] = await Promise.all([
    settingsApi.publicDownloads().catch(() => null),
    getClientArchitecture().catch(() => null),
  ])
  if (assets) {
    downloadAssets.value = assets
  }
  detectedArch.value = arch
})

onUnmounted(() => document.removeEventListener('click', closePlatforms))
</script>

<style scoped>
@keyframes harness-hero-enter {
  from {
    opacity: 0;
    transform: translateY(24px);
    filter: blur(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
    filter: blur(0);
  }
}

.hero-enter {
  animation: harness-hero-enter 0.9s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.hero-preview-enter {
  animation: harness-hero-enter 1s cubic-bezier(0.2, 0.7, 0.2, 1) 0.35s both;
}

.platform-select-enter-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
}

.platform-select-leave-active {
  transition: opacity 0.1s ease, transform 0.1s ease;
}

.platform-select-enter-from,
.platform-select-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.96);
}

@media (prefers-reduced-motion: reduce) {
  .hero-enter,
  .hero-preview-enter {
    animation: none;
  }
}
</style>
