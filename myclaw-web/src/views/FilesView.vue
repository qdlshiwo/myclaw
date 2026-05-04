<template>
  <div class="files-view">
    <h2>Files</h2>
    <div class="breadcrumb">
      <span class="crumb" @click="navigateTo('')">workspace</span>
      <span v-for="(part, i) in pathParts" :key="i" class="crumb-sep">/</span>
      <span v-for="(part, i) in pathParts" :key="i" class="crumb" @click="navigateTo(pathParts.slice(0, i + 1).join('/'))">{{ part }}</span>
    </div>
    <div class="file-list">
      <div v-for="entry in entries" :key="entry.name" class="file-row" @click="openEntry(entry)">
        <span class="icon">{{ entry.isDirectory ? '📁' : '📄' }}</span>
        <span class="name">{{ entry.name }}</span>
        <span v-if="!entry.isDirectory" class="size">{{ formatSize(entry.size) }}</span>
      </div>
      <div v-if="entries.length === 0" class="empty">Empty directory</div>
    </div>
    <div v-if="fileContent !== null" class="file-preview">
      <div class="preview-header">
        <span>{{ currentFileName }}</span>
        <button @click="fileContent = null">Close</button>
      </div>
      <pre>{{ fileContent }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useGatewayStore } from '@/stores/gatewayStore'

const gatewayStore = useGatewayStore()

const currentPath = ref('')
const entries = ref<{ name: string; isDirectory: boolean; size: number }[]>([])
const fileContent = ref<string | null>(null)
const currentFileName = ref('')

const pathParts = computed(() => {
  return currentPath.value.split('/').filter(Boolean)
})

async function load() {
  const data = await gatewayStore.loadFileList(currentPath.value)
  if (data && data.entries) {
    entries.value = data.entries
  }
}

function navigateTo(path: string) {
  currentPath.value = path
  fileContent.value = null
  load()
}

async function openEntry(entry: { name: string; isDirectory: boolean }) {
  const fullPath = currentPath.value ? `${currentPath.value}/${entry.name}` : entry.name
  if (entry.isDirectory) {
    navigateTo(fullPath)
  } else {
    const data = await gatewayStore.readFile(fullPath)
    if (data && data.content !== undefined) {
      fileContent.value = data.content
      currentFileName.value = entry.name
    }
  }
}

function formatSize(n: number) {
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.files-view {
  padding: 24px;
  display: flex;
  flex-direction: column;
  height: 100%;
}
h2 {
  margin: 0 0 12px 0;
  font-size: 20px;
  color: #c9d1d9;
}
.breadcrumb {
  font-size: 13px;
  color: #8b949e;
  margin-bottom: 12px;
}
.crumb {
  cursor: pointer;
  color: #58a6ff;
}
.crumb:hover {
  text-decoration: underline;
}
.crumb-sep {
  margin: 0 4px;
}
.file-list {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 10px;
  overflow: auto;
  flex: 1;
}
.file-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid #21262d;
  cursor: pointer;
  transition: background 0.1s;
}
.file-row:hover {
  background: #21262d;
}
.file-row:last-child {
  border-bottom: none;
}
.icon {
  font-size: 14px;
}
.name {
  flex: 1;
  font-size: 14px;
  color: #c9d1d9;
}
.size {
  font-size: 12px;
  color: #8b949e;
}
.empty {
  padding: 20px;
  color: #8b949e;
  font-size: 14px;
}
.file-preview {
  margin-top: 16px;
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 10px;
  overflow: hidden;
  max-height: 40%;
  display: flex;
  flex-direction: column;
}
.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #21262d;
  font-size: 13px;
  color: #c9d1d9;
}
.preview-header button {
  background: #30363d;
  border: 1px solid #484f58;
  color: #c9d1d9;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}
.preview-header button:hover {
  background: #484f58;
}
.file-preview pre {
  padding: 14px;
  margin: 0;
  overflow: auto;
  font-size: 13px;
  line-height: 1.5;
  color: #c9d1d9;
  background: #0d1117;
}
</style>
