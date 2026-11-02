<template>
  <div class="mx-auto flex w-full max-w-4xl flex-col gap-5 px-4 pb-16 pt-6 md:px-8">
    <section
      class="relative overflow-hidden rounded-[2rem] border border-amber-100 bg-amber-50/90 p-5 shadow-sm dark:border-amber-900/30 dark:bg-amber-950/20 md:p-7"
    >

      <div class="relative flex flex-col gap-5 md:flex-row md:items-center">
        <div class="flex size-16 shrink-0 items-center justify-center rounded-3xl bg-rose-500 text-white shadow-lg shadow-rose-500/20">
          <IconStarFilled class="size-8" />
        </div>
        <div class="min-w-0 flex-1">
          <p class="text-xs font-medium tracking-[0.2em] text-rose-400/90 dark:text-rose-200/70">MY COLLECTION</p>
          <h1 class="mt-1 text-2xl font-bold tracking-tight text-slate-900 dark:text-white md:text-3xl">我的收藏</h1>
          <p class="mt-1 text-sm text-slate-600/80 dark:text-rose-100/70">把在 Cognia 里遇见的灵感与答案，收进自己的口袋。</p>
        </div>
        <div class="flex items-center gap-2">
          <Badge class="rounded-full border-white/60 bg-white/65 px-3 py-1 text-rose-600 backdrop-blur dark:border-white/10 dark:bg-white/10 dark:text-rose-200">
            {{ favorites.length }} 条收藏
          </Badge>
          <Button class="rounded-full bg-slate-900 text-white hover:bg-slate-800 dark:bg-white dark:text-slate-900 dark:hover:bg-slate-100" @click="handleAdd">
            <IconPlus class="mr-1.5 size-4" />
            新增收藏
          </Button>
        </div>
      </div>
    </section>

    <section class="flex flex-col gap-3 md:flex-row md:items-center">
      <div class="flex flex-1 items-center gap-1 overflow-x-auto rounded-full border bg-card/75 p-1 shadow-sm backdrop-blur scrollbar-none">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="shrink-0 rounded-full px-3.5 py-1.5 text-xs font-medium transition"
          :class="activeCategory === tab.key
            ? 'bg-foreground text-background shadow-sm'
            : 'text-muted-foreground hover:bg-muted hover:text-foreground'"
          @click="activeCategory = tab.key"
        >
          {{ tab.label }}
          <span class="ml-1 opacity-60">{{ countFor(tab.key) }}</span>
        </button>
      </div>
      <div class="relative md:w-64">
        <IconSearch class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input v-model="search" class="h-10 rounded-full bg-card/75 pl-9 backdrop-blur" placeholder="搜索收藏..." />
      </div>
    </section>

    <section v-if="filteredFavorites.length" class="grid gap-3 md:grid-cols-2">
      <article
        v-for="item in filteredFavorites"
        :key="item.id"
        class="group cursor-pointer rounded-3xl border bg-card/80 p-4 shadow-sm backdrop-blur transition duration-300 hover:-translate-y-0.5 hover:shadow-lg"
        @click="openDetail(item)"
      >
        <div class="flex items-start gap-3">
          <ColorfulIcon :icon="categoryMeta[item.category].icon" :color="item.color" padding="p-2.5" class="size-11 shrink-0 rounded-2xl" />
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <p class="truncate text-sm font-semibold">{{ item.title }}</p>
              <Badge variant="secondary" class="shrink-0 rounded-full px-2 py-0 text-[10px]">
                {{ categoryMeta[item.category].label }}
              </Badge>
            </div>
            <p class="mt-1 line-clamp-2 text-xs leading-5 text-muted-foreground">{{ item.description }}</p>
          </div>
          <Button
            variant="ghost"
            size="icon"
            class="size-8 shrink-0 rounded-full text-amber-500 opacity-80 transition hover:bg-amber-50 hover:text-amber-600 dark:hover:bg-amber-950/30"
            aria-label="取消收藏"
            @click.stop="removeFavorite(item)"
          >
            <IconStarFilled class="size-4" />
          </Button>
        </div>

        <div class="mt-4 flex items-center gap-2 overflow-hidden">
          <span v-for="tag in item.tags" :key="tag" class="rounded-full bg-muted px-2 py-0.5 text-[10px] text-muted-foreground">
            # {{ tag }}
          </span>
          <span class="flex-1" />
          <span class="shrink-0 text-[10px] text-muted-foreground">{{ item.updatedAt }}</span>
        </div>
      </article>
    </section>

    <section v-else class="flex min-h-72 flex-col items-center justify-center gap-3 rounded-3xl border bg-card/70 px-6 text-center shadow-sm backdrop-blur">
      <div class="flex size-16 items-center justify-center rounded-3xl bg-amber-50 text-rose-500 dark:bg-amber-950/30 dark:text-rose-300">
        <IconBookmark class="size-8" />
      </div>
      <div>
        <h2 class="text-base font-semibold">{{ favorites.length ? '没有匹配的收藏' : '收藏夹还是空的' }}</h2>
        <p class="mt-1 text-sm text-muted-foreground">
          {{ favorites.length ? '试试换个关键词或分类。' : '遇到喜欢的对话、知识库或文件，就把它们收进来吧。' }}
        </p>
      </div>
      <Button v-if="!favorites.length" variant="outline" class="rounded-full" @click="restoreSamples">
        <IconRestore class="mr-1.5 size-4" />
        恢复示例收藏
      </Button>
      <Button v-else variant="outline" class="rounded-full" @click="clearFilters">
        清除筛选
      </Button>
    </section>

    <p class="text-center text-[11px] text-muted-foreground">收藏数据为前端演示，接入后端后将与你账号同步。</p>

    <Dialog v-model:open="detailOpen">
      <DialogContent v-if="selected" class="sm:max-w-xl">
        <DialogHeader>
          <div class="flex items-center gap-3">
            <ColorfulIcon :icon="categoryMeta[selected.category].icon" :color="selected.color" padding="p-2.5" class="size-12 rounded-2xl" />
            <div class="min-w-0">
              <DialogTitle class="truncate">{{ selected.title }}</DialogTitle>
              <DialogDescription>{{ categoryMeta[selected.category].label }} · {{ selected.updatedAt }}</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div class="flex flex-col gap-4">
          <p class="text-sm leading-6 text-muted-foreground">{{ selected.description }}</p>
          <div class="rounded-2xl border bg-muted/35 p-4 text-sm leading-6">
            {{ selected.excerpt }}
          </div>
          <div class="flex flex-wrap gap-1.5">
            <span v-for="tag in selected.tags" :key="tag" class="rounded-full bg-muted px-2.5 py-1 text-xs text-muted-foreground">
              # {{ tag }}
            </span>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" class="rounded-full" @click="detailOpen = false">关闭</Button>
          <Button variant="outline" class="rounded-full text-rose-500 hover:text-rose-600" @click="removeSelected">
            取消收藏
          </Button>
          <Button class="rounded-full" @click="openSelected">
            打开内容
            <IconArrowUpRight class="ml-1.5 size-4" />
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import {
  IconArrowUpRight,
  IconBookmark,
  IconBooks,
  IconFileText,
  IconMessageCircle,
  IconPlus,
  IconRestore,
  IconSearch,
  IconStarFilled,
} from '@tabler/icons-vue'
import ColorfulIcon from '@/components/ui/colorful-icon/ColorfulIcon.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

