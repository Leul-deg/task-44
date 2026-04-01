<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue';
import alertsApi from '@/api/alerts';
import { ElMessage } from 'element-plus';

const tabs = ['All', 'Unread', 'Critical', 'Warning'];
const activeTab = ref('All');
const alerts = ref([]);
const loading = ref(false);
let intervalId;

const severityMap = {
  All: null,
  Unread: null,
  Critical: 'CRITICAL',
  Warning: 'WARNING'
};

watch(activeTab, () => {
  fetchAlerts();
});

const fetchAlerts = async () => {
  loading.value = true;
  const params = { page: 0, size: 20 };
  const severity = severityMap[activeTab.value];
  if (severity) {
    params.severity = severity;
  }
  if (activeTab.value === 'Unread') {
    params.is_read = false;
  }
  try {
    const { data } = await alertsApi.getAlerts(params);
    alerts.value = data.content;
  } finally {
    loading.value = false;
  }
};

const markRead = async (id) => {
  await alertsApi.markAlertRead(id);
  ElMessage.success('Marked as read');
  fetchAlerts();
};

const acknowledge = async (id) => {
  await alertsApi.acknowledgeAlert(id);
  ElMessage.success('Acknowledged');
  fetchAlerts();
};

onMounted(() => {
  fetchAlerts();
  intervalId = setInterval(fetchAlerts, 30000);
});

onUnmounted(() => {
  clearInterval(intervalId);
});
</script>

<template>
  <section class="page-card" v-loading="loading">
    <div class="toolbar">
      <div>
        <h1>Alert Inbox</h1>
        <p class="muted">Monitor anomalies and acknowledgments.</p>
      </div>
    </div>
    <el-tabs v-model="activeTab" gutter="16">
      <el-tab-pane v-for="tab in tabs" :key="tab" :label="tab" :name="tab" />
    </el-tabs>
    <el-table :data="alerts" style="width: 100%">
      <el-table-column label="Severity" width="100">
        <template #default="{ row }">
          <el-badge :value="''">
            <el-icon>
              <svg v-if="row.severity === 'CRITICAL'" width="16" height="16" viewBox="0 0 16 16" fill="#f56c6c"><circle cx="8" cy="8" r="7"></circle></svg>
              <svg v-else-if="row.severity === 'WARNING'" width="16" height="16" viewBox="0 0 16 16" fill="#e6a23c"><polygon points="8,1 15,14 1,14"/></svg>
              <svg v-else width="16" height="16" viewBox="0 0 16 16" fill="#409eff"><circle cx="8" cy="8" r="7"></circle></svg>
            </el-icon>
          </el-badge>
        </template>
      </el-table-column>
      <el-table-column label="Type" prop="alertType" />
      <el-table-column label="Message" prop="message" />
      <el-table-column label="Created" prop="createdAt" />
      <el-table-column label="Status">
        <template #default="{ row }">
          <el-tag type="info" v-if="!row.read">Unread</el-tag>
          <el-tag type="success" v-else-if="row.acknowledgedBy">Acknowledged</el-tag>
          <el-tag type="default" v-else>Read</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="220">
        <template #default="{ row }">
          <el-button size="mini" type="primary" @click="markRead(row.id)" v-if="!row.read">Mark Read</el-button>
          <el-button size="mini" type="success" @click="acknowledge(row.id)">Acknowledge</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.muted {
  color: #6b7280;
}
</style>
