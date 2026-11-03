<template>
  <DefineReasoningRow v-slot="{ id, text: reasoning, running }">
    <TraceCollapsible :open="reasoningOpen[id] ?? running" @update:open="reasoningOpen[id] = $event">
      <TraceCollapsibleTrigger>
        <TraceLine :status="running ? 'running' : undefined">
          <TraceIcon>
            <LoaderCircleIcon v-if="running" class="animate-spin" />
            <CircleCheckIcon v-else />
          </TraceIcon>
          <TraceTitle>{{ running ? 'Thinking' : 'Thought' }}</TraceTitle>
        </TraceLine>
      </TraceCollapsibleTrigger>
      <TraceCollapsibleContent class="whitespace-pre-wrap">
        {{ reasoning }}
      </TraceCollapsibleContent>
    </TraceCollapsible>
  </DefineReasoningRow>

  <DefineToolRow v-slot="{ part, streaming }">
    <TraceCollapsible v-if="part.state === 'output-available' || part.state === 'output-error'">
      <TraceCollapsibleTrigger>
        <TraceLine :status="part.state === 'output-error' ? 'error' : undefined">
          <TraceIcon>
            <TerminalIcon />
          </TraceIcon>
          <TraceTitle>
            {{ part.state === 'output-error' ? 'Failed to run' : 'Ran' }} {{ part.input.command }}
          </TraceTitle>
        </TraceLine>
      </TraceCollapsibleTrigger>
      <TraceCollapsibleContent>
        <CodeBlock size="sm">
          <CodeBlockContent wrap class="max-h-64">
            {{ part.state === 'output-error' ? part.errorText : part.output?.stdout }}
          </CodeBlockContent>
        </CodeBlock>
      </TraceCollapsibleContent>
    </TraceCollapsible>
    <TraceLine v-else :status="streaming ? 'running' : 'error'">
      <TraceIcon>
        <TerminalIcon />
      </TraceIcon>
      <TraceTitle>
        {{ streaming ? 'Running' : 'Failed to run' }} {{ part.input.command }}
      </TraceTitle>
    </TraceLine>
  </DefineToolRow>

  <MessageScrollerProvider auto-scroll>
    <div
      ref="chatRoot"
      class="relative flex h-[calc(98dvh-var(--header-height))] min-h-0 w-full flex-col overflow-hidden [[data-slot=preview]:has(&)]:bg-background [[data-slot=preview]:has(&)>div]:p-0!"
      @dragenter.prevent="onDragEnter" @dragover.prevent="onDragOver"
      @dragleave.prevent="onDragLeave" @drop.prevent="onDrop">
      <div v-if="dragActive" class="pointer-events-none absolute inset-3 z-50 flex items-center justify-center rounded-2xl border-2 border-dashed border-primary/60 bg-background/85 backdrop-blur-sm">
        <div class="flex flex-col items-center gap-2 text-center">
          <PaperclipIcon class="size-8 text-primary" />
          <p class="text-sm font-medium">松开以上传文件</p>
          <p class="text-xs text-muted-foreground">图片 JPEG / GIF / PNG / WebP，文档 PDF / Office</p>
        </div>
      </div>
      <div class="mx-auto flex h-full min-h-0 w-full max-w-4xl flex-col gap-4">
        <div class="min-h-0 flex-1">
          <Empty v-if="messages.length === 0" class="h-full">
            <FlickeringGrid
              class="absolute top-0 left-0 w-full h-full mask-[radial-gradient(450px_circle_at_center,white,transparent)]"
              :square-size="8" :grid-gap="4" :color="'#9C9C9C'" :max-opacity="0.15" :flicker-chance="0.1" />
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <MessageCircleDashedIcon />
              </EmptyMedia>
              <EmptyTitle>准备好问点什么了吗？</EmptyTitle>
              <EmptyDescription>
                输入@，选择一个你的知识库频道，再向我提问
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
          <MessageScroller v-else class="h-full min-h-0">
            <MessageScrollerViewport class="mt-4 scrollbar-none">
              <MessageScrollerContent :aria-busy="busy" class="gap-6 px-8 py-0">
                <template v-for="message in messages" :key="message.id">
                  <MessageScrollerItem v-if="message.role === 'user'" :message-id="message.id" scroll-anchor
                    class="ms-anim-slide-up">
                    <Message align="end">
                      <MessageContent>
                        <Bubble align="end" variant="muted">
                          <BubbleContent>{{ getMessageText(message) }}</BubbleContent>
                        </Bubble>
                        <div v-if="message.attachments?.length" class="mt-1 flex flex-wrap justify-end gap-1">
                          <Badge v-for="attachment in message.attachments" :key="attachment.name"
                            variant="outline" class="max-w-56 gap-1 text-[11px]">
                            <FileTextIcon class="size-3 shrink-0" />
                            <span class="truncate">{{ attachment.name }}</span>
                          </Badge>
                        </div>
                      </MessageContent>
                    </Message>
                  </MessageScrollerItem>
                  <MessageScrollerItem v-else :message-id="message.id">
                    <Message>
                      <MessageContent class="gap-3">
                        <Trace v-if="getSteps(message).length > 0">
                          <template v-for="(part, index) in getSteps(message)" :key="index">
                            <ReasoningRow v-if="part.type === 'reasoning'" :id="`${message.id}-${index}`"
                              :text="part.text" :running="part.state === 'streaming'" />
                            <ToolRow v-else :part="part" :streaming="busy && message === messages.at(-1)" />
                          </template>
                        </Trace>
                        <Markdown :text="getMessageText(message)" :streaming="busy && message === messages.at(-1)" />
                      </MessageContent>
                    </Message>
                  </MessageScrollerItem>
                </template>
              </MessageScrollerContent>
            </MessageScrollerViewport>
            <MessageScrollerButton />
          </MessageScroller>
        </div>
        <div class="shrink-0 p-8 pt-0">
          <div v-if="temporary || capabilities.webSearchAvailable" class="mb-2 flex flex-wrap items-center gap-2">
            <Badge v-if="temporary" variant="outline" class="gap-1"><SparklesIcon class="size-3" />临时对话 · 关闭后不保存</Badge>
            <Button v-if="capabilities.allowUserWebSearch" type="button" size="sm" class="h-7 rounded-full"
              :variant="webSearch ? 'secondary' : 'outline'" @click="webSearch = !webSearch">
              <GlobeIcon class="mr-1 size-3.5" />联网搜索{{ webSearch ? '已开启' : '已关闭' }}
            </Button>
            <Badge v-else-if="capabilities.webSearchAvailable" variant="secondary" class="gap-1">
              <GlobeIcon class="size-3" />联网搜索已启用
            </Badge>
          </div>
          <div v-if="selectedChannels.length" class="mb-2 flex flex-wrap gap-1.5">
            <Badge v-for="channel in selectedChannels" :key="channel.id" variant="secondary" class="gap-1">
              <HashIcon class="size-3" />{{ channel.ownerLabel }} / {{ channel.name }}
              <button type="button" class="ml-1 text-muted-foreground hover:text-foreground" @click="toggleChannel(channel)">
                <XIcon class="size-3" />
              </button>
            </Badge>
          </div>
          <div v-if="chatAttachments.length" class="mb-2 flex flex-wrap gap-2">
            <div v-for="(attachment, index) in chatAttachments" :key="`${attachment.name}-${index}`"
              class="flex max-w-64 items-center gap-2 rounded-xl border bg-muted/40 p-1.5 pr-2">
              <img v-if="attachment.type === 'IMAGE' && imagePreview(index)" :src="imagePreview(index)"
                :alt="attachment.name" class="size-9 rounded-md object-cover">
              <div v-else class="flex size-9 items-center justify-center rounded-md bg-background">
                <FileTextIcon class="size-4 text-muted-foreground" />
              </div>
              <div class="min-w-0">
                <p class="truncate text-xs font-medium">{{ attachment.name }}</p>
                <p class="text-[10px] text-muted-foreground">
                  {{ attachment.type === 'IMAGE' ? '图片' : '已解析文本' }}
                </p>
              </div>
              <button type="button" class="ml-1 text-muted-foreground hover:text-foreground"
                :aria-label="`移除 ${attachment.name}`" @click="removeAttachment(index)">
                <XIcon class="size-3.5" />
              </button>
            </div>
            <div v-if="uploadingAttachment" class="flex items-center gap-2 rounded-xl border bg-muted/40 px-3 text-xs text-muted-foreground">
              <LoaderCircleIcon class="size-3.5 animate-spin" />解析中…
            </div>
          </div>
          <form ref="formRef" class="relative w-full" @submit.prevent="onSubmit">
            <div v-if="mentionOpen"
              class="absolute bottom-full left-3 z-50 mb-2 w-80 overflow-hidden rounded-xl border bg-popover text-popover-foreground shadow-lg">
              <div class="flex items-center justify-between border-b px-3 py-2">
                <span class="text-xs font-medium text-muted-foreground">选择知识库频道</span>
                <span class="text-[11px] text-muted-foreground">Tab 多选</span>
              </div>
              <div class="max-h-60 overflow-y-auto p-1">
                <p v-if="mentionItems.length === 0" class="px-3 py-5 text-center text-xs text-muted-foreground">
                  没有匹配的频道
                </p>
                <button v-for="(channel, index) in mentionItems" :key="channel.id" type="button"
                  class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm"
                  :class="index === mentionIndex ? 'bg-accent text-accent-foreground' : 'hover:bg-muted'"
                  @mouseenter="mentionIndex = index" @mousedown.prevent="selectMention(channel, false)">
                  <span class="flex size-4 shrink-0 items-center justify-center rounded border"
                    :class="channelIds.includes(channel.id) ? 'border-primary bg-primary text-primary-foreground' : ''">
                    <CheckIcon v-if="channelIds.includes(channel.id)" class="size-3" />
                  </span>
                  <HashIcon class="size-3.5 shrink-0 text-muted-foreground" />
                  <span class="truncate">{{ channel.name }}</span>
                  <span class="ml-auto shrink-0 text-[11px] text-muted-foreground">{{ channel.ownerLabel }}</span>
                </button>
              </div>
              <div class="border-t px-3 py-1.5 text-[11px] text-muted-foreground">
                ↑↓ 选择 · Tab 多选 · Enter 完成 · Esc 关闭
              </div>
            </div>
            <InputGroup class="rounded-3xl bg-background">
              <InputGroupTextarea ref="textareaRef" v-model="text" placeholder="Ask the agent..." aria-label="Message" class="min-h-14"
                @input="onComposerInput" @keydown="onKeydown" @blur="onComposerBlur" />
              <InputGroupAddon align="block-end" class="pt-1">
                <InputGroupButton type="button" size="icon-sm" variant="outline" class="rounded-full"
                  aria-label="上传附件" :disabled="!supportsAttachments || uploadingAttachment"
                  :title="supportsAttachments ? '上传图片或文档' : '当前模型不支持文件上传'"
                  @click="attachmentInput?.click()">
                  <LoaderCircleIcon v-if="uploadingAttachment" class="animate-spin" />
                  <PaperclipIcon v-else />
                </InputGroupButton>
                <input ref="attachmentInput" type="file" multiple class="hidden"
                  accept="image/jpeg,image/gif,image/png,image/webp,.pdf,.docx,.xlsx,.pptx,.txt,.md,.markdown,.csv,.json,.html,.htm"
                  @change="onAttachmentInput">
                <InputGroupButton v-if="capabilities.allowUserWebSearch" type="button" size="sm" variant="outline"
                  class="rounded-full" :aria-pressed="webSearch" @click="webSearch = !webSearch">
                  <GlobeIcon class="size-4" />联网搜索
                </InputGroupButton>
                <Popover v-model:open="channelPickerOpen">
                  <PopoverTrigger as-child>
                    <InputGroupButton size="sm" variant="outline" class="rounded-full" aria-label="选择知识库频道">
                      <DatabaseIcon class="size-4" />
                      知识库
                      <Badge v-if="channelIds.length" variant="secondary" class="ml-1 h-4 px-1 text-[10px]">{{ channelIds.length }}</Badge>
                      <ChevronDownIcon />
                    </InputGroupButton>
                  </PopoverTrigger>
                  <PopoverContent align="start" class="w-80 p-0">
                    <div class="border-b px-3 py-2 text-xs font-medium text-muted-foreground">选择检索范围</div>
                    <div class="max-h-64 overflow-y-auto p-1">
                      <p v-if="availableChannels.length === 0" class="px-3 py-6 text-center text-xs text-muted-foreground">
                        暂无可用频道
                      </p>
                      <button v-for="channel in availableChannels" :key="channel.id" type="button"
                        class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-muted"
                        @click="toggleChannel(channel)">
                        <span class="flex size-4 items-center justify-center rounded border"
                          :class="channelIds.includes(channel.id) ? 'border-primary bg-primary text-primary-foreground' : ''">
                          <CheckIcon v-if="channelIds.includes(channel.id)" class="size-3" />
                        </span>
                        <HashIcon class="size-3.5 text-muted-foreground" />
                        <span class="truncate">{{ channel.name }}</span>
                        <span class="ml-auto shrink-0 text-[11px] text-muted-foreground">{{ channel.ownerLabel }}</span>
                      </button>
                    </div>
                    <div class="border-t px-3 py-1.5 text-[11px] text-muted-foreground">
                      不选择频道时，本次对话不检索知识库。
                    </div>
                  </PopoverContent>
                </Popover>
                <div class="flex-1"></div>
                <DropdownMenu>
                  <DropdownMenuTrigger as-child>
                    <InputGroupButton size="sm" variant="outline" :aria-label="`Model: ${currentModel?.name || '未选择'}`"
                      class="ml-auto rounded-full">
                      {{ currentModel?.name || '未配置模型' }}
                      <ChevronDownIcon />
                    </InputGroupButton>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start" class="w-48">
                    <DropdownMenuRadioGroup v-model="model">
                      <template v-for="(provider, index) in providers" :key="provider.name">
                        <DropdownMenuSeparator v-if="index > 0" />
                        <DropdownMenuGroup>
                          <DropdownMenuLabel>{{ provider.name }}</DropdownMenuLabel>
                          <DropdownMenuRadioItem v-for="item in provider.models" :key="item.id" :value="item.id">
                            {{ item.name }}
                          </DropdownMenuRadioItem>
                        </DropdownMenuGroup>
                      </template>
                    </DropdownMenuRadioGroup>
                  </DropdownMenuContent>
                </DropdownMenu>
                <DropdownMenu v-if="currentModel?.thinking">
                  <DropdownMenuTrigger as-child>
                    <InputGroupButton size="sm" variant="outline" :aria-label="`Effort: ${currentEffort.name}`"
                      class="rounded-full">
                      <span class="text-muted-foreground">推理强度</span>
                      {{ currentEffort.name }}
                      <ChevronDownIcon />
                    </InputGroupButton>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start" class="w-36">
                    <DropdownMenuGroup>
                      <DropdownMenuLabel>推理强度</DropdownMenuLabel>
                      <DropdownMenuRadioGroup v-model="effort">
                        <DropdownMenuRadioItem v-for="item in efforts" :key="item.id" :value="item.id">
                          {{ item.name }}
                        </DropdownMenuRadioItem>
                      </DropdownMenuRadioGroup>
                    </DropdownMenuGroup>
                  </DropdownMenuContent>
                </DropdownMenu>
                <InputGroupButton type="submit" variant="default" size="icon-sm" :disabled="!canSend"
                  class="ml-auto rounded-full">
                  <ArrowUpIcon />
                  <span class="sr-only">Send</span>
                </InputGroupButton>
              </InputGroupAddon>
            </InputGroup>
          </form>
          <div v-if="cacheInfo || contextInfo"
            class="flex items-center justify-end gap-5 px-3 pt-1.5 text-[11px] text-muted-foreground">
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger as-child>
                  <div v-if="cacheInfo" class="flex cursor-default items-center gap-1.5">
                    <div class="relative size-9">
                      <svg viewBox="0 0 20 20" class="size-9 -rotate-90">
                        <circle cx="10" cy="10" r="8" fill="none" stroke="currentColor" stroke-width="2" class="text-muted/40" />
                        <circle cx="10" cy="10" r="8" fill="none" stroke="currentColor" stroke-width="2"
                          stroke-linecap="round" class="text-primary transition-[stroke-dashoffset] duration-300"
                          :stroke-dasharray="ringLength" :stroke-dashoffset="cacheRingOffset" />
                      </svg>
                      <span class="absolute inset-0 flex items-center justify-center text-[9px] font-semibold text-foreground">
                        {{ cachePercent }}%
                      </span>
                    </div>
                    <div class="leading-tight">
                      <p>缓存命中率</p>
                      <p class="text-muted-foreground">{{ cacheHitLabel }}<span v-if="cacheInfo.hitType !== 'NONE'" class="ml-1">· {{ Math.round((cacheInfo.similarity ?? 0) * 100) }}%</span></p>
                    </div>
                  </div>
                </TooltipTrigger>
                <TooltipContent side="top" class="max-w-64 text-xs">
                  <p>本次：{{ cacheHitLabel }}<span v-if="cacheInfo?.hitType === 'SEMANTIC'">（相似度 {{ ((cacheInfo?.similarity ?? 0) * 100).toFixed(1) }}%）</span></p>
                  <p v-if="cacheInfo?.cacheKey">key：{{ cacheInfo.cacheKey }}</p>
                  <p>累计命中：{{ cacheInfo?.hits ?? 0 }} / {{ cacheInfo?.requests ?? 0 }} 次请求</p>
                </TooltipContent>
              </Tooltip>
              <Tooltip>
                <TooltipTrigger as-child>
                  <div v-if="contextInfo" class="flex cursor-default items-center gap-1.5">
                    <div class="relative size-9">
                      <svg viewBox="0 0 20 20" class="size-9 -rotate-90">
                        <circle cx="10" cy="10" r="8" fill="none" stroke="currentColor" stroke-width="2" class="text-muted/40" />
                        <circle cx="10" cy="10" r="8" fill="none" stroke="currentColor" stroke-width="2"
                          stroke-linecap="round" class="text-emerald-500 transition-[stroke-dashoffset] duration-300"
                          :stroke-dasharray="ringLength" :stroke-dashoffset="contextRingOffset" />
                      </svg>
                      <span class="absolute inset-0 flex items-center justify-center text-[9px] font-semibold text-foreground">
                        {{ contextPercent }}%
                      </span>
                    </div>
                    <div class="leading-tight">
                      <p>上下文占用</p>
                      <p class="text-muted-foreground">{{ contextInfo.usedTokens.toLocaleString() }} / {{ contextInfo.maxTokens.toLocaleString() }}</p>
                    </div>
                  </div>
                </TooltipTrigger>
                <TooltipContent side="top" class="max-w-64 text-xs">
                  <p>上下文：{{ contextInfo?.usedTokens.toLocaleString() }} / {{ contextInfo?.maxTokens.toLocaleString() }} tokens（{{ contextInfo?.percent }}%）</p>
                  <p v-if="contextInfo?.compressionThreshold">压缩阈值：{{ contextInfo.compressionThreshold }}%</p>
                  <p v-if="contextInfo?.compressed">已压缩历史对话<span v-if="contextInfo.droppedMessages">，裁剪 {{ contextInfo.droppedMessages }} 条</span></p>
                  <p v-else>未触发压缩<span v-if="contextInfo?.droppedMessages">，滑动窗口裁剪 {{ contextInfo.droppedMessages }} 条</span></p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>
        </div>
      </div>
      <ConversationOutline
        v-if="outlineRounds.length > 0"
        :rounds="outlineRounds"
        :active-id="activeRoundId"
        class="absolute top-24 right-2 z-30 hidden xl:block"
        @jump="jumpToRound"
      />
    </div>
  </MessageScrollerProvider>
