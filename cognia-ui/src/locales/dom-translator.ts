import { watch } from 'vue'
import { enPhrases } from './phrases'

const ATTRIBUTES = ['placeholder', 'title', 'aria-label', 'alt'] as const
const SKIP_SELECTOR = [
  '[data-i18n-skip]',
  'pre',
  'code',
  'textarea',
  'input',
  'script',
  'style',
  'svg',
  '.cm-editor',
  '.markdown-body',
  '.citation-snippet',
].join(',')

type TextOriginal = string
const textOriginals = new WeakMap<Text, TextOriginal>()
const attributeOriginals = new WeakMap<Element, Record<string, string>>()
let titleOriginal = ''
let titleTranslated = ''

function normalize(value: string) {
  return value.trim()
}

const DYNAMIC_RULES: Array<[RegExp, (match: RegExpMatchArray) => string]> = [
  [/^更新于 (.+)$/, m => `Updated ${m[1]}`],
  [/^正常 (\d+)$/, m => `Active ${m[1]}`],
  [/^封禁 (\d+)$/, m => `Banned ${m[1]}`],
  [/^30 天不活跃 (\d+)$/, m => `Inactive 30d ${m[1]}`],
  [/^公开 (\d+) · 私有 (\d+)$/, m => `Public ${m[1]} · Private ${m[2]}`],
  [/^([\d.]+\s?[KMGT]?B) 已存储$/, m => `${m[1]} stored`],
  [/^今日 (\d+) · 7 天 (\d+)$/, m => `Today ${m[1]} · 7 days ${m[2]}`],
  [/^(\d+) 个文件$/, m => `${m[1]} files`],
  [/^UP · 连接 (\d+)$/, m => `UP · connections ${m[1]}`],
  [/^UP · 在线 (\d+)$/, m => `UP · online ${m[1]}`],
  [/^IDLE · 队列 (\d+)$/, m => `IDLE · queue ${m[1]}`],
  [/^UP · (\d+) 启用$/, m => `UP · ${m[1]} enabled`],
  [/^([\d.]+\s?[KMGT]?B) · 慢查询 (\d+)$/, m => `${m[1]} · slow queries ${m[2]}`],
  [/^Redis 命中率 ([\d.]+%)$/, m => `Redis hit rate ${m[1]}`],
  [/^对象 (\d+)$/, m => `Objects ${m[1]}`],
  [/^临时文件生命周期 (\d+) 天$/, m => `Temporary file lifecycle: ${m[1]} days`],
  [/^消息队列 未启用（同步处理）$/, () => 'Message queue disabled (synchronous processing)'],
  [/^(\d+) 类$/, m => `${m[1]} types`],
  [/^共 (\d+) 个频道$/, m => `${m[1]} channels`],
  [/^(\d+) 个频道$/, m => `${m[1]} channels`],
  [/^(\d+) 个文档$/, m => `${m[1]} documents`],
  [/^(\d+) 文档$/, m => `${m[1]} documents`],
  [/^(\d+) 订阅$/, m => `${m[1]} subscribers`],
  [/^(\d+) 条$/, m => `${m[1]} items`],
  [/^(\d+) 位成员$/, m => `${m[1]} members`],
  [/^扫描到 (\d+) 个超时文档，标记失败 (\d+) 个$/, m => `Scanned ${m[1]} timed-out documents, marked ${m[2]} failed`],
  [/^清理超过 (\d+) 小时的残留分片，处理 (\d+) 个$/, m => `Cleaned incomplete uploads older than ${m[1]} hours; processed ${m[2]}`],
  [/^清理超过 (\d+) 天的回收站记录，处理 (\d+) 个$/, m => `Cleaned recycle bin records older than ${m[1]} days; processed ${m[2]}`],
  [/^清理注销超过 (\d+) 天的用户\/团队文件，处理 (\d+) 个$/, m => `Cleaned files of users/teams deleted over ${m[1]} days ago; processed ${m[2]}`],
  [/^将临时文件生命周期同步为 (\d+) 天$/, m => `Synced temporary file lifecycle to ${m[1]} days`],
]

export function translatePhrase(value: string): string | undefined {
  const key = normalize(value)
  if (!key)
    return undefined
  const exact = enPhrases[key]
  if (exact)
    return exact
  for (const [pattern, format] of DYNAMIC_RULES) {
    const match = key.match(pattern)
    if (match)
      return format(match)
  }
  return undefined
}