type FavoriteCategory = 'chat' | 'knowledge' | 'file'
type FavoriteFilter = 'all' | FavoriteCategory

interface FavoriteItem {
  id: string
  title: string
  description: string
  excerpt: string
  category: FavoriteCategory
  color: string
  tags: string[]
  updatedAt: string
}

const categoryMeta = {
  chat: { label: '对话', icon: IconMessageCircle },
  knowledge: { label: '知识库', icon: IconBooks },
  file: { label: '文件', icon: IconFileText },
} as const

const tabs: Array<{ key: FavoriteFilter; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'chat', label: '对话' },
  { key: 'knowledge', label: '知识库' },
  { key: 'file', label: '文件' },
]

const mockFavorites: FavoriteItem[] = [
  {
    id: 'fav-1',
    category: 'chat',
    title: 'Q4 产品路线图讨论',
    description: '关于下一季度 AI 知识库、客户端体验与团队协作的完整讨论。',
    excerpt: '我们先把 Q4 的重点放在知识库检索质量、桌面端体验和团队权限三件事上。第一优先级是让新用户能在 3 分钟内完成一次有价值的问答……',
    color: '#6366F1',
    tags: ['产品', '路线图', 'Q4'],
    updatedAt: '今天 10:24',
  },
  {
    id: 'fav-2',
    category: 'knowledge',
    title: '工程规范手册',
    description: '前后端接口约定、提交规范、发布流程与线上问题排查指南。',
    excerpt: '所有 API 统一使用 /api/v1 前缀与统一响应信封；提交信息遵循 Conventional Commits；生产发布必须经过冒烟测试与回滚演练……',
    color: '#0EA5E9',
    tags: ['工程', '规范', '团队'],
    updatedAt: '昨天 18:07',
  },
  {
    id: 'fav-3',
    category: 'file',
    title: 'Cognia 产品白皮书.pdf',
    description: '产品定位、核心能力、典型场景与安全合规说明。',
    excerpt: 'Cognia 是面向企业知识工作的 AI 协作平台，围绕知识接入、检索增强、智能问答与权限治理构建完整闭环……',
    color: '#F59E0B',
    tags: ['产品', 'PDF', '12 页'],
    updatedAt: '10 月 24 日',
  },
  {
    id: 'fav-4',
    category: 'chat',
    title: '品牌语气与文案风格',
    description: '如何让 Cognia 的文案更克制、更清晰，同时保留一点温度。',
    excerpt: '好的产品文案应该像一个靠谱的同事：先给结论，再给上下文；少用形容词，多用具体的动词和数字……',
    color: '#EC4899',
    tags: ['品牌', '文案'],
    updatedAt: '10 月 22 日',
  },
  {
    id: 'fav-5',
    category: 'knowledge',
    title: '品牌视觉指南',
    description: 'Logo 使用、色彩系统、插画风格与界面组件示例。',
    excerpt: '主色使用靛蓝与柔和的紫粉渐变；插画选择低饱和的动物形象；界面圆角统一使用 16px 与 24px 两档……',
    color: '#8B5CF6',
    tags: ['设计', '品牌', '视觉'],
    updatedAt: '10 月 20 日',
  },
  {
    id: 'fav-6',
    category: 'file',
    title: '2026 年度预算.xlsx',
    description: '研发、市场与客户成功三条线的年度预算拆分。',
    excerpt: '研发投入占比 46%，市场与品牌占比 24%，客户成功与支持占比 18%，预留 12% 作为创新项目机动预算……',
    color: '#10B981',
    tags: ['财务', 'XLSX', '预算'],
    updatedAt: '10 月 18 日',
  },
  {
    id: 'fav-7',
    category: 'knowledge',
    title: '新成员入职地图',
    description: '从环境配置到第一次独立交付的 30 天成长路径。',
    excerpt: '第一周完成环境配置与产品体验；第二周跟随导师参与一次需求评审；第三周独立完成一个小需求并走完发布流程……',
    color: '#14B8A6',
    tags: ['入职', '成长', '团队'],
    updatedAt: '10 月 15 日',
  },
]