</template>
<script setup lang="ts">
import {
  ArrowUpIcon,
  SparklesIcon,
  CheckIcon,
  CircleCheckIcon,
  ChevronDownIcon,
  DatabaseIcon,
  GlobeIcon,
  HashIcon,
  LoaderCircleIcon,
  MessageCircleDashedIcon,
  PaperclipIcon,
  FileTextIcon,
  TerminalIcon,
  XIcon,
} from '@lucide/vue'
import { createReusableTemplate } from '@vueuse/core'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import Markdown from '@/components/Markdown.vue'
import ConversationOutline from '@/components/ConversationOutline.vue'
import { Bubble, BubbleContent } from '@/components/ui/bubble'
import { CodeBlock, CodeBlockContent } from '@/components/ui/code-block'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupTextarea,
} from '@/components/ui/input-group'
import { Message, MessageContent } from '@/components/ui/message'
import {
  MessageScroller,
  MessageScrollerButton,
  MessageScrollerContent,
  MessageScrollerItem,
  MessageScrollerProvider,
  MessageScrollerViewport,
} from '@/components/ui/message-scroller'
import {
  Trace,
  TraceCollapsible,
  TraceCollapsibleContent,
  TraceCollapsibleTrigger,
  TraceIcon,
  TraceLine,
  TraceTitle,
} from '@/components/ui/trace'
import FlickeringGrid from '@/components/ui/flickering-grid/FlickeringGrid.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { chatApi, channelApi, discoverApi, profileApi } from '@/api'
import type { ChatAttachmentPayload, ChatAttachmentResult, ChatCacheInfo, ChatContextInfo } from '@/api/chat'
import type { ChannelCard, ChatCapabilities, ChatConversationDetail, PublicChatModel } from '@/types/api'

