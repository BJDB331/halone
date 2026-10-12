import { describe, expect, it } from 'vitest'
import {
  addDays,
  daysInMonth,
  formatTime,
  formatWeek,
  hexToHsl,
  hslToHex,
  parseColor,
  parseTime,
  parseWeek,
  timeStep,
  weekday,
  within,
} from '../src/menu/dates'

describe('dates', () => {
  it('knows month lengths, leap years included', () => {
    expect(daysInMonth(2024, 2)).toBe(29)
    expect(daysInMonth(2026, 2)).toBe(28)
    expect(daysInMonth(2026, 12)).toBe(31)
  })

  it('counts weekdays from Monday', () => {
    expect(weekday({ year: 2026, month: 10, day: 12 })).toBe(0)
    expect(weekday({ year: 2026, month: 10, day: 11 })).toBe(6)
  })

  it('writes ISO weeks, the Thursday deciding the year', () => {
    expect(formatWeek({ year: 2026, month: 10, day: 9 })).toBe('2026-W41')
    expect(formatWeek({ year: 2027, month: 1, day: 1 })).toBe('2026-W53')
    expect(formatWeek({ year: 2024, month: 12, day: 30 })).toBe('2025-W01')
  })

  it('reads an ISO week back to its Monday', () => {
    expect(parseWeek('2026-W41')).toEqual({ year: 2026, month: 10, day: 5 })
    expect(parseWeek('2025-W01')).toEqual({ year: 2024, month: 12, day: 30 })
  })

  it('moves across month and year ends', () => {
    expect(addDays({ year: 2026, month: 12, day: 31 }, 1)).toEqual({ year: 2027, month: 1, day: 1 })
  })

  it('reads and writes times, with seconds only for a finer step', () => {
    expect(parseTime('09:30')).toBe(34_200)
    expect(parseTime('2026-10-09T23:05')).toBe(83_100)
    expect(formatTime(34_200, 60)).toBe('09:30')
    expect(formatTime(34_215, 1)).toBe('09:30:15')
    expect(formatTime(-60, 60)).toBe('23:59')
  })

  it('takes a step of any or none as a minute', () => {
    expect(timeStep('any')).toBe(60)
    expect(timeStep(undefined)).toBe(60)
    expect(timeStep('900')).toBe(900)
  })

  it('compares values with the input limits as text', () => {
    expect(within('2026-10-09', '2026-01-01', '2026-12-31')).toBe(true)
    expect(within('2027-01-01', undefined, '2026-12-31')).toBe(false)
  })
})

describe('colours', () => {
  it('reads hex, short or long', () => {
    expect(parseColor('#ABC')).toBe('#aabbcc')
    expect(parseColor('#00ff7f')).toBe('#00ff7f')
    expect(parseColor('red')).toBeNull()
  })

  it('goes to HSL and back', () => {
    expect(hslToHex(0, 100, 50)).toBe('#ff0000')
    expect(hexToHsl('#ff0000')).toEqual([0, 100, 50])
    expect(hslToHex(...hexToHsl('#3366cc'))).toBe('#3366cc')
  })
})
