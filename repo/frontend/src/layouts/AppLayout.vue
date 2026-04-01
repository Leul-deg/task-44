<script setup>
import Sidebar from '../components/Sidebar.vue';
import HeaderBar from '../components/HeaderBar.vue';
</script>

<template>
  <el-container class="app-shell">
    <el-aside width="240px" class="app-shell__aside">
      <Sidebar />
    </el-aside>
    <el-container>
      <el-header class="app-shell__header">
        <HeaderBar />
      </el-header>
      <el-main class="app-shell__content">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7ff 0%, #eef4ff 100%);
}

.app-shell__aside {
  background: #0a1d37;
  color: #f5f7ff;
}

.app-shell__header {
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid #e4e7ed;
  backdrop-filter: blur(8px);
}

.app-shell__content {
  padding: 24px;
}
</style>
