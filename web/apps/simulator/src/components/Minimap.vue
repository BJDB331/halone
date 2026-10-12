<script setup lang="ts">
import { computed } from 'vue'
import { useSimulator } from '../injection'
import { angularHalfWidthDegrees, horizontalFovDegrees } from '../shell/geometry'

const { shell } = useSimulator()
const state = shell.state

const SIZE = 200
const CENTRE = SIZE / 2
const RADIUS = 78

function point(yawDegrees: number, radius: number) {
  const radians = (yawDegrees * Math.PI) / 180
  return [CENTRE + radius * Math.sin(radians), CENTRE - radius * Math.cos(radians)] as const
}

function arc(fromDegrees: number, toDegrees: number, radius: number) {
  const [x1, y1] = point(fromDegrees, radius)
  const [x2, y2] = point(toDegrees, radius)
  const large = toDegrees - fromDegrees > 180 ? 1 : 0
  return `M ${x1} ${y1} A ${radius} ${radius} 0 ${large} 1 ${x2} ${y2}`
}

const fov = computed(() => {
  const yaw = state.head.yawDegrees
  const half = horizontalFovDegrees / 2
  const [x1, y1] = point(yaw - half, RADIUS + 14)
  const [x2, y2] = point(yaw + half, RADIUS + 14)
  return `M ${CENTRE} ${CENTRE} L ${x1} ${y1} L ${x2} ${y2} Z`
})

const panels = computed(() =>
  state.windows.map((window) => {
    const half = angularHalfWidthDegrees(window.widthPx)
    return {
      id: window.id,
      path: arc(window.yawDegrees - half, window.yawDegrees + half, RADIUS),
      pinned: window.pinned,
      focused: window.id === state.focusedId,
    }
  }),
)

const anchor = computed(() => point(state.anchorYawDegrees, RADIUS - 16))
</script>

<template>
  <svg :viewBox="`0 0 ${SIZE} ${SIZE}`" class="minimap">
    <circle :cx="CENTRE" :cy="CENTRE" :r="RADIUS" class="ring" />
    <path :d="fov" class="fov" />
    <path
      v-for="panel in panels"
      :key="panel.id"
      :d="panel.path"
      class="panel"
      :class="{ pinned: panel.pinned, focused: panel.focused }"
      @click="shell.focus(panel.id)"
    />
    <circle :cx="anchor[0]" :cy="anchor[1]" r="3" class="anchor" :class="{ returning: state.returning }" />
    <circle :cx="CENTRE" :cy="CENTRE" r="4" class="head" />
  </svg>
</template>

<style scoped>
.minimap {
  display: block;
  width: 100%;
  max-width: 220px;
  margin: 0 auto;
}

.ring {
  fill: none;
  stroke: var(--border);
  stroke-dasharray: 2 4;
}

.fov {
  fill: rgba(92, 200, 255, 0.1);
  stroke: rgba(92, 200, 255, 0.4);
}

.panel {
  fill: none;
  stroke: rgba(92, 200, 255, 0.55);
  stroke-width: 7;
  stroke-linecap: round;
  cursor: pointer;
}

.panel.focused {
  stroke: var(--accent-strong);
}

.panel.pinned {
  stroke: rgba(255, 180, 84, 0.6);
}

.panel.pinned.focused {
  stroke: var(--pinned);
}

.anchor {
  fill: var(--text-muted);
}

.anchor.returning {
  fill: var(--ok);
}

.head {
  fill: var(--text);
}
</style>
