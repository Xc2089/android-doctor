<script setup lang="ts">
import { ref } from 'vue'
import type { Finding } from '../types'
import SeverityBadge from './SeverityBadge.vue'

defineProps<{ finding: Finding; index: number }>()
const open = ref(true)
</script>

<template>
  <article class="finding-card">
    <button class="finding-head" type="button" @click="open = !open">
      <span class="finding-index">{{ String(index + 1).padStart(2, '0') }}</span>
      <span class="finding-title-group">
        <span class="finding-title">{{ finding.title }}</span>
        <span class="finding-meta">{{ finding.category }} · {{ finding.confidence }}% 置信度</span>
      </span>
      <SeverityBadge :severity="finding.severity" />
      <span class="chevron" :class="{ open }">⌄</span>
    </button>
    <div v-if="open" class="finding-body">
      <section v-if="finding.evidence.length">
        <h4>命中证据</h4>
        <pre v-for="line in finding.evidence" :key="line" class="evidence">{{ line }}</pre>
      </section>
      <div class="finding-columns">
        <section>
          <h4>可能原因</h4>
          <ul><li v-for="item in finding.possibleCauses" :key="item">{{ item }}</li></ul>
        </section>
        <section>
          <h4>修复建议</h4>
          <ol><li v-for="item in finding.suggestions" :key="item">{{ item }}</li></ol>
        </section>
      </div>
    </div>
  </article>
</template>

