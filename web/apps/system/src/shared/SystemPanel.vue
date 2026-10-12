<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { PROTOCOL_VERSION, useHalone, type HeadPose, type SystemInfo, type WindowInfo } from '@halone/sdk'
import { useFocus, useWindows } from './host'

const halone = useHalone()
const windows = useWindows(halone)
const focus = useFocus(halone)
const info = ref<SystemInfo | null>(null)
const self = ref<WindowInfo | null>(null)
const pose = ref<HeadPose | null>(null)
const latency = ref<{ median: number; p95: number; samples: number } | null>(null)
const measuring = ref(false)

halone.system.info().then((value) => (info.value = value), () => {})
halone.windows.self().then((value) => (self.value = value), () => {})
halone.on('windows.changed', (list) => (self.value = list.find((w) => w.id === self.value?.id) ?? self.value))

const poll = setInterval(() => {
  if (focus.focused) halone.scene.headPose().then((value) => (pose.value = value), () => {})
}, 100)
onBeforeUnmount(() => clearInterval(poll))

async function measure() {
  measuring.value = true
  const samples: number[] = []
  for (let i = 0; i < 50; i++) samples.push(await halone.system.ping())
  samples.sort((a, b) => a - b)
  latency.value = {
    median: samples[Math.floor(samples.length / 2)]!,
    p95: samples[Math.floor(samples.length * 0.95)]!,
    samples: samples.length,
  }
  measuring.value = false
}

const degrees = (value: number) => `${value.toFixed(1)}°`
</script>

<template>
  <div class="cards">
    <section class="x-card">
      <h2 class="x-section-title">Device</h2>
      <dl v-if="info">
        <dt>Glasses</dt>
        <dd>{{ info.device.name }}</dd>
        <dt>Display</dt>
        <dd>{{ info.device.displayWidthPx }}×{{ info.device.displayHeightPx }} per eye</dd>
        <dt>Vertical FoV</dt>
        <dd>{{ degrees(info.device.verticalFovDegrees) }}</dd>
        <dt>Platform</dt>
        <dd>{{ info.platform }} {{ info.version }}</dd>
      </dl>
    </section>

    <section class="x-card">
      <h2 class="x-section-title">Bridge</h2>
      <dl>
        <dt>Protocol</dt>
        <dd>host {{ info?.protocolVersion ?? '…' }} · sdk {{ PROTOCOL_VERSION }}</dd>
        <dt>Round trip</dt>
        <dd v-if="latency">{{ latency.median.toFixed(2) }} ms median · {{ latency.p95.toFixed(2) }} ms p95</dd>
        <dd v-else class="x-muted">not measured</dd>
      </dl>
      <button class="x-button small" :disabled="measuring" @click="measure">{{ measuring ? 'Measuring…' : 'Measure 50 pings' }}</button>
    </section>

    <section class="x-card">
      <h2 class="x-section-title">This window</h2>
      <dl v-if="self">
        <dt>Id</dt>
        <dd>{{ self.id }}</dd>
        <dt>Size</dt>
        <dd>{{ self.widthPx }}×{{ self.heightPx }}</dd>
        <dt>Heading</dt>
        <dd>{{ degrees(self.yawDegrees) }} {{ self.pinned ? '(pinned)' : '(following)' }}</dd>
        <dt>Focused</dt>
        <dd>{{ focus.focused ? 'yes' : 'no' }}</dd>
      </dl>
    </section>

    <section class="x-card">
      <h2 class="x-section-title">Head</h2>
      <dl v-if="pose">
        <dt>Yaw</dt>
        <dd>{{ degrees(pose.yawDegrees) }}</dd>
        <dt>Pitch</dt>
        <dd>{{ degrees(pose.pitchDegrees) }}</dd>
      </dl>
      <p v-else class="x-muted small">Updates while this window has focus.</p>
      <p class="x-muted small">{{ windows.length }} window{{ windows.length === 1 ? '' : 's' }} open</p>
    </section>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px 20px;
  margin: 0 0 16px;
}

dt {
  color: var(--text-muted);
}

dd {
  margin: 0;
  font-variant-numeric: tabular-nums;
}

.small {
  font-size: var(--font-size-small);
  margin: 0 0 8px;
}
</style>
