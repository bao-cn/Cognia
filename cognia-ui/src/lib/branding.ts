type BrandVariant = 'w' | 'b' | 'g'

const LEGACY_DEFAULT_LOGO = '/logo.png'

function getBrandVariant(): BrandVariant {
  if (import.meta.env.DEV)
    return 'g'
  return import.meta.env.VITE_APP_CHANNEL === 'beta' || import.meta.env.MODE === 'beta' ? 'b' : 'w'
}

export function getDefaultBrandIcon(): string {
  return `/cognia-logo/cognia-icon-born-${getBrandVariant()}.svg`
}

export function resolveBrandLogo(logoUrl?: string | null): string {
  const configuredLogo = logoUrl?.trim()
  return configuredLogo && configuredLogo !== LEGACY_DEFAULT_LOGO
    ? configuredLogo
    : getDefaultBrandIcon()
}

export function setFavicon(logoUrl?: string | null): void {
  let icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (!icon) {
    icon = document.createElement('link')
    icon.rel = 'icon'
    document.head.appendChild(icon)
  }
  icon.href = resolveBrandLogo(logoUrl)
}
