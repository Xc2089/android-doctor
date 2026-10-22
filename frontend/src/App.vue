<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import FindingCard from './components/FindingCard.vue'
import HistoryPanel from './components/HistoryPanel.vue'
import SeverityBadge from './components/SeverityBadge.vue'
import AiSettingsModal from './components/AiSettingsModal.vue'
import { api } from './services/api'
import type { AppConfig, Diagnosis, HistoryItem } from './types'

const sample = `F AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: java.lang.UnsatisfiedLinkError: dlopen failed: library "libvision.so" not found
E AndroidRuntime:   at java.lang.Runtime.loadLibrary0(Runtime.java:1087)
E linker: dlopen failed: "/data/app/com.example/lib/arm64/libvision.so" is 32-bit instead of 64-bit
W audit: avc: denied { read } for path="/dev/ttyS4" scontext=u:r:untrusted_app:s0 tcontext=u:object_r:serial_device:s0`

const log = ref('')
const selectedFile = ref<File | null>(null)
const dragActive = ref(false)
const useAi = ref(false)
const config = ref<AppConfig | null>(null)
const result = ref<Diagnosis | null>(null)
const loading = ref(false)
const error = ref('')
const historyOpen = ref(false)
const history = ref<HistoryItem[]>([])
const historyLoading = ref(false)
const showSanitized = ref(false)
const settingsOpen = ref(false)

const charCount = computed(() => log.value.length)
const canAnalyze = computed(() => !loading.value && (!!selectedFile.value || !!log.value.trim()))

onMounted(async () => {
  try { config.value = await api.config() } catch { error.value = '后端服务尚未就绪，请检查启动状态' }
})

function chooseFile(files: FileList | null) {
  if (!files?.length) return
  selectedFile.value = files[0]
  log.value = ''
  error.value = ''
}

async function analyze() {
  if (!canAnalyze.value) return
  loading.value = true
  error.value = ''
  try {
    result.value = selectedFile.value
      ? await api.analyzeFile(selectedFile.value, useAi.value)
      : await api.analyzeText(log.value, useAi.value)
    await loadHistory()
    requestAnimationFrame(() => document.querySelector('#result')?.scrollIntoView({ behavior: 'smooth' }))
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '诊断失败，请稍后重试'
  } finally { loading.value = false }
}

async function loadHistory() {
  historyLoading.value = true
  try { history.value = await api.history() } finally { historyLoading.value = false }
}

async function openHistory() {
  historyOpen.value = true
  await loadHistory()
}

async function selectHistory(id: string) {
  result.value = await api.get(id)
  historyOpen.value = false
  requestAnimationFrame(() => document.querySelector('#result')?.scrollIntoView({ behavior: 'smooth' }))
}

async function removeHistory(id: string) {
  await api.remove(id)
  history.value = history.value.filter(item => item.id !== id)
  if (result.value?.id === id) result.value = null
}

async function clearHistory() {
  if (!window.confirm('确定清空全部脱敏诊断记录吗？此操作不可撤销。')) return
  await api.clear()
  history.value = []
  result.value = null
}

function resetInput() {
  log.value = ''
  selectedFile.value = null
  result.value = null
  error.value = ''
}

function updateAiConfig(ai: AppConfig['ai']) {
  if (config.value) config.value = { ...config.value, ai }
  if (ai.enabled) useAi.value = true
}

async function downloadReport() {
  if (!result.value) return
  try { await api.downloadReport(result.value.id) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '报告下载失败' }
}
</script>

