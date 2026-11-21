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
      <div v-if="conversationLoading" class="absolute inset-0 z-40 flex items-center justify-center bg-background">
        <div class="flex flex-col items-center gap-3 text-muted-foreground">
          <LoaderCircleIcon class="size-6 animate-spin" />
          <p class="text-xs">加载中…</p>
        </div>
      </div>
      <div class="mx-auto flex h-full min-h-0 w-full max-w-4xl flex-col gap-4">
        <div class="min-h-0 flex-1">
          <Empty v-if="messages.length === 0" class="h-full">
            <FlickeringGrid
              class="absolute top-0 left-0 w-full h-full mask-[radial-gradient(450px_circle_at_center,white,transparent)]"
              :square-size="8" :grid-gap="4" :color="'#9C9C9C'" :max-opacity="0.15" :flicker-chance="0.1" />
          </Empty>
          <MessageScroller v-else class="h-full min-h-0">
            <MessageScrollerViewport class="mt-4 scrollbar-none">
              <MessageScrollerContent :aria-busy="busy" class="gap-6 px-8 py-0">
                <template v-for="(message, index) in messages" :key="message.id">
                  <MessageScrollerItem v-if="message.role === 'user'" :message-id="message.id" scroll-anchor
                    class="ms-anim-slide-up">
                    <Message align="end">
                      <MessageContent>
                        <Bubble align="end" variant="muted">
                          <BubbleContent>{{ getMessageText(message) }}</BubbleContent>
                        </Bubble>
                        <div v-if="message.attachments?.length" class="mt-1 flex flex-wrap justify-end gap-1">
                          <template v-for="attachment in message.attachments" :key="attachment.name">
                            <img v-if="attachment.kind === 'IMAGE' && attachment.url" :src="attachment.url"
                              :alt="attachment.name"
                              class="size-24 cursor-zoom-in rounded-lg border object-cover"
                              @click="openImageUrl(attachment.url)">
                            <Badge v-else variant="outline" class="max-w-56 gap-1 text-[11px]">
                              <FileTextIcon class="size-3 shrink-0" />
                              <span class="truncate">{{ attachment.name }}</span>
                            </Badge>
                          </template>
                        </div>
                        <MessageFooter class="mt-1 gap-0.5 opacity-0 transition-opacity group-hover/message:opacity-100">
                          <TooltipProvider :delay-duration="150">
                            <Tooltip>
                              <TooltipTrigger as-child>
                                <Button type="button" variant="ghost" size="icon-xs" aria-label="复制" @click="copyMessage(message)">
                                  <CopyIcon />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>复制</TooltipContent>
                            </Tooltip>
                            <Tooltip>
                              <TooltipTrigger as-child>
                                <Button type="button" variant="ghost" size="icon-xs" aria-label="编辑" @click="editMessage(message, index)">
                                  <PencilIcon />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>编辑</TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        </MessageFooter>
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
                        <CitationMarkdown
                          v-if="message.role === 'assistant'"
                          :text="getMessageText(message)"
                          :citations="message.citations"
                          :streaming="busy && message === messages.at(-1)"
                          @cite="index => scrollToCitation(message, index)"
                        />
                        <Markdown v-else :text="getMessageText(message)" :streaming="busy && message === messages.at(-1)" />
                        <MessageCitations
                          v-if="message.role === 'assistant' && message.citations?.length && !(busy && message === messages.at(-1))"
                          :message-id="message.id"
                          :sources="message.citations"
                          :reveal-index="message.revealIndex"
                          @preview="openCitationPreview"
                        />
                        <MessageFooter
                          v-if="!(busy && message === messages.at(-1))"
                          class="mt-1 gap-0.5 opacity-0 transition-opacity group-hover/message:opacity-100"
                        >
                          <TooltipProvider :delay-duration="150">
                            <Tooltip>
                              <TooltipTrigger as-child>
                                <Button type="button" variant="ghost" size="icon-xs" aria-label="有帮助"
                                  :class="message.feedback === 'LIKE' ? 'text-emerald-500' : ''"
                                  @click="submitFeedback(message, 'LIKE')">
                                  <ThumbsUpIcon :fill="message.feedback === 'LIKE' ? 'currentColor' : 'none'" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>有帮助</TooltipContent>
                            </Tooltip>
                            <Tooltip>
                              <TooltipTrigger as-child>
                                <Button type="button" variant="ghost" size="icon-xs" aria-label="没帮助"
                                  :class="message.feedback === 'DISLIKE' ? 'text-destructive' : ''"
                                  @click="submitFeedback(message, 'DISLIKE')">
                                  <ThumbsDownIcon :fill="message.feedback === 'DISLIKE' ? 'currentColor' : 'none'" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>没帮助</TooltipContent>
                            </Tooltip>
                            <Tooltip>
                              <TooltipTrigger as-child>
                                <Button type="button" variant="ghost" size="icon-xs" :aria-label="message.favorited ? '取消收藏' : '收藏'"
                                  @click="toggleMessageFavorite(message)">
                                  <StarIcon :fill="message.favorited ? 'currentColor' : 'none'" :class="message.favorited ? 'text-amber-500' : ''" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>{{ message.favorited ? '取消收藏' : '收藏' }}</TooltipContent>
                            </Tooltip>
                            <Tooltip>
                              <TooltipTrigger as-child>
                                <Button type="button" variant="ghost" size="icon-xs" aria-label="复制" @click="copyMessage(message)">
                                  <CopyIcon />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>复制</TooltipContent>
                            </Tooltip>
                            <Tooltip>
                              <TooltipTrigger as-child>
                                <Button type="button" variant="ghost" size="icon-xs" aria-label="分叉到新对话" @click="forkMessage(index)">
                                  <GitBranchIcon />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>分叉到新对话</TooltipContent>
                            </Tooltip>
                            <Tooltip v-if="index === messages.length - 1">
                              <TooltipTrigger as-child>
                                <Button type="button" variant="ghost" size="icon-xs" aria-label="重新生成"
                                  @click="regenerateMessage(index)">
                                  <RotateCcwIcon />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>重新生成</TooltipContent>
                            </Tooltip>
                            <Tooltip v-if="message.traceId && canDebug">
                              <TooltipTrigger as-child>
                                <Button type="button" variant="ghost" size="icon-xs" aria-label="查看链路"
                                  @click="debugStore.showTrace(message.traceId!)">
                                  <ActivityIcon />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>查看开发链路</TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        </MessageFooter>
                      </MessageContent>
                    </Message>
                  </MessageScrollerItem>
                </template>
                <div data-composer-spacer :style="{ height: `${trayHeight}px` }" class="shrink-0 transition-[height] duration-200" aria-hidden="true" />
              </MessageScrollerContent>
            </MessageScrollerViewport>
            <MessageScrollerButton :style="{ bottom: trayVisible ? `${16 + trayHeight}px` : undefined, transition: 'bottom 240ms cubic-bezier(0.22, 1, 0.36, 1)' }" />
          </MessageScroller>
        </div>
        <div
          :class="messages.length === 0
            ? 'absolute inset-x-0 top-1/2 z-10 mx-auto w-full max-w-4xl -translate-y-1/2 px-8'
            : 'shrink-0 p-8 pt-0'">
          <div v-if="messages.length === 0" class="mb-6 text-left">
            <img
              :src="greetingLogo"
              alt="Cognia"
              class="animate-pop-in mb-4 size-15 select-none"
              draggable="false"
            />
            <p class="animate-pop-in text-2xl font-semibold leading-relaxed tracking-tight">
              <span class="block">你好，{{ userStore.displayName }}。</span>
              <span class="block">今天要创造点什么？</span>
            </p>
          </div>
          <div v-if="truncatedNotice"
            class="mx-auto mb-2 flex w-full max-w-3xl items-center justify-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-xs text-destructive">
            <span>{{ truncatedNotice }}</span>
            <button type="button" class="rounded p-0.5 hover:bg-destructive/10" aria-label="关闭提示" @click="truncatedNotice = ''">
              <XIcon class="size-3" />
            </button>
          </div>
          <div v-if="archived"
            class="mx-auto mb-2 flex w-full max-w-3xl flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
            <span>该对话已归档，恢复后才能继续对话。</span>
            <Button variant="outline" size="sm" class="h-7" @click="restoreArchived">恢复对话</Button>
          </div>
          <div v-if="!canUseChat" class="mx-auto mb-2 w-full max-w-3xl rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
            当前角色没有发起对话的权限，只能查看历史对话。
          </div>
          <div v-if="editingIndex !== null"
            class="mx-auto mb-2 flex w-full max-w-3xl items-center justify-between gap-2 rounded-lg border border-primary/20 bg-primary/5 px-3 py-2 text-xs text-primary">
            <span>正在编辑第 {{ editingIndex + 1 }} 条消息，发送后将重新生成后续回答</span>
            <button type="button" class="rounded p-0.5 hover:bg-primary/10" @click="cancelEdit">取消</button>
          </div>
          <form ref="formRef" class="group relative w-full" @submit.prevent="onSubmit()">
            <Button
              v-if="hasTrayContent && !mentionOpen"
              type="button"
              variant="ghost"
              class="absolute right-2.5 top-2 z-20 flex size-7 items-center justify-center rounded-full opacity-0 transition-all group-hover:opacity-100 focus-visible:opacity-100"
              :aria-label="trayOpen ? '收起状态栏' : '展开状态栏'"
              @click="toggleTray">
              <ChevronDownIcon v-if="trayOpen" class="size-4" />
              <ChevronUpIcon v-else class="size-4" />
            </Button>
            <Transition name="composer-tray">
              <div
                v-if="trayVisible"
                ref="trayRef"
                data-composer-tray
                class="absolute inset-x-0 bottom-full z-0 -mb-3.5 composer-tray-surface rounded-t-2xl px-3 pb-7 pt-3">
                <div class="flex max-h-[32vh] flex-col gap-2 overflow-y-auto">
                  <div v-if="webSearchEnabled && capabilities.webSearchAvailable" class="flex flex-wrap items-center gap-2">
                    <Badge variant="secondary" class="gap-1">
                      <GlobeIcon class="size-3" />联网搜索已开启
                    </Badge>
                  </div>
                  <div v-if="selectedChannels.length" class="flex flex-wrap gap-1.5">
                    <Badge v-for="channel in selectedChannels" :key="channel.id" variant="secondary" class="gap-1">
                      <HashIcon class="size-3" />{{ channel.ownerLabel }} / {{ channel.name }}
                      <button type="button" class="ml-1 text-muted-foreground hover:text-foreground" @click="toggleChannel(channel)">
                        <XIcon class="size-3" />
                      </button>
                    </Badge>
                  </div>
                  <div v-if="chatAttachments.length || uploadingAttachment" class="flex flex-wrap gap-2">
                    <div v-for="(attachment, index) in chatAttachments" :key="`${attachment.name}-${index}`"
                      class="flex max-w-64 items-center gap-2 rounded-xl border bg-background p-1.5 pr-2">
                      <img v-if="attachment.type === 'IMAGE' && imagePreview(index)" :src="imagePreview(index)"
                        :alt="attachment.name" class="size-9 cursor-zoom-in rounded-md object-cover"
                        @click="openImagePreview(index)">
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
                    <div v-if="uploadingAttachment" class="flex items-center gap-2 rounded-xl border bg-background px-3 text-xs text-muted-foreground">
                      <LoaderCircleIcon class="size-3.5 animate-spin" />解析中…
                    </div>
                  </div>
                </div>
              </div>
            </Transition>
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
            <InputGroup class="relative z-10 rounded-3xl bg-background dark:bg-background">
              <InputGroupTextarea ref="textareaRef" v-model="text" :disabled="archived || !canUseChat" :placeholder="canUseChat ? '描述任务，输入 @ 引用知识库，或直接上传材料开始' : '当前角色没有发起对话的权限'" aria-label="Message" class="max-h-40 min-h-14 overflow-y-auto"
                @input="onComposerInput" @keydown="onKeydown" @blur="onComposerBlur" @paste="onComposerPaste" />
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
                <InputGroupButton v-if="capabilities.allowUserWebSearch" type="button" size="sm" variant="ghost"
                  class="rounded-full" :aria-pressed="webSearch" @click="webSearch = !webSearch">
                  <GlobeIcon class="size-4" />联网搜索
                </InputGroupButton>
                <Popover v-model:open="channelPickerOpen">
                  <PopoverTrigger as-child>
                    <InputGroupButton size="sm" variant="ghost" class="rounded-full" aria-label="选择知识库频道">
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
                        class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm"
                        :class="channel.embeddingReady ? 'hover:bg-muted' : 'cursor-not-allowed opacity-40'"
                        :disabled="!channel.embeddingReady"
                        @click="toggleChannel(channel)">
                        <span class="flex size-4 items-center justify-center rounded border"
                          :class="channelIds.includes(channel.id) ? 'border-primary bg-primary text-primary-foreground' : ''">
                          <CheckIcon v-if="channelIds.includes(channel.id)" class="size-3" />
                        </span>
                        <HashIcon class="size-3.5 text-muted-foreground" />
                        <span class="truncate">{{ channel.name }}</span>
                        <span v-if="!channel.embeddingReady" class="ml-auto shrink-0 text-[10px] text-destructive">未就绪</span>
                        <span v-else class="ml-auto shrink-0 text-[11px] text-muted-foreground">{{ channel.ownerLabel }}</span>
                      </button>
                    </div>
                    <div class="border-t px-3 py-1.5 text-[11px] text-muted-foreground">
                      不选择频道时，本次对话不检索知识库。
                    </div>
                  </PopoverContent>
                </Popover>
                <div class="flex-1"></div>
                <DropdownMenu v-if="currentModel">
                  <DropdownMenuTrigger as-child>
                    <InputGroupButton size="sm" variant="ghost"
                      :aria-label="`Model: ${currentModel.name}${currentModel.thinking ? `, effort: ${currentEffort.name}` : ''}`"
                      class="ml-auto max-w-[min(46vw,22rem)] rounded-full">
                      <span class="truncate text-foreground">{{ currentModel.name }}</span>
                      <span v-if="currentModel.thinking" class="shrink-0 text-foreground/50">{{ currentEffort.name }}</span>
                      <ChevronDownIcon class="shrink-0" />
                    </InputGroupButton>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent side="top" align="start" class="w-64">
                    <DropdownMenuSub>
                      <DropdownMenuSubTrigger>
                        <span class="w-16 shrink-0 text-muted-foreground">模型</span>
                        <span class="min-w-0 flex-1 truncate">{{ currentModel.name }}</span>
                      </DropdownMenuSubTrigger>
                      <DropdownMenuSubContent class="w-64">
                        <DropdownMenuRadioGroup v-model="model">
                          <template v-for="(provider, index) in providers" :key="provider.name">
                            <DropdownMenuSeparator v-if="index > 0" />
                            <DropdownMenuGroup>
                              <DropdownMenuLabel>{{ provider.name }}</DropdownMenuLabel>
                              <DropdownMenuRadioItem v-for="item in provider.models" :key="item.id" :value="String(item.id)">
                                {{ item.name }}
                              </DropdownMenuRadioItem>
                            </DropdownMenuGroup>
                          </template>
                        </DropdownMenuRadioGroup>
                      </DropdownMenuSubContent>
                    </DropdownMenuSub>
                    <DropdownMenuSub v-if="currentModel.thinking">
                      <DropdownMenuSubTrigger>
                        <span class="w-16 shrink-0 text-muted-foreground">推理等级</span>
                        <span class="min-w-0 flex-1 truncate">{{ currentEffort.name }}</span>
                      </DropdownMenuSubTrigger>
                      <DropdownMenuSubContent class="w-44">
                        <DropdownMenuRadioGroup v-model="effort">
                          <DropdownMenuRadioItem v-for="item in efforts" :key="item.id" :value="item.id">
                            {{ item.name }}
                          </DropdownMenuRadioItem>
                        </DropdownMenuRadioGroup>
                      </DropdownMenuSubContent>
                    </DropdownMenuSub>
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
          <Transition name="privacy-mode">
            <p v-if="temporary" class="mt-2 px-3 text-xs text-muted-foreground">隐私模式</p>
          </Transition>
          <div v-if="outlineRounds.length || usageInfo || contextInfo"
            class="flex flex-wrap items-center justify-between gap-x-2 gap-y-1 px-3 pt-2 text-[11px] text-muted-foreground">
            <TooltipProvider>
              <div class="flex min-w-0 flex-wrap items-center gap-1">
                <Tooltip v-if="outlineRounds.length">
                  <TooltipTrigger as-child>
                    <button type="button" class="inline-flex h-7 items-center gap-1.5 rounded-lg px-2 transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                      <ActivityIcon class="size-3.5" />
                      <span class="font-medium tabular-nums text-foreground">{{ roundCount }} 轮</span>
                      <span>{{ stepCount }} 步</span>
                      <span v-if="lastTps" class="border-l border-border/70 pl-1.5 font-mono tabular-nums">{{ lastTps }} tok/s</span>
                    </button>
                  </TooltipTrigger>
                  <TooltipContent side="top" class="max-w-none border bg-popover p-3 text-xs shadow-xl">
                    <div class="mb-2 flex w-60 items-center justify-between border-b pb-2">
                      <span class="flex items-center gap-1.5 font-medium text-foreground"><ActivityIcon class="size-3.5" />会话统计</span>
                      <span class="text-muted-foreground">{{ roundCount }} 轮 {{ stepCount }} 步</span>
                    </div>
                    <div class="space-y-1.5">
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">模型用时</span><span>{{ formatDuration(turnDurationMs) }}</span></div>
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">工具调用用时</span><span>{{ formatDuration(turnToolMs) }}</span></div>
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">首 token 平均（TTFT）</span><span>{{ formatDuration(turnTtftMs) }}</span></div>
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">输出速度（TPS）</span><span>{{ lastTps ? `${lastTps} tok/s` : '—' }}</span></div>
                    </div>
                  </TooltipContent>
                </Tooltip>

                <Tooltip v-if="usageInfo">
                  <TooltipTrigger as-child>
                    <button type="button" class="inline-flex h-7 items-center gap-1.5 rounded-lg px-2 transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                      <DatabaseIcon class="size-3.5" />
                      <span class="tabular-nums">{{ formatCompact(tokenTotal) }} tok</span>
                      <span>缓存 {{ tokenCachePercent }}%</span>
                    </button>
                  </TooltipTrigger>
                  <TooltipContent side="top" class="max-w-none border bg-popover p-3 text-xs shadow-xl">
                    <div class="mb-2 flex w-64 items-center justify-between border-b pb-2">
                      <span class="flex items-center gap-1.5 font-medium text-foreground"><DatabaseIcon class="size-3.5" />Token 用量</span>
                      <span class="font-medium text-foreground">{{ usageInfo.promptTokens + usageInfo.completionTokens }} tok</span>
                    </div>
                    <div class="space-y-1.5">
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">缓存命中</span><span>{{ tokenCachePercent }}%</span></div>
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">未缓存输入</span><span>{{ usageInfo.cacheMissTokens.toLocaleString() }} tok</span></div>
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">缓存读取</span><span>{{ usageInfo.cacheHitTokens.toLocaleString() }} tok</span></div>
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">输出</span><span>{{ usageInfo.completionTokens.toLocaleString() }} tok</span></div>
                    </div>
                  </TooltipContent>
                </Tooltip>
              </div>

              <Tooltip v-if="contextInfo">
                <TooltipTrigger as-child>
                  <button type="button" :aria-label="`上下文占用 ${contextPercent}%`"
                    class="inline-flex h-7 shrink-0 items-center gap-2 rounded-lg px-2 transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                    <span>上下文</span>
                    <span class="block h-1.5 w-16 overflow-hidden rounded-full bg-muted sm:w-24" aria-hidden="true">
                      <span class="block h-full rounded-full transition-[width,background-color] duration-300"
                        :class="contextPercent >= 95 ? 'bg-destructive' : contextPercent >= (contextInfo.compressionThreshold ?? 80) ? 'bg-amber-500' : 'bg-primary'"
                        :style="{ width: `${contextPercent}%` }" />
                    </span>
                    <span class="w-8 text-right font-mono tabular-nums text-foreground">{{ contextPercent }}%</span>
                  </button>
                </TooltipTrigger>
                <TooltipContent side="top" class="max-w-none border bg-popover p-3 text-xs shadow-xl">
                  <div class="w-64">
                    <div class="mb-2 flex items-center justify-between border-b pb-2">
                      <span>上下文已用 <strong class="font-semibold text-foreground">{{ contextPercent }}%</strong></span>
                      <span class="font-mono text-[11px]">~{{ formatCompact(contextInfo.usedTokens) }} / {{ formatCompact(contextInfo.maxTokens) }}</span>
                    </div>
                    <div class="mb-3 h-1.5 overflow-hidden rounded-full bg-muted">
                      <div class="h-full rounded-full transition-[width,background-color] duration-300"
                        :class="contextPercent >= 95 ? 'bg-destructive' : contextPercent >= (contextInfo.compressionThreshold ?? 80) ? 'bg-amber-500' : 'bg-primary'"
                        :style="{ width: `${contextPercent}%` }" />
                    </div>
                    <div class="space-y-1.5">
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">已用</span><span>{{ contextInfo.usedTokens.toLocaleString() }} tok</span></div>
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">剩余</span><span>{{ Math.max(0, contextInfo.maxTokens - contextInfo.usedTokens).toLocaleString() }} tok</span></div>
                      <div class="flex items-center justify-between gap-6"><span class="text-muted-foreground">压缩阈值</span><span>{{ contextInfo.compressionThreshold ?? 80 }}%</span></div>
                    </div>
                    <p class="mt-2 border-t pt-2 text-[11px] text-muted-foreground">
                      {{ contextInfo.compressed ? '已压缩历史对话' : '未触发压缩' }}<span v-if="contextInfo.droppedMessages"> · 裁剪 {{ contextInfo.droppedMessages }} 条</span>
                    </p>
                  </div>
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

    <Dialog v-model:open="imagePreviewOpen">
      <DialogContent class="max-w-4xl p-2">
        <DialogTitle class="sr-only">图片预览</DialogTitle>
        <img v-if="imagePreviewUrl" :src="imagePreviewUrl" alt="图片预览"
          class="mx-auto max-h-[78vh] w-auto rounded-md object-contain">
      </DialogContent>
    </Dialog>
    <CitationPreviewDialog v-model:open="citationPreviewOpen" :source="citationPreviewSource" />
  </template>
