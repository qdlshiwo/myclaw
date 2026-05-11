<template>
  <div class="cron-view">
    <div class="header">
      <h2>Cron Jobs</h2>
      <button class="btn primary" @click="showAddModal = true">+ New Job</button>
    </div>

    <!-- Job list -->
    <div class="job-list">
      <div class="table-header">
        <span class="th enabled"></span>
        <span class="th name">Name</span>
        <span class="th schedule">Schedule</span>
        <span class="th payload">Payload</span>
        <span class="th last-run">Last Run</span>
        <span class="th status">Status</span>
        <span class="th actions">Actions</span>
      </div>

      <div v-for="job in jobs" :key="job.id" class="job-row" :class="{ disabled: !job.enabled }">
        <span class="td enabled">
          <span class="status-dot" :class="{ active: job.enabled }"></span>
        </span>
        <span class="td name" :title="job.name">{{ job.name }}</span>
        <span class="td schedule">{{ formatSchedule(job) }}</span>
        <span class="td payload">{{ job.payload?.kind || '—' }}</span>
        <span class="td last-run">{{ formatTime(job.lastRunAt) }}</span>
        <span class="td status">
          <span class="badge" :class="job.lastRunStatus || ''">{{ job.lastRunStatus || '—' }}</span>
        </span>
        <span class="td actions">
          <button class="btn sm" @click="toggleJob(job)" :title="job.enabled ? 'Disable' : 'Enable'">
            {{ job.enabled ? '⏸' : '▶' }}
          </button>
          <button class="btn sm" @click="runJobNow(job)" title="Run now">▶ Run</button>
          <button class="btn sm danger" @click="deleteJob(job)" title="Delete">✕</button>
        </span>
      </div>

      <div v-if="jobs.length === 0 && !loading" class="empty">
        No cron jobs yet. Click "+ New Job" to create one.
      </div>
      <div v-if="loading" class="loading">Loading...</div>
    </div>

    <!-- Add/Edit Modal -->
    <div v-if="showAddModal" class="modal-overlay" @click.self="showAddModal = false">
      <div class="modal">
        <h3>New Cron Job</h3>
        <div class="form-group">
          <label>Name</label>
          <input v-model="newJob.name" placeholder="e.g. Daily Report" class="input" />
        </div>
        <div class="form-group">
          <label>Description</label>
          <input v-model="newJob.description" placeholder="Optional description" class="input" />
        </div>
        <div class="form-group">
          <label>Schedule Type</label>
          <select v-model="newJob.scheduleKind" class="input">
            <option value="cron">Cron Expression</option>
            <option value="every">Fixed Interval</option>
            <option value="at">One-shot (At)</option>
          </select>
        </div>
        <div class="form-group" v-if="newJob.scheduleKind === 'cron'">
          <label>Cron Expression</label>
          <input v-model="newJob.scheduleExpr" placeholder="0 9 * * *" class="input" />
          <small class="hint">minute hour day month dow (e.g. 0 9 * * * = daily at 9am)</small>
        </div>
        <div class="form-group" v-if="newJob.scheduleKind === 'cron'">
          <label>Timezone</label>
          <input v-model="newJob.scheduleTz" placeholder="Asia/Shanghai" class="input" />
        </div>
        <div class="form-group" v-if="newJob.scheduleKind === 'every'">
          <label>Interval (minutes)</label>
          <input v-model.number="newJob.everyMinutes" type="number" min="1" class="input" />
        </div>
        <div class="form-group" v-if="newJob.scheduleKind === 'at'">
          <label>Run At (ISO timestamp)</label>
          <input v-model="newJob.scheduleAt" placeholder="2026-05-12T09:00:00Z" class="input" />
        </div>
        <div class="form-group">
          <label>Payload Type</label>
          <select v-model="newJob.payloadKind" class="input">
            <option value="agentTurn">Agent Turn</option>
            <option value="systemEvent">System Event</option>
          </select>
        </div>
        <div class="form-group" v-if="newJob.payloadKind === 'agentTurn'">
          <label>Message</label>
          <textarea v-model="newJob.payloadMessage" rows="3" placeholder="Message for the agent..." class="input"></textarea>
        </div>
        <div class="form-group" v-if="newJob.payloadKind === 'systemEvent'">
          <label>Text</label>
          <input v-model="newJob.payloadText" placeholder="System event text" class="input" />
        </div>
        <div class="form-group">
          <label>Model (optional)</label>
          <input v-model="newJob.payloadModel" placeholder="Leave empty to use default" class="input" />
        </div>
        <div class="modal-actions">
          <button class="btn" @click="showAddModal = false">Cancel</button>
          <button class="btn primary" @click="createJob" :disabled="!newJob.name">Create</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useGatewayStore } from '@/stores/gatewayStore'

const gatewayStore = useGatewayStore()

interface CronJob {
  id: string
  name: string
  description?: string
  enabled: boolean
  agentId: string
  sessionKey: string
  sessionTarget: string
  schedule?: { kind: string; expr?: string; tz?: string; at?: string; everyMs?: number }
  payload?: { kind: string; message?: string; text?: string; model?: string }
  lastRunAt?: string
  lastRunStatus?: string
  consecutiveErrors: number
  createdAt?: string
}

const jobs = ref<CronJob[]>([])
const loading = ref(false)
const showAddModal = ref(false)

const newJob = ref({
  name: '',
  description: '',
  scheduleKind: 'cron',
  scheduleExpr: '0 9 * * *',
  scheduleTz: 'Asia/Shanghai',
  scheduleAt: '',
  everyMinutes: 60,
  payloadKind: 'agentTurn',
  payloadMessage: '',
  payloadText: '',
  payloadModel: '',
})

