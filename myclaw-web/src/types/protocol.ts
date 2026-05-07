export interface GatewayFrame {
  type: 'req' | 'res' | 'event'
  id?: string
  method?: string
  params?: any
  ok?: boolean
  payload?: any
  error?: { code: string; message: string }
  event?: string
  seq?: number
}

export interface AgentStreamEvent {
  runId: string
  stream: 'assistant' | 'tool' | 'lifecycle' | 'error'
  delta?: string
  phase?: 'start' | 'end' | 'error'
  text?: string
  error?: string
}

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp?: string
  runId?: string
  isError?: boolean
}

export interface ModelInfo {
  id: string
  name: string
}

export interface AiProvider {
  id: string
  name: string
  websiteUrl?: string
  notes?: string
  baseUrl: string
  apiKey: string
  apiFormat: string
  currentModel: string
  enabled: boolean
  models: ModelInfo[]
  modelMappings?: Record<string, string>
}
