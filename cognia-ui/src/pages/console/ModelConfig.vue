<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconBrain class="size-5 text-muted-foreground" />
          模型配置
        </h1>
        <p class="text-sm text-muted-foreground">
          接入任意 OpenAI 兼容供应商。
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Badge variant="outline" class="h-8 px-3">{{ providers.length }} 个供应商</Badge>
        <Badge variant="outline" class="h-8 px-3">{{ configs.length }} 个模型</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="loadAll">
          <IconRefresh class="mr-1 size-4" />重新加载
        </Button>
        <Button v-if="canUpdate" size="sm" @click="openProviderDialog()">
          <IconPlus class="mr-1 size-4" />添加供应商
        </Button>
      </div>
    </div>

    <Alert v-if="!canUpdate" variant="destructive">
      <IconAlertTriangle class="size-4" />
      <AlertDescription>当前账户只有查看权限，无法修改模型配置。</AlertDescription>
    </Alert>
    <Alert v-if="error" variant="destructive">
      <IconAlertTriangle class="size-4" />
      <AlertDescription>{{ error }}</AlertDescription>
    </Alert>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <Tabs v-else v-model="activeTab" class="gap-4">
      <TabsList class="h-10 w-full justify-start overflow-x-auto bg-muted/60 p-1">
        <TabsTrigger value="providers" class="flex-none px-3"><IconServer class="size-4" />供应商</TabsTrigger>
        <TabsTrigger value="models" class="flex-none px-3"><IconCpu class="size-4" />模型列表</TabsTrigger>
      </TabsList>

      <TabsContent value="providers">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-3">
              <div class="flex flex-col gap-1">
                <h2 class="flex items-center gap-2 text-sm font-semibold">
                  <IconCloudDownload class="size-4" />Ollama 本地模型
                </h2>
                <p class="text-xs text-muted-foreground">
                  {{ ollamaStatus?.configured ? (ollamaStatus.connected ? `已连接 ${ollamaStatus.baseUrl}` : ollamaStatus.message) : '未配置本地 Ollama，可使用 Docker Compose 的 ollama profile，或连接外部 Ollama。' }}
                </p>
              </div>
              <div class="flex items-center gap-2">
                <Badge :variant="ollamaStatus?.connected ? 'default' : 'secondary'">
                  {{ ollamaStatus?.connected ? '已连接' : '未连接' }}
                </Badge>
                <Button variant="outline" size="sm" @click="loadOllama"><IconRefresh class="mr-1 size-4" />检测</Button>
                <Button v-if="!ollamaStatus?.configured" variant="outline" size="sm" @click="openOllamaProvider">添加 Ollama 供应商</Button>
              </div>
            </header>
            <div class="flex flex-col gap-3 p-4">
              <div class="flex flex-wrap gap-2">
                <Badge v-for="name in ollamaStatus?.models ?? []" :key="name" variant="secondary">{{ name }}</Badge>
                <span v-if="!ollamaStatus?.models?.length" class="text-xs text-muted-foreground">暂无本地模型</span>
              </div>
              <div v-if="ollamaStatus?.connected" class="flex flex-wrap items-center gap-2">
                <Input v-model="ollamaModel" class="max-w-sm" placeholder="例如 qwen3:8b / nomic-embed-text" />
                <Button :disabled="ollamaPulling || !ollamaModel.trim()" @click="pullOllama">
                  <Spinner v-if="ollamaPulling" class="mr-1 size-3" />拉取模型
                </Button>
                <label class="flex cursor-pointer items-center gap-2 text-xs text-muted-foreground">
                  <Checkbox v-model="ollamaInsecure" /> 允许不安全来源
                </label>
                <Button v-if="ollamaStatus.models.length" variant="outline" @click="openOllamaFetch">添加到模型列表</Button>
              </div>
              <Progress v-if="ollamaPulling || ollamaProgress > 0" :model-value="ollamaProgress" class="h-2" />
              <p v-if="ollamaPullMessage" class="text-xs text-muted-foreground">{{ ollamaPullMessage }}</p>
            </div>
          </section>
          <div v-if="providers.length === 0" class="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed py-16">
            <IconPlug class="size-10 text-muted-foreground" />
            <p class="text-sm text-muted-foreground">还没有供应商，添加一个 OpenAI 兼容端点以开始。</p>
            <Button size="sm" @click="openProviderDialog()"><IconPlus class="mr-1 size-4" />添加供应商</Button>
          </div>
          <div v-else class="grid gap-4 xl:grid-cols-2">
            <section v-for="provider in providers" :key="provider.id" class="flex flex-col rounded-xl border bg-card">
              <header class="flex flex-wrap items-start justify-between gap-2 border-b px-4 py-3">
                <div class="min-w-0">
                  <div class="flex items-center gap-2">
                    <h2 class="truncate text-sm font-semibold">{{ provider.name }}</h2>
                    <Badge :variant="provider.enabled ? 'default' : 'secondary'">
                      {{ provider.enabled ? '已启用' : '已停用' }}
                    </Badge>
                    <Badge variant="outline">{{ protocolLabel(provider.protocol) }}</Badge>
                  </div>
                  <p class="mt-0.5 truncate text-xs text-muted-foreground">{{ provider.baseUrl }}</p>
                </div>
                <div class="flex items-center gap-1">
                  <Button variant="ghost" size="icon-sm" title="编辑" @click="openProviderDialog(provider)">
                    <IconPencil class="size-4" />
                  </Button>
                  <Button variant="ghost" size="icon-sm" title="删除" :disabled="saving" @click="askRemoveProvider(provider)">
                    <IconTrash class="size-4" />
                  </Button>
                </div>
              </header>
              <div class="grid gap-3 p-4 text-sm sm:grid-cols-2">
                <div>
                  <p class="text-xs text-muted-foreground">厂商适配</p>
                  <p class="font-medium">{{ provider.vendor || '自定义' }}</p>
                </div>
                <div>
                  <p class="text-xs text-muted-foreground">API Key</p>
                  <Badge :variant="provider.apiKeyConfigured ? 'outline' : 'secondary'">
                    {{ provider.apiKeyConfigured ? '已配置' : '未配置' }}
                  </Badge>
                </div>
                <div>
                  <p class="text-xs text-muted-foreground">模型数量</p>
                  <p class="font-medium">{{ provider.modelCount }}</p>
                </div>
                <div>
                  <p class="text-xs text-muted-foreground">更新时间</p>
                  <p class="font-medium">{{ provider.updatedAt || '—' }}</p>
                </div>
              </div>
              <footer class="mt-auto flex flex-wrap gap-2 border-t px-4 py-3">
                <Button variant="outline" size="sm" :disabled="saving" @click="openFetch(provider)">
                  <IconCloudDownload class="mr-1 size-4" />拉取模型
                </Button>
                <Button variant="outline" size="sm" @click="activeTab = 'models'">
                  <IconList class="mr-1 size-4" />查看模型
                </Button>
              </footer>
            </section>
          </div>
        </fieldset>
      </TabsContent>

      <TabsContent value="models">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3">
              <div class="flex flex-col gap-1">
                <h2 class="text-sm font-semibold">已配置模型</h2>
                <p class="text-xs text-muted-foreground">
                  「对用户可见」决定普通用户能否在聊天中选择该模型；Models.dev 的价格以 USD/百万 Token 提供，并按 1 USD = 1,000,000 积分换算。
                </p>
              </div>
              <Button variant="outline" size="sm" :disabled="saving || importingPrices" @click="importModelsDevPrices">
                <Spinner v-if="importingPrices" class="mr-1 size-4" />
                <IconCloudDownload v-else class="mr-1 size-4" />从 Models.dev 导入价格
              </Button>
            </header>
            <div class="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>模型</TableHead>
                    <TableHead>供应商</TableHead>
                    <TableHead>类型</TableHead>
                    <TableHead>计费</TableHead>
                    <TableHead>启用</TableHead>
                    <TableHead>用户可见</TableHead>
                    <TableHead>工具</TableHead>
                    <TableHead>思考</TableHead>
                    <TableHead>推理强度</TableHead>
                    <TableHead class="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow v-if="configs.length === 0">
                    <TableCell :colspan="10" class="h-24 text-center text-sm text-muted-foreground">
                      暂无模型，先在供应商中拉取模型。
                    </TableCell>
                  </TableRow>
                  <TableRow v-for="model in configs" :key="model.id">
                    <TableCell>
                      <div class="flex flex-col">
                        <span class="font-medium">{{ model.displayName || model.modelId }}</span>
                        <span class="text-xs text-muted-foreground">{{ model.modelId }}</span>
                      </div>
                    </TableCell>
                    <TableCell>{{ model.providerName }}</TableCell>
                    <TableCell><Badge variant="outline">{{ typeLabel(model.modelType) }}</Badge></TableCell>
                    <TableCell class="whitespace-nowrap text-xs">
                      {{ model.modelType !== 'CHAT' ? '—' : model.billingMode === 'ROUND'
                        ? `每轮 ${model.creditsPerRound} 积分`
                        : `输入/输出 ${model.inputCreditsPerMillion}/${model.outputCreditsPerMillion} 积分/百万 Token` }}
                    </TableCell>
                    <TableCell><Switch :model-value="model.enabled" @update:model-value="toggle(model, 'enabled', $event)" /></TableCell>
                    <TableCell><Switch :model-value="model.visible" @update:model-value="toggle(model, 'visible', $event)" /></TableCell>
                    <TableCell><Switch :model-value="model.supportsTools" @update:model-value="toggle(model, 'supportsTools', $event)" /></TableCell>
                    <TableCell><Switch :model-value="model.thinkingEnabled" @update:model-value="toggle(model, 'thinkingEnabled', $event)" /></TableCell>
                    <TableCell>{{ model.reasoningEffort || '—' }}</TableCell>
                    <TableCell class="text-right">
                      <Button variant="ghost" size="icon-sm" title="编辑" @click="openModelDialog(model)">
                        <IconPencil class="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" title="删除" @click="askRemoveModel(model)">
                        <IconTrash class="size-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </div>
          </section>
        </fieldset>
      </TabsContent>
    </Tabs>

    <Dialog v-model:open="providerDialogOpen">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ editingProvider ? '编辑供应商' : '添加供应商' }}</DialogTitle>
          <DialogDescription>
            支持 OpenAI Chat Completions、OpenAI Response 与 Anthropic Messages 协议；填写端点与 Key 后可拉取模型。
          </DialogDescription>
        </DialogHeader>
        <div class="grid gap-4">
          <div class="grid gap-2">
            <Label for="provider-name">名称</Label>
            <Input id="provider-name" v-model="providerForm.name" placeholder="例如 DeepSeek" />
          </div>
          <div class="grid gap-2">
            <Label for="provider-vendor">厂商适配</Label>
            <Select v-model="providerForm.vendor">
              <SelectTrigger id="provider-vendor" class="w-full"><SelectValue placeholder="自定义" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="OPENAI">OpenAI 标准</SelectItem>
                <SelectItem value="DEEPSEEK">DeepSeek</SelectItem>
                <SelectItem value="QWEN">通义千问</SelectItem>
                <SelectItem value="OLLAMA">Ollama（本地/外部）</SelectItem>
                <SelectItem value="CUSTOM">自定义</SelectItem>
              </SelectContent>
            </Select>
            <p class="text-xs text-muted-foreground">
              选择厂商后，系统会自动注入 thinking / reasoning_effort 等兼容参数。
            </p>
          </div>
          <div class="grid gap-2">
            <Label for="provider-protocol">协议</Label>
            <Select v-model="providerForm.protocol">
              <SelectTrigger id="provider-protocol" class="w-full"><SelectValue placeholder="OpenAI Chat Completions" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="OPENAI_CHAT">OpenAI Chat Completions</SelectItem>
                <SelectItem value="OPENAI_RESPONSE">OpenAI Response</SelectItem>
                <SelectItem value="ANTHROPIC_MESSAGES">Anthropic Messages</SelectItem>
              </SelectContent>
            </Select>
            <p class="text-xs text-muted-foreground">
              决定请求路径与消息体格式；绝大多数 OpenAI 兼容端点使用 Chat Completions。
            </p>
          </div>
          <div class="grid gap-2">
            <Label for="provider-base">API 端点</Label>
            <Input id="provider-base" v-model="providerForm.baseUrl" placeholder="https://api.deepseek.com" />
          </div>
          <div class="grid gap-2">
            <Label for="provider-key">API Key</Label>
            <Input id="provider-key" v-model="providerForm.apiKey" type="password"
              :placeholder="editingProvider?.apiKeyConfigured ? '已配置，留空保持不变' : 'sk-...'" />
            <p class="text-xs text-muted-foreground">密钥仅存储加密后的密文，接口不会返回明文。</p>
          </div>
          <SettingSwitchRow title="启用供应商" description="停用后其下全部模型不可用" :model-value="providerForm.enabled"
            @update:model-value="providerForm.enabled = $event" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="providerDialogOpen = false">取消</Button>
          <Button :disabled="saving" @click="saveProvider">
            <Spinner v-if="saving" class="mr-2" />保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="fetchDialogOpen">
      <DialogContent class="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>拉取模型 · {{ fetchProvider?.name }}</DialogTitle>
          <DialogDescription>从供应商 /models 接口读取并选择需要接入的模型。</DialogDescription>
        </DialogHeader>
        <div class="flex items-center gap-3">
          <Label class="text-xs">添加到</Label>
          <Select v-model="bulkType">
            <SelectTrigger class="w-40"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="CHAT">聊天模型</SelectItem>
              <SelectItem value="EMBEDDING">向量模型</SelectItem>
              <SelectItem value="RERANK">重排模型</SelectItem>
            </SelectContent>
          </Select>
          <template v-if="bulkType === 'EMBEDDING'">
            <Label class="text-xs">向量维度</Label>
            <Input v-model.number="bulkDimension" type="number" min="1" class="w-28" />
          </template>
          <span class="text-xs text-muted-foreground">已选 {{ selectedRemote.length }} 个</span>
        </div>
        <div class="max-h-72 overflow-y-auto rounded-lg border">
          <div v-if="fetching" class="flex h-40 items-center justify-center"><Spinner /></div>
          <div v-else-if="remoteModels.length === 0" class="p-8 text-center text-sm text-muted-foreground">
            未获取到模型，请检查端点与 Key。
          </div>
          <label v-for="item in remoteModels" :key="item.id"
            class="flex cursor-pointer items-center gap-3 border-b px-3 py-2 text-sm last:border-b-0 hover:bg-muted/50">
            <Checkbox
              :model-value="selectedRemote.includes(item.id)"
              @update:model-value="(checked) => toggleRemote(item.id, checked === true)"
            />
            <span class="min-w-0 flex-1 truncate">{{ item.id }}</span>
            <span v-if="item.ownedBy" class="text-xs text-muted-foreground">{{ item.ownedBy }}</span>
            <Badge v-if="existingModelIds.has(item.id)" variant="secondary">已添加</Badge>
          </label>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="fetchDialogOpen = false">取消</Button>
          <Button :disabled="saving || selectedRemote.length === 0" @click="addSelectedModels">
            <Spinner v-if="saving" class="mr-2" />添加所选
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="modelDialogOpen">
      <DialogContent class="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>编辑模型</DialogTitle>
          <DialogDescription>为单个模型配置可见性、思考模式与厂商扩展参数。</DialogDescription>
        </DialogHeader>
        <div class="grid max-h-[70vh] gap-4 overflow-y-auto pr-1">
          <div class="grid gap-2">
            <Label for="model-name">显示名称</Label>
            <Input id="model-name" v-model="modelForm.displayName" />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="grid gap-2">
              <Label>模型类型</Label>
              <Select v-model="modelForm.modelType">
                <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="CHAT">聊天模型</SelectItem>
                  <SelectItem value="EMBEDDING">向量模型</SelectItem>
                  <SelectItem value="RERANK">重排模型</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="grid gap-2">
              <Label>向量维度</Label>
              <Input v-model.number="modelForm.dimension" type="number" placeholder="1024" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="grid gap-2">
              <Label for="model-context">上下文窗口（tokens）</Label>
              <Input id="model-context" v-model.number="modelForm.contextWindow" type="number" min="1000" max="2000000" placeholder="32000" />
              <p class="text-xs text-muted-foreground">用于滑动窗口裁剪与上下文压缩阈值计算，聊天模型必填。</p>
            </div>
            <div class="grid gap-2">
              <Label for="model-max-output">最大输出 token 数</Label>
              <Input id="model-max-output" v-model.number="modelForm.maxOutputTokens" type="number" min="1" max="1000000" placeholder="4096" />
              <p class="text-xs text-muted-foreground">单次回复的输出上限，留空使用默认 4096。</p>
            </div>
          </div>
          <div v-if="modelForm.modelType === 'CHAT'" class="grid gap-3 rounded-lg border p-4">
            <div class="grid gap-2">
              <Label>计费模式</Label>
              <Select v-model="modelForm.billingMode">
                <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="TOKEN">按输入 / 输出 Token 计费</SelectItem>
                  <SelectItem value="ROUND">按对话轮次计费</SelectItem>
                </SelectContent>
              </Select>
              <p class="text-xs text-muted-foreground">按轮模式下，每次用户提问与助手回复计一轮；工具调用过程中的额外模型请求不单独计费。</p>
            </div>
            <div v-if="modelForm.billingMode === 'TOKEN'" class="grid grid-cols-2 gap-3">
              <div class="grid gap-2">
                <Label for="model-input-price">输入额度 / 百万 Token</Label>
                <Input id="model-input-price" v-model.number="modelForm.inputCreditsPerMillion" type="number" min="0" max="1000000000000" step="1" />
              </div>
              <div class="grid gap-2">
                <Label for="model-output-price">输出额度 / 百万 Token</Label>
                <Input id="model-output-price" v-model.number="modelForm.outputCreditsPerMillion" type="number" min="0" max="1000000000000" step="1" />
              </div>
            </div>
            <div v-else class="grid gap-2">
              <Label for="model-round-price">每轮额度</Label>
              <Input id="model-round-price" v-model.number="modelForm.creditsPerRound" type="number" min="1" max="1000000000000" step="1" />
            </div>
          </div>
          <div class="grid gap-2">
            <Label>输入类型</Label>
            <div class="flex flex-wrap items-center gap-6">
              <label class="flex cursor-pointer items-center gap-2 text-sm">
                <Checkbox
                  :model-value="modelForm.supportsText"
                  @update:model-value="(checked) => modelForm.supportsText = checked === true"
                />
                文本
              </label>
              <label class="flex cursor-pointer items-center gap-2 text-sm">
                <Checkbox
                  :model-value="modelForm.supportsVision"
                  @update:model-value="(checked) => modelForm.supportsVision = checked === true"
                />
                图片
              </label>
            </div>
            <p class="text-xs text-muted-foreground">勾选「图片」后允许在对话中上传图片，仅视觉模型可用。</p>
          </div>
          <div class="grid gap-2">
            <Label>推理强度</Label>
            <Select v-model="modelForm.reasoningEffort">
              <SelectTrigger class="w-full"><SelectValue placeholder="不设置" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="low">low</SelectItem>
                <SelectItem value="medium">medium</SelectItem>
                <SelectItem value="high">high</SelectItem>
                <SelectItem value="max">max</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="grid gap-2">
            <Label for="model-extra">额外请求参数（JSON）</Label>
            <Textarea id="model-extra" v-model="modelForm.extraBody" rows="4"
              placeholder='{"thinking":{"type":"enabled"}}' />
          </div>
          <SettingSwitchRow title="启用" :model-value="modelForm.enabled"
            @update:model-value="modelForm.enabled = $event" />
          <SettingSwitchRow title="对用户可见" :model-value="modelForm.visible"
            @update:model-value="modelForm.visible = $event" />
          <SettingSwitchRow title="支持工具调用" :model-value="modelForm.supportsTools"
            @update:model-value="modelForm.supportsTools = $event" />
          <SettingSwitchRow title="默认开启思考" description="开启后默认展示思考过程，并可使用下方推理档位"
            :model-value="modelForm.thinkingEnabled"
            @update:model-value="modelForm.thinkingEnabled = $event" />
          <div class="grid gap-3 rounded-lg border p-4" :class="!modelForm.thinkingEnabled && 'opacity-60'">
            <div class="flex flex-wrap items-start justify-between gap-2">
              <div class="flex flex-col gap-0.5">
                <Label>推理档位</Label>
                <p class="text-xs text-muted-foreground">
                  档位用于界面展示，API 参数值留空则直接使用档位名称；仅思考开启时生效。
                </p>
              </div>
              <Button variant="outline" size="sm" :disabled="!modelForm.thinkingEnabled" @click="addReasoningLevel">
                <IconPlus class="mr-1 size-4" />添加档位
              </Button>
            </div>
            <div v-if="modelForm.reasoningLevels.length === 0"
              class="rounded-lg border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
              还没有配置档位，例如 off / low / medium / high。
            </div>
            <template v-else>
              <div class="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] items-center gap-2 text-xs text-muted-foreground">
                <span>档位</span>
                <span>API 参数值</span>
                <span class="w-8" />
              </div>
              <div v-for="(level, index) in modelForm.reasoningLevels" :key="index"
                class="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] items-center gap-2">
                <Input v-model="level.label" :disabled="!modelForm.thinkingEnabled" placeholder="off / low / medium" />
                <Input v-model="level.value" :disabled="!modelForm.thinkingEnabled" placeholder="留空则直接使用档位" />
                <Button variant="ghost" size="icon-sm" title="删除档位" :disabled="!modelForm.thinkingEnabled"
                  @click="removeReasoningLevel(index)">
                  <IconTrash class="size-4" />
                </Button>
              </div>
            </template>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="modelDialogOpen = false">取消</Button>
          <Button :disabled="saving" @click="saveModel">
            <Spinner v-if="saving" class="mr-2" />保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!deleteTarget" @update:open="(open) => { if (!open) deleteTarget = null }">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>确认删除</DialogTitle>
          <DialogDescription>
            {{ deleteTarget?.kind === 'provider'
              ? `删除供应商「${deleteTarget?.name}」及其下全部模型？此操作不可撤销。`
              : `删除模型「${deleteTarget?.name}」？此操作不可撤销。` }}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" :disabled="deleting" @click="deleteTarget = null">取消</Button>
          <Button variant="destructive" :disabled="deleting" @click="confirmRemove">
            <Spinner v-if="deleting" class="mr-2" />删除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import {
  IconAlertTriangle, IconBrain, IconCloudDownload, IconCpu, IconList, IconPencil, IconPlug,
  IconPlus, IconRefresh, IconServer, IconTrash,
} from '@tabler/icons-vue'
import { modelApi, ollamaApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type {
  ModelConfig, ModelProvider, ModelType, ProviderProtocol, ReasoningLevel, RemoteModel,
  SaveModelConfigPayload, SaveModelProviderPayload,
} from '@/types/api'
import type { OllamaStatus } from '@/api/ollama'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Progress } from '@/components/ui/progress'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import SettingSwitchRow from '@/components/console/SettingSwitchRow.vue'

