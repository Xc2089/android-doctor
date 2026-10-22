import type { AdminStatus, AiConfig, AiConfigUpdate, AiTestResult, AppConfig, Diagnosis, HistoryItem } from '../types'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, { credentials: 'same-origin', ...options })
  if (!response.ok) {
    let message = `请求失败（${response.status}）`
    try {
      const data = await response.json() as { message?: string }
      if (data.message) message = data.message
    } catch { /* keep fallback */ }
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  config: () => request<AppConfig>('/config'),
  updateAiConfig: (config: AiConfigUpdate) => request<AiConfig>('/config/ai', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config)
  }),
  testAiConfig: () => request<AiTestResult>('/config/ai/test', { method: 'POST' }),
  resetAiConfig: () => request<AiConfig>('/config/ai/reset', { method: 'POST' }),
  adminStatus: () => request<AdminStatus>('/admin/status'),
  adminLogin: (password: string) => request<AdminStatus>('/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password })
  }),
  adminLogout: () => request<AdminStatus>('/admin/logout', { method: 'POST' }),
  analyzeText: (log: string, useAi: boolean) => request<Diagnosis>('/diagnoses', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ log, sourceName: '粘贴日志', useAi })
  }),
  analyzeFile: (file: File, useAi: boolean) => {
    const body = new FormData()
    body.append('file', file)
    body.append('useAi', String(useAi))
    return request<Diagnosis>('/diagnoses/upload', { method: 'POST', body })
  },
  history: () => request<HistoryItem[]>('/diagnoses?limit=50'),
  get: (id: string) => request<Diagnosis>(`/diagnoses/${id}`),
  remove: (id: string) => request<void>(`/diagnoses/${id}`, { method: 'DELETE' }),
  clear: () => request<void>('/diagnoses', { method: 'DELETE' }),
  downloadReport: async (id: string) => {
    const response = await fetch(`/api/diagnoses/${id}/report.md`, { credentials: 'same-origin' })
    if (!response.ok) throw new Error(`报告下载失败（${response.status}）`)
    const blob = await response.blob()
    const disposition = response.headers.get('Content-Disposition') ?? ''
    const filename = disposition.match(/filename="([^"]+)"/)?.[1] ?? `android-doctor-${id.slice(0, 8)}.md`
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  }
}

