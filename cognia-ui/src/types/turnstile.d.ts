interface TurnstileRenderOptions {
  sitekey: string
  theme?: 'auto' | 'light' | 'dark'
  language?: string
  action?: string
  callback?: (token: string) => void
  'expired-callback'?: () => void
  'error-callback'?: (errorCode: string) => void
  'timeout-callback'?: () => void
}

interface TurnstileApi {
  render(container: HTMLElement | string, options: TurnstileRenderOptions): string
  reset(widgetId?: string): void
  remove(widgetId: string): void
  getResponse(widgetId?: string): string | undefined
}

interface Window {
  turnstile?: TurnstileApi
}
