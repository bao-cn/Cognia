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

  <MessageScrollerProvider>
    <div
      class="relative flex h-[calc(98dvh-var(--header-height))] min-h-0 w-full flex-col overflow-hidden [[data-slot=preview]:has(&)]:bg-background [[data-slot=preview]:has(&)>div]:p-0!">
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
          <div v-if="selectedChannels.length" class="mb-2 flex flex-wrap gap-1.5">
            <Badge v-for="channel in selectedChannels" :key="channel.id" variant="secondary" class="gap-1">
              <HashIcon class="size-3" />{{ channel.ownerLabel }} / {{ channel.name }}
              <button type="button" class="ml-1 text-muted-foreground hover:text-foreground" @click="toggleChannel(channel)">
                <XIcon class="size-3" />
              </button>
            </Badge>
          </div>
          <form ref="formRef" class="w-full" @submit.prevent="onSubmit">
            <InputGroup class="rounded-3xl bg-background">
              <InputGroupTextarea v-model="text" placeholder="Ask the agent..." aria-label="Message" class="min-h-14"
                @keydown="onKeydown" />
              <InputGroupAddon align="block-end" class="pt-1">
                <InputGroupButton size="icon-sm" variant="outline" class="rounded-full" aria-label="Attach">
                  <PlusIcon />
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
        </div>
      </div>
    </div>
  </MessageScrollerProvider>
</template>
<script setup lang="ts">
import {
  ArrowUpIcon,
  CheckIcon,
  CircleCheckIcon,
  ChevronDownIcon,
  DatabaseIcon,
  HashIcon,
  LoaderCircleIcon,
  MessageCircleDashedIcon,
  PlusIcon,
  TerminalIcon,
  XIcon,
} from '@lucide/vue'
import { createReusableTemplate } from '@vueuse/core'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import Markdown from '@/components/Markdown.vue'
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
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { chatApi, channelApi, profileApi } from '@/api'
import type { ChannelCard, ChatConversationDetail, PublicChatModel } from '@/types/api'

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
}

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const messages = ref<ChatMessage[]>([])
const conversationId = ref<string | null>(null)
const status = ref<'ready' | 'submitted' | 'streaming'>('ready')
const draft = ref<string | null>(null)
const formRef = ref<HTMLFormElement | null>(null)
// Open state of each reasoning row, set once the person toggles it.
const reasoningOpen = reactive<Record<string, boolean | undefined>>({})

const busy = computed(() => status.value !== 'ready')
const text = computed({
  get: () => draft.value ?? '',
  set: (value: string) => {
    draft.value = value
  },
})
const canSend = computed(() => text.value.trim().length > 0 && !busy.value && !!currentModel.value)

async function onSubmit() {
  if (!canSend.value || !currentModel.value)
    return
  const userMessage: ChatMessage = {
    id: `user-${Date.now()}`,
    role: 'user',
    parts: [{ type: 'text', text: text.value.trim() }],
  }
  const assistant = reactive<ChatMessage>({
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    parts: [],
  })
  const history = [...messages.value, userMessage]
    .map(message => ({ role: message.role, content: getMessageText(message) }))
    .filter(message => message.content.trim().length > 0)
  messages.value.push(userMessage, assistant)
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
      conversationId: conversationId.value || undefined,
    }, {
      onStart: (data) => {
        status.value = 'streaming'
        if (data.conversationId && !conversationId.value) {
          conversationId.value = String(data.conversationId)
          void router.replace({ name: 'ChatConversation', params: { conversationId: conversationId.value } })
        }
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
        part.text += delta
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
        void chatStore.loadConversations().catch(() => {})
      },
      onError: (message) => {
        finished = true
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
    availableChannels.value = channels
  } catch {
    // Channel selection is optional.
  }
}

function resetConversation() {
  messages.value = []
  conversationId.value = null
  status.value = 'ready'
  draft.value = ''
}

async function loadConversation(id: string) {
  try {
    const detail: ChatConversationDetail = await chatApi.conversation(id)
    conversationId.value = String(detail.id)
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
  if (id === conversationId.value)
    return
  if (!id) {
    resetConversation()
    return
  }
  void loadConversation(id)
}

watch(() => route.params.conversationId, syncRoute)

function handleReset() {
  resetConversation()
}

onMounted(async () => {
  window.addEventListener('chat:reset', handleReset)
  await loadModels()
  await loadChannels()
  syncRoute()
})

onUnmounted(() => {
  window.removeEventListener('chat:reset', handleReset)
})

</script>
