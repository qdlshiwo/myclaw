<template>
  <div class="settings-view">
    <h2>Settings</h2>
    <div class="card">
      <div class="field">
        <label>Model</label>
        <select v-model="config.model">
          <option v-for="m in models" :key="m.id" :value="m.id">{{ m.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>API Key</label>
        <input v-model="config.apiKey" type="password" placeholder="sk-..." />
      </div>
      <div class="field">
        <label>Base URL</label>
        <input v-model="config.baseUrl" type="text" placeholder="https://api.openai.com" />
      </div>
      <div class="actions">
        <button @click="saveConfig" :disabled="saving">{{ saving ? 'Saving...' : 'Save' }}</button>
        <span v-if="saved" class="saved">Saved!</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useGatewayStore } from '@/stores/gatewayStore'

const gatewayStore = useGatewayStore()

const config = ref({ model: 'gpt-4o-mini', apiKey: '', baseUrl: 'https://api.openai.com' })
const models = ref<{ id: string; name: string }[]>([])
const saving = ref(false)
const saved = ref(false)

async function load() {
  await gatewayStore.loadConfig()
  const c = gatewayStore.config
  if (c) {
    config.value = { ...config.value, ...c }
  }
  const m = await gatewayStore.loadModels()
  if (m) {
    models.value = m
  }
}

async function saveConfig() {
  saving.value = true
  await gatewayStore.saveConfig(config.value)
  saving.value = false
  saved.value = true
  setTimeout(() => (saved.value = false), 1500)
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.settings-view {
  padding: 24px;
  max-width: 640px;
}
h2 {
  margin: 0 0 16px 0;
  font-size: 20px;
  color: #c9d1d9;
}
.card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 10px;
  padding: 20px;
}
.field {
  margin-bottom: 16px;
}
.field label {
  display: block;
  font-size: 13px;
  color: #8b949e;
  margin-bottom: 6px;
}
.field input,
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
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.actions button {
  background: #238636;
  color: #fff;
  border: none;
  padding: 10px 18px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}
.actions button:hover {
  background: #2ea043;
}
.actions button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.saved {
  color: #3fb950;
  font-size: 13px;
}
</style>
