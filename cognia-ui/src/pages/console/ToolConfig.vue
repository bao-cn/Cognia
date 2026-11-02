<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-col gap-1">
      <h1 class="flex items-center gap-2 text-xl font-semibold">
        <IconTool class="size-5 text-muted-foreground" />
        工具配置
      </h1>
      <p class="text-sm text-muted-foreground">
        内置 web_search 与 web_fetch，按 OpenAI function calling 协议提供给支持工具调用的模型。
      </p>
    </div>

    <div class="grid gap-4 md:grid-cols-2">
      <section class="rounded-xl border bg-card">
        <header class="flex flex-col gap-1 border-b px-4 py-3">
          <h2 class="text-sm font-semibold">web_search</h2>
          <p class="text-xs text-muted-foreground">按 query 搜索网页，返回标题、链接与摘要。</p>
        </header>
        <div class="p-4 text-sm text-muted-foreground">
          支持 DeepSeek 搜索端点（填写 API Key 后优先调用），未配置时使用 DuckDuckGo 兜底；可在「检索参数配置 → 网页工具」调整。
        </div>
      </section>
      <section class="rounded-xl border bg-card">
        <header class="flex flex-col gap-1 border-b px-4 py-3">
          <h2 class="text-sm font-semibold">web_fetch</h2>
          <p class="text-xs text-muted-foreground">抓取单个网页并转为纯文本 / Markdown。</p>
        </header>
        <div class="p-4 text-sm text-muted-foreground">
          自动移除脚本和样式，限制返回长度，避免超出模型上下文。
        </div>
      </section>
    </div>

    <div class="flex items-center gap-3 rounded-xl border bg-muted/30 px-4 py-3 text-sm">
      <IconInfoCircle class="size-4 text-muted-foreground" />
      <span class="text-muted-foreground">搜索端点和密钥在检索参数配置中统一维护。</span>
      <RouterLink to="/console/system/retrieval" class="ml-auto font-medium text-primary hover:underline">
        前往配置
      </RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { IconInfoCircle, IconTool } from '@tabler/icons-vue'
</script>
