<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  addDays,
  formatDate,
  formatTime,
  formatWeek,
  isoWeek,
  parseDate,
  parseTime,
  parseWeek,
  timeStep,
  today,
  weekday,
  within,
  type Day,
  type InputType,
} from './dates'
import Icon from '../shared/Icon.vue'

const props = defineProps<{ type: InputType; value: string; min?: string; max?: string; step?: string }>()
const emit = defineEmits<{ 'update:value': [value: string] }>()

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
const WEEKDAYS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su']

const hasDate = props.type !== 'time'
const hasTime = props.type === 'time' || props.type === 'datetime-local'
const step = timeStep(props.step)

function initialDay(): Day {
  if (props.type === 'week') return parseWeek(props.value) ?? today()
  if (props.type === 'month') {
    const match = props.value.match(/^(\d{4,})-(\d{2})$/)
    return match ? { year: Number(match[1]), month: Number(match[2]), day: 1 } : today()
  }
  return parseDate(props.value) ?? today()
}

const selected = ref<Day>(initialDay())
const viewYear = ref(selected.value.year)
const viewMonth = ref(selected.value.month)
const seconds = ref(parseTime(props.value) ?? Math.round((new Date().getHours() * 3600) / step) * step)

const output = computed(() => {
  const time = formatTime(seconds.value, step)
  const day = selected.value
  switch (props.type) {
    case 'date':
      return formatDate(day)
    case 'month':
      return formatDate(day).slice(0, 7)
    case 'week':
      return formatWeek(day)
    case 'time':
      return time
    default:
      return `${formatDate(day)}T${time}`
  }
})
watch(output, (value) => emit('update:value', value), { immediate: true })

/** Six rows of seven days from the Monday on or before the 1st, as wall calendars lay them out. */
const weeks = computed(() => {
  const first = { year: viewYear.value, month: viewMonth.value, day: 1 }
  const start = addDays(first, -weekday(first))
  return Array.from({ length: 6 }, (_, row) => Array.from({ length: 7 }, (_, column) => addDays(start, row * 7 + column)))
})

const dateLimit = (limit?: string) => (props.type === 'datetime-local' ? limit?.slice(0, 10) : limit)

function allowed(day: Day) {
  const value = props.type === 'week' ? formatWeek(day) : formatDate(day)
  return within(value, props.type === 'week' ? props.min : dateLimit(props.min), props.type === 'week' ? props.max : dateLimit(props.max))
}

const monthAllowed = (month: number) => within(`${String(viewYear.value).padStart(4, '0')}-${String(month).padStart(2, '0')}`, props.min, props.max)

const same = (a: Day, b: Day) => a.year === b.year && a.month === b.month && a.day === b.day
const isSelected = (day: Day) =>
  props.type === 'week' ? formatWeek(day) === formatWeek(selected.value) : same(day, selected.value)
const isToday = (day: Day) => same(day, today())

function pick(day: Day) {
  if (!allowed(day)) return
  selected.value = day
  viewYear.value = day.year
  viewMonth.value = day.month
}

function pickMonth(month: number) {
  if (!monthAllowed(month)) return
  selected.value = { year: viewYear.value, month, day: 1 }
}

function shiftMonth(by: number) {
  const index = viewYear.value * 12 + viewMonth.value - 1 + by
  viewYear.value = Math.floor(index / 12)
  viewMonth.value = (index % 12) + 1
}

/** Hours step by an hour, or the input's step if coarser; minutes and seconds by the step that applies to them. */
const columns = computed(() => {
  const all = [
    { label: 'Hour', unit: 3600, by: Math.max(3600, step), modulo: 24 },
    { label: 'Minute', unit: 60, by: step >= 3600 ? 0 : Math.max(60, step), modulo: 60 },
  ]
  if (step < 60) all.push({ label: 'Second', unit: 1, by: step, modulo: 60 })
  return all.filter((column) => column.by > 0)
})

function partOf(unit: number, modulo: number) {
  return Math.floor(seconds.value / unit) % modulo
}