const userStore = useUserStore()
const canUpdate = computed(() => userStore.hasPermission('model:update') || userStore.hasPermission('settings:update'))

const loading = ref(true)
const saving = ref(false)
const importingPrices = ref(false)
const error = ref('')
const activeTab = ref('providers')
const providers = ref<ModelProvider[]>([])
const configs = ref<ModelConfig[]>([])

const providerDialogOpen = ref(false)
const editingProvider = ref<ModelProvider | null>(null)
const ollamaStatus = ref<OllamaStatus | null>(null)
const ollamaModel = ref('')
const ollamaPulling = ref(false)
const ollamaInsecure = ref(false)
const ollamaProgress = ref(0)
const ollamaPullMessage = ref('')

const providerForm = reactive({
  name: '', vendor: 'OPENAI', baseUrl: '',
  protocol: 'OPENAI_CHAT' as ProviderProtocol,
  apiKey: '', enabled: true,
})

const fetchDialogOpen = ref(false)
const fetchProvider = ref<ModelProvider | null>(null)
const fetching = ref(false)
const remoteModels = ref<RemoteModel[]>([])
const selectedRemote = ref<string[]>([])

function toggleRemote(modelId: string, checked: boolean) {
  if (checked) {
    if (!selectedRemote.value.includes(modelId))
      selectedRemote.value.push(modelId)
  } else {
    selectedRemote.value = selectedRemote.value.filter(id => id !== modelId)
  }
}
const bulkType = ref<ModelType>('CHAT')
const bulkDimension = ref(1024)