// The parts of an agent turn, shaped like AI SDK UI message parts.
type ChatPart
  = | { type: 'reasoning', text: string, state: 'streaming' | 'done' }
  | {
    type: 'tool-runCommand'
    toolCallId?: string
    state: 'input-available' | 'output-available' | 'output-error'
    input: { command: string }
    output?: { stdout: string }
    errorText?: string
  }
  | { type: 'text', text: string }

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  parts: ChatPart[]
  attachments?: { name: string, kind: string }[]
}

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()

function setActiveConversation(id: string | null, title: string) {
  chatStore.$patch({
    activeConversationId: id,
    activeTitle: title.trim() || '新对话',
  })
}
const messages = ref<ChatMessage[]>([])
const chatRoot = ref<HTMLElement | null>(null)
const activeRoundId = ref<string | undefined>()
const conversationId = ref<string | null>(null)
const status = ref<'ready' | 'submitted' | 'streaming'>('ready')
const temporary = computed(() => route.name === 'ChatTemporary')
const capabilities = ref<ChatCapabilities>({ webSearchEnabled: false, allowUserWebSearch: false, webSearchAvailable: false })
const cacheInfo = ref<ChatCacheInfo | null>(null)
const contextInfo = ref<ChatContextInfo | null>(null)
const webSearch = ref(true)
let textBuffer = ''
let flushTimer: ReturnType<typeof setTimeout> | undefined
const draft = ref<string | null>(null)
const formRef = ref<HTMLFormElement | null>(null)
// Open state of each reasoning row, set once the person toggles it.
const reasoningOpen = reactive<Record<string, boolean | undefined>>({})

