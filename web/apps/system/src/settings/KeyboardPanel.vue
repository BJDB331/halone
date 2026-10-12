<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useHalone } from '@halone/sdk'

interface Shortcut {
  keys: string
  label: string
  browser?: boolean
  fallback?: boolean
}

const halone = useHalone()
const capture = ref<boolean | null>(null)
const shortcuts = ref<Shortcut[]>([])

fetch('/shortcuts.json')
  .then((response) => response.json())
  .then((list: Shortcut[]) => (shortcuts.value = list), () => {})

const check = () => halone.input.keyCapture().then(({ enabled }) => (capture.value = enabled), () => (capture.value = null))
check()
// Turned on in the phone's settings, so look again now and then rather than wait to be told.
const poll = setInterval(check, 2000)
onBeforeUnmount(() => clearInterval(poll))

const pretty = (keys: string) => keys.replace(/ArrowLeft/g, '←').replace(/ArrowRight/g, '→').replace(/\+/g, ' + ')
const shell = computed(() => shortcuts.value.filter((s) => !s.browser && !!s.fallback === !capture.value))
const browser = computed(() => shortcuts.value.filter((s) => s.browser))
</script>

<template>
  <div class="keyboard">
    <div class="capture" :class="{ on: capture }">
      <div>
        <strong>{{ capture ? 'Key capture is on' : 'Key capture is off' }}</strong>
        <p class="x-muted">
          {{
            capture
              ? 'Alt+Tab, the Meta key and PrintScreen are Halone’s while the environment runs.'
              : 'Android keeps Alt+Tab, the Meta key and PrintScreen for itself. Turn on “Halone keyboard shortcuts” in the phone’s accessibility settings to use them here; until then the Alt shortcuts below stand in.'
          }}
        </p>
      </div>
      <button v-if="capture === false" class="x-button primary" @click="halone.input.openKeyCaptureSettings().catch(() => {})">
        Open on the phone
      </button>
    </div>

    <h3>Everywhere</h3>
    <dl>
      <template v-for="s in shell" :key="s.keys">
        <dt><kbd>{{ pretty(s.keys) }}</kbd></dt>
        <dd>{{ s.label }}</dd>
      </template>
    </dl>
    <h3>In a web window</h3>
    <dl>
      <template v-for="s in browser" :key="s.keys">
        <dt><kbd>{{ pretty(s.keys) }}</kbd></dt>
        <dd>{{ s.label }}</dd>
      </template>
    </dl>
    <p class="x-muted">In any app, Ctrl+C, Ctrl+X and Ctrl+V copy, cut and paste; in a terminal, add Shift.</p>
  </div>
</template>

<style scoped>
.capture {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  border-radius: 16px;
  background: var(--surface);
}
.capture p {
  margin: 6px 0 0;
}
h3 {
  margin: 24px 0 8px;
}
dl {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 8px 24px;
  margin: 0;
}
dt,
dd {
  margin: 0;
}
kbd {
  font: inherit;
  padding: 2px 10px;
  border-radius: 8px;
  background: var(--surface-hover);
}
</style>
