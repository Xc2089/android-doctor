<script setup lang="ts">
import type { HistoryItem } from '../types'
import SeverityBadge from './SeverityBadge.vue'

defineProps<{ open: boolean; items: HistoryItem[]; loading: boolean }>()
const emit = defineEmits<{
  close: []
  select: [id: string]
  remove: [id: string]
  clear: []
}>()

const formatDate = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
}).format(new Date(value))
</script>

<template>
  <Transition name="fade">
    <div v-if="open" class="drawer-mask" @click.self="emit('close')">
      <aside class="drawer" aria-label="诊断历史">
        <header class="drawer-header">
          <div><span class="eyebrow">LOCAL HISTORY</span><h2>诊断历史</h2></div>
          <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
        </header>
        <p class="drawer-note">仅保存脱敏后的日志，数据存放在本机 H2 数据库。</p>
        <div v-if="loading" class="empty">正在加载…</div>
        <div v-else-if="!items.length" class="empty">还没有诊断记录</div>
        <div v-else class="history-list">
          <article v-for="item in items" :key="item.id" class="history-item" @click="emit('select', item.id)">
            <div class="history-top">
              <SeverityBadge :severity="item.severity" />
              <time>{{ formatDate(item.createdAt) }}</time>
              <button class="delete-button" type="button" @click.stop="emit('remove', item.id)">删除</button>
            </div>
            <h3>{{ item.summary }}</h3>
            <p>{{ item.sourceName }} · {{ item.category }}<span v-if="item.aiUsed"> · AI 增强</span></p>
          </article>
        </div>
        <button v-if="items.length" class="clear-button" type="button" @click="emit('clear')">清空全部历史</button>
      </aside>
    </div>
  </Transition>
</template>

