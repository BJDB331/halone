import type { SettingKey, Settings } from '@halone/sdk'
import { HaloneError } from '@halone/sdk'
import schema from '../../../system/public/settings-schema.json'

type Rule =
  | { type: 'boolean'; default: boolean }
  | { type: 'number'; default: number; min: number; max: number }
  | { type: 'enum'; default: string; values: string[] }
  | { type: 'list'; default: string[]; maxItems: number }

const RULES = schema as Record<SettingKey, Rule>

export const DEFAULT_SETTINGS = Object.fromEntries(
  Object.entries(RULES).map(([key, rule]) => [key, rule.default]),
) as unknown as Settings

export function validateSetting(key: string, value: unknown): asserts key is SettingKey {
  const rule = RULES[key as SettingKey]
  if (!rule) throw new HaloneError('invalid-setting', `unknown setting: ${key}`)
  const valid =
    rule.type === 'boolean'
      ? typeof value === 'boolean'
      : rule.type === 'number'
        ? typeof value === 'number' && Number.isFinite(value) && value >= rule.min && value <= rule.max
        : rule.type === 'list'
          ? Array.isArray(value) &&
            value.length <= rule.maxItems &&
            value.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 512) &&
            new Set(value).size === value.length
          : rule.values.includes(value as string)
  if (!valid) throw new HaloneError('invalid-setting', `invalid value for ${key}: ${JSON.stringify(value)}`)
}

export function loadSettings(stored: string | null): Settings {
  if (!stored) return { ...DEFAULT_SETTINGS }
  try {
    const parsed = JSON.parse(stored) as Record<string, unknown>
    const settings = { ...DEFAULT_SETTINGS }
    for (const [key, value] of Object.entries(parsed)) {
      try {
        validateSetting(key, value)
        ;(settings as Record<string, unknown>)[key] = value
      } catch {}
    }
    return settings
  } catch {
    return { ...DEFAULT_SETTINGS }
  }
}
