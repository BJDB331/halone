import { ref, watchEffect, type Ref } from 'vue'
import type { AppInfo } from '@halone/sdk'

type Rgb = [number, number, number]

const FALLBACK: Rgb = [38, 48, 64]
/** White text on a tile stays readable (contrast near 4.5:1) up to this relative luminance. */
const MAX_LUMINANCE = 0.18
const SAMPLE_PX = 32

let canvas: HTMLCanvasElement | null = null
function context() {
  canvas ??= Object.assign(document.createElement('canvas'), { width: SAMPLE_PX, height: SAMPLE_PX })
  return canvas.getContext('2d', { willReadFrequently: true })!
}

/** Any CSS colour as RGB, or null when the browser does not read it as one. */
export function parseColor(text: string): Rgb | null {
  const ctx = context()
  ctx.fillStyle = '#000001'
  ctx.fillStyle = text
  const normal = String(ctx.fillStyle)
  if (normal === '#000001') return null
  const hex = /^#([0-9a-f]{6})$/i.exec(normal)
  if (hex) {
    const value = parseInt(hex[1]!, 16)
    return [value >> 16, (value >> 8) & 255, value & 255]
  }
  const rgb = /^rgba?\((\d+),\s*(\d+),\s*(\d+)/.exec(normal)
  return rgb ? [Number(rgb[1]), Number(rgb[2]), Number(rgb[3])] : null
}

function luminance([r, g, b]: Rgb) {
  const channel = (c: number) => {
    const v = c / 255
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

/** [rgb], darkened until white text on it is readable. */
export function readable(rgb: Rgb): Rgb {
  let scaled = rgb
  for (let factor = 1; luminance(scaled) > MAX_LUMINANCE && factor > 0.05; factor -= 0.05) {
    scaled = rgb.map((c) => Math.round(c * factor)) as Rgb
  }
  return scaled
}

/**
 * The colour an icon is mostly drawn in: opaque pixels bucketed by hue,
 * vivid ones counting for more than greys, so a coloured glyph on a white
 * plate still gives its colour.
 */
function dominant(image: HTMLImageElement): Rgb | null {
  const ctx = context()
  ctx.clearRect(0, 0, SAMPLE_PX, SAMPLE_PX)
  ctx.drawImage(image, 0, 0, SAMPLE_PX, SAMPLE_PX)
  let data: Uint8ClampedArray
  try {
    data = ctx.getImageData(0, 0, SAMPLE_PX, SAMPLE_PX).data
  } catch {
    return null
  }
  const buckets = new Map<number, { weight: number; r: number; g: number; b: number }>()
  for (let i = 0; i < data.length; i += 4) {
    const [r, g, b, a] = [data[i]!, data[i + 1]!, data[i + 2]!, data[i + 3]!]
    if (a < 128) continue
    const max = Math.max(r, g, b)
    const min = Math.min(r, g, b)
    const saturation = max === 0 ? 0 : (max - min) / max
    const weight = 0.1 + saturation * saturation * (max / 255)
    const key = ((r >> 5) << 6) | ((g >> 5) << 3) | (b >> 5)
    const bucket = buckets.get(key) ?? { weight: 0, r: 0, g: 0, b: 0 }
    bucket.weight += weight
    bucket.r += r * weight
    bucket.g += g * weight
    bucket.b += b * weight
    buckets.set(key, bucket)
  }
  let best: { weight: number; r: number; g: number; b: number } | null = null
  for (const bucket of buckets.values()) if (!best || bucket.weight > best.weight) best = bucket
  return best ? [best.r / best.weight, best.g / best.weight, best.b / best.weight].map(Math.round) as Rgb : null
}

const fromIcons = new Map<string, Promise<Rgb | null>>()

function iconColor(url: string): Promise<Rgb | null> {
  let pending = fromIcons.get(url)
  if (!pending) {
    pending = new Promise((resolve) => {
      const image = new Image()
      if (!url.startsWith('data:') && new URL(url, location.href).origin !== location.origin) image.crossOrigin = 'anonymous'
      image.onload = () => resolve(dominant(image))
      image.onerror = () => resolve(null)
      image.src = url
    })
    fromIcons.set(url, pending)
  }
  return pending
}

const css = ([r, g, b]: Rgb) => `rgb(${r}, ${g}, ${b})`

/** The tile colour for [app]: its own, or failing that its icon's, made dark enough for light text. */
export function useTileColor(app: () => AppInfo | null | undefined): Ref<string> {
  const color = ref(css(FALLBACK))
  watchEffect(() => {
    const current = app()
    const own = current?.color ? parseColor(current.color) : null
    if (own) {
      color.value = css(readable(own))
      return
    }
    color.value = css(FALLBACK)
    const url = current?.iconUrl
    if (!url) return
    iconColor(url).then((rgb) => {
      if (rgb && app()?.iconUrl === url && !app()?.color) color.value = css(readable(rgb))
    })
  })
  return color
}
