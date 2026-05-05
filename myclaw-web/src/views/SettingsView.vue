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
          <div class="field-row">
            <div class="field">
              <label>Provider Name</label>
              <input v-model="editForm.name" type="text" placeholder="e.g. OpenAI" />
            </div>
            <div class="field">
              <label>Notes</label>
              <input v-model="editForm.notes" type="text" placeholder="e.g. Company account" />
            </div>
          </div>

          <div class="field">
            <label>Website URL</label>
            <input v-model="editForm.websiteUrl" type="text" placeholder="https://..." />
          </div>

          <div class="field">
            <label>API Key</label>
            <input v-model="editForm.apiKey" type="password" placeholder="sk-..." />
          </div>

          <div class="field">
            <label>Base URL (Request Address)</label>
            <input v-model="editForm.baseUrl" type="text" placeholder="https://api.openai.com" />
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

          <!-- Advanced Options -->
          <div class="advanced-section">
            <button type="button" class="advanced-toggle" @click="advancedExpanded = !advancedExpanded">
              <span class="arrow" :class="{ expanded: advancedExpanded }">&#9654;</span>
              Advanced Options
            </button>
            <div v-if="advancedExpanded" class="advanced-content">
              <div class="field">
                <label>API Format</label>
                <select v-model="editForm.apiFormat">
                  <option value="openai">OpenAI Chat Completions</option>
                  <option value="anthropic">Anthropic Messages</option>
                </select>
                <span class="hint">Select the API input format of the provider</span>
              </div>

              <!-- Model Mappings -->
              <div class="model-mappings">
                <div class="mappings-header">
                  <label>Model Mappings</label>
                  <button type="button" class="btn-quick-set" @click="quickSetModels" :disabled="!canQuickSet">
                    Quick Set
                  </button>
                </div>
                <span class="hint">Map model roles to actual provider model IDs</span>
                <div class="mappings-grid">
                  <div class="mapping-field">
                    <label>Main Model</label>
                    <input v-model="modelMappings.main" type="text" placeholder="model-id" />
                  </div>
                  <div class="mapping-field">
                    <label>Thinking Model</label>
                    <input v-model="modelMappings.thinking" type="text" placeholder="model-id" />
                  </div>
                  <div class="mapping-field">
                    <label>Haiku Default</label>
                    <input v-model="modelMappings.haiku" type="text" placeholder="model-id" />
                  </div>
                  <div class="mapping-field">
                    <label>Sonnet Default</label>
                    <input v-model="modelMappings.sonnet" type="text" placeholder="model-id" />
                  </div>
                  <div class="mapping-field">
                    <label>Opus Default</label>
                    <input v-model="modelMappings.opus" type="text" placeholder="model-id" />
                  </div>
                </div>
              </div>

              <div class="field models-field">
                <label>Available Models</label>
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
import { ref, onMounted, computed } from 'vue'
import { useGatewayStore } from '@/stores/gatewayStore'
import type { AiProvider, ModelInfo } from '@/types/protocol'

const gatewayStore = useGatewayStore()

const editing = ref(false)
const isAdding = ref(false)
const saving = ref(false)
const switchingId = ref('')
const advancedExpanded = ref(false)

const emptyForm = (): Partial<AiProvider> & { models: ModelInfo[] } => ({
  id: '',
  name: '',
  websiteUrl: '',
  notes: '',
  baseUrl: '',
  apiKey: '',
  apiFormat: 'openai',
  currentModel: '',
  enabled: true,
  models: [],
})

const editForm = ref(emptyForm())

const modelMappings = ref<Record<string, string>>({
  main: '',
  thinking: '',
  haiku: '',
  sonnet: '',
  opus: '',
})

const canQuickSet = computed(() => {
  return modelMappings.value.main || modelMappings.value.thinking || modelMappings.value.haiku || modelMappings.value.sonnet || modelMappings.value.opus
})

async function load() {
  await gatewayStore.loadProviders()
}

function startAdd() {
  isAdding.value = true
  editForm.value = emptyForm()
  modelMappings.value = { main: '', thinking: '', haiku: '', sonnet: '', opus: '' }
  advancedExpanded.value = false
  editing.value = true
}

function editProvider(p: AiProvider) {
  isAdding.value = false
  editForm.value = {
    ...p,
    models: p.models ? p.models.map((m) => ({ ...m })) : [],
  }
  const mappings = p.modelMappings || {}
  modelMappings.value = {
    main: mappings.main || '',
    thinking: mappings.thinking || '',
    haiku: mappings.haiku || '',
    sonnet: mappings.sonnet || '',
    opus: mappings.opus || '',
  }
  advancedExpanded.value = !!(p.apiFormat !== 'openai' || Object.values(modelMappings.value).some((v) => v))
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

function quickSetModels() {
  const value = modelMappings.value.main || modelMappings.value.thinking || modelMappings.value.haiku || modelMappings.value.sonnet || modelMappings.value.opus || ''
  if (!value) return
  modelMappings.value.main = modelMappings.value.main || value
  modelMappings.value.thinking = modelMappings.value.thinking || value
  modelMappings.value.haiku = modelMappings.value.haiku || value
  modelMappings.value.sonnet = modelMappings.value.sonnet || value
  modelMappings.value.opus = modelMappings.value.opus || value
}

async function saveProvider() {
  saving.value = true
  const payload = {
    ...editForm.value,
    models: editForm.value.models.filter((m) => m.id.trim() !== ''),
    modelMappings: Object.fromEntries(
      Object.entries(modelMappings.value).filter(([, v]) => v.trim() !== '')
    ),
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
  width: 580px;
  max-height: 90vh;
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

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
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

.hint {
  display: block;
  font-size: 11px;
  color: #8b949e;
  margin-top: 4px;
}

/* Advanced Section */
.advanced-section {
  margin-top: 8px;
  border: 1px solid #21262d;
  border-radius: 8px;
  overflow: hidden;
}

.advanced-toggle {
  width: 100%;
  padding: 10px 14px;
  background: #0d1117;
  border: none;
  color: #c9d1d9;
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  text-align: left;
}

.advanced-toggle:hover {
  background: #161b22;
}

.arrow {
  font-size: 10px;
  transition: transform 0.2s;
  display: inline-block;
}

.arrow.expanded {
  transform: rotate(90deg);
}

.advanced-content {
  padding: 14px;
  border-top: 1px solid #21262d;
}

/* Model Mappings */
.model-mappings {
  margin-bottom: 14px;
}

.mappings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.mappings-header label {
  font-size: 12px;
  color: #8b949e;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.btn-quick-set {
  background: #21262d;
  border: 1px solid #30363d;
  color: #c9d1d9;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.btn-quick-set:hover {
  border-color: #58a6ff;
  color: #58a6ff;
}

.btn-quick-set:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.mappings-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 8px;
}

.mapping-field label {
  display: block;
  font-size: 11px;
  color: #8b949e;
  margin-bottom: 4px;
}

.mapping-field input {
  width: 100%;
  background: #0d1117;
  border: 1px solid #30363d;
  color: #c9d1d9;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
}

.mapping-field input:focus {
  border-color: #58a6ff;
}

/* Models list */
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
