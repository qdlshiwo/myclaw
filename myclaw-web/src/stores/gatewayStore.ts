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
    await gateway.sendRequest('send', { content, sessionKey: 'main' })
  }

  async function runAgent(message: string) {
    ensureConnected()
    // Add user message locally for immediate feedback
    messages.value.push({ role: 'user', content: message })
    await gateway.sendRequest('agent', {
      message,
      sessionKey: 'main',
      model: 'gpt-4o-mini',
    })
  }

  async function loadChatHistory() {
    ensureConnected()
    const data = await gateway.sendRequest('chat', { sessionKey: 'main' })
    if (data.messages) {
      messages.value = data.messages
    }
  }

  return {
    isConnected,
    messages,
    streaming,
    currentDelta,
    connect: gateway.connect,
    disconnect: gateway.disconnect,
    sendMessage,
    runAgent,
    loadChatHistory,
  }
})
