/** The value formats of `<input type=date|month|week|time|datetime-local>`, as Gecko hands them over and takes them back. */
export type InputType = 'date' | 'month' | 'week' | 'time' | 'datetime-local'

export interface Day {
  year: number
  /** 1 to 12. */
  month: number
  day: number
}

const pad = (n: number, width = 2) => String(n).padStart(width, '0')

export const formatDate = ({ year, month, day }: Day) => `${pad(year, 4)}-${pad(month)}-${pad(day)}`

export function parseDate(text: string | undefined): Day | null {
  const match = text?.match(/^(\d{4,})-(\d{2})-(\d{2})/)
  if (!match) return null
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) }
}

export function daysInMonth(year: number, month: number) {
  return new Date(Date.UTC(year, month, 0)).getUTCDate()
}

/** Monday 0 to Sunday 6, as ISO weeks count. */
export function weekday({ year, month, day }: Day) {
  return (new Date(Date.UTC(year, month - 1, day)).getUTCDay() + 6) % 7
}

export function addDays(date: Day, days: number): Day {
  const moved = new Date(Date.UTC(date.year, date.month - 1, date.day + days))
  return { year: moved.getUTCFullYear(), month: moved.getUTCMonth() + 1, day: moved.getUTCDate() }
}

/** The ISO week a day falls in: the week's Thursday decides its year. */
export function isoWeek(date: Day): { year: number; week: number } {
  const thursday = addDays(date, 3 - weekday(date))
  const firstThursday = addDays({ year: thursday.year, month: 1, day: 4 }, 3 - weekday({ year: thursday.year, month: 1, day: 4 }))
  const days = (Date.UTC(thursday.year, thursday.month - 1, thursday.day) - Date.UTC(firstThursday.year, firstThursday.month - 1, firstThursday.day)) / 86_400_000
  return { year: thursday.year, week: Math.round(days / 7) + 1 }
}

export const formatWeek = (date: Day) => {
  const { year, week } = isoWeek(date)
  return `${pad(year, 4)}-W${pad(week)}`
}

/** The Monday of an ISO week written `2026-W41`. */
export function parseWeek(text: string | undefined): Day | null {
  const match = text?.match(/^(\d{4,})-W(\d{2})$/)
  if (!match) return null
  const year = Number(match[1])
  const jan4 = { year, month: 1, day: 4 }
  return addDays(addDays(jan4, -weekday(jan4)), (Number(match[2]) - 1) * 7)
}

/** Seconds since midnight from `HH:MM` or `HH:MM:SS`. */
export function parseTime(text: string | undefined): number | null {
  const match = text?.match(/(?:^|T)(\d{2}):(\d{2})(?::(\d{2}))?/)
  if (!match) return null
  return Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3] ?? 0)
}

/** Seconds shown only when the step is finer than a minute. */
export function formatTime(seconds: number, step: number) {
  const s = ((Math.round(seconds) % 86_400) + 86_400) % 86_400
  const text = `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}`
  return step % 60 === 0 ? text : `${text}:${pad(s % 60)}`
}

/** The input's step in seconds for a time, 60 when it gives none or "any". */
export function timeStep(step: string | undefined) {
  const value = Number(step)
  return Number.isFinite(value) && value > 0 ? value : 60
}

/** Whether a value lies within the input's min and max, which compare as text in these formats. */
export function within(value: string, min?: string, max?: string) {
  return (!min || value >= min) && (!max || value <= max)
}

export function today(): Day {
  const now = new Date()
  return { year: now.getFullYear(), month: now.getMonth() + 1, day: now.getDate() }
}

/** `#rrggbb`, lower case, or null for anything else. */
export function parseColor(text: string | undefined): string | null {
  const value = text?.trim().toLowerCase() ?? ''
  if (/^#[0-9a-f]{6}$/.test(value)) return value
  if (/^#[0-9a-f]{3}$/.test(value)) return '#' + [...value.slice(1)].map((c) => c + c).join('')
  return null
}

export function hslToHex(hue: number, saturation: number, lightness: number) {
  const s = saturation / 100
  const l = lightness / 100
  const k = (n: number) => (n + hue / 30) % 12
  const a = s * Math.min(l, 1 - l)
  const channel = (n: number) => Math.round(255 * (l - a * Math.max(-1, Math.min(k(n) - 3, 9 - k(n), 1))))
  return '#' + [channel(0), channel(8), channel(4)].map((v) => v.toString(16).padStart(2, '0')).join('')
}

export function hexToHsl(hex: string): [number, number, number] {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255) as [number, number, number]
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const l = (max + min) / 2
  if (max === min) return [0, 0, Math.round(l * 100)]
  const d = max - min
  const s = d / (1 - Math.abs(2 * l - 1))
  const h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4
  return [Math.round(((h * 60) + 360) % 360), Math.round(s * 100), Math.round(l * 100)]
}
