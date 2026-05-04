<template>
  <div class="app">
    <aside class="sidebar">
      <div class="logo">MyClaw</div>
      <nav>
        <router-link to="/chat" class="nav-item" active-class="active">Chat</router-link>
        <router-link to="/sessions" class="nav-item" active-class="active">Sessions</router-link>
        <router-link to="/files" class="nav-item" active-class="active">Files</router-link>
        <router-link to="/settings" class="nav-item" active-class="active">Settings</router-link>
      </nav>
      <div class="status">
        <span class="dot" :class="{ online: gatewayStore.isConnected }"></span>
        {{ gatewayStore.isConnected ? 'Connected' : 'Disconnected' }}
      </div>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useGatewayStore } from '@/stores/gatewayStore'

const gatewayStore = useGatewayStore()

onMounted(() => {
  gatewayStore.connect()
})
</script>

<style scoped>
.app {
  display: flex;
  height: 100vh;
}
.sidebar {
  width: 220px;
  background: #161b22;
  border-right: 1px solid #30363d;
  display: flex;
  flex-direction: column;
  padding: 16px;
}
.logo {
  font-size: 20px;
  font-weight: 700;
  color: #58a6ff;
  margin-bottom: 24px;
}
.nav-item {
  display: block;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  color: #8b949e;
  transition: all 0.15s;
  text-decoration: none;
}
.nav-item:hover, .nav-item.active {
  background: #21262d;
  color: #c9d1d9;
}
.status {
  margin-top: auto;
  padding: 10px 12px;
  font-size: 12px;
  color: #8b949e;
  display: flex;
  align-items: center;
  gap: 8px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #da3633;
}
.dot.online {
  background: #3fb950;
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #0d1117;
}
</style>