const modelDialogOpen = ref(false)
const editingModel = ref<ModelConfig | null>(null)
const modelForm = reactive({
  displayName: '', modelType: 'CHAT' as ModelType, dimension: undefined as number | undefined,
  contextWindow: 32000 as number | undefined,
  maxOutputTokens: 4096 as number | undefined,
  billingMode: 'TOKEN' as 'TOKEN' | 'ROUND',
  inputCreditsPerMillion: 1_000_000,
  outputCreditsPerMillion: 1_000_000,
  creditsPerRound: 0,
  supportsText: true,
  reasoningEffort: '', extraBody: '', enabled: true, visible: true, supportsTools: false, thinkingEnabled: false, supportsVision: false,
  reasoningLevels: [] as ReasoningLevel[],
})

const existingModelIds = computed(() => new Set(configs.value.map(item => item.modelId)))

async function loadOllama() {
  try {
    ollamaStatus.value = await ollamaApi.status()
  } catch {
    ollamaStatus.value = null
  }
}

async function pullOllama() {
  const model = ollamaModel.value.trim()
  if (!model || ollamaPulling.value) return
  ollamaPulling.value = true
  ollamaPullMessage.value = `正在拉取 ${model}…`
  try {
    ollamaProgress.value = 0
    let task = await ollamaApi.pull(ollamaStatus.value?.providerId, model, ollamaInsecure.value)
    while (!task.done) {
      ollamaProgress.value = task.percent ?? 0
      await new Promise(resolve => setTimeout(resolve, 1000))
      task = await ollamaApi.pullStatus(task.taskId)
      ollamaPullMessage.value = task.message
    }
    ollamaProgress.value = task.percent ?? 100
    if (task.status === 'succeeded') {
      toast.success(`模型 ${model} 拉取完成`)
      ollamaModel.value = ''
      await loadOllama()
    } else {
      toast.error(task.message)
    }
  } catch (e: any) {
    toast.error(e?.message || '拉取模型失败')
  } finally {
    ollamaPulling.value = false
  }
}

