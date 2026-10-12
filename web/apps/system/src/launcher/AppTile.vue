<script setup lang="ts">
import type { AppInfo } from '@halone/sdk'
import AppIcon from '../shared/AppIcon.vue'
import Icon from '../shared/Icon.vue'
import { useTileColor } from '../shared/tileColor'

const props = defineProps<{ app: AppInfo; pinned: boolean }>()
defineEmits<{ open: []; menu: [event: MouseEvent]; togglePin: [] }>()

const color = useTileColor(() => props.app)
</script>

<template>
  <button class="app-tile" :style="{ '--tile': color }" :title="app.name" @click="$emit('open')" @contextmenu.prevent="$emit('menu', $event)">
    <span
      class="dock-pin"
      :class="{ active: pinned }"
      role="button"
      :aria-label="pinned ? 'Remove from the dock' : 'Keep in the dock'"
      :title="pinned ? 'Remove from the dock' : 'Keep in the dock'"
      @click.stop="$emit('togglePin')"
    >
      <Icon :name="pinned ? 'keep' : 'keep_off'" />
    </span>
    <AppIcon class="icon" :app="app" />
    <span class="name">{{ app.name }}<span v-if="app.dev" class="dev-badge">dev</span></span>
    <span class="description">{{ app.description }}</span>
  </button>
</template>

<style scoped>
.app-tile {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
  height: 228px;
  padding: 22px 24px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--tile) 80%, white);
  border-radius: var(--radius);
  background: linear-gradient(160deg, color-mix(in srgb, var(--tile) 90%, white), var(--tile));
  color: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 120ms, filter 120ms;
}

.app-tile:hover {
  border-color: var(--accent);
  filter: brightness(1.15);
}

.icon {
  flex-shrink: 0;
  margin-bottom: 10px;
  font-size: 72px;
}

.name {
  max-width: 100%;
  overflow: hidden;
  font-size: var(--font-size-large);
  font-weight: 500;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.description {
  display: -webkit-box;
  max-width: 100%;
  overflow: hidden;
  color: rgb(255 255 255 / 0.72);
  font-size: var(--font-size-small);
  line-height: 1.3;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.dev-badge {
  margin-left: 8px;
  padding: 1px 6px;
  border-radius: 6px;
  background: var(--accent);
  color: #000;
  font-size: 13px;
  vertical-align: middle;
}

.dock-pin {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  opacity: 0.45;
}

.dock-pin:hover {
  opacity: 1;
  background: rgb(255 255 255 / 0.14);
}

.dock-pin.active {
  opacity: 1;
}
</style>
