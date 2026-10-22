export interface Finding {
  code: string
  title: string
  category: string
  severity: Severity
  confidence: number
  evidence: string[]
  possibleCauses: string[]
  suggestions: string[]
}

export type Severity = 'critical' | 'high' | 'medium' | 'low' | 'info'

export interface Diagnosis {
  id: string
  createdAt: string
  sourceName: string
  category: string
  severity: Severity
  summary: string
  sanitizedLog: string
  redactionCount: number
  findings: Finding[]
  aiUsed: boolean
  aiAnalysis?: string
  aiWarning?: string
}

export interface HistoryItem {
  id: string
  createdAt: string
  sourceName: string
  category: string
  severity: Severity
  summary: string
  aiUsed: boolean
  redactionCount: number
}

export interface AppConfig {
  ai: AiConfig
  maxLogChars: number
  maxUploadBytes: number
}

export interface AiConfig {
  enabled: boolean
  model: string
  baseUrl: string
  timeoutSeconds: number
  apiKeyConfigured: boolean
  source: 'environment' | 'database'
}

export interface AiConfigUpdate {
  enabled: boolean
  model: string
  baseUrl: string
  timeoutSeconds: number
  apiKey?: string
  clearApiKey: boolean
}

export interface AiTestResult {
  success: boolean
  latencyMs: number
  message: string
}

export interface AdminStatus {
  authenticated: boolean
  passwordSource: 'environment' | 'generated-file'
  passwordHint: string
}