function openOllamaFetch() {
  if (!ollamaStatus.value?.providerId) return
  fetchProvider.value = {
    id: Number(ollamaStatus.value.providerId),
    name: ollamaStatus.value.providerName || 'Ollama',
    vendor: 'OLLAMA',
    baseUrl: ollamaStatus.value.baseUrl || '',
    protocol: 'OPENAI_CHAT',
    apiKeyConfigured: false,
    enabled: true,
    modelCount: ollamaStatus.value.models.length,
  }
  remoteModels.value = ollamaStatus.value.models.map(name => ({ id: name, ownedBy: 'Ollama' }))
  selectedRemote.value = []
  fetchDialogOpen.value = true
}

function openOllamaProvider() {
  openProviderDialog()
  providerForm.name = 'Ollama（本地）'
  providerForm.vendor = 'OLLAMA'
  providerForm.baseUrl = 'http://127.0.0.1:11434/v1'
  providerForm.protocol = 'OPENAI_CHAT'
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [providerList, modelList] = await Promise.all([modelApi.providers(), modelApi.configs()])
    providers.value = providerList
    configs.value = modelList
    await loadOllama()
  } catch (err: any) {
    error.value = err?.message || '加载模型配置失败'
  } finally {
    loading.value = false
  }
}

interface ModelsDevPriceMatch {
  model: ModelConfig
  sourceProvider: string
  inputCreditsPerMillion: number
  outputCreditsPerMillion: number
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function normalizedProviderName(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]/g, '')
}