<template>
  <div class="page-shell">
    <header class="topbar">
      <a class="brand" href="#" @click.prevent="resetInput">
        <span class="brand-mark">A+</span>
        <span><strong>Android Doctor</strong><small>Crash Intelligence</small></span>
      </a>
      <nav>
        <span class="status-dot"><i /> 规则引擎在线</span>
        <button class="ghost-button model-button" type="button" @click="settingsOpen = true">模型设置</button>
        <button class="ghost-button" type="button" @click="openHistory">历史记录</button>
      </nav>
    </header>

    <main>
      <section class="hero">
        <div class="hero-copy">
          <span class="eyebrow">ANDROID · HARMONYOS · NATIVE</span>
          <h1>让崩溃日志<br><em>直接说出答案</em></h1>
          <p>从异常堆栈到可执行修复建议。默认本地规则诊断，敏感信息自动脱敏，大模型增强完全可选。</p>
        </div>
        <div class="privacy-card">
          <span class="shield">⌾</span>
          <div><strong>安全优先</strong><p>原始日志不落盘<br>仅保存脱敏版本</p></div>
        </div>
      </section>

      <section class="workspace-card">
        <div class="workspace-head">
          <div><span class="step">01</span><h2>输入崩溃日志</h2></div>
          <button class="text-button" type="button" @click="log = sample; selectedFile = null">载入示例</button>
        </div>

        <div class="input-grid">
          <div class="editor-wrap">
            <div class="editor-toolbar">
              <span><i class="dot red"/><i class="dot amber"/><i class="dot green"/></span>
              <span>{{ selectedFile ? selectedFile.name : 'crash.log' }}</span>
              <span>{{ charCount.toLocaleString() }} 字符</span>
            </div>
            <textarea v-model="log" :disabled="!!selectedFile" spellcheck="false"
              placeholder="粘贴 logcat、tombstone、ANR traces 或 HarmonyOS hilog…" />
            <button v-if="log" class="editor-clear" type="button" @click="log = ''">清空</button>
          </div>

          <label class="drop-zone" :class="{ active: dragActive, selected: selectedFile }"
            @dragenter.prevent="dragActive = true" @dragover.prevent @dragleave.prevent="dragActive = false"
            @drop.prevent="dragActive = false; chooseFile($event.dataTransfer?.files ?? null)">
            <input type="file" accept=".txt,.log,.trace,.tombstone,text/plain" @change="chooseFile(($event.target as HTMLInputElement).files)" />
            <span class="upload-icon">⇧</span>
            <strong>{{ selectedFile ? selectedFile.name : '拖放日志文件' }}</strong>
            <span>{{ selectedFile ? `${(selectedFile.size / 1024).toFixed(1)} KB` : '或点击选择 · 最大 2 MB' }}</span>
            <button v-if="selectedFile" type="button" class="file-remove" @click.prevent="selectedFile = null">移除文件</button>
          </label>
        </div>

        <div class="action-row">
          <label class="ai-toggle" :class="{ disabled: !config?.ai.enabled }">
            <input v-model="useAi" type="checkbox" :disabled="!config?.ai.enabled" />
            <span class="switch" />
            <span><strong>大模型补充分析</strong><small>{{ config?.ai.enabled ? config.ai.model : '未配置 · 使用纯规则模式' }}</small></span>
          </label>
          <div class="analyze-group">
            <span v-if="config">最大 {{ (config.maxLogChars / 10000).toFixed(0) }} 万字符</span>
            <button class="primary-button" type="button" :disabled="!canAnalyze" @click="analyze">
              <span v-if="loading" class="spinner" />{{ loading ? '正在诊断…' : '开始诊断' }}<b v-if="!loading">→</b>
            </button>
          </div>
        </div>
        <p v-if="error" class="error-banner">{{ error }}</p>
      </section>

      <section v-if="result" id="result" class="results">
        <div class="section-heading">
          <div><span class="step">02</span><h2>诊断结果</h2></div>
          <div class="result-actions">
            <span class="result-id">#{{ result.id.slice(0, 8) }}</span>
            <button type="button" @click="downloadReport">下载 Markdown 报告</button>
          </div>
        </div>

        <div class="summary-card">
          <div>
            <span class="eyebrow">PRIMARY DIAGNOSIS</span>
            <h2>{{ result.summary }}</h2>
            <p>{{ result.category }} · 共命中 {{ result.findings.length }} 条规则</p>
          </div>
          <SeverityBadge :severity="result.severity" />
          <div class="summary-stats">
            <span><strong>{{ result.findings[0]?.confidence }}%</strong>置信度</span>
            <span><strong>{{ result.redactionCount }}</strong>处脱敏</span>
            <span><strong>{{ result.aiUsed ? '已启用' : '规则' }}</strong>分析模式</span>
          </div>
        </div>

        <div v-if="result.aiWarning" class="warning-banner">{{ result.aiWarning }}</div>
        <article v-if="result.aiAnalysis" class="ai-card">
          <div class="ai-card-head"><span>✦</span><div><strong>大模型补充意见</strong><small>{{ config?.ai.model }}</small></div></div>
          <p>{{ result.aiAnalysis }}</p>
        </article>

        <div class="findings">
          <FindingCard v-for="(finding, index) in result.findings" :key="finding.code" :finding="finding" :index="index" />
        </div>

        <article class="sanitized-card">
          <button type="button" @click="showSanitized = !showSanitized">
            <span><strong>查看脱敏日志</strong><small>原始日志未保存 · {{ result.redactionCount }} 处敏感信息已替换</small></span>
            <span>{{ showSanitized ? '收起' : '展开' }}</span>
          </button>
          <pre v-if="showSanitized">{{ result.sanitizedLog }}</pre>
        </article>
      </section>
    </main>

    <footer><span>Android Doctor MVP</span><span>Local-first · Rule-powered · AI optional</span></footer>
    <HistoryPanel :open="historyOpen" :items="history" :loading="historyLoading"
      @close="historyOpen = false" @select="selectHistory" @remove="removeHistory" @clear="clearHistory" />
    <AiSettingsModal :open="settingsOpen" :config="config?.ai ?? null"
      @close="settingsOpen = false" @saved="updateAiConfig" />
  </div>
</template>