<script setup lang="ts">
import {
  ActivityIcon,
  ArrowUpIcon,
  CheckIcon,
  CircleCheckIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  DatabaseIcon,
  GlobeIcon,
  HashIcon,
  LoaderCircleIcon,
  PaperclipIcon,
  FileTextIcon,
  TerminalIcon,
  CopyIcon,
  PencilIcon,
  GitBranchIcon,
  RotateCcwIcon,
  StarIcon,
  ThumbsDownIcon,
  ThumbsUpIcon,
  XIcon,
} from '@lucide/vue'
import { createReusableTemplate } from '@vueuse/core'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { useDebugStore } from '@/stores/debug'
import { getDefaultBrandIcon } from '@/lib/branding'
import { useUserStore } from '@/stores/user'
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
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Empty } from '@/components/ui/empty'
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupTextarea,
} from '@/components/ui/input-group'
import { Message, MessageContent, MessageFooter } from '@/components/ui/message'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
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
import { chatApi, channelApi, discoverApi, favoriteApi, profileApi } from '@/api'
import type { ChatAttachmentPayload, ChatAttachmentResult, ChatCacheInfo, ChatContextInfo, ChatUsageInfo, CitationSource } from '@/api/chat'
import CitationMarkdown from '@/components/CitationMarkdown.vue'
import CitationPreviewDialog from '@/components/CitationPreviewDialog.vue'
import MessageCitations from '@/components/MessageCitations.vue'
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
  serverId?: string
  role: 'user' | 'assistant'
  parts: ChatPart[]
  attachments?: { name: string, kind: string, url?: string }[]
  citations?: CitationSource[]
  revealIndex?: number
  favorited?: boolean
  feedback?: 'LIKE' | 'DISLIKE' | null
  traceId?: string
}

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const debugStore = useDebugStore()
const userStore = useUserStore()
const canDebug = computed(() => userStore.hasPermission('trace:read'))
const canUseChat = computed(() => userStore.hasPermission('chat:use'))

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
const conversationLoading = ref(Boolean(route.params.conversationId))
let conversationLoadToken = 0
const status = ref<'ready' | 'submitted' | 'streaming'>('ready')
const archived = ref(false)
const editingIndex = ref<number | null>(null)
const truncatedNotice = ref('')
const citationPreviewOpen = ref(false)
const citationPreviewSource = ref<CitationSource | null>(null)
const temporary = computed(() => route.name === 'ChatTemporary')
const greetingLogo = getDefaultBrandIcon()
const capabilities = ref<ChatCapabilities>({ webSearchEnabled: false, allowUserWebSearch: false, webSearchAvailable: false })
const cacheInfo = ref<ChatCacheInfo | null>(null)
const contextInfo = ref<ChatContextInfo | null>(null)
const usageInfo = ref<ChatUsageInfo | null>(null)
const turnStartedAt = ref(0)
const turnFirstTokenAt = ref(0)
const turnDurationMs = ref(0)
const turnTtftMs = ref(0)
const turnToolMs = ref(0)
const toolStartedAt = new Map<string, number>()
const webSearch = ref(Boolean(userStore.profile?.autoWebSearch))
const draft = ref<string | null>(null)
const formRef = ref<HTMLFormElement | null>(null)
const trayRef = ref<HTMLElement | null>(null)
const trayOpen = ref(false)
const trayHeight = ref(0)
let trayResizeObserver: ResizeObserver | null = null
// Open state of each reasoning row, set once the person toggles it.
const reasoningOpen = reactive<Record<string, boolean | undefined>>({})

