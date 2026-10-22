<script setup lang="ts">
import type { Severity } from '../types'

const props = defineProps<{ severity: Severity }>()
const labels: Record<Severity, string> = {
  critical: '严重', high: '高风险', medium: '中风险', low: '低风险', info: '待确认'
}
</script>

<template>
  <span class="severity" :class="`severity--${props.severity}`">{{ labels[props.severity] }}</span>
</template>

