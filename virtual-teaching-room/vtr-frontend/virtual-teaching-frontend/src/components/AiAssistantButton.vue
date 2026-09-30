<script setup>
import { ChatDotRound } from '@element-plus/icons-vue'

defineProps({
  label: { type: String, default: 'AI讲解' }
})

defineEmits(['click'])
</script>

<template>
  <button type="button" class="ai-assistant-button" @click="$emit('click')">
    <ChatDotRound class="ai-assistant-button-icon" />
    <span>{{ label }}</span>
  </button>
</template>

<style scoped>
.ai-assistant-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 11px;
  border: 1px solid transparent;
  border-radius: 6px;
  background:
    linear-gradient(#fff, #fff) padding-box,
    linear-gradient(115deg, #5b5ff4, #8b5cf6, #3b82f6) border-box;
  color: #5b55d9;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  transition: box-shadow .18s ease, transform .18s ease, color .18s ease;
  white-space: nowrap;
}

.ai-assistant-button:hover {
  color: #4338ca;
  box-shadow: 0 5px 14px rgba(91, 85, 217, .18);
  transform: translateY(-1px);
}

.ai-assistant-button:active {
  transform: translateY(0);
}

.ai-assistant-button-icon {
  width: 15px;
  height: 15px;
}
</style>
