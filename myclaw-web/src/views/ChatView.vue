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
        :class="msg.role"
      >
        <div class="meta">{{ msg.role }}</div>
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

// Auto scroll
watch(() => gatewayStore.messages.length, () => {
  nextTick(() => {
    messagesRef.value?.scrollTo({ top: messagesRef.value.scrollHeight, behavior: 'smooth' })
  })
})
watch(() => gatewayStore.currentDelta, () => {
  nextTick(() => {
    messagesRef.value?.scrollTo({ top: messagesRef.value.scrollHeight, behavior: 'smooth' })
  })
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
.meta {
  font-size: 11px;
  color: #8b949e;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
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