function matchModelsDevProvider(provider: ModelProvider, catalogProviderId: string, catalogName: string): boolean {
  const vendorAliases: Record<string, string[]> = {
    OPENAI: ['openai'],
    DEEPSEEK: ['deepseek'],
    QWEN: ['alibaba', 'qwen'],
    OLLAMA: ['ollama'],
  }
  const configured = [
    provider.name,
    provider.vendor ?? '',
    ...(vendorAliases[provider.vendor ?? ''] ?? []),
  ].map(normalizedProviderName).filter(Boolean)
  const catalog = [catalogProviderId, catalogName].map(normalizedProviderName).filter(Boolean)
  return configured.some(name => catalog.includes(name))
}

function findModelsDevPriceMatches(catalog: unknown): ModelsDevPriceMatch[] {
  if (!isRecord(catalog))
    return []
  const catalogModels: Array<{
    id: string
    providerId: string
    providerName: string
    input: number
    output: number
  }> = []
  for (const [providerId, providerValue] of Object.entries(catalog)) {
    if (!isRecord(providerValue) || !isRecord(providerValue.models))
      continue
    const providerName = typeof providerValue.name === 'string' ? providerValue.name : providerId
    for (const [modelKey, modelValue] of Object.entries(providerValue.models)) {
      if (!isRecord(modelValue) || !isRecord(modelValue.cost))
        continue
      const input = modelValue.cost.input
      const output = modelValue.cost.output
      if (typeof input !== 'number' || !Number.isFinite(input) || input < 0
        || typeof output !== 'number' || !Number.isFinite(output) || output < 0)
        continue
      const inputCreditsPerMillion = Math.round(input * 1_000_000)
      const outputCreditsPerMillion = Math.round(output * 1_000_000)
      if (inputCreditsPerMillion > 1_000_000_000_000 || outputCreditsPerMillion > 1_000_000_000_000)
        continue
      catalogModels.push({
        id: typeof modelValue.id === 'string' ? modelValue.id : modelKey,
        providerId,
        providerName,
        input: inputCreditsPerMillion,
        output: outputCreditsPerMillion,
      })
    }
  }

  const matches: ModelsDevPriceMatch[] = []
  for (const model of configs.value.filter(item => item.modelType === 'CHAT')) {
    const exactMatches = catalogModels.filter(item => item.id.toLowerCase() === model.modelId.toLowerCase())
    const provider = providers.value.find(item => item.id === model.providerId)
    if (!provider)
      continue
    const providerMatches = exactMatches.filter(item =>
      matchModelsDevProvider(provider, item.providerId, item.providerName))
    const match = providerMatches.length === 1
      ? providerMatches[0]
      : providerMatches.length === 0 && exactMatches.length === 1 ? exactMatches[0] : undefined
    if (match) {
      matches.push({
        model,
        sourceProvider: match.providerName,
        inputCreditsPerMillion: match.input,
        outputCreditsPerMillion: match.output,
      })
    }
  }
  return matches
}

