<template>
  <div class="chat-view">
    <div class="header">
      <h2>Chat</h2>
      <button class="btn" @click="gatewayStore.loadChatHistory()" :disabled="!gatewayStore.isConnected">
        Refresh
      </button>
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
      <textarea
        v-model="inputText"
        @keydown.enter.prevent="send"
        placeholder="Type a message..."
        rows="2"
      ></textarea>
      <button class="send-btn" @click="send" :disabled="!inputText.trim() || gatewayStore.streaming">
        Send
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
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
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #30363d;
}
.header h2 {
  font-size: 16px;
  font-weight: 600;
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
  display: flex;
  gap: 10px;
  padding: 12px 20px;
  border-top: 1px solid #30363d;
  background: #161b22;
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
