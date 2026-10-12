<script setup lang="ts">
import { ref, watch } from 'vue'
import type { AppInfo } from '@halone/sdk'
import Icon from './Icon.vue'

const props = defineProps<{ app: AppInfo | null | undefined }>()
const broken = ref(false)
watch(
  () => props.app?.iconUrl,
  () => (broken.value = false),
)
</script>

<template>
  <span class="app-icon">
    <img v-if="app?.iconUrl && !broken" :src="app.iconUrl" alt="" draggable="false" @error="broken = true" />
    <span v-else-if="app" class="letter">{{ app.name.slice(0, 1).toUpperCase() }}</span>
    <Icon v-else name="web_asset" />
  </span>
</template>

<style scoped>
.app-icon {
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
  width: 1em;
  height: 1em;
  line-height: 1;
}

img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 0.18em;
}

.letter {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  border-radius: 0.22em;
  background: var(--surface-hover);
  color: var(--text);
  font-size: 0.55em;
  font-weight: 600;
}

.x-icon {
  font-size: 1em;
}
</style>
