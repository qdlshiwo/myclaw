import { ref } from 'vue'

const WS_URL = `ws://${location.host}/ws`
const RECONNECT_DELAY = 3000
const MAX_RECONNECT_ATTEMPTS = 10

export function useGateway() {
  const ws = ref<WebSocket | null>(null)
  const connected = ref(false)
  const connecting = ref(false)
  const error = ref<string | null>(null)

  let messageId = 0
  const pendingResolvers = new Map<string, { resolve: (v: any) => void; reject: (e: any) => void }>()
  const eventListeners = new Map<string, Set<(payload: any) => void>>()

  let reconnectAttempts = 0
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null

  function connect(url: string = WS_URL) {
    if (ws.value?.readyState === WebSocket.OPEN) return
    connecting.value = true
    error.value = null

    const socket = new WebSocket(url)
    ws.value = socket

    socket.onopen = () => {
      reconnectAttempts = 0
      // Send connect handshake
      sendRequest('connect', { deviceId: 'web-client', platform: 'web' })
        .then(() => {
          connected.value = true
          connecting.value = false
        })
        .catch((e) => {
          error.value = e.message
          connecting.value = false
        })
    }

    socket.onmessage = (e) => {
      try {
        const frame: import('../types/protocol').GatewayFrame = JSON.parse(e.data)
        if (frame.type === 'res' && frame.id && pendingResolvers.has(frame.id)) {
          const { resolve, reject } = pendingResolvers.get(frame.id)!
          pendingResolvers.delete(frame.id)
          if (frame.ok) resolve(frame.payload)
          else reject(new Error(frame.error?.message || 'Unknown error'))
        } else if (frame.type === 'event' && frame.event) {
          const listeners = eventListeners.get(frame.event)
          if (listeners) listeners.forEach((cb) => cb(frame.payload))
        }
      } catch (err) {
        console.error('Failed to parse WS message:', err)
      }
    }

    socket.onclose = () => {
      connected.value = false
      connecting.value = false
      ws.value = null
      scheduleReconnect(url)
    }

    socket.onerror = (e) => {
      error.value = 'WebSocket connection failed, will retry...'
      connecting.value = false
    }
  }

  function scheduleReconnect(url: string) {
    if (reconnectTimer) return
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      error.value = `Failed to connect after ${MAX_RECONNECT_ATTEMPTS} attempts`
      return
    }
    reconnectAttempts++
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect(url)
    }, RECONNECT_DELAY)
  }

  function sendRequest(method: string, params: any): Promise<any> {
    return new Promise((resolve, reject) => {
      if (!ws.value || ws.value.readyState !== WebSocket.OPEN) {
        reject(new Error('WebSocket not connected'))
        return
      }
      const id = `req-${++messageId}`
      pendingResolvers.set(id, { resolve, reject })
      ws.value.send(JSON.stringify({ type: 'req', id, method, params }))
    })
  }

  function onEvent(event: string, callback: (payload: any) => void) {
    if (!eventListeners.has(event)) eventListeners.set(event, new Set())
    eventListeners.get(event)!.add(callback)
    return () => eventListeners.get(event)?.delete(callback)
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    ws.value?.close()
  }

  return {
    ws,
    connected,
    connecting,
    error,
    connect,
    disconnect,
    sendRequest,
    onEvent,
  }
}