const busy = computed(() => status.value !== 'ready')
const cachePercent = computed(() => Math.round(Math.max(0, Math.min(1, cacheInfo.value?.hitRate ?? 0)) * 100))
const contextPercent = computed(() => Math.round(Math.max(0, Math.min(100, contextInfo.value?.percent ?? 0))))
const ringLength = 2 * Math.PI * 8
const cacheRingOffset = computed(() => ringLength * (1 - cachePercent.value / 100))
const contextRingOffset = computed(() => ringLength * (1 - contextPercent.value / 100))
const cacheHitLabel = computed(() => cacheInfo.value?.hitType === 'SEMANTIC'
  ? '语义缓存'
  : cacheInfo.value?.hitType === 'RETRIEVAL' ? '检索缓存' : '未命中')
const text = computed({
  get: () => draft.value ?? '',
  set: (value: string) => {
    draft.value = value
  },
})
const canSend = computed(() => (text.value.trim().length > 0 || chatAttachments.value.length > 0)
  && !busy.value && !!currentModel.value)

const chatAttachments = ref<ChatAttachmentPayload[]>([])
const attachmentPreviews = ref<ChatAttachmentResult[]>([])
const uploadingAttachment = ref(false)
const attachmentInput = ref<HTMLInputElement | null>(null)
const dragActive = ref(false)
const supportsAttachments = computed(() => !!currentModel.value?.supportsVision)
const imagePreview = (index: number) => {
  const preview = attachmentPreviews.value[index]
  return preview?.kind === 'IMAGE' ? preview.dataUrl : undefined
}

