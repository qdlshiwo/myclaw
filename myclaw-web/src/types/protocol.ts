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
}