async function importModelsDevPrices() {
  importingPrices.value = true
  try {
    const response = await fetch('https://models.dev/api.json?type=all')
    if (!response.ok)
      throw new Error(`Models.dev 请求失败（HTTP ${response.status}）`)
    const matches = findModelsDevPriceMatches(await response.json())
    if (!matches.length) {
      toast.error('没有找到供应商和模型 ID 唯一匹配且包含输入、输出价格的聊天模型')
      return
    }
    const preview = matches.slice(0, 5)
      .map(match => `${match.model.modelId} ← ${match.sourceProvider}`)
      .join('\n')
    const more = matches.length > 5 ? `\n以及其他 ${matches.length - 5} 个模型` : ''
    const confirmed = window.confirm(
      `找到 ${matches.length} 个可匹配的聊天模型：\n${preview}${more}\n\n继续后将覆盖这些模型的输入/输出计费价格，并切换为按 Token 计费。`,
    )
    if (!confirmed)
      return
    saving.value = true
    let applied = 0
    try {
      for (const match of matches) {
        await modelApi.saveConfig({
          providerId: match.model.providerId,
          modelId: match.model.modelId,
          modelType: match.model.modelType,
          billingMode: 'TOKEN',
          inputCreditsPerMillion: match.inputCreditsPerMillion,
          outputCreditsPerMillion: match.outputCreditsPerMillion,
          creditsPerRound: match.model.creditsPerRound,
        })
        applied++
      }
      toast.success(`已从 Models.dev 更新 ${applied} 个模型的计费价格`)
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : '请求失败'
      toast.error(`价格导入中断，已更新 ${applied}/${matches.length} 个模型：${message}`)
    }
    if (applied > 0)
      await loadAll()
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : '请求失败'
    toast.error(`读取 Models.dev 价格失败：${message}`)
  } finally {
    saving.value = false
    importingPrices.value = false
  }
}

