import { describe, expect, it } from 'vitest'
import type { AppInfo } from '@halone/sdk'
import { manifestLink, processManifest } from '../server/webapps'
import { Shell } from '../src/shell/shell'
import { DEFAULT_SETTINGS } from '../src/shell/settings'
import { bestIcon, parseManifest, SimWebApps } from '../src/shell/webapps'

const app = (id: string): AppInfo => ({ id, name: id, description: '', icon: '', entry: `/${id}/`, widthPx: 1280, heightPx: 800 })

const SQUOOSH = {
  name: 'Squoosh',
  start_url: 'https://squoosh.app/',
  scope: 'https://squoosh.app/',
  display: 'standalone',
  icons: [
    { src: 'https://squoosh.app/48.png', sizes: '48x48' },
    { src: 'https://squoosh.app/512.png', sizes: '512x512' },
    { src: 'https://squoosh.app/256.png', sizes: '256x256' },
    { src: 'https://squoosh.app/mask.png', sizes: '192x192', purpose: 'maskable' },
  ],
}

describe('web app manifests', () => {
  it('can be installed with a name, a start inside the scope and an app display', () => {
    expect(parseManifest(SQUOOSH)?.name).toBe('Squoosh')
    expect(parseManifest({ ...SQUOOSH, display: 'browser' })).toBeNull()
    expect(parseManifest({ ...SQUOOSH, display: 'browser', display_override: ['minimal-ui'] })?.display).toBe('minimal-ui')
    expect(parseManifest({ ...SQUOOSH, name: undefined })).toBeNull()
    expect(parseManifest({ ...SQUOOSH, scope: 'https://squoosh.app/editor/' })).toBeNull()
  })

  it('keep the smallest any-purpose icon of at least 192', () => {
    expect(bestIcon(parseManifest(SQUOOSH)!)?.src).toBe('https://squoosh.app/256.png')
  })

  it('are found in a page and processed as Gecko does', () => {
    expect(manifestLink('<head><link rel="icon" href="/x.png"><link href=/app.webmanifest rel="manifest"></head>')).toBe('/app.webmanifest')
    const processed = processManifest(
      { name: 'App', start_url: '../?pwa', icons: [{ src: 'icon.png', sizes: '192x192' }] },
      'https://example.com/static/manifest.json',
      'https://example.com/page',
    )
    expect(processed.start_url).toBe('https://example.com/?pwa')
    expect(processed.scope).toBe('https://example.com/')
    expect(processed.display).toBe('browser')
    expect((processed.icons as { src: string }[])[0]!.src).toBe('https://example.com/static/icon.png')
    expect(processManifest({ start_url: 'https://evil.example/' }, 'https://example.com/m.json', 'https://example.com/a').start_url).toBe(
      'https://example.com/a',
    )
  })
})

describe('installed web apps', () => {
  async function browsing() {
    const s = new Shell({
      apps: [app('files')],
      appsOrigin: 'http://apps.test',
      settings: { ...DEFAULT_SETTINGS },
      webApps: new SimWebApps(),
      findManifest: async () => SQUOOSH,
    })
    const web = s.openUrl('https://squoosh.app/')
    await new Promise((resolve) => setTimeout(resolve))
    return { s, web }
  }

  it('are offered by the browser once the page has an installable manifest', async () => {
    const { s, web } = await browsing()
    expect(s.browserState(web.id).webApp).toMatchObject({ name: 'Squoosh', installed: false })
    const installed = s.installWebApp(web.id)
    expect(installed.kind).toBe('web')
    expect(s.browserState(web.id).webApp?.installed).toBe(true)
    expect(s.state.apps.map((a) => a.id)).toEqual(['files', installed.id])
  })

  it('open in a window of their own without chrome, going to a route only inside the app', async () => {
    const { s, web } = await browsing()
    const { id } = s.installWebApp(web.id)
    const window = s.launch(id)
    expect(window).toMatchObject({ kind: 'app', appId: id, standalone: true, url: 'https://squoosh.app/', browser: null })
    s.launch(id, 'https://evil.example/')
    expect(window.url).toBe('https://squoosh.app/')
    s.launch(id, 'https://squoosh.app/editor')
    expect(window.url).toBe('https://squoosh.app/editor')
  })

  it('uninstall closing their window and leaving the dock, but system apps stay', async () => {
    const { s, web } = await browsing()
    const { id } = s.installWebApp(web.id)
    const window = s.launch(id)
    s.setSetting('dock.pinnedApps', ['files', id])
    s.uninstall(id)
    expect(s.state.windows.some((w) => w.id === window.id)).toBe(false)
    expect(s.state.settings['dock.pinnedApps']).toEqual(['files'])
    expect(s.state.apps.map((a) => a.id)).toEqual(['files'])
    expect(() => s.uninstall('files')).toThrow('part of the system')
  })
})
