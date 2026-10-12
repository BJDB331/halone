import { HaloneError, type AppInfo } from '@halone/sdk'
import type { BrowsingStore } from './browsing'

export interface ManifestIcon {
  src: string
  sizes: number[]
  type: string | null
  purposes: string[]
}

/** What `WebManifest.parse` (core-shell) accepts from a processed manifest. */
export interface WebManifest {
  appId: string
  name: string
  startUrl: string
  scope: string
  display: string
  icons: ManifestIcon[]
  themeColor: string | null
}

export interface WebApp {
  id: string
  name: string
  startUrl: string
  scope: string
  display: string
  iconUrl: string | null
  themeColor?: string | null
  installedAt: number
}

const INSTALLABLE_DISPLAYS = ['standalone', 'fullscreen', 'minimal-ui', 'window-controls-overlay']
const ICON_PX = 192
const isWeb = (url: string) => url.startsWith('https://') || url.startsWith('http://')
const text = (value: unknown) => (typeof value === 'string' && value.trim() ? value.trim() : null)
const words = (value: unknown): string[] =>
  (Array.isArray(value) ? value.filter((v) => typeof v === 'string').join(' ') : typeof value === 'string' ? value : '')
    .split(' ')
    .filter(Boolean)

/** A stable id from the app's identity; not the glasses' hash, which only needs to be stable there. */
function appIdOf(identity: string) {
  let hash = 0xcbf29ce484222325n
  for (const byte of new TextEncoder().encode(identity)) hash = BigInt.asUintN(64, (hash ^ BigInt(byte)) * 0x100000001b3n)
  return `web-${hash.toString(16).padStart(16, '0').slice(0, 12)}`
}

export function parseManifest(json: Record<string, unknown> | null): WebManifest | null {
  if (!json) return null
  const name = text(json.name) ?? text(json.short_name)
  const startUrl = text(json.start_url)
  if (!name || !startUrl || !isWeb(startUrl)) return null
  const scopeText = text(json.scope)
  const scope = scopeText && isWeb(scopeText) ? scopeText : new URL('.', startUrl).href
  if (!startUrl.startsWith(scope)) return null
  const display = [...words(json.display_override), ...(text(json.display) ? [text(json.display)!] : [])].find((d) =>
    INSTALLABLE_DISPLAYS.includes(d),
  )
  if (!display) return null
  const id = text(json.id)
  const icons = (Array.isArray(json.icons) ? json.icons : []).flatMap((icon: Record<string, unknown>) => {
    const src = text(icon?.src)
    if (!src || !(isWeb(src) || src.startsWith('data:'))) return []
    const sizes = words(icon.sizes).flatMap((size) => {
      const parts = size.toLowerCase().split('x').map(Number)
      return parts.length === 2 && parts.every(Number.isInteger) ? [Math.max(...parts)] : []
    })
    return [{ src, sizes, type: text(icon.type), purposes: words(icon.purpose).map((p) => p.toLowerCase()) }]
  })
  return { appId: appIdOf(id && isWeb(id) ? id : startUrl), name: name.slice(0, 80), startUrl, scope, display, icons, themeColor: text(json.theme_color) }
}

/** The smallest any-purpose bitmap of at least 192, or else the largest. */
export function bestIcon(manifest: WebManifest): ManifestIcon | null {
  const largest = (icon: ManifestIcon) => Math.max(0, ...icon.sizes)
  const usable = manifest.icons.filter(
    (icon) => (!icon.purposes.length || icon.purposes.includes('any')) && !icon.type?.includes('svg') && !icon.src.endsWith('.svg'),
  )
  const bigEnough = usable.filter((icon) => largest(icon) >= ICON_PX).sort((a, b) => largest(a) - largest(b))
  return bigEnough[0] ?? usable.sort((a, b) => largest(b) - largest(a))[0] ?? null
}

export function toAppInfo(app: WebApp): AppInfo {
  return {
    id: app.id,
    name: app.name,
    description: new URL(app.startUrl).host,
    icon: '',
    entry: app.startUrl,
    widthPx: 1280,
    heightPx: 800,
    kind: 'web',
    iconUrl: app.iconUrl,
    scope: app.scope,
    color: app.themeColor ?? null,
  }
}

/**
 * Sites installed as apps, as `WebApps` (core-shell) keeps them. The icon
 * stays a link to the site here rather than a copy of it.
 */
export class SimWebApps {
  apps: WebApp[]
  private readonly listeners = new Set<() => void>()

  constructor(private readonly store?: BrowsingStore) {
    try {
      this.apps = JSON.parse(store?.load('webApps') ?? '[]')
    } catch {
      this.apps = []
    }
  }

  onChange(listener: () => void) {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  find(id: string | null | undefined) {
    return id ? this.apps.find((app) => app.id === id) : undefined
  }

  install(manifest: WebManifest): WebApp {
    const previous = this.find(manifest.appId)
    const app: WebApp = {
      id: manifest.appId,
      name: manifest.name,
      startUrl: manifest.startUrl,
      scope: manifest.scope,
      display: manifest.display,
      iconUrl: bestIcon(manifest)?.src ?? previous?.iconUrl ?? null,
      themeColor: manifest.themeColor,
      installedAt: previous?.installedAt ?? Date.now(),
    }
    this.apps = previous ? this.apps.map((a) => (a.id === app.id ? app : a)) : [...this.apps, app]
    this.changed()
    return app
  }

  remove(id: string) {
    if (!this.find(id)) throw new HaloneError('no-such-app', `no such app: ${id}`)
    this.apps = this.apps.filter((app) => app.id !== id)
    this.changed()
  }

  private changed() {
    this.store?.save('webApps', JSON.stringify(this.apps))
    for (const listener of this.listeners) listener()
  }
}
