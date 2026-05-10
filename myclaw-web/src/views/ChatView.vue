<template>
  <div class="chat-view">
    <!-- Session Sidebar -->
    <aside class="session-sidebar">
      <div class="sidebar-header">
        <button class="new-session-btn" @click="createNewSession" :disabled="gatewayStore.streaming">
          + New Session
        </button>
      </div>
      <div class="session-list">
        <div
          v-for="s in gatewayStore.sessions"
          :key="s.sessionKey"
          class="session-item"
          :class="{ active: s.sessionKey === gatewayStore.currentSession }"
          @click="switchTo(s.sessionKey)"
        >
          <div class="session-name">{{ s.sessionKey }}</div>
          <div class="session-meta">
            <span>{{ s.messageCount }} msg</span>
            <span v-if="s.lastInteractionAt">{{ formatDateShort(s.lastInteractionAt) }}</span>
          </div>
        </div>
        <div v-if="gatewayStore.sessions.length === 0" class="empty-sessions">
          No sessions yet
        </div>
      </div>
    </aside>

    <!-- Chat Area -->
    <div class="chat-area">
      <div class="header">
        <h2>{{ gatewayStore.currentSession }}</h2>
        <div class="header-actions">
          <button class="btn" @click="gatewayStore.loadChatHistory()" :disabled="!gatewayStore.isConnected || gatewayStore.streaming">
            Refresh
          </button>
        </div>
      </div>

      <div class="messages" ref="messagesRef">
        <div
          v-for="(msg, i) in gatewayStore.messages"
          :key="i"
          class="message"
          :class="[msg.role, { error: msg.isError }]"
        >
          <div class="meta">
            <span>{{ msg.role }}</span>
            <button
              v-if="msg.role === 'user'"
              class="resend-btn"
              title="Resend"
              @click="resend(msg.content)"
              :disabled="gatewayStore.streaming"
            >
              Retry
            </button>
          </div>
          <div class="content" v-html="renderMarkdown(msg.content)"></div>
        </div>

        <!-- Streaming delta -->
        <div v-if="gatewayStore.streaming && gatewayStore.currentDelta" class="message assistant">
          <div class="meta">assistant</div>
          <div class="content" v-html="renderMarkdown(gatewayStore.currentDelta)"></div>
          <span class="cursor"></span>
        </div>
      </div>

      <div class="input-area">
        <div v-if="!gatewayStore.isConnected" class="offline-bar">
          Waiting for server connection...
        </div>
        <div class="input-row">
          <textarea
            v-model="inputText"
            @keydown.enter.prevent="send"
            placeholder="Type a message..."
            rows="2"
            :disabled="!gatewayStore.isConnected"
          ></textarea>
          <button class="send-btn" @click="send" :disabled="!inputText.trim() || gatewayStore.streaming || !gatewayStore.isConnected">
            Send
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue'
import { useGatewayStore } from '@/stores/gatewayStore'
import { marked } from 'marked'

const gatewayStore = useGatewayStore()
const inputText = ref('')
const messagesRef = ref<HTMLDivElement>()

function renderMarkdown(text: string) {
  return marked.parse(text || '') as string
}

async function send() {
  const text = inputText.value.trim()
  if (!text || gatewayStore.streaming) return
  inputText.value = ''
  await gatewayStore.runAgent(text)
}

async function resend(content: string) {
  if (gatewayStore.streaming) return
  await gatewayStore.runAgent(content)
}

async function createNewSession() {
  await gatewayStore.newSession()
}

async function switchTo(key: string) {
  if (key === gatewayStore.currentSession) return
  await gatewayStore.switchSession(key)
}

function formatDateShort(d: string) {
  try {
    return new Date(d).toLocaleDateString()
  } catch {
    return d
  }
}

function addCopyButtons() {
  nextTick(() => {
    if (!messagesRef.value) return
    messagesRef.value.querySelectorAll('.content pre').forEach((pre) => {
      const el = pre as HTMLElement
      if (el.dataset.copyBtn === '1') return
      const btn = document.createElement('button')
      btn.className = 'copy-btn'
      btn.textContent = 'Copy'
      btn.onclick = () => {
        const code = el.querySelector('code')?.textContent || ''
        navigator.clipboard.writeText(code).then(() => {
          btn.textContent = 'Copied!'
          setTimeout(() => (btn.textContent = 'Copy'), 2000)
        })
      }
      el.style.position = 'relative'
      el.dataset.copyBtn = '1'
      el.appendChild(btn)
    })
  })
}

