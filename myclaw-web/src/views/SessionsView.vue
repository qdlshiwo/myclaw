<template>
  <div class="sessions-view">
    <div class="header">
      <h2>Sessions</h2>
      <button class="btn danger" @click="deleteSelected" :disabled="selectedKeys.size === 0 || deleting">
        Delete Selected ({{ selectedKeys.size }})
      </button>
    </div>

    <!-- Search bar -->
    <div class="search-bar">
      <input
        v-model="searchQuery"
        @keyup.enter="doSearch"
        placeholder="Search by session key, agent ID, or message content..."
        class="search-input"
      />
      <button class="btn" @click="doSearch">Search</button>
    </div>

    <!-- Session list -->
    <div class="session-list">
      <div class="table-header">
        <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" class="cb" />
        <span class="th key">Session Key</span>
        <span class="th agent">Agent</span>
        <span class="th msgs">Messages</span>
        <span class="th last-interaction">Last Activity</span>
      </div>

      <div
        v-for="s in sessions"
        :key="s.sessionKey"
        class="session-row"
        :class="{ selected: selectedKeys.has(s.sessionKey) }"
        @click="switchSession(s.sessionKey)"
      >
        <input
          type="checkbox"
          :checked="selectedKeys.has(s.sessionKey)"
          @click.stop="toggleSelect(s.sessionKey)"
          class="cb"
        />
        <span class="td key" :title="s.sessionKey">{{ s.sessionKey }}</span>
        <span class="td agent">{{ s.agentId }}</span>
        <span class="td msgs">{{ s.messageCount }}</span>
        <span class="td last-interaction">{{ formatDateTime(s.lastInteractionAt) }}</span>
      </div>

      <div v-if="sessions.length === 0 && !loading" class="empty">
        No sessions found
      </div>
      <div v-if="loading" class="loading">Loading...</div>
    </div>

    <!-- Pagination -->
    <div class="pagination" v-if="totalPages > 1">
      <button class="btn" @click="goPage(page - 1)" :disabled="page <= 0">&laquo; Prev</button>
      <span class="page-info">Page {{ page + 1 }} of {{ totalPages }} ({{ total }} total)</span>
      <button class="btn" @click="goPage(page + 1)" :disabled="page >= totalPages - 1">Next &raquo;</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useGatewayStore } from '@/stores/gatewayStore'

const gatewayStore = useGatewayStore()
const router = useRouter()

interface SessionInfo {
  sessionId: string
  sessionKey: string
  agentId: string
  messageCount: number
  createdAt?: string
  lastInteractionAt?: string
}

const sessions = ref<SessionInfo[]>([])
const page = ref(0)
const pageSize = 10
const total = ref(0)
const totalPages = ref(1)
const searchQuery = ref('')
const loading = ref(false)
const deleting = ref(false)
const selectedKeys = ref<Set<string>>(new Set())

const allSelected = computed(() => sessions.value.length > 0 && selectedKeys.value.size === sessions.value.length)

async function loadSessions() {
  loading.value = true
  try {
    const data = await (gatewayStore as any).sendRequest('sessions', {
      action: 'list',
      page: page.value,
      pageSize,
      search: searchQuery.value || undefined,
    })
    if (data) {
      sessions.value = data.sessions || []
      total.value = data.total || 0
      totalPages.value = data.totalPages || 1
    }
  } catch (e) {
    console.warn('Failed to load sessions', e)
  } finally {
    loading.value = false
  }
}

function doSearch() {
  page.value = 0
  selectedKeys.value.clear()
  loadSessions()
}

function goPage(p: number) {
  page.value = p
  selectedKeys.value.clear()
  loadSessions()
}

function toggleSelect(key: string) {
  const s = new Set(selectedKeys.value)
  if (s.has(key)) s.delete(key)
  else s.add(key)
  selectedKeys.value = s
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedKeys.value.clear()
  } else {
    selectedKeys.value = new Set(sessions.value.map(s => s.sessionKey))
  }
}

async function deleteSelected() {
  if (selectedKeys.value.size === 0) return
  if (!confirm(`Delete ${selectedKeys.value.size} session(s)? This cannot be undone.`)) return

  deleting.value = true
  try {
    const keys = Array.from(selectedKeys.value)
    await (gatewayStore as any).sendRequest('sessions', {
      action: 'delete',
      sessionKeys: keys,
    })
    selectedKeys.value.clear()
    await loadSessions()
  } catch (e) {
    console.error('Delete failed', e)
    alert('Delete failed: ' + (e as Error).message)
  } finally {
    deleting.value = false
  }
}

function switchSession(key: string) {
  gatewayStore.switchSession(key)
  router.push('/chat')
}

function formatDateTime(d?: string) {
  if (!d) return '—'
  try {
    return new Date(d).toLocaleString()
  } catch {
    return d
  }
}

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.sessions-view {
  padding: 24px;
  max-width: 1200px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
h2 {
  margin: 0;
  font-size: 20px;
  color: #c9d1d9;
}
.btn {
  background: #21262d;
  border: 1px solid #30363d;
  color: #c9d1d9;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;
}
.btn:hover { background: #30363d; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn.danger {
  background: #da3633;
  border-color: #f85149;
  color: #fff;
}
.btn.danger:hover { background: #f85149; }

.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.search-input {
  flex: 1;
  background: #0d1117;
  border: 1px solid #30363d;
  border-radius: 6px;
  padding: 8px 12px;
  color: #c9d1d9;
  font-size: 14px;
  outline: none;
}
.search-input:focus {
  border-color: #58a6ff;
}
.search-input::placeholder {
  color: #484f58;
}

.session-list {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 8px;
  overflow: hidden;
}
.table-header {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  background: #0d1117;
  font-size: 12px;
  color: #8b949e;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid #30363d;
}
.session-row {
  display: flex;
  align-items: center;
  padding: 12px 14px;
  cursor: pointer;
  transition: background 0.1s;
  border-bottom: 1px solid #21262d;
}
.session-row:last-child { border-bottom: none; }
.session-row:hover { background: #1c2128; }
.session-row.selected { background: #1a2332; }
.cb {
  margin-right: 12px;
  cursor: pointer;
  accent-color: #58a6ff;
}
.td, .th {
  font-size: 13px;
  color: #c9d1d9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.key { flex: 2; font-weight: 600; }
.agent { flex: 1; color: #8b949e; }
.msgs { flex: 0.5; text-align: center; }
.last-interaction { flex: 1.5; text-align: right; }
.empty, .loading {
  padding: 40px 0;
  text-align: center;
  color: #8b949e;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
}
.page-info {
  font-size: 13px;
  color: #8b949e;
}
</style>
