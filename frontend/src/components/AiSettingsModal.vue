<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { api } from '../services/api'
import type { AdminStatus, AiConfig } from '../types'

const props = defineProps<{ open: boolean; config: AiConfig | null }>()
const emit = defineEmits<{ close: []; saved: [config: AiConfig] }>()
const form = reactive({ enabled: false, baseUrl: 'http://localhost:11434/v1', model: 'qwen2.5-coder:7b', timeoutSeconds: 45, apiKey: '', clearApiKey: false })
const auth = ref<AdminStatus | null>(null)
const authLoading = ref(false)
const adminPassword = ref('')
const signingIn = ref(false)
const saving = ref(false)
const testing = ref(false)
const error = ref('')
const notice = ref('')

watch(() => props.open, async (open) => {
  if (!open) return
  syncForm()
  error.value = ''
  notice.value = ''
  adminPassword.value = ''
  authLoading.value = true
  try { auth.value = await api.adminStatus() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '无法读取管理员状态' }
  finally { authLoading.value = false }
}, { immediate: true })

function syncForm(config = props.config) {
  if (!config) return
  form.enabled = config.enabled
  form.baseUrl = config.baseUrl
  form.model = config.model
  form.timeoutSeconds = config.timeoutSeconds
  form.apiKey = ''
  form.clearApiKey = false
}

async function login() {
  if (!adminPassword.value) return
  signingIn.value = true
  error.value = ''
  try {
    auth.value = await api.adminLogin(adminPassword.value)
    adminPassword.value = ''
    notice.value = '管理员验证成功。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '管理员验证失败' }
  finally { signingIn.value = false }
}

async function logout() {
  auth.value = await api.adminLogout()
  notice.value = ''
  error.value = ''
}

function useOllama() {
  form.baseUrl = 'http://localhost:11434/v1'
  form.apiKey = ''
  form.clearApiKey = true
  notice.value = '已填入本机 Ollama 地址；请确认模型已下载并启动。'
}

async function save(showSuccess = true): Promise<AiConfig | null> {
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const updated = await api.updateAiConfig({
      enabled: form.enabled, baseUrl: form.baseUrl.trim(), model: form.model.trim(),
      timeoutSeconds: Number(form.timeoutSeconds), apiKey: form.apiKey.trim() || undefined,
      clearApiKey: form.clearApiKey
    })
    form.apiKey = ''
    form.clearApiKey = false
    emit('saved', updated)
    if (showSuccess) notice.value = '配置已加密保存，重启服务后仍会生效。'
    return updated
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败'
    if (error.value.includes('管理员') && auth.value) auth.value = { ...auth.value, authenticated: false }
    return null
  } finally { saving.value = false }
}

async function testConnection() {
  const updated = await save(false)
  if (!updated) return
  testing.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await api.testAiConfig()
    if (result.success) notice.value = `${result.message} · ${result.latencyMs} ms`
    else error.value = `${result.message} · ${result.latencyMs} ms`
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '连接测试失败' }
  finally { testing.value = false }
}

async function resetConfig() {
  if (!window.confirm('恢复环境变量配置，并删除数据库中保存的模型设置？')) return
  try {
    const updated = await api.resetAiConfig()
    emit('saved', updated)
    syncForm(updated)
    notice.value = '已恢复环境变量配置。'
    error.value = ''
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '恢复失败' }
}
</script>

<template>
  <Transition name="fade">
    <div v-if="open" class="modal-mask" @click.self="emit('close')">
      <section class="settings-modal" role="dialog" aria-modal="true" aria-label="大模型设置">
        <header class="settings-head">
          <div><span class="eyebrow">OPENAI-COMPATIBLE</span><h2>大模型设置</h2></div>
          <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <div class="settings-security">
          <span>⌾</span>
          <p><strong>API Key 使用 AES-GCM 加密保存</strong>密钥不会通过查询接口回传，也不会进入诊断日志或报告。</p>
        </div>

        <div v-if="authLoading" class="auth-loading">正在检查管理员会话…</div>
        <form v-else-if="!auth?.authenticated" class="admin-gate" @submit.prevent="login">
          <span class="lock-icon">⌁</span>
          <h3>需要管理员验证</h3>
          <p v-if="auth?.passwordSource === 'generated-file'">首次启动生成的密码位于：<code>{{ auth.passwordHint }}</code></p>
          <p v-else>请输入环境变量 <code>APP_ADMIN_PASSWORD</code> 设置的密码。</p>
          <input v-model="adminPassword" type="password" autocomplete="current-password" placeholder="管理员密码" autofocus />
          <button class="primary-button" type="submit" :disabled="signingIn || !adminPassword">{{ signingIn ? '验证中…' : '解锁设置' }}</button>
        </form>

        <template v-else>
          <div class="authenticated-row"><span>管理员会话已验证</span><button type="button" @click="logout">退出</button></div>
          <label class="settings-enable">
            <input v-model="form.enabled" type="checkbox" /><span class="switch" />
            <span><strong>启用大模型增强</strong><small>每次诊断仍可单独选择是否调用</small></span>
          </label>
          <div class="preset-row"><span>快速配置</span><button type="button" @click="useOllama">本机 Ollama</button></div>
          <div class="form-field">
            <label for="ai-base-url">Base URL</label>
            <input id="ai-base-url" v-model="form.baseUrl" type="url" placeholder="https://provider.example.com/v1" />
            <small>填写到 `/v1`；系统会请求 `/chat/completions`。</small>
          </div>
          <div class="settings-grid">
            <div class="form-field"><label for="ai-model">模型名称</label><input id="ai-model" v-model="form.model" type="text" placeholder="模型 ID" /></div>
            <div class="form-field"><label for="ai-timeout">超时（秒）</label><input id="ai-timeout" v-model.number="form.timeoutSeconds" type="number" min="5" max="120" /></div>
          </div>
          <div class="form-field">
            <label for="ai-api-key">API Key</label>
            <input id="ai-api-key" v-model="form.apiKey" type="password" :placeholder="config?.apiKeyConfigured ? '已配置；留空表示保持不变' : '本地无鉴权服务可留空'" autocomplete="new-password" />
            <small v-if="config?.apiKeyConfigured">当前已有加密密钥；出于安全原因不会显示。</small>
          </div>
          <label v-if="config?.apiKeyConfigured" class="clear-key"><input v-model="form.clearApiKey" type="checkbox" /> 清除当前 API Key</label>

          <p v-if="notice" class="settings-notice success">{{ notice }}</p>
          <p v-if="error" class="settings-notice error">{{ error }}</p>
          <footer class="settings-actions">
            <button class="reset-button" type="button" :disabled="saving || testing" @click="resetConfig">恢复环境配置</button>
            <span class="action-spacer" />
            <button class="secondary-button" type="button" :disabled="saving || testing" @click="testConnection">{{ testing ? '正在测试…' : '保存并测试连接' }}</button>
            <button class="primary-button" type="button" :disabled="saving || testing" @click="save(true)">{{ saving ? '保存中…' : '保存配置' }}</button>
          </footer>
          <p class="test-cost-note">连接测试会向所选模型发送一条极短请求，可能产生少量 API 用量。</p>
        </template>

        <p v-if="error && !auth?.authenticated" class="settings-notice error auth-error">{{ error }}</p>
      </section>
    </div>
  </Transition>
</template>
