import { HaloneError, type AppInfo, type Permission } from '@halone/sdk'
import type { CheckedManifest } from '@halone/sdk/package'
import type { BrowsingStore } from './browsing'

/** A Halone app in the simulator: a developer's, from their server, or a package the dev server unpacked. */
export interface SimPackageApp {
  manifest: CheckedManifest
  granted: Permission[]
  /** Where the entry page is; its origin is the app's. */
  entryUrl: string
  dev: boolean
  installedAt: number
}

export const originOf = (url: string) => new URL(url).origin

export function iconUrlOf(app: SimPackageApp) {
  return app.manifest.icon ? new URL(app.manifest.icon, app.entryUrl).href : null
}

export function toAppInfo(app: SimPackageApp): AppInfo {
  return {
    id: app.manifest.id,
    name: app.manifest.name,
    description: app.manifest.description,
    icon: '',
    entry: app.entryUrl,
    widthPx: app.manifest.window.width,
    heightPx: app.manifest.window.height,
    opens: app.manifest.opens,
    kind: 'package',
    iconUrl: iconUrlOf(app),
    permissions: [...app.granted],
    dev: app.dev,
    color: app.manifest.color ?? null,
  }
}

/** As `PackageApps` (core-shell) keeps them, minus the files, which the dev server holds. */
export class SimPackageApps {
  apps: SimPackageApp[]
  private readonly listeners = new Set<() => void>()

  constructor(private readonly store?: BrowsingStore) {
    try {
      this.apps = JSON.parse(store?.load('packageApps') ?? '[]')
    } catch {
      this.apps = []
    }
  }

  onChange(listener: () => void) {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  find(id: string | null | undefined) {
    return id ? this.apps.find((app) => app.manifest.id === id) : undefined
  }

  /** Installs or replaces [manifest]'s app, granting what it asks for: the wearer agreed, or it is a developer's. */
  install(manifest: CheckedManifest, entryUrl: string, dev: boolean): SimPackageApp {
    const previous = this.find(manifest.id)
    const app: SimPackageApp = { manifest, granted: [...manifest.permissions], entryUrl, dev, installedAt: previous?.installedAt ?? Date.now() }
    this.apps = previous ? this.apps.map((a) => (a.manifest.id === manifest.id ? app : a)) : [...this.apps, app]
    this.changed()
    return app
  }

  remove(id: string) {
    if (!this.find(id)) throw new HaloneError('no-such-app', `no such app: ${id}`)
    this.apps = this.apps.filter((app) => app.manifest.id !== id)
    this.changed()
  }

  removeDev() {
    const before = this.apps.length
    this.apps = this.apps.filter((app) => !app.dev)
    if (this.apps.length !== before) this.changed()
  }

  private changed() {
    this.store?.save('packageApps', JSON.stringify(this.apps))
    for (const listener of this.listeners) listener()
  }
}
