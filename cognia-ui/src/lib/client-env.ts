export type ClientPlatform = 'windows' | 'macos' | 'linux' | 'ios' | 'android' | 'unknown'

interface CogniaClientWindow extends Window {
  __COGNIA_CLIENT__?: boolean
  __COGNIA_CLIENT_VERSION__?: string
  __COGNIA_CLIENT_PLATFORM__?: string
  __TAURI__?: unknown
  __TAURI_INTERNALS__?: unknown
  electronAPI?: unknown
  cogniaDesktop?: unknown
  process?: {
    versions?: {
      electron?: string
    }
  }
  cognia?: {
    isClient?: boolean
    version?: string
    platform?: string
  }
}

function clientWindow(): CogniaClientWindow | null {
  if (typeof window === 'undefined') return null
  return window as CogniaClientWindow
}

function normalizePlatform(value?: string): ClientPlatform | null {
  if (!value) return null
  const normalized = value.toLowerCase()
  if (normalized.includes('win')) return 'windows'
  if (normalized.includes('mac') || normalized.includes('darwin')) return 'macos'
  if (normalized.includes('linux')) return 'linux'
  if (normalized.includes('iphone') || normalized.includes('ipad') || normalized.includes('ios')) return 'ios'
  if (normalized.includes('android')) return 'android'
  return null
}

/**
 * Desktop/mobile clients set a global flag or include a product token in the
 * user agent. `?client=1` is a deterministic development override; it lets the
 * download page be tested without shipping a desktop build.
 */
export function isCogniaClient(): boolean {
  const win = clientWindow()
  if (!win) return false
  const override = new URLSearchParams(win.location.search).get('client')
  if (override === '1') return true
  if (override === '0') return false
  if (win.__COGNIA_CLIENT__ || win.cognia?.isClient) return true
  if (win.__TAURI__ || win.__TAURI_INTERNALS__ || win.electronAPI || win.cogniaDesktop) return true
  return /CogniaClient|CogniaDesktop|Electron|Tauri/i.test(win.navigator.userAgent)
}

/**
 * The sidebar download CTA is only hidden inside an actual Electron shell.
 * Tauri, PWA and the `?client=1` preview override intentionally do not count.
 */
export function isElectronClient(): boolean {
  const win = clientWindow()
  if (!win) return false
  if (win.process?.versions?.electron) return true
  if (win.electronAPI) return true
  return /Electron/i.test(win.navigator.userAgent)
}

export function getClientVersion(): string | null {
  const win = clientWindow()
  return win?.__COGNIA_CLIENT_VERSION__ || win?.cognia?.version || null
}

export function getClientPlatform(): ClientPlatform {
  const win = clientWindow()
  if (!win) return 'unknown'
  const explicit = normalizePlatform(win.__COGNIA_CLIENT_PLATFORM__ || win.cognia?.platform)
  if (explicit && explicit !== 'unknown') return explicit

  const ua = win.navigator.userAgent
  if (/Windows/i.test(ua)) return 'windows'
  if (/Macintosh|Mac OS X/i.test(ua)) return 'macos'
  if (/Android/i.test(ua)) return 'android'
  if (/iPhone|iPad|iPod/i.test(ua)) return 'ios'
  if (/Linux/i.test(ua)) return 'linux'
  return 'unknown'
}

interface UserAgentDataLike {
  architecture?: string
  bitness?: string
  getHighEntropyValues?: (hints: string[]) => Promise<{ architecture?: string; bitness?: string }>
}

/**
 * Best-effort CPU architecture detection for selecting the recommended build.
 * Chromium exposes User-Agent Client Hints; Safari/Firefox fall back to WebGL
 * renderer strings and finally to a sane per-platform default.
 */
export async function getClientArchitecture(): Promise<'x64' | 'arm64' | null> {
  if (typeof window === 'undefined') return null
  const nav = window.navigator as Navigator & { userAgentData?: UserAgentDataLike }
  try {
    const data = await nav.userAgentData?.getHighEntropyValues?.(['architecture', 'bitness'])
    const architecture = data?.architecture?.toLowerCase()
    if (architecture === 'arm') return 'arm64'
    if (architecture === 'x86') return 'x64'
  } catch {
    // Fall through to heuristics.
  }

  try {
    const canvas = document.createElement('canvas')
    const gl = canvas.getContext('webgl') as WebGLRenderingContext | null
    const renderer = gl?.getExtension('WEBGL_debug_renderer_info')
      ? String(gl.getParameter(gl.getExtension('WEBGL_debug_renderer_info')!.UNMASKED_RENDERER_WEBGL))
      : ''
    if (/Apple M|ARM|Mali|Adreno/i.test(renderer)) return 'arm64'
    if (/Intel|AMD|NVIDIA|x86/i.test(renderer)) return 'x64'
  } catch {
    // Ignore WebGL failures.
  }

  if (getClientPlatform() === 'macos') return 'arm64'
  return 'x64'
}

export function platformLabel(platform: ClientPlatform): string {
  switch (platform) {
    case 'windows': return 'Windows'
    case 'macos': return 'macOS'
    case 'linux': return 'Linux'
    case 'ios': return 'iOS'
    case 'android': return 'Android'
    default: return '未知平台'
  }
}