async function loadJobs() {
  loading.value = true
  try {
    const data = await (gatewayStore as any).sendRequest('cron', { action: 'list' })
    if (data && data.jobs) {
      jobs.value = data.jobs
    }
  } catch (e) {
    console.warn('Failed to load cron jobs', e)
  } finally {
    loading.value = false
  }
}

async function createJob() {
  const job = buildJob()
  try {
    await (gatewayStore as any).sendRequest('cron', { action: 'add', job })
    showAddModal.value = false
    resetNewJob()
    await loadJobs()
  } catch (e) {
    alert('Failed to create job: ' + (e as Error).message)
  }
}

function buildJob(): any {
  const j = newJob.value
  const schedule: any = { kind: j.scheduleKind }
  if (j.scheduleKind === 'cron') {
    schedule.expr = j.scheduleExpr
    schedule.tz = j.scheduleTz
  } else if (j.scheduleKind === 'every') {
    schedule.everyMs = j.everyMinutes * 60 * 1000
  } else if (j.scheduleKind === 'at') {
    schedule.at = j.scheduleAt
  }

  const payload: any = { kind: j.payloadKind }
  if (j.payloadKind === 'agentTurn') {
    payload.message = j.payloadMessage
    if (j.payloadModel) payload.model = j.payloadModel
  } else {
    payload.text = j.payloadText
  }

  return { name: j.name, description: j.description, schedule, payload }
}

function resetNewJob() {
  newJob.value = {
    name: '', description: '', scheduleKind: 'cron', scheduleExpr: '0 9 * * *',
    scheduleTz: 'Asia/Shanghai', scheduleAt: '', everyMinutes: 60,
    payloadKind: 'agentTurn', payloadMessage: '', payloadText: '', payloadModel: '',
  }
}

async function toggleJob(job: CronJob) {
  try {
    await (gatewayStore as any).sendRequest('cron', { action: 'toggle', id: job.id })
    await loadJobs()
  } catch (e) {
    alert('Failed to toggle job: ' + (e as Error).message)
  }
}

async function runJobNow(job: CronJob) {
  try {
    await (gatewayStore as any).sendRequest('cron', { action: 'run', id: job.id })
    await loadJobs()
  } catch (e) {
    alert('Failed to run job: ' + (e as Error).message)
  }
}

async function deleteJob(job: CronJob) {
  if (!confirm(`Delete job "${job.name}"?`)) return
  try {
    await (gatewayStore as any).sendRequest('cron', { action: 'delete', id: job.id })
    await loadJobs()
  } catch (e) {
    alert('Failed to delete job: ' + (e as Error).message)
  }
}

function formatSchedule(job: CronJob) {
  if (!job.schedule) return '—'
  const s = job.schedule
  if (s.kind === 'cron') return `${s.expr || '(no expr)'} (${s.tz || 'UTC'})`
  if (s.kind === 'every') {
    const mins = (s.everyMs || 0) / 60000
    return `Every ${mins} min`
  }
  if (s.kind === 'at') return `At ${s.at || '(no time)'}`
  return s.kind
}

function formatTime(d?: string) {
  if (!d) return '—'
  try { return new Date(d).toLocaleString() } catch { return d }
}

onMounted(() => {
  loadJobs()
})
</script>

<style scoped>
.cron-view {
  padding: 24px;
  max-width: 1200px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
h2 { margin: 0; font-size: 20px; color: #c9d1d9; }
.btn {
  background: #21262d;
  border: 1px solid #30363d;
  color: #c9d1d9;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.btn:hover { background: #30363d; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn.primary { background: #238636; border-color: #2ea043; color: #fff; }
.btn.primary:hover { background: #2ea043; }
.btn.sm { padding: 4px 8px; font-size: 12px; }
.btn.danger { background: #da3633; border-color: #f85149; color: #fff; }
.btn.danger:hover { background: #f85149; }

.job-list {
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
.job-row {
  display: flex;
  align-items: center;
  padding: 12px 14px;
  border-bottom: 1px solid #21262d;
  transition: background 0.1s;
}
.job-row:last-child { border-bottom: none; }
.job-row:hover { background: #1c2128; }
.job-row.disabled { opacity: 0.5; }
.td, .th {
  font-size: 13px;
  color: #c9d1d9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.enabled { flex: 0.3; text-align: center; }
.name { flex: 1.5; font-weight: 600; }
.schedule { flex: 1.5; font-family: 'SF Mono', monospace; font-size: 12px; }
.payload { flex: 0.8; }
.last-run { flex: 1; }
.status { flex: 0.6; }
.actions { flex: 1.2; display: flex; gap: 6px; }
.status-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #484f58; display: inline-block;
}
.status-dot.active { background: #3fb950; }
.badge {
  font-size: 11px; padding: 2px 6px; border-radius: 4px;
  background: #21262d; color: #8b949e;
}
.badge.success { background: #1a3d2a; color: #3fb950; }
.badge.error { background: #3d1f1f; color: #f85149; }
.empty, .loading { padding: 40px 0; text-align: center; color: #8b949e; }

/* Modal */
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center;
  z-index: 100;
}
.modal {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 24px;
  width: 500px;
  max-height: 80vh;
  overflow-y: auto;
}
.modal h3 { margin: 0 0 16px 0; color: #c9d1d9; }
.form-group { margin-bottom: 12px; }
.form-group label {
  display: block; font-size: 12px; color: #8b949e;
  margin-bottom: 4px; text-transform: uppercase;
}
.input {
  width: 100%; background: #0d1117; border: 1px solid #30363d;
  border-radius: 6px; padding: 8px 12px; color: #c9d1d9;
  font-size: 14px; outline: none; box-sizing: border-box;
}
.input:focus { border-color: #58a6ff; }
.hint { color: #484f58; font-size: 11px; }
.modal-actions {
  display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px;
}
</style>
