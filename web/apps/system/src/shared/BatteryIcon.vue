<script setup lang="ts">
import { computed, useId } from 'vue'

const props = defineProps<{
  /** 0 to 1. */
  level: number
  charging: boolean
  /** Height in CSS pixels; the width follows. */
  height: number
}>()

const id = useId()
const percent = computed(() => Math.round(props.level * 100))
const low = computed(() => percent.value <= 15 && !props.charging)
const BODY_WIDTH = 26
const fillWidth = computed(() => Math.max(0, Math.min(1, props.level)) * BODY_WIDTH)
</script>

<template>
  <svg
    class="battery-icon"
    :class="{ low, charging }"
    viewBox="0 0 30 14"
    :height="height"
    :width="(height * 30) / 14"
    role="img"
    :aria-label="`Battery ${percent}%${charging ? ', charging' : ''}`"
  >
    <defs>
      <clipPath :id="`${id}-body`"><rect x="0" y="0" width="26" height="14" rx="4.5" /></clipPath>
    </defs>
    <rect class="track" x="0" y="0" width="26" height="14" rx="4.5" />
    <rect class="nub" x="27" y="4.5" width="2" height="5" rx="1" />
    <g :clip-path="`url(#${id}-body)`">
      <rect class="fill" x="0" y="0" :width="fillWidth" height="14" />
    </g>
    <text class="number" x="13" y="7.4">{{ percent }}</text>
  </svg>
</template>

<style scoped>
.battery-icon {
  display: block;
  flex: none;
}

.track,
.nub {
  fill: currentColor;
  opacity: 0.6;
}

.fill {
  fill: var(--text);
}

.low .fill {
  fill: #ff5a4f;
}

.charging .fill {
  fill: #34c759;
}

.number {
  font-size: 10px;
  font-weight: 700;
  text-anchor: middle;
  dominant-baseline: central;
  font-variant-numeric: tabular-nums;
  fill: rgb(22, 28, 38);
}
</style>
