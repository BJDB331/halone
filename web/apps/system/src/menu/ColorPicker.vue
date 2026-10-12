<script setup lang="ts">
import { ref, watch } from 'vue'
import { hexToHsl, hslToHex, parseColor } from './dates'

const props = defineProps<{ value: string; swatches: string[] }>()
const emit = defineEmits<{ 'update:value': [value: string] }>()

/** Ten hues across, from light to dark, then greys: enough to land near most colours with one press. */
const HUES = [0, 30, 50, 90, 140, 175, 200, 230, 270, 320]
const PALETTE = [
  ...HUES.map((hue) => hslToHex(hue, 85, 75)),
  ...HUES.map((hue) => hslToHex(hue, 80, 55)),
  ...HUES.map((hue) => hslToHex(hue, 75, 35)),
  ...[100, 85, 70, 55, 45, 35, 25, 15, 8, 0].map((lightness) => hslToHex(0, 0, lightness)),
]

const color = ref(parseColor(props.value) ?? '#000000')
const [initialHue, , initialLightness] = hexToHsl(color.value)
const hue = ref(initialHue)
const lightness = ref(initialLightness)
const text = ref(color.value)
const page = props.swatches.map(parseColor).filter((swatch): swatch is string => !!swatch)

watch(color, (value) => {
  text.value = value
  emit('update:value', value)
}, { immediate: true })

function choose(hex: string) {
  color.value = hex
  const [h, , l] = hexToHsl(hex)
  hue.value = h
  lightness.value = l
}

function fromSliders() {
  color.value = hslToHex(hue.value, 85, lightness.value)
}

function fromText() {
  const parsed = parseColor(text.value)
  if (parsed) choose(parsed)
}
</script>

<template>
  <div class="colors">
    <div class="row">
      <span class="preview" :style="{ background: color }" />
      <input v-model="text" class="x-input hex" spellcheck="false" autocomplete="off" @input="fromText" />
    </div>
    <div v-if="page.length" class="swatches">
      <button v-for="swatch in page" :key="'page' + swatch" type="button" :class="{ chosen: swatch === color }" :style="{ background: swatch }" :aria-label="swatch" @click="choose(swatch)" />
    </div>
    <div class="swatches">
      <button v-for="swatch in PALETTE" :key="swatch" type="button" :class="{ chosen: swatch === color }" :style="{ background: swatch }" :aria-label="swatch" @click="choose(swatch)" />
    </div>
    <label>Hue <input v-model.number="hue" type="range" min="0" max="359" class="hue" @input="fromSliders" /></label>
    <label>Lightness <input v-model.number="lightness" type="range" min="0" max="100" @input="fromSliders" /></label>
  </div>
</template>

<style scoped>
.colors {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.preview {
  width: 120px;
  height: 52px;
  border: 1px solid var(--border);
  border-radius: 12px;
}

.hex {
  flex: 1;
  height: 52px;
  font-variant-numeric: tabular-nums;
}

.swatches {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 6px;
}

.swatches button {
  height: 40px;
  border: 2px solid transparent;
  border-radius: 8px;
  cursor: pointer;
}

.swatches button.chosen {
  border-color: var(--text);
}

label {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--text-muted);
  font-size: 18px;
}

label input {
  flex: 1;
}

.hue {
  accent-color: var(--accent);
}
</style>
