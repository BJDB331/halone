<script setup lang="ts">
defineProps<{ name: string }>()
</script>

<template>
  <span class="x-icon" aria-hidden="true">{{ name }}</span>
</template>