const FAVORITES_STORAGE_KEY = 'cognia.favorites.demo.v1'

function loadStoredFavorites(): FavoriteItem[] {
  try {
    const raw = localStorage.getItem(FAVORITES_STORAGE_KEY)
    if (!raw) return [...mockFavorites]
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return [...mockFavorites]
    const valid = parsed.filter((item): item is FavoriteItem => {
      if (!item || typeof item !== 'object') return false
      const candidate = item as Partial<FavoriteItem>
      return typeof candidate.id === 'string'
        && typeof candidate.title === 'string'
        && typeof candidate.description === 'string'
        && typeof candidate.excerpt === 'string'
        && typeof candidate.color === 'string'
        && Array.isArray(candidate.tags)
        && typeof candidate.updatedAt === 'string'
        && (candidate.category === 'chat' || candidate.category === 'knowledge' || candidate.category === 'file')
    })
    return valid
  } catch {
    return [...mockFavorites]
  }
}

const favorites = ref<FavoriteItem[]>(loadStoredFavorites())
watch(favorites, value => {
  try {
    localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(value))
  } catch {
    // A storage quota/private-mode failure must not break the page.
  }
}, { deep: true })

const activeCategory = ref<FavoriteFilter>('all')
const search = ref('')
const detailOpen = ref(false)
const selected = ref<FavoriteItem | null>(null)

const filteredFavorites = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return favorites.value.filter(item => {
    const matchesCategory = activeCategory.value === 'all' || item.category === activeCategory.value
    const matchesKeyword = !keyword
      || item.title.toLowerCase().includes(keyword)
      || item.description.toLowerCase().includes(keyword)
      || item.tags.some(tag => tag.toLowerCase().includes(keyword))
    return matchesCategory && matchesKeyword
  })
})

function countFor(filter: FavoriteFilter): number {
  return filter === 'all' ? favorites.value.length : favorites.value.filter(item => item.category === filter).length
}

function openDetail(item: FavoriteItem): void {
  selected.value = item
  detailOpen.value = true
}

function removeFavorite(item: FavoriteItem): void {
  favorites.value = favorites.value.filter(favorite => favorite.id !== item.id)
  if (selected.value?.id === item.id) {
    detailOpen.value = false
    selected.value = null
  }
  toast.success('已取消收藏')
}

function removeSelected(): void {
  if (selected.value) removeFavorite(selected.value)
}

function openSelected(): void {
  toast.info('演示收藏暂未接入详情页')
}

function handleAdd(): void {
  toast.info('演示模式：新增收藏将在接入后端后开放')
}

function clearFilters(): void {
  activeCategory.value = 'all'
  search.value = ''
}

function restoreSamples(): void {
  favorites.value = [...mockFavorites]
  clearFilters()
  toast.success('示例收藏已恢复')
}
</script>