// Auto scroll & copy buttons
watch(() => gatewayStore.messages.length, () => {
  nextTick(() => {
    messagesRef.value?.scrollTo({ top: messagesRef.value.scrollHeight, behavior: 'smooth' })
  })
  addCopyButtons()
})
watch(() => gatewayStore.currentDelta, () => {
  nextTick(() => {
    messagesRef.value?.scrollTo({ top: messagesRef.value.scrollHeight, behavior: 'smooth' })
  })
  addCopyButtons()
})

onMounted(() => {
  // Auto-restore last session when connected
  gatewayStore.autoRestoreSession()
})
</script>

<style scoped>
.chat-view {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* Session Sidebar */
.session-sidebar {
  width: 200px;
  background: #161b22;
  border-right: 1px solid #30363d;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #30363d;
}
.new-session-btn {
  width: 100%;
  background: #238636;
  border: none;
  color: #fff;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}
.new-session-btn:hover { background: #2ea043; }
.new-session-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.session-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  border: 1px solid transparent;
}
.session-item:hover {
  background: #21262d;
}
.session-item.active {
  background: #21262d;
  border-color: #58a6ff;
}
.session-name {
  font-size: 13px;
  font-weight: 600;
  color: #c9d1d9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-meta {
  font-size: 11px;
  color: #8b949e;
  display: flex;
  gap: 8px;
  margin-top: 2px;
}
.empty-sessions {
  color: #8b949e;
  font-size: 12px;
  padding: 20px 0;
  text-align: center;
}

/* Chat Area */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #30363d;
  flex-shrink: 0;
}
.header h2 {
  font-size: 16px;
  font-weight: 600;
  color: #c9d1d9;
}
.btn {
  background: #21262d;
  border: 1px solid #30363d;
  color: #c9d1d9;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.btn:hover { background: #30363d; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.message {
  max-width: 85%;
}
.message.user {
  align-self: flex-end;
}
.message.assistant {
  align-self: flex-start;
}
.message.error .content {
  background: #3d1f1f;
  border-color: #f85149;
  color: #f85149;
}
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #8b949e;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.resend-btn {
  background: transparent;
  border: 1px solid #30363d;
  color: #8b949e;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 10px;
  text-transform: none;
  letter-spacing: 0;
}
.resend-btn:hover {
  background: #30363d;
  color: #c9d1d9;
}
.resend-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.content {
  background: #21262d;
  border: 1px solid #30363d;
  border-radius: 10px;
  padding: 12px 16px;
  line-height: 1.6;
  font-size: 14px;
}
.message.user .content {
  background: #1f4d7a;
  border-color: #2a6fbd;
}
.content :deep(p) { margin: 0 0 8px; }
.content :deep(p:last-child) { margin-bottom: 0; }
.content :deep(pre) {
  background: #0d1117;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}
.content :deep(code) {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 12px;
}
.content :deep(.copy-btn) {
  position: absolute;
  top: 6px;
  right: 6px;
  background: #21262d;
  border: 1px solid #30363d;
  color: #c9d1d9;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.15s;
}
.content :deep(pre:hover .copy-btn) {
  opacity: 1;
}
.content :deep(.copy-btn:hover) {
  background: #30363d;
}
.cursor {
  display: inline-block;
  width: 2px;
  height: 16px;
  background: #58a6ff;
  margin-left: 4px;
  animation: blink 1s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.input-area {
  padding: 12px 20px;
  border-top: 1px solid #30363d;
  background: #161b22;
  flex-shrink: 0;
}
.offline-bar {
  background: #3d1f1f;
  color: #f85149;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  margin-bottom: 8px;
  text-align: center;
}
.input-row {
  display: flex;
  gap: 10px;
}
.input-area textarea {
  flex: 1;
  background: #0d1117;
  border: 1px solid #30363d;
  border-radius: 10px;
  padding: 10px 14px;
  color: #c9d1d9;
  font-size: 14px;
  resize: none;
  outline: none;
}
.input-area textarea:focus {
  border-color: #58a6ff;
}
.input-area textarea:disabled {
  opacity: 0.5;
}
.send-btn {
  background: #238636;
  border: none;
  color: #fff;
  padding: 10px 20px;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 600;
}
.send-btn:hover { background: #2ea043; }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
