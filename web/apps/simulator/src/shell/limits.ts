export interface SizeLimits {
  minWidthPx: number
  minHeightPx: number
  maxWidthPx: number
  maxHeightPx: number
}

/** As `SizeLimits.defaultFor` in core-scene: a sensible floor, and two and a half times the opened size. */
export function defaultLimits(widthPx: number, heightPx: number): SizeLimits {
  return {
    minWidthPx: Math.min(480, widthPx),
    minHeightPx: Math.min(360, heightPx),
    maxWidthPx: Math.floor(widthPx * 2.5),
    maxHeightPx: Math.floor(heightPx * 2.5),
  }
}

/**
 * A page's declared limits, read from the same tags as on the glasses
 * (ShellSession's DECLARED_LIMITS_JS): `halone:window`, then a numeric viewport
 * `width=` for the minimum width.
 */
export function declaredLimits(html: string, defaults: SizeLimits): SizeLimits {
  const metas = [...html.matchAll(/<meta\s[^>]*>/gi)].map(([tag]) => {
    const attribute = (key: string) => tag.match(new RegExp(`${key}\\s*=\\s*["']([^"']*)["']`, 'i'))?.[1]
    return { name: attribute('name'), content: attribute('content') ?? '' }
  })
  const read = (name: string) => metas.find((meta) => meta.name === name)?.content ?? ''
  const pairs = (text: string) =>
    Object.fromEntries(
      text
        .split(',')
        .map((pair) => pair.split('=').map((part) => part.trim()))
        .filter((pair) => pair.length === 2),
    )
  const n = (value: string | undefined) => {
    const parsed = parseInt(value ?? '', 10)
    return parsed > 0 ? parsed : 0
  }
  const own = pairs(read('halone:window'))
  const viewport = pairs(read('viewport'))
  return {
    minWidthPx: n(own['min-width']) || n(viewport['width']) || defaults.minWidthPx,
    minHeightPx: n(own['min-height']) || defaults.minHeightPx,
    maxWidthPx: n(own['max-width']) || defaults.maxWidthPx,
    maxHeightPx: n(own['max-height']) || defaults.maxHeightPx,
  }
}
