<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { BellFilled, SwitchButton } from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';
import alertsApi from '@/api/alerts';

const router = useRouter();
const authStore = useAuthStore();
const roleLabel = computed(() => authStore.role ?? 'EMPLOYER');
const unreadCount = ref(0);
let intervalId;

const loadCount = async () => {
  if (authStore.role !== 'ADMIN') {
    unreadCount.value = 0;
    return;
  }
  const { data } = await alertsApi.getUnreadCount();
  unreadCount.value = data.count;
};

onMounted(() => {
  loadCount();
  intervalId = setInterval(loadCount, 60000);
});

onUnmounted(() => {
  clearInterval(intervalId);
});

const gotoAlerts = () => {
  if (authStore.role === 'ADMIN') {
    router.push('/admin/alerts');
  }
};
</script>

<template>
  <div class="header">
    <div class="header__title">
      <h2>ShiftWorks JobOps</h2>
      <small>Stay on top of every posting and alert</small>
    </div>
    <div class="header__actions">
      <template v-if="authStore.role === 'ADMIN'">
        <el-badge :value="unreadCount" type="danger" :hidden="unreadCount === 0">
          <el-button circle size="large" :icon="BellFilled" @click="gotoAlerts" />
        </el-badge>
      </template>
      <el-button
        v-else
        circle
        size="large"
        :icon="BellFilled"
        @click="gotoAlerts"
        :disabled="true"
      />
      <div class="header__user">
        <el-avatar size="large">{{ authStore.initials }}</el-avatar>
        <div>
          <strong>{{ authStore.username }}</strong>
          <el-tag type="info" size="small">{{ roleLabel }}</el-tag>
        </div>
      </div>
      <el-button type="primary" :icon="SwitchButton" plain @click="authStore.logout">
        Logout
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header__title h2 {
  margin: 0;
  font-weight: 600;
}

.header__title small {
  color: #909399;
}

.header__actions {
  display: flex;
  gap: 16px;
  align-items: center;
}

.header__user {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
