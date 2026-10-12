import type { Plugin } from 'vite'

export const WEBAPP_PREFIX = '/__webapp'

const resolve = (value: unknown, base: string) => {
  if (typeof value !== 'string' || !value.trim()) return null
  try {
    return new URL(value.trim(), base).href
  } catch {
    return null
  }
}

const sameOrigin = (a: string, b: string) => new URL(a).origin === new URL(b).origin

/** The `href` of a page's `<link rel="manifest">`, read from its HTML without running it. */
export function manifestLink(html: string): string | null {
  for (const tag of html.match(/<link\b[^>]*>/gi) ?? []) {
    const attribute = (name: string) => tag.match(new RegExp(`\\b${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s>]+))`, 'i'))?.slice(1).find((v) => v !== undefined)
    if (attribute('rel')?.toLowerCase().split(/\s+/).includes('manifest')) return attribute('href') ?? null
  }
  return null
}

/**
 * A manifest as Gecko hands it over on the glasses: addresses absolute, a
 * start URL on the page's origin, a scope that holds it, `display` defaulted.
 */
export function processManifest(raw: Record<string, unknown>, manifestUrl: string, documentUrl: string): Record<string, unknown> {
  const declaredStart = resolve(raw.start_url, manifestUrl)
  const startUrl = declaredStart && sameOrigin(declaredStart, documentUrl) ? declaredStart : documentUrl
  const declaredScope = resolve(raw.scope, manifestUrl)
  const scope =
    declaredScope && sameOrigin(declaredScope, startUrl) && startUrl.startsWith(declaredScope) ? declaredScope : new URL('.', startUrl).href
  const id = resolve(raw.id, new URL(startUrl).origin)
  return {
    name: raw.name,
    short_name: raw.short_name,
    start_url: startUrl,
    scope,
    id: id && sameOrigin(id, startUrl) ? id : startUrl,
    display: typeof raw.display === 'string' ? raw.display : 'browser',
    display_override: Array.isArray(raw.display_override) ? raw.display_override : [],
    theme_color: raw.theme_color,
    background_color: raw.background_color,
    icons: (Array.isArray(raw.icons) ? raw.icons : []).flatMap((icon: Record<string, unknown>) => {
      const src = resolve(icon?.src, manifestUrl)
      return src ? [{ src, sizes: icon.sizes, type: icon.type, purpose: icon.purpose }] : []
    }),
  }
}

/**
 * Finds a page's web app manifest for the simulator's web windows, whose
 * pages are cross-origin frames it cannot look into: `?url=` the page, and
 * the answer is its processed manifest, or null.
 */
export function webAppsPlugin(): Plugin {
  return {
    name: 'halone-webapps',
    configureServer(server) {
      server.middlewares.use(WEBAPP_PREFIX + '/manifest', async (request, response) => {
        const page = new URL(request.url ?? '', 'http://localhost').searchParams.get('url')
        let result: Record<string, unknown> | null = null
        try {
          if (page && /^https?:\/\//.test(page)) {
            const html = await fetch(page, { redirect: 'follow' })
            const documentUrl = html.url || page
            const href = manifestLink(await html.text())
            const manifestUrl = href ? resolve(href, documentUrl) : null
            if (manifestUrl) {
              const manifest = await fetch(manifestUrl)
              if (manifest.ok) result = processManifest(await manifest.json(), manifestUrl, documentUrl)
            }
          }
        } catch {
          result = null
        }
        response.setHeader('content-type', 'application/json')
        response.end(JSON.stringify(result))
      })
    },
  }
}
