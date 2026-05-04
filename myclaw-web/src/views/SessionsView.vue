<template>
  <div class="sessions-view">
    <h2>Sessions</h2>
    <div class="session-list">
      <div
        v-for="s in sessions"
        :key="s.sessionKey"
        class="session-card"
        :class="{ active: s.sessionKey === currentSession }"
        @click="switchSession(s.sessionKey)"
      >
        <div class="session-title">{{ s.sessionKey }}</div>
        <div class="session-meta">
          <span>{{ s.messageCount }} messages</span>
          <span v-if="s.lastInteractionAt">{{ formatDate(s.lastInteractionAt) }}</span>
        </div>
      </div>
      <div v-if="sessions.length === 0" class="empty">No sessions yet</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useGatewayStore } from '@/stores/gatewayStore'

const gatewayStore = useGatewayStore()

interface SessionInfo {
  sessionId: string
  sessionKey: string
  agentId: string
  messageCount: number
  createdAt?: string
  lastInteractionAt?: string
}

const sessions = ref<SessionInfo[]>([])
const currentSession = ref('main')

async function load() {
  const data = await gatewayStore.listSessions()
  if (data && data.sessions) {
    sessions.value = data.sessions
  }
}

function switchSession(key: string) {
  currentSession.value = key
  gatewayStore.switchSession(key)
}

function formatDate(d: string) {
  try {
    return new Date(d).toLocaleString()
  } catch {
    return d
  }
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.sessions-view {
  padding: 24px;
}
h2 {
  margin: 0 0 16px 0;
  font-size: 20px;
  color: #c9d1d9;
}
.session-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.session-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 8px;
  padding: 14px 16px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.session-card:hover,
.session-card.active {
  border-color: #58a6ff;
}
.session-title {
  font-weight: 600;
  color: #c9d1d9;
  margin-bottom: 6px;
}
.session-meta {
  font-size: 12px;
  color: #8b949e;
  display: flex;
  gap: 12px;
}
.empty {
  color: #8b949e;
  font-size: 14px;
  padding: 20px 0;
}
</style>