async function addFiles(files: FileList | File[]) {
  if (!supportsAttachments.value) {
    toast.error('当前模型不支持文件上传，请选择支持图片输入的模型')
    return
  }
  const list = Array.from(files)
  if (!list.length)
    return
  if (chatAttachments.value.length + list.length > 6) {
    toast.error('最多添加 6 个附件')
    return
  }
  uploadingAttachment.value = true
  try {
    for (const file of list) {
      const allowedImage = ['image/jpeg', 'image/gif', 'image/png', 'image/webp'].includes(file.type)
      const allowedDoc = /\.(pdf|docx|xlsx|pptx|txt|md|markdown|csv|json|html?)$/i.test(file.name)
      if (!allowedImage && !allowedDoc) {
        toast.error(`不支持的文件类型：${file.name}`)
        continue
      }
      const result = await chatApi.uploadAttachment(file)
      const payload: ChatAttachmentPayload = result.kind === 'IMAGE'
        ? { type: 'IMAGE', name: result.name, mimeType: result.mimeType, dataUrl: result.dataUrl }
        : { type: 'TEXT', name: result.name, mimeType: result.mimeType, text: result.text }
      chatAttachments.value.push(payload)
      attachmentPreviews.value.push(result)
    }
  }
  catch (err: any) {
    toast.error(err?.message || '附件上传失败')
  }
  finally {
    uploadingAttachment.value = false
    if (attachmentInput.value)
      attachmentInput.value.value = ''
  }
}

function removeAttachment(index: number) {
  chatAttachments.value.splice(index, 1)
  attachmentPreviews.value.splice(index, 1)
}

function onAttachmentInput(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files)
    void addFiles(input.files)
}

function onDragEnter(event: DragEvent) {
  if (!supportsAttachments.value)
    return
  event.preventDefault()
  if (event.dataTransfer)
    event.dataTransfer.dropEffect = 'copy'
  dragActive.value = true
}

function onDragOver(event: DragEvent) {
  if (!supportsAttachments.value)
    return
  event.preventDefault()
  if (event.dataTransfer)
    event.dataTransfer.dropEffect = 'copy'
}

function onDragLeave(event: DragEvent) {
  if (event.currentTarget === event.target)
    dragActive.value = false
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  dragActive.value = false
  if (event.dataTransfer?.files)
    void addFiles(event.dataTransfer.files)
}


function scheduleTextFlush(message: ChatMessage) {
  if (flushTimer)
    return
  flushTimer = setTimeout(() => {
    flushTimer = undefined
    flushTextBuffer(message)
  }, 55)
}