function openProviderDialog(provider?: ModelProvider) {
  editingProvider.value = provider ?? null
  providerForm.name = provider?.name ?? ''
  providerForm.vendor = provider?.vendor || 'OPENAI'
  providerForm.baseUrl = provider?.baseUrl ?? ''
  providerForm.protocol = provider?.protocol ?? 'OPENAI_CHAT'
  providerForm.apiKey = ''
  providerForm.enabled = provider?.enabled ?? true
  providerDialogOpen.value = true
}

async function saveProvider() {
  if (!providerForm.name.trim() || !providerForm.baseUrl.trim()) {
    toast.error('请填写名称和 API 端点')
    return
  }
  saving.value = true
  try {
    const payload: SaveModelProviderPayload = {
      name: providerForm.name.trim(),
      vendor: providerForm.vendor,
      baseUrl: providerForm.baseUrl.trim(),
      protocol: providerForm.protocol,
      apiKey: providerForm.apiKey.trim() || undefined,
      enabled: providerForm.enabled,
    }
    const saved = editingProvider.value
      ? await modelApi.updateProvider(editingProvider.value.id, payload)
      : await modelApi.createProvider(payload)
    toast.success(editingProvider.value ? '供应商已更新' : '供应商已添加')
    providerDialogOpen.value = false
    await loadAll()
    if (!editingProvider.value && saved) {
      openFetch(saved)
    }
  } catch (err: any) {
    toast.error(err?.message || '保存供应商失败')
  } finally {
    saving.value = false
  }
}

type DeleteTarget = { kind: 'provider' | 'model'; id: number; name: string }
const deleteTarget = ref<DeleteTarget | null>(null)
const deleting = ref(false)

function askRemoveProvider(provider: ModelProvider) {
  deleteTarget.value = { kind: 'provider', id: provider.id, name: provider.name }
}

function askRemoveModel(model: ModelConfig) {
  deleteTarget.value = { kind: 'model', id: model.id, name: model.displayName || model.modelId }
}

