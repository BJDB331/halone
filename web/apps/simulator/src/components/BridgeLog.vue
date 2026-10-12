<script setup lang="ts">
import { computed, ref } from 'vue'
import type { WireMessage } from '@halone/sdk'
import { useSimulator } from '../injection'

const { bridge } = useSimulator()
const filter = ref('')
const hideEvents = ref(false)

function label(message: WireMessage) {
  if (message.type === 'req') return `→ ${message.method} #${message.id}`
  if (message.type === 'res') return `← ${message.ok ? 'ok' : `error ${message.error.code}`} #${message.id}`
  return `⇠ ${message.name}`
}

function payload(message: WireMessage) {
  const body = message.type === 'req' ? message.params : message.type === 'res' ? (message.ok ? message.result : message.error) : message.data
  if (body === undefined || body === null) return ''
  const text = JSON.stringify(body)
  return text.length > 160 ? `${text.slice(0, 160)}…` : text
}

const entries = computed(() => {
  const needle = filter.value.toLowerCase()
  return bridge.traffic
    .filter((entry) => !(hideEvents.value && entry.message.type === 'evt'))
    .map((entry) => ({
      seq: entry.seq,
      windowId: entry.windowId,
      kind: entry.message.type,
      failed: entry.message.type === 'res' && !entry.message.ok,
      label: label(entry.message),
      payload: payload(entry.message),
    }))
    .filter((entry) => !needle || `${entry.windowId} ${entry.label} ${entry.payload}`.toLowerCase().includes(needle))
    .slice(-120)
    .reverse()
})
</script>

<template>
  <div class="log">
    <div class="controls">
      <input v-model="filter" class="filter" placeholder="filter" />
      <label><input v-model="hideEvents" type="checkbox" /> hide events</label>
      <button class="link" @click="bridge.clearTraffic()">clear</button>
    </div>
    <div v-if="entries.length === 0" class="empty">No traffic yet.</div>
    <div v-for="entry in entries" :key="entry.seq" class="entry" :class="[entry.kind, { failed: entry.failed }]">
      <span class="window">{{ entry.windowId }}</span>
      <span class="label">{{ entry.label }}</span>
      <div v-if="entry.payload" class="payload">{{ entry.payload }}</div>
    </div>
  </div>
</template>

<style scoped>
.log {
  font-family: ui-monospace, 'SF Mono', monospace;
  font-size: 11px;
}

.controls {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-family: inherit;
}

.filter {
  flex: 1;
  min-width: 0;
  padding: 3px 6px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--bg);
  font-size: 11px;
}

.link {
  border: 0;
  background: none;
  color: var(--accent);
  cursor: pointer;
  font-size: 11px;
}

.empty {
  color: var(--text-muted);
}

.entry {
  padding: 3px 0;
  border-bottom: 1px solid #141a23;
}

.window {
  display: inline-block;
  min-width: 26px;
  color: var(--text-muted);
}

.req .label {
  color: var(--accent);
}

.res .label {
  color: var(--ok);
}

.evt .label {
  color: #c79bff;
}

.failed .label {
  color: var(--danger);
}

.payload {
  padding-left: 30px;
  color: var(--text-muted);
  word-break: break-all;
  user-select: text;
}
</style>