function flushTextBuffer(message: ChatMessage) {
  if (!textBuffer)
    return
  const part = message.parts.find(item => item.type === 'text') as Extract<ChatPart, { type: 'text' }> | undefined
  if (part)
    part.text += textBuffer
  else
    message.parts.push({ type: 'text', text: textBuffer })
  textBuffer = ''
}

async function onSubmit() {
  mentionOpen.value = false
  if (!canSend.value || !currentModel.value)
    return
  const pendingAttachments = [...chatAttachments.value]
  const userMessage: ChatMessage = {
    id: `user-${Date.now()}`,
    role: 'user',
    parts: [{ type: 'text', text: text.value.trim() }],
    attachments: pendingAttachments.map(item => ({ name: item.name, kind: item.type })),
  }
  const assistant = reactive<ChatMessage>({
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    parts: [],
  })
  const history = [...messages.value, userMessage]
    .map(message => ({ role: message.role, content: getMessageText(message) }))
    .filter((message, index, all) => message.content.trim().length > 0 || index === all.length - 1)
  if (!temporary.value && !conversationId.value)
    setActiveConversation(null, (text.value.trim() || '图片消息').slice(0, 60))
  messages.value.push(userMessage, assistant)
  chatAttachments.value = []
  attachmentPreviews.value = []
  draft.value = ''
  status.value = 'submitted'
  let finished = false
  try {
    await chatApi.stream({
      modelId: currentModel.value.id,
      messages: history,
      channelIds: channelIds.value.length ? channelIds.value : undefined,
      effort: currentModel.value.thinking ? effort.value : undefined,
      thinking: currentModel.value.thinking || undefined,
      conversationId: temporary.value ? undefined : (conversationId.value || undefined),
      temporary: temporary.value || undefined,
      webSearch: capabilities.value.webSearchAvailable ? webSearch.value : undefined,
      attachments: pendingAttachments.length ? pendingAttachments : undefined,
    }, {
      onStart: (data) => {
        status.value = 'streaming'
        if (!temporary.value && data.conversationId && !conversationId.value) {
          conversationId.value = String(data.conversationId)
          setActiveConversation(conversationId.value, chatStore.activeTitle)
          void router.replace({ name: 'ChatConversation', params: { conversationId: conversationId.value } })
        }
      },
      onCache: (data) => {
        cacheInfo.value = data
      },
      onContext: (data) => {
        contextInfo.value = data
      },
      onReasoning: (delta) => {
        status.value = 'streaming'
        let part = assistant.parts.find(item => item.type === 'reasoning') as Extract<ChatPart, { type: 'reasoning' }> | undefined
        if (!part) {
          part = { type: 'reasoning', text: '', state: 'streaming' }
          assistant.parts.push(part)
        }
        part.text += delta
      },
      onText: (delta) => {
        status.value = 'streaming'
        let part = assistant.parts.find(item => item.type === 'text') as Extract<ChatPart, { type: 'text' }> | undefined
        if (!part) {
          part = { type: 'text', text: '' }
          assistant.parts.push(part)
        }
        textBuffer += delta
        scheduleTextFlush(assistant)
      },
      onTool: (data) => {
        status.value = 'streaming'
        let part = assistant.parts.find(item => item.type === 'tool-runCommand' && item.toolCallId === data.id) as ToolPart | undefined
        if (!part) {
          part = {
            type: 'tool-runCommand',
            toolCallId: data.id,
            state: 'input-available',
            input: { command: `${data.name} ${data.arguments ?? ''}`.trim() },
          }
          assistant.parts.push(part)
        }
        if (data.status === 'running') {
          part.state = 'input-available'
          part.input = { command: `${data.name} ${data.arguments ?? ''}`.trim() }
        }
        if (data.status === 'done') {
          part.state = 'output-available'
          part.output = { stdout: data.output ?? '' }
        }
      },
      onDone: () => {
        finished = true
        flushTextBuffer(assistant)
        refreshConversationTitle()
        // The LLM title is generated asynchronously after the answer; poll a few times.
        if (!temporary.value) {
          for (const delay of [1500, 3500, 7000])
            window.setTimeout(refreshConversationTitle, delay)
        }
      },
      onError: (message) => {
        finished = true
        flushTextBuffer(assistant)
        toast.error(message)
        assistant.parts.push({ type: 'text', text: `\n\n> 请求失败：${message}` })
      },
    })
  } catch (err: any) {
    toast.error(err?.message || '聊天请求失败')
    if (assistant.parts.length === 0)
      assistant.parts.push({ type: 'text', text: `> 请求失败：${err?.message || '未知错误'}` })
  } finally {
    for (const part of assistant.parts) {
      if (part.type === 'reasoning')
        part.state = 'done'
      if (part.type === 'tool-runCommand' && part.state === 'input-available')
        part.state = 'output-error'
    }
    status.value = 'ready'
    if (!finished && assistant.parts.length === 0)
      assistant.parts.push({ type: 'text', text: '> 模型未返回内容' })
  }
}

function onKeydown(event: KeyboardEvent) {
  if (mentionOpen.value) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      if (mentionItems.value.length)
        mentionIndex.value = (mentionIndex.value + 1) % mentionItems.value.length
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      if (mentionItems.value.length)
        mentionIndex.value = (mentionIndex.value - 1 + mentionItems.value.length) % mentionItems.value.length
      return
    }
    if (event.key === 'Tab') {
      event.preventDefault()
      const channel = mentionItems.value[mentionIndex.value]
      if (channel)
        selectMention(channel, true)
      return
    }
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      const channel = mentionItems.value[mentionIndex.value]
      if (channel)
        selectMention(channel, false)
      else
        mentionOpen.value = false
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      mentionOpen.value = false
      return
    }
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    formRef.value?.requestSubmit()
  }
}