async function confirmRemove() {
  const target = deleteTarget.value
  if (!target) return
  deleting.value = true
  try {
    if (target.kind === 'provider') {
      await modelApi.deleteProvider(target.id)
      toast.success('供应商已删除')
    } else {
      await modelApi.deleteConfig(target.id)
      toast.success('模型已删除')
    }
    deleteTarget.value = null
    await loadAll()
  } catch (err: any) {
    toast.error(err?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

async function openFetch(provider: ModelProvider) {
  fetchProvider.value = provider
  remoteModels.value = []
  selectedRemote.value = []
  fetchDialogOpen.value = true
  fetching.value = true
  try {
    remoteModels.value = await modelApi.fetchModels(provider.id)
    if (remoteModels.value.length === 0)
      toast.error('供应商未返回任何模型')
  } catch (err: any) {
    toast.error(err?.message || '拉取模型失败')
  } finally {
    fetching.value = false
  }
}

async function addSelectedModels() {
  if (!fetchProvider.value)
    return
  saving.value = true
  try {
    for (const modelId of selectedRemote.value) {
      if (existingModelIds.value.has(modelId))
        continue
      const payload: SaveModelConfigPayload = {
        providerId: fetchProvider.value.id,
        modelId,
        modelType: bulkType.value,
        dimension: bulkType.value === 'EMBEDDING' ? bulkDimension.value : undefined,
        displayName: modelId,
        enabled: true,
        visible: bulkType.value === 'CHAT',
        supportsTools: bulkType.value === 'CHAT',
      }
      await modelApi.saveConfig(payload)
    }
    toast.success(`已添加 ${selectedRemote.value.length} 个模型`)
    fetchDialogOpen.value = false
    await loadAll()
    activeTab.value = 'models'
  } catch (err: any) {
    toast.error(err?.message || '添加模型失败')
  } finally {
    saving.value = false
  }
}

async function toggle(model: ModelConfig, field: 'enabled' | 'visible' | 'supportsTools' | 'thinkingEnabled', value: boolean) {
  const previous = model[field]
  model[field] = value
  try {
    await modelApi.saveConfig({ ...model, [field]: value })
  } catch (err: any) {
    model[field] = previous
    toast.error(err?.message || '更新失败')
  }
}

function protocolLabel(protocol?: ProviderProtocol) {
  if (protocol === 'OPENAI_RESPONSE')
    return 'Responses'
  if (protocol === 'ANTHROPIC_MESSAGES')
    return 'Anthropic'
  return 'Chat Completions'
}

function addReasoningLevel() {
  modelForm.reasoningLevels.push({ label: '' })
}

function removeReasoningLevel(index: number) {
  modelForm.reasoningLevels.splice(index, 1)
}

function parseReasoningLevels(raw?: string | null): ReasoningLevel[] {
  if (!raw?.trim())
    return []
  try {
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed))
      return []
    return parsed.flatMap<ReasoningLevel>((item) => {
      if (!item || typeof item !== 'object')
        return []
      const record = item as Record<string, unknown>
      const label = typeof record.label === 'string' ? record.label.trim() : ''
      if (!label)
        return []
      const value = typeof record.value === 'string' && record.value.trim() ? record.value : undefined
      return [{ label, value }]
    })
  } catch {
    return []
  }
}

function serializeReasoningLevels(rows: ReasoningLevel[]) {
  const normalized = rows.flatMap<ReasoningLevel>((row) => {
    const label = row.label.trim()
    if (!label)
      return []
    const value = row.value?.trim()
    return [value ? { label, value } : { label }]
  })
  return normalized.length ? JSON.stringify(normalized) : ''
}

function openModelDialog(model: ModelConfig) {
  editingModel.value = model
  modelForm.displayName = model.displayName
  modelForm.modelType = model.modelType
  modelForm.dimension = model.dimension
  modelForm.contextWindow = model.contextWindow ?? 32000
  modelForm.maxOutputTokens = model.maxOutputTokens ?? 4096
  modelForm.billingMode = model.billingMode ?? 'TOKEN'
  modelForm.inputCreditsPerMillion = model.inputCreditsPerMillion ?? 1_000_000
  modelForm.outputCreditsPerMillion = model.outputCreditsPerMillion ?? 1_000_000
  modelForm.creditsPerRound = model.creditsPerRound ?? 0
  modelForm.supportsText = model.supportsText ?? true
  modelForm.reasoningEffort = model.reasoningEffort ?? ''
  modelForm.extraBody = model.extraBody ?? ''
  modelForm.enabled = model.enabled
  modelForm.visible = model.visible
  modelForm.supportsTools = model.supportsTools
  modelForm.thinkingEnabled = model.thinkingEnabled
  modelForm.supportsVision = !!model.supportsVision
  modelForm.reasoningLevels = parseReasoningLevels(model.reasoningLevels)
  modelDialogOpen.value = true
}

async function saveModel() {
  if (!editingModel.value)
    return
  saving.value = true
  try {
    const maxOutput = Number(modelForm.maxOutputTokens)
    const maxOutputTokens = Number.isFinite(maxOutput) && maxOutput >= 1
      ? Math.min(Math.round(maxOutput), 1000000)
      : undefined
    await modelApi.saveConfig({
      providerId: editingModel.value.providerId,
      modelId: editingModel.value.modelId,
      modelType: modelForm.modelType,
      displayName: modelForm.displayName.trim() || editingModel.value.modelId,
      enabled: modelForm.enabled,
      visible: modelForm.visible,
      supportsTools: modelForm.supportsTools,
      supportsText: modelForm.supportsText,
      thinkingEnabled: modelForm.thinkingEnabled,
      supportsVision: modelForm.supportsVision,
      reasoningEffort: modelForm.reasoningEffort || undefined,
      reasoningLevels: serializeReasoningLevels(modelForm.reasoningLevels),
      extraBody: modelForm.extraBody.trim() || undefined,
      dimension: modelForm.dimension,
      contextWindow: modelForm.contextWindow,
      maxOutputTokens,
      billingMode: modelForm.billingMode,
      inputCreditsPerMillion: modelForm.inputCreditsPerMillion,
      outputCreditsPerMillion: modelForm.outputCreditsPerMillion,
      creditsPerRound: modelForm.creditsPerRound,
    })
    toast.success('模型已更新')
    modelDialogOpen.value = false
    await loadAll()
  } catch (err: any) {
    toast.error(err?.message || '保存模型失败')
  } finally {
    saving.value = false
  }
}


function typeLabel(type: ModelType) {
  return type === 'CHAT' ? '聊天' : type === 'EMBEDDING' ? '向量' : '重排'
}

onMounted(() => void loadAll())
</script>
