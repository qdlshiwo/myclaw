<template>
  <div class="settings-view">
    <h2>Model Providers</h2>

    <!-- Provider List -->
    <div class="provider-list">
      <div
        v-for="p in gatewayStore.providers"
        :key="p.id"
        class="provider-card"
        :class="{ active: p.id === gatewayStore.currentProviderId }"
      >
        <div class="provider-header">
          <div class="provider-name">
            <span class="name">{{ p.name }}</span>
            <span v-if="p.id === gatewayStore.currentProviderId" class="badge">Current</span>
            <span v-if="!p.enabled" class="badge disabled">Disabled</span>
          </div>
          <div class="provider-actions">
            <button
              v-if="p.id !== gatewayStore.currentProviderId"
              class="btn-switch"
              @click="switchProvider(p.id)"
              :disabled="switchingId === p.id"
            >
              {{ switchingId === p.id ? 'Switching...' : 'Switch' }}
            </button>
            <button class="btn-edit" @click="editProvider(p)">Edit</button>
            <button
              class="btn-delete"
              @click="removeProvider(p.id)"
              :disabled="gatewayStore.providers.length <= 1"
            >
              Delete
            </button>
          </div>
        </div>
        <div class="provider-meta">
          <div class="meta-item">
            <span class="meta-label">Base URL</span>
            <span class="meta-value">{{ p.baseUrl || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">API Format</span>
            <span class="meta-value">{{ p.apiFormat }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">Current Model</span>
            <span class="meta-value">{{ p.currentModel }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">Models</span>
            <span class="meta-value">{{ p.models?.length || 0 }}</span>
          </div>
        </div>
      </div>

      <button class="btn-add" @click="startAdd">
        <span class="plus">+</span> Add Provider
      </button>
    </div>

    <!-- Provider Edit Dialog -->
    <div v-if="editing" class="dialog-overlay" @click.self="cancelEdit">
      <div class="dialog">
        <h3>{{ isAdding ? 'Add Provider' : 'Edit Provider' }}</h3>

        <div class="dialog-body">
          <div class="field">
            <label>Name</label>
            <input v-model="editForm.name" type="text" placeholder="e.g. OpenAI" />
          </div>

          <div class="field">
            <label>Base URL</label>
            <input v-model="editForm.baseUrl" type="text" placeholder="https://api.openai.com" />
          </div>

          <div class="field">
            <label>API Key</label>
            <input v-model="editForm.apiKey" type="password" placeholder="sk-..." />
          </div>

          <div class="field">
            <label>API Format</label>
            <select v-model="editForm.apiFormat">
              <option value="openai">OpenAI</option>
              <option value="anthropic">Anthropic</option>
            </select>
          </div>

          <div class="field">
            <label>Current Model</label>
            <input v-model="editForm.currentModel" type="text" placeholder="gpt-4o-mini" />
          </div>

          <div class="field checkbox">
            <label>
              <input v-model="editForm.enabled" type="checkbox" />
              Enabled
            </label>
          </div>

          <div class="field models-field">
            <label>Models</label>
            <div class="model-list">
              <div v-for="(m, idx) in editForm.models" :key="idx" class="model-row">
                <input v-model="m.id" type="text" placeholder="model-id" />
                <input v-model="m.name" type="text" placeholder="Display Name" />
                <button class="btn-row-delete" @click="removeModel(idx)">x</button>
              </div>
              <button class="btn-row-add" @click="addModel">+ Add Model</button>
            </div>
          </div>
        </div>

        <div class="dialog-footer">
          <button class="btn-cancel" @click="cancelEdit">Cancel</button>
          <button class="btn-save" @click="saveProvider" :disabled="saving">
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useGatewayStore } from '@/stores/gatewayStore'
import type { AiProvider, ModelInfo } from '@/types/protocol'

const gatewayStore = useGatewayStore()

const editing = ref(false)
const isAdding = ref(false)
const saving = ref(false)
const switchingId = ref('')

const emptyForm = (): Partial<AiProvider> & { models: ModelInfo[] } => ({
  id: '',
  name: '',
  baseUrl: '',
  apiKey: '',
  apiFormat: 'openai',
  currentModel: '',
  enabled: true,
  models: [],
})

const editForm = ref(emptyForm())

async function load() {
  await gatewayStore.loadProviders()
}

function startAdd() {
  isAdding.value = true
  editForm.value = emptyForm()
  editing.value = true
}

function editProvider(p: AiProvider) {
  isAdding.value = false
  editForm.value = {
    ...p,
    models: p.models ? p.models.map((m) => ({ ...m })) : [],
  }
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

function addModel() {
  editForm.value.models.push({ id: '', name: '' })
}

function removeModel(idx: number) {
  editForm.value.models.splice(idx, 1)
}

async function saveProvider() {
  saving.value = true
  const payload = {
    ...editForm.value,
    models: editForm.value.models.filter((m) => m.id.trim() !== ''),
  } as AiProvider

  try {
    if (isAdding.value) {
      await gatewayStore.addProvider(payload)
    } else {
      await gatewayStore.updateProvider(payload)
    }
    editing.value = false
  } finally {
    saving.value = false
  }
}

async function switchProvider(id: string) {
  switchingId.value = id
  try {
    await gatewayStore.switchProvider(id)
  } finally {
    switchingId.value = ''
  }
}

async function removeProvider(id: string) {
  if (!confirm('Are you sure you want to delete this provider?')) return
  await gatewayStore.deleteProvider(id)
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.settings-view {
  padding: 24px;
  max-width: 800px;
}

h2 {
  margin: 0 0 20px 0;
  font-size: 20px;
  color: #c9d1d9;
}

.provider-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.provider-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 10px;
  padding: 16px;
  transition: border-color 0.2s;
}

.provider-card.active {
  border-color: #58a6ff;
  box-shadow: 0 0 0 1px #58a6ff33;
}

.provider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.provider-name {
  display: flex;
  align-items: center;
  gap: 10px;
}

.provider-name .name {
  font-size: 16px;
  font-weight: 600;
  color: #c9d1d9;
}

.badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 12px;
  background: #238636;
  color: #fff;
}

.badge.disabled {
  background: #484f58;
}

.provider-actions {
  display: flex;
  gap: 8px;
}

.provider-actions button {
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: opacity 0.2s;
}

.provider-actions button:hover {
  opacity: 0.85;
}

.provider-actions button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-switch {
  background: #1f6feb;
  color: #fff;
}

.btn-edit {
  background: #3b3b3b;
  color: #c9d1d9;
  border: 1px solid #484f58 !important;
}

.btn-delete {
  background: #da3633;
  color: #fff;
}

.provider-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-label {
  font-size: 11px;
  color: #8b949e;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.meta-value {
  font-size: 13px;
  color: #c9d1d9;
  word-break: break-all;
}

.btn-add {
  margin-top: 8px;
  padding: 12px;
  background: #161b22;
  border: 1px dashed #30363d;
  border-radius: 10px;
  color: #8b949e;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;
}

.btn-add:hover {
  border-color: #58a6ff;
  color: #58a6ff;
}

.plus {
  font-size: 18px;
  line-height: 1;
}

/* Dialog */
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.dialog {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  width: 520px;
  max-height: 85vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dialog h3 {
  margin: 0;
  padding: 16px 20px;
  font-size: 16px;
  color: #c9d1d9;
  border-bottom: 1px solid #21262d;
}

.dialog-body {
  padding: 20px;
  overflow-y: auto;
}

.field {
  margin-bottom: 14px;
}

.field label {
  display: block;
  font-size: 12px;
  color: #8b949e;
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.field.checkbox label {
  display: flex;
  align-items: center;
  gap: 8px;
  text-transform: none;
  font-size: 14px;
  color: #c9d1d9;
  cursor: pointer;
}

.field input[type='text'],
.field input[type='password'],
.field select {
  width: 100%;
  background: #0d1117;
  border: 1px solid #30363d;
  color: #c9d1d9;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.field input:focus,
.field select:focus {
  border-color: #58a6ff;
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.model-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.model-row input {
  flex: 1;
}

.btn-row-delete {
  background: #da3633;
  color: #fff;
  border: none;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  flex-shrink: 0;
}

.btn-row-add {
  background: transparent;
  border: 1px dashed #30363d;
  color: #8b949e;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.btn-row-add:hover {
  border-color: #58a6ff;
  color: #58a6ff;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid #21262d;
}

.dialog-footer button {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  border: none;
}

.btn-cancel {
  background: #21262d;
  color: #c9d1d9;
  border: 1px solid #30363d !important;
}

.btn-save {
  background: #238636;
  color: #fff;
}

.btn-save:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