function stepTime(by: number) {
  seconds.value = (((seconds.value + by) % 86_400) + 86_400) % 86_400
}
</script>

<template>
  <div class="picker" :class="type">
    <section v-if="type === 'month'" class="calendar">
      <header>
        <button type="button" class="nav" aria-label="Previous year" @click="viewYear--"><Icon name="chevron_left" /></button>
        <span>{{ viewYear }}</span>
        <button type="button" class="nav" aria-label="Next year" @click="viewYear++"><Icon name="chevron_right" /></button>
      </header>
      <div class="months">
        <button
          v-for="(name, index) in MONTHS"
          :key="name"
          type="button"
          class="cell"
          :class="{ chosen: selected.year === viewYear && selected.month === index + 1, off: !monthAllowed(index + 1) }"
          @click="pickMonth(index + 1)"
        >
          {{ name.slice(0, 3) }}
        </button>
      </div>
    </section>

    <section v-else-if="hasDate" class="calendar">
      <header>
        <button type="button" class="nav" aria-label="Previous month" @click="shiftMonth(-1)"><Icon name="chevron_left" /></button>
        <span>{{ MONTHS[viewMonth - 1] }} {{ viewYear }}</span>
        <button type="button" class="nav" aria-label="Next month" @click="shiftMonth(1)"><Icon name="chevron_right" /></button>
      </header>
      <div class="grid" :class="{ weeks: type === 'week' }">
        <span v-if="type === 'week'" class="head">Wk</span>
        <span v-for="name in WEEKDAYS" :key="name" class="head">{{ name }}</span>
        <template v-for="(week, row) in weeks" :key="row">
          <span v-if="type === 'week'" class="head number">{{ isoWeek(week[0]!).week }}</span>
          <button
            v-for="day in week"
            :key="formatDate(day)"
            type="button"
            class="cell"
            :class="{ chosen: isSelected(day), outside: day.month !== viewMonth, today: isToday(day), off: !allowed(day) }"
            @click="pick(day)"
          >
            {{ day.day }}
          </button>
        </template>
      </div>
    </section>

    <section v-if="hasTime" class="time">
      <div v-for="column in columns" :key="column.label" class="column">
        <button type="button" class="nav" :aria-label="`Later ${column.label}`" @click="stepTime(column.by)"><Icon name="expand_less" /></button>
        <span class="value">{{ String(partOf(column.unit, column.modulo)).padStart(2, '0') }}</span>
        <button type="button" class="nav" :aria-label="`Earlier ${column.label}`" @click="stepTime(-column.by)"><Icon name="expand_more" /></button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.picker {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 32px;
  justify-content: center;
}

.calendar {
  display: flex;
  flex-direction: column;
  width: 520px;
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  font-weight: 600;
}

.nav {
  width: 52px;
  height: 52px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  font-size: 26px;
  cursor: pointer;
}

.nav:hover {
  background: var(--surface-hover);
}

.grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.grid.weeks {
  grid-template-columns: 0.8fr repeat(7, 1fr);
}

.head {
  display: grid;
  place-items: center;
  height: 32px;
  color: var(--text-muted);
  font-size: 16px;
}

.head.number {
  height: 52px;
}

.cell {
  height: 52px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
}

.cell:hover:not(.off) {
  background: var(--surface-hover);
}

.cell.outside {
  color: var(--text-muted);
}

.cell.today {
  box-shadow: inset 0 0 0 2px var(--border);
}

.cell.chosen {
  background: var(--accent);
  color: #000;
}

.cell.off {
  opacity: 0.3;
  cursor: default;
}

.weeks .cell.chosen {
  border-radius: 0;
}

.months {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px 12px;
}

.months .cell {
  height: 56px;
}

.time {
  display: flex;
  align-items: center;
  gap: 24px;
}

.column {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.value {
  width: 96px;
  height: 72px;
  border-radius: 14px;
  background: var(--surface);
  font-size: 40px;
  font-variant-numeric: tabular-nums;
  line-height: 72px;
  text-align: center;
}
</style>
