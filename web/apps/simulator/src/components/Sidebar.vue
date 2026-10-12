<script setup lang="ts">
import { ref } from 'vue'
import type { SettingKey } from '@halone/sdk'
import BridgeLog from './BridgeLog.vue'
import Minimap from './Minimap.vue'
import { useSimulator } from '../injection'

const showRoom = defineModel<boolean>('showRoom', { required: true })

const { shell, appsOrigin } = useSimulator()
const iconOf = (url: string) => new URL(url, appsOrigin).href
const state = shell.state
const url = ref('https://example.com')
const error = ref<string | null>(null)

function attempt(action: () => void) {
  try {
    error.value = null
    action()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : String(caught)
  }
}

function setNumber(key: SettingKey, event: Event) {
  attempt(() => shell.setSetting(key, Number((event.target as HTMLInputElement).value)))
}

const numericSettings: { key: SettingKey; min: number; max: number; step: number; unit: string }[] = [
  { key: 'follow.marginDegrees', min: 0, max: 30, step: 1, unit: '°' },
  { key: 'follow.catchUpSeconds', min: 0.05, max: 2, step: 0.05, unit: 's' },
  { key: 'layout.slotSpacingDegrees', min: 15, max: 60, step: 1, unit: '°' },
  { key: 'display.brightness', min: 0.1, max: 1, step: 0.05, unit: '' },
]

const format = (value: number) => (Number.isInteger(value) ? value : value.toFixed(2))
</script>

<template>
  <div class="sidebar">
    <section>
      <h2>Scene</h2>
      <Minimap />
      <div class="row">
        <span>head</span>
        <span class="mono">yaw {{ format(state.head.yawDegrees) }}° · pitch {{ format(state.head.pitchDegrees) }}°</span>
      </div>
      <div class="row">
        <span>anchor</span>
        <span class="mono">{{ format(state.anchorYawDegrees) }}° {{ state.returning ? '(following)' : '' }}</span>
      </div>
      <input
        type="range"
        min="-180"
        max="180"
        step="0.5"
        :value="state.head.yawDegrees"
        @input="shell.setHead(Number(($event.target as HTMLInputElement).value), state.head.pitchDegrees)"
      />
      <div class="buttons">
        <button @click="shell.recenter()">Recenter <span class="sim-kbd">R</span></button>
        <button @click="shell.setHead(0, 0)">Head to 0°</button>
        <label><input v-model="showRoom" type="checkbox" /> room (additive)</label>
      </div>
      <p class="hint">
        Drag the background to look around, or use <span class="sim-kbd">←↑↓→</span> (shift for 10°). Drag a window's bar to move it.
        <span class="sim-kbd">L</span> launcher, <span class="sim-kbd">`</span> sidebar.
      </p>
    </section>

    <section>
      <h2>Apps</h2>
      <div class="buttons">
        <button :class="{ active: state.launcherOpen }" @click="shell.setLauncherOpen(!state.launcherOpen)">
          🏠 Launcher <span class="sim-kbd">L</span>
        </button>
        <button v-for="app in state.apps.filter((a) => !a.hidden)" :key="app.id" @click="attempt(() => shell.launch(app.id))">
          <img v-if="app.iconUrl" class="app-icon" :src="iconOf(app.iconUrl)" alt="" />
          {{ app.name }}
        </button>
      </div>
      <form class="url" @submit.prevent="attempt(() => shell.openUrl(url))">
        <input v-model="url" />
        <button type="submit">Open</button>
      </form>
      <p v-if="error" class="error">{{ error }}</p>
      <p class="hint">System apps from <span class="mono">{{ appsOrigin }}</span></p>
    </section>

    <section>
      <h2>Windows ({{ state.windows.length }})</h2>
      <div v-for="window in state.windows" :key="window.id" class="window" :class="{ focused: window.id === state.focusedId }">
        <div class="window-title" @click="shell.focus(window.id)">
          <span class="mono muted">{{ window.id }}</span> {{ window.title }}
        </div>
        <div class="mono muted small">
          {{ window.kind }} · yaw {{ format(window.yawDegrees) }}° · {{ window.widthPx }}×{{ window.heightPx }}
          {{ window.pinned ? '· pinned' : window.handPlaced ? '· placed' : '' }}
        </div>
        <div class="buttons">
          <button @click="shell.setPinned(window.id, !window.pinned)">{{ window.pinned ? 'Unpin' : 'Pin' }}</button>
          <button @click="shell.close(window.id)">Close</button>
        </div>
      </div>
    </section>

    <section>
      <h2>Settings</h2>
      <label class="row">
        <span>follow</span>
        <input
          type="checkbox"
          :checked="state.settings['follow.enabled']"
          @change="attempt(() => shell.setSetting('follow.enabled', ($event.target as HTMLInputElement).checked))"
        />
      </label>
      <label v-for="setting in numericSettings" :key="setting.key" class="setting">
        <span class="row">
          <span>{{ setting.key }}</span>
          <span class="mono">{{ format(state.settings[setting.key] as number) }}{{ setting.unit }}</span>
        </span>
        <input
          type="range"
          :min="setting.min"
          :max="setting.max"
          :step="setting.step"
          :value="state.settings[setting.key]"
          @input="setNumber(setting.key, $event)"
        />
      </label>
      <label class="row">
        <span>input.pointer</span>
        <select
          :value="state.settings['input.pointer']"
          @change="attempt(() => shell.setSetting('input.pointer', ($event.target as HTMLSelectElement).value))"
        >
          <option value="phone">phone</option>
          <option value="gaze">gaze</option>
        </select>
      </label>
    </section>

    <section>
      <h2>Bridge</h2>
      <BridgeLog />
    </section>
  </div>
</template>

<style scoped>
.sidebar {
  padding: 4px 14px 24px;
}

section {
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}

h2 {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin: 4px 0;
}

.setting {
  display: block;
  margin: 8px 0;
}

input[type='range'] {
  width: 100%;
}

.buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 6px 0;
}

button {
  padding: 4px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface-raised);
  cursor: pointer;
  font-size: 12px;
}

button:hover {
  border-color: var(--accent);
}

.url {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.url input,
select {
  flex: 1;
  min-width: 0;
  padding: 4px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg);
  font-size: 12px;
  user-select: text;
}

select {
  flex: 0;
}

.window {
  padding: 8px;
  margin: 6px 0;
  border: 1px solid var(--border);
  border-radius: 8px;
}

.window.focused {
  border-color: var(--accent);
}

.window-title {
  cursor: pointer;
}

.mono {
  font-family: ui-monospace, 'SF Mono', monospace;
  font-size: 11px;
}

.muted {
  color: var(--text-muted);
}

.small {
  margin-top: 2px;
}

.hint {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.error {
  color: var(--danger);
  font-size: 12px;
}
.app-icon {
  width: 1.2em;
  height: 1.2em;
  margin-right: 4px;
  vertical-align: -0.25em;
}
</style>