const busy = computed(() => status.value !== 'ready')
const contextPercent = computed(() => Math.round(Math.max(0, Math.min(100, contextInfo.value?.percent ?? 0))))
const roundCount = computed(() => outlineRounds.value.length)
const stepCount = computed(() => messages.value.reduce((total, message) => total + message.parts.length, 0))
const tokenTotal = computed(() => usageInfo.value ? usageInfo.value.promptTokens + usageInfo.value.completionTokens : 0)
const tokenCachePercent = computed(() => {
  const value = usageInfo.value
  if (!value) return 0
  const total = value.cacheHitTokens + value.cacheMissTokens
  return total > 0 ? Math.round((value.cacheHitTokens / total) * 100) : 0
})
const lastTps = computed(() => {
  if (!usageInfo.value || turnDurationMs.value <= 0)
    return 0
  return Math.round(usageInfo.value.completionTokens / (turnDurationMs.value / 1000))
})
function formatCompact(value: number): string {
  if (!Number.isFinite(value) || value <= 0)
    return '0'
  if (value >= 1e9)
    return `${(value / 1e9).toFixed(2).replace(/\.?0+$/, '')}B`
  if (value >= 1e6)
    return `${(value / 1e6).toFixed(1).replace(/\.0$/, '')}M`
  if (value >= 1e3)
    return `${(value / 1e3).toFixed(1).replace(/\.0$/, '')}K`
  return String(Math.round(value))
}
function formatDuration(ms: number): string {
  if (!ms || ms <= 0)
    return '—'
  if (ms >= 60000)
    return `${Math.floor(ms / 60000)} 分 ${Math.round((ms % 60000) / 1000)} 秒`
  return `${(ms / 1000).toFixed(1)} 秒`
}
const text = computed({
  get: () => draft.value ?? '',
  set: (value: string) => {
    draft.value = value
  },
})
const canSend = computed(() => canUseChat.value
  && (text.value.trim().length > 0 || chatAttachments.value.length > 0)
  && !busy.value && !archived.value && !!currentModel.value)

