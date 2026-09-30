<script setup>
defineProps({
  label: {
    type: String,
    required: true
  },
  value: {
    type: [String, Number],
    default: '--'
  },
  foot: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <div class="metric-card">
    <div class="metric-label">{{ label }}</div>
    <div class="metric-value">{{ value }}</div>
    <div v-if="foot" class="metric-foot">{{ foot }}</div>
  </div>
</template>
