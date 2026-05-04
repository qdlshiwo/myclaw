import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useGateway } from '@/composables/useGateway'
import type { AgentStreamEvent, ChatMessage } from '@/types/protocol'

export const useGatewayStore = defineStore('gateway', () => {
  const gateway = useGateway()

  const messages = ref<ChatMessage[]>([])
  const streaming = ref(false)
  const currentRunId = ref<string | null>(null)
  const currentDelta = ref('')
  const currentSession = ref('main')
  const config = ref<Record<string, string> | null>(null)
  const models = ref<{ id: string; name: string }[]>([])

  const isConnected = computed(() => gateway.connected.value)

  function ensureConnected() {
    if (!gateway.connected.value) {
      gateway.connect()
    }
  }

  // Listen to agent stream events
  gateway.onEvent('agent', (payload: AgentStreamEvent) => {
    if (payload.stream === 'lifecycle' && payload.phase === 'start') {
      streaming.value = true
      currentRunId.value = payload.runId
      currentDelta.value = ''
    } else if (payload.stream === 'assistant') {
      currentDelta.value += payload.delta || ''
    } else if (payload.stream === 'lifecycle' && payload.phase === 'end') {
      if (currentDelta.value) {
        messages.value.push({
          role: 'assistant',
          content: currentDelta.value,
          runId: payload.runId,
        })
      }
      streaming.value = false
      currentRunId.value = null
      currentDelta.value = ''
    } else if (payload.stream === 'error') {
      streaming.value = false
      currentRunId.value = null
      currentDelta.value = ''
    }
  })

  // Listen to chat events (from send / other clients)
  gateway.onEvent('chat', (payload: any) => {
    if (payload.role === 'user') {
      messages.value.push({
        role: 'user',
        content: payload.content,
        timestamp: payload.timestamp,
      })
    }
  })

  async function sendMessage(content: string) {
    ensureConnected()
    await gateway.sendRequest('send', { content, sessionKey: currentSession.value })
  }

  async function runAgent(message: string) {
    ensureConnected()
    const model = config.value?.model || 'gpt-4o-mini'
    // Add user message locally for immediate feedback
    messages.value.push({ role: 'user', content: message })
    await gateway.sendRequest('agent', {
      message,
      sessionKey: currentSession.value,
      model,
    })
  }

  async function loadChatHistory() {
    ensureConnected()
    const data = await gateway.sendRequest('chat', { sessionKey: currentSession.value })
    if (data.messages) {
      messages.value = data.messages
    }
  }

  async function switchSession(key: string) {
    currentSession.value = key
    messages.value = []
    await loadChatHistory()
  }

  async function listSessions() {
    ensureConnected()
    return gateway.sendRequest('sessions', {})
  }

  async function loadConfig() {
    ensureConnected()
    const data = await gateway.sendRequest('config', { action: 'get' })
    if (data.config) {
      config.value = data.config
    }
    return config.value
  }

  async function saveConfig(newConfig: Record<string, string>) {
    ensureConnected()
    const data = await gateway.sendRequest('config', { action: 'set', config: newConfig })
    if (data.config) {
      config.value = data.config
    }
    return config.value
  }

  async function loadModels() {
    ensureConnected()
    const data = await gateway.sendRequest('models', {})
    if (data.models) {
      models.value = data.models
    }
    return models.value
  }

  async function loadFileList(path: string) {
    ensureConnected()
    return gateway.sendRequest('file', { action: 'list', path })
  }

  async function readFile(path: string) {
    ensureConnected()
    return gateway.sendRequest('file', { action: 'read', path })
  }

  async function writeFile(path: string, content: string) {
    ensureConnected()
    return gateway.sendRequest('file', { action: 'write', path, content })
  }

  return {
    isConnected,
    messages,
    streaming,
    currentDelta,
    currentSession,
    config,
    models,
    connect: gateway.connect,
    disconnect: gateway.disconnect,
    sendMessage,
    runAgent,
    loadChatHistory,
    switchSession,
    listSessions,
    loadConfig,
    saveConfig,
    loadModels,
    loadFileList,
    readFile,
    writeFile,
  }
})