function shouldSkip(node: Node): boolean {
  const element = node.nodeType === Node.ELEMENT_NODE ? node as Element : node.parentElement
  return !!element?.closest(SKIP_SELECTOR)
}

function translateTextNode(node: Text, english: boolean) {
  if (shouldSkip(node))
    return

  if (!english) {
    const original = textOriginals.get(node)
    if (original != null && node.nodeValue !== original)
      node.nodeValue = original
    return
  }

  const value = node.nodeValue ?? ''
  const translated = translatePhrase(value)
  if (!translated)
    return

  if (!textOriginals.has(node))
    textOriginals.set(node, value)

  const leading = value.match(/^\s*/)?.[0] ?? ''
  const trailing = value.match(/\s*$/)?.[0] ?? ''
  const next = `${leading}${translated}${trailing}`
  if (node.nodeValue !== next)
    node.nodeValue = next
}

function translateAttributes(element: Element, english: boolean) {
  if (shouldSkip(element))
    return

  const stored = attributeOriginals.get(element) ?? {}
  let changed = false

  for (const name of ATTRIBUTES) {
    const current = element.getAttribute(name)
    if (current == null)
      continue

    if (!english) {
      if (stored[name] != null && current !== stored[name]) {
        element.setAttribute(name, stored[name])
        changed = true
      }
      continue
    }

    const translated = translatePhrase(current)
    if (!translated)
      continue

    if (stored[name] == null)
      stored[name] = current
    if (current !== translated) {
      element.setAttribute(name, translated)
      changed = true
    }
  }

  if (Object.keys(stored).length)
    attributeOriginals.set(element, stored)
  return changed
}

function walk(root: Node, english: boolean) {
  if (root.nodeType === Node.TEXT_NODE) {
    translateTextNode(root as Text, english)
    return
  }
  if (root.nodeType !== Node.ELEMENT_NODE && root.nodeType !== Node.DOCUMENT_FRAGMENT_NODE)
    return

  const element = root as Element
  if (element.nodeType === Node.ELEMENT_NODE)
    translateAttributes(element, english)

  const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      return shouldSkip(node) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT
    },
  })
  let node: Node | null = walker.currentNode
  while ((node = walker.nextNode())) {
    if (node.nodeType === Node.TEXT_NODE)
      translateTextNode(node as Text, english)
    else
      translateAttributes(node as Element, english)
  }
}


function translateDocumentTitle(english: boolean) {
  const node = document.querySelector('title')
  if (!node)
    return
  if (!english) {
    if (titleOriginal && node.textContent === titleTranslated)
      node.textContent = titleOriginal
    return
  }
  const value = node.textContent ?? ''
  if (value === titleTranslated)
    return
  const translated = value
    .split(' · ')
    .map(part => translatePhrase(part) ?? part)
    .join(' · ')
  if (translated !== value) {
    titleOriginal = value
    titleTranslated = translated
    node.textContent = translated
  }
}

export function setupDomTranslator(i18n: any) {
  const isEnglish = () => (i18n.global.locale as any).value === 'enUs'
  let observer: MutationObserver | null = null

  const start = () => {
    observer?.disconnect()
    const english = isEnglish()
    walk(document.body, english)
    translateDocumentTitle(english)
    if (!english)
      return

    observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        if (mutation.type === 'characterData') {
          translateTextNode(mutation.target as Text, true)
        }
        else if (mutation.type === 'attributes') {
          if (mutation.attributeName && (ATTRIBUTES as readonly string[]).includes(mutation.attributeName))
            translateAttributes(mutation.target as Element, true)
        }
        else {
          for (const node of mutation.addedNodes)
            walk(node, true)
        }
      }
    })
    observer.observe(document.body, {
      subtree: true,
      childList: true,
      characterData: true,
      attributes: true,
      attributeFilter: [...ATTRIBUTES],
    })
  }

  const titleObserver = new MutationObserver(() => translateDocumentTitle(isEnglish()))
  titleObserver.observe(document.head, { subtree: true, childList: true, characterData: true })

  watch(() => (i18n.global.locale as any).value, () => {
    observer?.disconnect()
    observer = null
    translateDocumentTitle(isEnglish())
    if (isEnglish()) {
      start()
    }
    else {
      walk(document.body, false)
      for (const node of document.querySelectorAll('*')) {
        if (node.nodeType === Node.ELEMENT_NODE)
          translateAttributes(node, false)
      }
    }
  })

  start()
}