function getMessageText(message: ChatMessage) {
  return message.parts
    .map(part => (part.type === 'text' ? part.text : ''))
    .join('')
}

const outlineRounds = computed(() => {
  const rounds: { id: string, index: number, user: string, assistant: string }[] = []
  messages.value.forEach((message, index) => {
    if (message.role !== 'user')
      return
    const answer = messages.value[index + 1]?.role === 'assistant' ? messages.value[index + 1] : undefined
    rounds.push({
      id: message.id,
      index: rounds.length,
      user: getMessageText(message).trim() || (message.attachments?.length ? '[图片/文件消息]' : '[空消息]'),
      assistant: answer ? getMessageText(answer).replace(/\s+/g, ' ').trim().slice(0, 220) : '',
    })
  })
  return rounds
})

let spyViewport: HTMLElement | null = null

function updateActiveRound() {
  const viewport = spyViewport
  if (!viewport || outlineRounds.value.length === 0) {
    activeRoundId.value = undefined
    return
  }
  const threshold = viewport.getBoundingClientRect().top + Math.min(96, viewport.clientHeight * 0.18)
  let current = outlineRounds.value[0]!.id
  for (const round of outlineRounds.value) {
    const element = viewport.querySelector<HTMLElement>(`[data-message-id="${round.id}"]`)
    if (element && element.getBoundingClientRect().top <= threshold)
      current = round.id
  }
  if (viewport.scrollTop + viewport.clientHeight >= viewport.scrollHeight - 4)
    current = outlineRounds.value.at(-1)!.id
  activeRoundId.value = current
}

function unbindScrollSpy() {
  if (spyViewport)
    spyViewport.removeEventListener('scroll', updateActiveRound)
  spyViewport = null
}

async function bindScrollSpy() {
  unbindScrollSpy()
  await nextTick()
  const viewport = chatRoot.value?.querySelector<HTMLElement>('[data-slot="message-scroller-viewport"]')
  if (!viewport) {
    activeRoundId.value = undefined
    return
  }
  spyViewport = viewport
  viewport.addEventListener('scroll', updateActiveRound, { passive: true })
  updateActiveRound()
}

function jumpToRound(id: string) {
  activeRoundId.value = id
  const element = chatRoot.value?.querySelector<HTMLElement>(`[data-message-id="${id}"]`)
  element?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  window.setTimeout(updateActiveRound, 500)
}

watch(() => [messages.value.length, conversationId.value], () => {
  void bindScrollSpy()
})

type ToolPart = Extract<ChatPart, { type: 'tool-runCommand' }>

const [DefineReasoningRow, ReasoningRow] = createReusableTemplate<{
  id: string
  text: string
  running: boolean
}>()
const [DefineToolRow, ToolRow] = createReusableTemplate<{
  part: ToolPart
  streaming: boolean
}>()

function getSteps(message: ChatMessage) {
  return message.parts.filter(
    part => part.type === 'reasoning' || part.type === 'tool-runCommand',
  ) as Exclude<ChatPart, { type: 'text' }>[]
}

const availableModels = ref<PublicChatModel[]>([])
const model = ref('')
const efforts = [
  { id: 'low', name: 'Low' },
  { id: 'medium', name: 'Medium' },
  { id: 'high', name: 'High' },
  { id: 'max', name: 'Max' },
]
const effort = ref(efforts[1]!.id)
const providers = computed(() => {
  const groups = new Map<string, PublicChatModel[]>()
  for (const item of availableModels.value) {
    if (!groups.has(item.provider))
      groups.set(item.provider, [])
    groups.get(item.provider)!.push(item)
  }
  return [...groups.entries()].map(([name, models]) => ({ name, models }))
})
const currentModel = computed(() => availableModels.value.find(item => String(item.id) === model.value))
watch(() => currentModel.value?.id, () => {
  if (!supportsAttachments.value && chatAttachments.value.length) {
    chatAttachments.value = []
    attachmentPreviews.value = []
  }
})
const currentEffort = computed(() => efforts.find(item => item.id === effort.value) ?? efforts[1]!)

interface PickableChannel {
  id: number
  name: string
  ownerLabel: string
}

const channelPickerOpen = ref(false)
const availableChannels = ref<PickableChannel[]>([])
const channelIds = ref<number[]>([])
const selectedChannels = computed(() =>
  availableChannels.value.filter(channel => channelIds.value.includes(channel.id)))

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const mentionOpen = ref(false)
const mentionQuery = ref('')
const mentionIndex = ref(0)
let mentionStart = 0
let mentionEnd = 0
const mentionItems = computed(() => {
  const query = mentionQuery.value.trim().toLowerCase()
  return availableChannels.value
    .filter(channel => !query
      || channel.name.toLowerCase().includes(query)
      || channel.ownerLabel.toLowerCase().includes(query))
    .slice(0, 8)
})

watch(mentionItems, items => {
  if (mentionIndex.value >= items.length)
    mentionIndex.value = 0
})

function onComposerInput(event: Event) {
  const target = event.target as HTMLTextAreaElement
  const caret = target.selectionStart ?? target.value.length
  const before = target.value.slice(0, caret)
  const match = before.match(/(?:^|\s)@([^\s@]*)$/)
  if (!match) {
    mentionOpen.value = false
    return
  }
  mentionQuery.value = match[1] ?? ''
  mentionStart = caret - (match[1]?.length ?? 0) - 1
  mentionEnd = caret
  mentionIndex.value = 0
  mentionOpen.value = true
}

