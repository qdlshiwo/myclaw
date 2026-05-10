import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useGateway } from '@/composables/useGateway'
import type { AgentStreamEvent, ChatMessage, AiProvider } from '@/types/protocol'

export const useGatewayStore = defineStore('gateway', () => {
  const gateway = useGateway()

  const messages = ref<ChatMessage[]>([])
  const streaming = ref(false)
  const currentRunId = ref<string | null>(null)
  const currentDelta = ref('')
  const currentSession = ref('main')
  const config = ref<Record<string, string> | null>(null)
  const models = ref<{ id: string; name: string }[]>([])

  // Provider state
  const providers = ref<AiProvider[]>([])
  const currentProviderId = ref<string>('')

  // Sessions state
  const sessions = ref<Array<{
    sessionId: string
    sessionKey: string
    agentId: string
    messageCount: number
    createdAt?: string
    lastInteractionAt?: string
  }>>([])

  const isConnected = computed(() => gateway.connected.value)

  const currentProvider = computed(() =>
    providers.value.find((p) => p.id === currentProviderId.value) || null
  )

  function ensureConnected(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (gateway.connected.value) {
        resolve()
        return
      }
      gateway.connect()
      // Wait up to 5s for connection
      let attempts = 0
      const timer = setInterval(() => {
        if (gateway.connected.value) {
          clearInterval(timer)
          resolve()
        } else if (attempts++ > 25) {
          clearInterval(timer)
          reject(new Error('Connection timeout'))
        }
      }, 200)
    })
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
      refreshSessions()
    } else if (payload.stream === 'error') {
      streaming.value = false
      currentRunId.value = null
      if (currentDelta.value) {
        messages.value.push({
          role: 'assistant',
          content: currentDelta.value,
          runId: payload.runId,
        })
      }
      messages.value.push({
        role: 'assistant',
        content: payload.error || 'Unknown error',
        runId: payload.runId,
        isError: true,
      })
      currentDelta.value = ''
      refreshSessions()
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
    try { await ensureConnected() } catch { return }
    await gateway.sendRequest('send', { content, sessionKey: currentSession.value })
  }

  async function runAgent(message: string, model?: string) {
    try { await ensureConnected() } catch { return }
    const resolvedModel = model || currentProvider.value?.currentModel || ''
    // Add user message locally for immediate feedback
    messages.value.push({ role: 'user', content: message })
    await gateway.sendRequest('agent', {
      message,
      sessionKey: currentSession.value,
      model: resolvedModel,
    })
  }

  async function loadChatHistory() {
    try { await ensureConnected() } catch { return }
    try {
      const data = await gateway.sendRequest('chat', { sessionKey: currentSession.value })
      if (data.messages) {
        messages.value = data.messages
      }
    } catch (e) {
      console.warn('Failed to load chat history', e)
    }
  }

  async function switchSession(key: string) {
    currentSession.value = key
    messages.value = []
    await loadChatHistory()
  }

  async function refreshSessions() {
    try { await ensureConnected() } catch { return }
    try {
      const data = await gateway.sendRequest('sessions', {})
      if (data && data.sessions) {
        sessions.value = data.sessions
      }
    } catch (e) {
      console.warn('Failed to load sessions', e)
    }
  }

  async function listSessions() {
    try { await ensureConnected() } catch { return { sessions: [] } }
    try {
      return await gateway.sendRequest('sessions', {})
    } catch (e) {
      console.warn('Failed to list sessions', e)
      return { sessions: [] }
    }
  }

  async function newSession() {
    const key = `session-${Date.now()}`
    currentSession.value = key
    messages.value = []
    await refreshSessions()
    return key
  }

  async function autoRestoreSession() {
    try { await ensureConnected() } catch { return }
    try {
      const data = await gateway.sendRequest('sessions', {})
      if (data && data.sessions && data.sessions.length > 0) {
        sessions.value = data.sessions
        const key = data.lastSessionKey || data.sessions[0].sessionKey
        if (key) {
          currentSession.value = key
          await loadChatHistory()
        }
      } else {
        sessions.value = []
        currentSession.value = 'main'
        await loadChatHistory()
      }
    } catch (e) {
      console.warn('Failed to auto-restore session', e)
    }
  }

  async function loadConfig() {
    try { await ensureConnected() } catch { return null }
    try {
      const data = await gateway.sendRequest('config', { action: 'get' })
      if (data.config) {
        config.value = data.config
      }
      return config.value
    } catch (e) {
      return null
    }
  }

  async function saveConfig(newConfig: Record<string, string>) {
    try { await ensureConnected() } catch { return null }
    try {
      const data = await gateway.sendRequest('config', { action: 'set', config: newConfig })
      if (data.config) {
        config.value = data.config
      }
      return config.value
    } catch (e) {
      return null
    }
  }

  async function loadModels() {
    try { await ensureConnected() } catch { return [] }
    try {
      const data = await gateway.sendRequest('models', {})
      if (data.models) {
        models.value = data.models
      }
      return models.value
    } catch (e) {
      return []
    }
  }

  async function loadFileList(path: string) {
    try { await ensureConnected() } catch { return [] }
    try {
      return await gateway.sendRequest('file', { action: 'list', path })
    } catch (e) {
      return []
    }
  }

  async function readFile(path: string) {
    try { await ensureConnected() } catch { return null }
    try {
      return await gateway.sendRequest('file', { action: 'read', path })
    } catch (e) {
      return null
    }
  }

  async function writeFile(path: string, content: string) {
    try { await ensureConnected() } catch { return null }
    try {
      return await gateway.sendRequest('file', { action: 'write', path, content })
    } catch (e) {
      return null
    }
  }

  // Provider methods
  async function loadProviders() {
    try { await ensureConnected() } catch { return { providers: [], current: '' } }
    try {
      const data = await gateway.sendRequest('providers', { action: 'list' })
      if (data.providers) {
        providers.value = data.providers
        currentProviderId.value = data.current || ''
      }
      return { providers: providers.value, current: currentProviderId.value }
    } catch (e) {
      return { providers: [], current: '' }
    }
  }

  async function getProvider(id: string) {
    try { await ensureConnected() } catch { return null }
    try {
      return await gateway.sendRequest('providers', { action: 'get', id })
    } catch (e) {
      return null
    }
  }

  async function addProvider(provider: Omit<AiProvider, 'id'> & { id?: string }) {
    try { await ensureConnected() } catch { return null }
    try {
      const data = await gateway.sendRequest('providers', { action: 'add', provider })
      await loadProviders()
      return data
    } catch (e) {
      return null
    }
  }

  async function updateProvider(provider: AiProvider) {
    try { await ensureConnected() } catch { return null }
    try {
      const data = await gateway.sendRequest('providers', { action: 'update', provider })
      await loadProviders()
      return data
    } catch (e) {
      return null
    }
  }

  async function deleteProvider(id: string) {
    try { await ensureConnected() } catch { return null }
    try {
      const data = await gateway.sendRequest('providers', { action: 'delete', id })
      await loadProviders()
      return data
    } catch (e) {
      return null
    }
  }

  async function switchProvider(id: string) {
    try { await ensureConnected() } catch { return null }
    try {
      const data = await gateway.sendRequest('providers', { action: 'switch', id })
      if (data.current) {
        currentProviderId.value = data.current
      }
      await loadProviders()
      return data
    } catch (e) {
      return null
    }
  }

  return {
    isConnected,
    messages,
    streaming,
    currentDelta,
    currentSession,
    config,
    models,
    providers,
    currentProviderId,
    currentProvider,
    sessions,
    connect: gateway.connect,
    disconnect: gateway.disconnect,
    sendMessage,
    runAgent,
    loadChatHistory,
    switchSession,
    refreshSessions,
    listSessions,
    newSession,
    autoRestoreSession,
    loadConfig,
    saveConfig,
    loadModels,
    loadFileList,
    readFile,
    writeFile,
    loadProviders,
    getProvider,
    addProvider,
    updateProvider,
    deleteProvider,
    switchProvider,
  }
})