const chatAttachments = ref<ChatAttachmentPayload[]>([])
const attachmentPreviews = ref<ChatAttachmentResult[]>([])
const uploadingAttachment = ref(false)
const attachmentInput = ref<HTMLInputElement | null>(null)
const dragActive = ref(false)
const supportsAttachments = computed(() => !!currentModel.value?.supportsVision)
const imagePreview = (index: number) => {
  const preview = attachmentPreviews.value[index]
  return preview?.kind === 'IMAGE' ? (preview.url || preview.dataUrl) : undefined
}
const imagePreviewOpen = ref(false)
const imagePreviewUrl = ref('')
function openImagePreview(index: number) {
  const url = imagePreview(index)
  if (url)
    openImageUrl(url)
}

function openImageUrl(url: string) {
  imagePreviewUrl.value = url
  imagePreviewOpen.value = true
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
      const result = await chatApi.uploadAttachment(file, conversationId.value)
      const payload: ChatAttachmentPayload = result.kind === 'IMAGE'
        ? { type: 'IMAGE', name: result.name, mimeType: result.mimeType, dataUrl: result.dataUrl, url: result.url, key: result.key }
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

function onComposerPaste(event: ClipboardEvent) {
  if (!supportsAttachments.value || archived.value)
    return
  const files = Array.from(event.clipboardData?.files || []).filter(file => file.type.startsWith('image/'))
  if (!files.length)
    return
  event.preventDefault()
  void addFiles(files)
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


async function restoreArchived() {
  if (!conversationId.value)
    return
  try {
    await chatApi.archiveConversation(conversationId.value, false)
    archived.value = false
    toast.success('对话已恢复')
    await chatStore.loadConversations()
  } catch (err: any) {
    toast.error(err?.message || '恢复对话失败')
  }
}

async function onSubmit(options: { regenerate?: boolean } = {}) {
  mentionOpen.value = false
  cacheInfo.value = null
  contextInfo.value = null
  usageInfo.value = null
  if (!canUseChat.value) {
    toast.error('当前角色没有发起对话的权限')
    return
  }
  if (!canSend.value || !currentModel.value)
    return
  const pendingAttachments = [...chatAttachments.value]
  const pendingPreviews = [...attachmentPreviews.value]
  const userMessage: ChatMessage = {
    id: `user-${Date.now()}`,
    role: 'user',
    parts: [{ type: 'text', text: text.value.trim() }],
    attachments: pendingAttachments.map((item, index) => ({
      name: item.name,
      kind: item.type,
      url: pendingPreviews[index]?.url,
    })),
  }
  const assistant = reactive<ChatMessage>({
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    parts: [],
  })
  const editIndex = editingIndex.value
  editingIndex.value = null
  const baseMessages = editIndex === null ? messages.value : messages.value.slice(0, editIndex)
  const history = [...baseMessages, userMessage]
    .map(message => ({ role: message.role, content: getMessageText(message) }))
    .filter((message, index, all) => message.content.trim().length > 0 || index === all.length - 1)
  if (!temporary.value && !conversationId.value)
    setActiveConversation(null, (text.value.trim() || '图片消息').slice(0, 60))
  if (editIndex !== null)
    messages.value = baseMessages
  messages.value.push(userMessage, assistant)
  chatAttachments.value = []
  attachmentPreviews.value = []
  draft.value = ''
  status.value = 'submitted'
  truncatedNotice.value = ''
  let finished = false
  turnStartedAt.value = performance.now()
  turnFirstTokenAt.value = 0
  turnDurationMs.value = 0
  turnTtftMs.value = 0
  turnToolMs.value = 0
  toolStartedAt.clear()
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
      regenerate: options.regenerate || undefined,
      editMessageIndex: editIndex ?? undefined,
      debugTopK: debugStore.enabled ? debugStore.overrides.topK : undefined,
      debugRerankEnabled: debugStore.enabled ? debugStore.overrides.rerankEnabled : undefined,
      debugToolsEnabled: debugStore.enabled ? debugStore.overrides.toolsEnabled : undefined,
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
      onUsage: (data) => {
        usageInfo.value = data
      },
      onCitations: (sources) => {
        assistant.citations = sources
      },
      onTrace: (traceId) => {
        assistant.traceId = traceId
      },
      onReasoning: (delta) => {
        status.value = 'streaming'
        if (!turnFirstTokenAt.value)
          turnFirstTokenAt.value = performance.now()
        let part = assistant.parts.find(item => item.type === 'reasoning') as Extract<ChatPart, { type: 'reasoning' }> | undefined
        if (!part) {
          part = { type: 'reasoning', text: '', state: 'streaming' }
          assistant.parts.push(part)
        }
        part.text += delta
      },
      onText: (delta) => {
        status.value = 'streaming'
        if (!turnFirstTokenAt.value)
          turnFirstTokenAt.value = performance.now()
        let part = assistant.parts.find(item => item.type === 'text') as Extract<ChatPart, { type: 'text' }> | undefined
        if (!part) {
          part = { type: 'text', text: '' }
          assistant.parts.push(part)
        }
        part.text += delta
      },
      onTool: (data) => {
        status.value = 'streaming'
        if (data.id && data.status === 'running') {
          toolStartedAt.set(data.id, performance.now())
        } else if (data.id && data.status === 'done') {
          const started = toolStartedAt.get(data.id)
          if (started) {
            turnToolMs.value += Math.round(performance.now() - started)
            toolStartedAt.delete(data.id)
          }
        }
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
      onDone: (data) => {
        finished = true
        turnDurationMs.value = Math.round(performance.now() - turnStartedAt.value)
        turnTtftMs.value = turnFirstTokenAt.value > 0 ? Math.round(turnFirstTokenAt.value - turnStartedAt.value) : 0
        if (data.assistantMessageId)
          assistant.serverId = String(data.assistantMessageId)
        refreshConversationTitle()
        // The LLM title is generated asynchronously after the answer; poll a few times.
        if (!temporary.value) {
          for (const delay of [1500, 3500, 7000])
            window.setTimeout(refreshConversationTitle, delay)
        }
      },
      onError: (message) => {
        finished = true
        toast.error(message)
        assistant.parts.push({ type: 'text', text: `\n\n> 请求失败：${message}` })
      },
      onTruncated: (message) => {
        finished = true
        status.value = 'ready'
        truncatedNotice.value = message || '生成已停止，请重新尝试'
        const index = messages.value.indexOf(assistant)
        if (index >= 0)
          messages.value.splice(index, 1)
        toast.error(truncatedNotice.value)
        void chatStore.loadConversations()
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

function scrollToCitation(message: ChatMessage, index: number) {
  message.revealIndex = undefined
  void nextTick(() => {
    message.revealIndex = index
  })
}

function openCitationPreview(source: CitationSource) {
  citationPreviewSource.value = source
  citationPreviewOpen.value = true
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
const MODEL_STORAGE_KEY = 'cognia:chat-model'
const model = ref(localStorage.getItem(MODEL_STORAGE_KEY) || '')
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
watch(model, (value) => {
  if (value)
    localStorage.setItem(MODEL_STORAGE_KEY, value)
})
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
  embeddingReady: boolean
}

const channelPickerOpen = ref(false)
const availableChannels = ref<PickableChannel[]>([])
const channelIds = ref<number[]>([])
const selectedChannels = computed(() =>
  availableChannels.value.filter(channel => channelIds.value.includes(channel.id)))
const webSearchEnabled = computed(() => webSearch.value
  || (capabilities.value.webSearchAvailable && !capabilities.value.allowUserWebSearch))
const hasTrayContent = computed(() =>
  (webSearchEnabled.value && capabilities.value.webSearchAvailable)
  || selectedChannels.value.length > 0
  || chatAttachments.value.length > 0
  || uploadingAttachment.value)
const trayVisible = computed(() => trayOpen.value && hasTrayContent.value && !mentionOpen.value)
function toggleTray() {
  if (!hasTrayContent.value) return
  trayOpen.value = !trayOpen.value
}
watch(
  [() => selectedChannels.value.length, () => chatAttachments.value.length, uploadingAttachment, webSearchEnabled],
  () => {
    if (!hasTrayContent.value) {
      trayOpen.value = false
      return
    }
    trayOpen.value = true
  },
)
watch(trayRef, (el) => {
  trayResizeObserver?.disconnect()
  trayResizeObserver = null
  if (!el) {
    trayHeight.value = 0
    return
  }
  const syncHeight = () => { trayHeight.value = Math.ceil(el.offsetHeight) }
  syncHeight()
  trayResizeObserver = new ResizeObserver(syncHeight)
  trayResizeObserver.observe(el)
})

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
    .filter(channel => channel.embeddingReady)
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
  if (!channel.embeddingReady) {
    toast.info('该频道尚未完成向量化，暂不可用于检索')
    return
  }
  if (channelIds.value.includes(channel.id))
    channelIds.value = channelIds.value.filter(id => id !== channel.id)
  else
    channelIds.value = [...channelIds.value, channel.id]
}

async function loadModels() {
  try {
    availableModels.value = await chatApi.models()
    if (!availableModels.value.length)
      return
    const saved = localStorage.getItem(MODEL_STORAGE_KEY) || model.value
    const exists = availableModels.value.some(item => String(item.id) === saved)
    model.value = exists ? saved : String(availableModels.value[0]!.id)
  } catch (err: any) {
    toast.error(err?.message || '加载模型列表失败')
  }
}

async function loadChannels() {
  try {
    const profile = await profileApi.me()
    const channels: PickableChannel[] = profile.channels
      .map(item => ({ id: Number(item.id), name: item.name, ownerLabel: '个人', embeddingReady: true }))
    for (const team of profile.teams) {
      try {
        const list: ChannelCard[] = await channelApi.list('TEAM', team.id)
        channels.push(...list.map(item => ({ id: Number(item.id), name: item.name, ownerLabel: team.name || team.slug, embeddingReady: true })))
      } catch {
        // A team the viewer left or cannot read should not block chat.
      }
    }
    const subscriptions = await discoverApi.subscriptions().catch(() => [])
    for (const item of subscriptions) {
      if (!channels.some(channel => channel.id === Number(item.id)))
        channels.push({ id: Number(item.id), name: item.name, ownerLabel: item.ownerSlug, embeddingReady: true })
    }
    const readiness: Record<string, boolean> = await channelApi.embeddingReadiness(channels.map(channel => channel.id)).catch(() => ({} as Record<string, boolean>))
    availableChannels.value = channels.map(channel => ({
      ...channel,
      embeddingReady: readiness[String(channel.id)] !== false,
    }))
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
  conversationLoadToken += 1
  conversationLoading.value = false
  messages.value = []
  activeRoundId.value = undefined
  conversationId.value = null
  archived.value = false
  setActiveConversation(null, temporary.value ? '临时对话' : '新对话')
  cacheInfo.value = null
  contextInfo.value = null
  usageInfo.value = null
  status.value = 'ready'
  draft.value = ''
  editingIndex.value = null
}

async function loadConversation(id: string) {
  const token = ++conversationLoadToken
  conversationLoading.value = true
  try {
    const detail: ChatConversationDetail = await chatApi.conversation(id)
    if (token !== conversationLoadToken)
      return
    cacheInfo.value = null
    contextInfo.value = null
    usageInfo.value = null
    conversationId.value = String(detail.id)
    archived.value = Boolean(detail.archived)
    setActiveConversation(conversationId.value, detail.title)
    channelIds.value = detail.channelIds ?? []
    const lastMetrics = [...detail.messages].reverse()
      .find(message => message.role === 'assistant' && message.metrics)?.metrics
    if (lastMetrics?.cache)
      cacheInfo.value = lastMetrics.cache as ChatCacheInfo
    if (lastMetrics?.context)
      contextInfo.value = lastMetrics.context as ChatContextInfo
    if (lastMetrics?.usage)
      usageInfo.value = lastMetrics.usage as ChatUsageInfo
    messages.value = detail.messages.map((message, index) => ({
      id: `${message.role}-${index}`,
      serverId: message.id,
      favorited: message.favorited,
      feedback: message.feedback ?? null,
      role: message.role,
      parts: message.role === 'assistant'
        ? [
            ...(message.reasoning ? [{ type: 'reasoning' as const, text: message.reasoning, state: 'done' as const }] : []),
            ...(message.content ? [{ type: 'text' as const, text: message.content }] : []),
          ]
        : [{ type: 'text' as const, text: message.content }],
      citations: message.citations ?? [],
      attachments: (message.attachments ?? []).map(attachment => ({
        name: attachment.name,
        kind: attachment.type,
        url: attachment.url,
      })),
    }))
    if (detail.modelId && availableModels.value.some(item => String(item.id) === String(detail.modelId)))
      model.value = String(detail.modelId)
    status.value = 'ready'
    editingIndex.value = null
  } catch (err: any) {
    if (token !== conversationLoadToken)
      return
    toast.error(err?.message || '加载对话失败')
    void router.replace({ name: 'Chat' })
  } finally {
    if (token === conversationLoadToken)
      conversationLoading.value = false
  }
}

async function submitFeedback(message: ChatMessage, type: 'LIKE' | 'DISLIKE') {
  const target = message.serverId || message.id
  if (!target || String(target).startsWith('assistant-')) {
    toast.error('消息尚未保存，暂时无法反馈')
    return
  }
  const next = message.feedback === type ? null : type
  try {
    if (next) await chatApi.submitFeedback(target, next)
    message.feedback = next
    if (next) toast.success(next === 'LIKE' ? '感谢反馈' : '已记录反馈，管理员会复核')
  } catch (err: any) {
    toast.error(err?.message || '反馈提交失败')
  }
}

async function toggleMessageFavorite(message: ChatMessage) {
  if (!message.serverId) {
    toast.error('消息尚未保存，请稍后再试')
    return
  }
  try {
    if (message.favorited)
      await favoriteApi.unfavoriteMessage(message.serverId)
    else
      await favoriteApi.favoriteMessage(message.serverId)
    message.favorited = !message.favorited
    toast.success(message.favorited ? '已收藏消息' : '已取消收藏')
  } catch (err: any) {
    toast.error(err?.message || '收藏失败')
  }
}

async function copyMessage(message: ChatMessage) {
  try {
    await navigator.clipboard.writeText(getMessageText(message))
    toast.success('已复制')
  } catch {
    toast.error('复制失败')
  }
}

function focusComposer() {
  void nextTick(() => {
    const el = (textareaRef.value as unknown as { $el?: HTMLTextAreaElement } | null)?.$el
      ?? (textareaRef.value as unknown as HTMLTextAreaElement | null)
    if (el instanceof HTMLTextAreaElement) {
      el.focus()
      el.setSelectionRange(el.value.length, el.value.length)
    }
  })
}

function editMessage(message: ChatMessage, index: number) {
  if (busy.value)
    return
  editingIndex.value = index
  text.value = getMessageText(message)
  void nextTick(() => focusComposer())
  toast.info('正在编辑消息，发送后将重新生成后续回答')
}

function cancelEdit() {
  editingIndex.value = null
  text.value = ''
  toast.info('已取消编辑')
}

async function forkMessage(index: number) {
  if (!conversationId.value)
    return
  try {
    const detail = await chatApi.forkConversation(conversationId.value, index + 1)
    toast.success('已分叉到新对话')
    await router.push({ name: 'ChatConversation', params: { conversationId: detail.id } })
  } catch (err: any) {
    toast.error(err?.message || '分叉失败')
  }
}

async function regenerateMessage(index: number) {
  const user = messages.value[index - 1]
  if (!user || user.role !== 'user')
    return
  messages.value.splice(index - 1, 2)
  text.value = getMessageText(user)
  await nextTick()
  await onSubmit({ regenerate: true })
}

function syncRoute() {
  const raw = route.params.conversationId
  const id = raw ? String(raw) : null
  if (!id) {
    resetConversation()
    return
  }
  if (id === conversationId.value) {
    conversationLoading.value = false
    return
  }
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
    webSearch.value = capabilities.value.webSearchAvailable && Boolean(userStore.profile?.autoWebSearch)
  } catch { /* capabilities are optional */ }
  syncRoute()
  void bindScrollSpy()
})

onUnmounted(() => {
  window.removeEventListener('chat:reset', handleReset)
  unbindScrollSpy()
  trayResizeObserver?.disconnect()
})

</script>

<style scoped>
.composer-tray-surface {
  background-color: color-mix(in oklab, var(--background) 36%, transparent);
  -webkit-backdrop-filter: blur(44px) saturate(185%) brightness(1.04);
  backdrop-filter: blur(44px) saturate(185%) brightness(1.04);
}
.composer-tray-enter-active,
.composer-tray-leave-active {
  transition: transform 260ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform;
}
.composer-tray-enter-from,
.composer-tray-leave-to {
  transform: translateY(100%);
}
.privacy-mode-enter-active,
.privacy-mode-leave-active {
  transition: opacity 180ms ease, transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}
.privacy-mode-enter-from,
.privacy-mode-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