function onComposerBlur() {
  window.setTimeout(() => {
    mentionOpen.value = false
  }, 120)
}

function selectMention(channel: PickableChannel, keepOpen: boolean) {
  const start = Math.min(mentionStart, text.value.length)
  const end = Math.min(mentionEnd, text.value.length)
  toggleChannel(channel)
  text.value = text.value.slice(0, start) + text.value.slice(end)
  mentionStart = start
  mentionEnd = start
  mentionQuery.value = ''
  if (keepOpen && mentionItems.value.length > 1)
    mentionIndex.value = (mentionIndex.value + 1) % mentionItems.value.length
  else
    mentionIndex.value = 0
  mentionOpen.value = keepOpen
  void nextTick(() => {
    const el = (textareaRef.value as unknown as { $el?: HTMLTextAreaElement } | null)?.$el
      ?? (textareaRef.value as unknown as HTMLTextAreaElement | null)
    if (!(el instanceof HTMLTextAreaElement)) return
    el.focus()
    const caret = Math.min(start, el.value.length)
    el.setSelectionRange(caret, caret)
  })
}

function toggleChannel(channel: PickableChannel) {
  if (channelIds.value.includes(channel.id))
    channelIds.value = channelIds.value.filter(id => id !== channel.id)
  else
    channelIds.value = [...channelIds.value, channel.id]
}

async function loadModels() {
  try {
    availableModels.value = await chatApi.models()
    if (!model.value && availableModels.value.length)
      model.value = String(availableModels.value[0]!.id)
  } catch (err: any) {
    toast.error(err?.message || '加载模型列表失败')
  }
}

async function loadChannels() {
  try {
    const profile = await profileApi.me()
    const channels: PickableChannel[] = profile.channels
      .map(item => ({ id: Number(item.id), name: item.name, ownerLabel: '个人' }))
    for (const team of profile.teams) {
      try {
        const list: ChannelCard[] = await channelApi.list('TEAM', team.id)
        channels.push(...list.map(item => ({ id: Number(item.id), name: item.name, ownerLabel: team.name || team.slug })))
      } catch {
        // A team the viewer left or cannot read should not block chat.
      }
    }
    const subscriptions = await discoverApi.subscriptions().catch(() => [])
    for (const item of subscriptions) {
      if (!channels.some(channel => channel.id === Number(item.id)))
        channels.push({ id: Number(item.id), name: item.name, ownerLabel: item.ownerSlug })
    }
    availableChannels.value = channels
  } catch {
    // Channel selection is optional.
  }
}

function refreshConversationTitle() {
  if (temporary.value)
    return
  const id = conversationId.value
  if (!id)
    return
  void chatStore.loadConversations().then(() => {
    if (String(conversationId.value) !== String(id))
      return
    const current = chatStore.conversations.find(item => String(item.id) === String(id))
    if (current)
      setActiveConversation(current.id, current.title)
  }).catch(() => {})
}

function resetConversation() {
  textBuffer = ''
  if (flushTimer) {
    clearTimeout(flushTimer)
    flushTimer = undefined
  }
  messages.value = []
  activeRoundId.value = undefined
  conversationId.value = null
  setActiveConversation(null, temporary.value ? '临时对话' : '新对话')
  cacheInfo.value = null
  contextInfo.value = null
  status.value = 'ready'
  draft.value = ''
}

async function loadConversation(id: string) {
  try {
    const detail: ChatConversationDetail = await chatApi.conversation(id)
    cacheInfo.value = null
    contextInfo.value = null
    conversationId.value = String(detail.id)
    setActiveConversation(conversationId.value, detail.title)
    channelIds.value = detail.channelIds ?? []
    messages.value = detail.messages.map((message, index) => ({
      id: `${message.role}-${index}`,
      role: message.role,
      parts: message.role === 'assistant'
        ? [
            ...(message.reasoning ? [{ type: 'reasoning' as const, text: message.reasoning, state: 'done' as const }] : []),
            ...(message.content ? [{ type: 'text' as const, text: message.content }] : []),
          ]
        : [{ type: 'text' as const, text: message.content }],
    }))
    if (detail.modelId && availableModels.value.some(item => item.id === detail.modelId))
      model.value = String(detail.modelId)
    status.value = 'ready'
  } catch (err: any) {
    toast.error(err?.message || '加载对话失败')
    void router.replace({ name: 'Chat' })
  }
}

function syncRoute() {
  const raw = route.params.conversationId
  const id = raw ? String(raw) : null
  if (!id) {
    resetConversation()
    return
  }
  if (id === conversationId.value)
    return
  void loadConversation(id)
}

watch(() => route.params.conversationId, syncRoute)
watch(() => route.name, () => {
  if (route.name === 'ChatTemporary')
    resetConversation()
})

function handleReset() {
  resetConversation()
}

onMounted(async () => {
  window.addEventListener('chat:reset', handleReset)
  await loadModels()
  await loadChannels()
  try {
    capabilities.value = await chatApi.capabilities()
    webSearch.value = capabilities.value.webSearchAvailable
  } catch { /* capabilities are optional */ }
  syncRoute()
  void bindScrollSpy()
})

onUnmounted(() => {
  window.removeEventListener('chat:reset', handleReset)
  unbindScrollSpy()
  if (flushTimer)
    clearTimeout(flushTimer)
})

</script>
