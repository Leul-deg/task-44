<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import dashboardsApi from '@/api/dashboards';
import { ElMessage, ElMessageBox } from 'element-plus';

const router = useRouter();
const dashboards = ref([]);
const loading = ref(false);

const loadDashboards = async () => {
  loading.value = true;
  try {
    const { data } = await dashboardsApi.getDashboards();
    dashboards.value = data;
  } finally {
    loading.value = false;
  }
};

const metricLabels = {
  post_volume: 'Post Volume',
  claim_count: 'Claim Count',
  review_count: 'Review Count',
  approval_rate: 'Approval Rate',
  handling_time: 'Handling Time',
  takedown_count: 'Takedown Count'
};

const viewDashboard = (dash) => {
  router.push({ path: `/analytics/builder/${dash.id}`, query: { mode: 'view' } });
};

const editDashboard = (dash) => {
  router.push(`/analytics/builder/${dash.id}`);
};

const deleteDashboard = async (dash) => {
  await ElMessageBox.confirm(`Delete "${dash.name}"?`, 'Delete Dashboard');
  await dashboardsApi.deleteDashboard(dash.id);
  ElMessage.success('Dashboard deleted');
  loadDashboards();
};

const createDashboard = () => {
  router.push('/analytics/builder');
};

onMounted(loadDashboards);
</script>

<template>
  <section class="page-card">
    <div class="toolbar">
      <div>
        <h1>Analytics Center</h1>
        <p class="muted">Build custom views over your shifts, reviews, and claims.</p>
      </div>
      <el-button type="primary" @click="createDashboard">Create New Dashboard</el-button>
    </div>
    <el-row gutter="16" v-if="dashboards.length">
      <el-col v-for="dash in dashboards" :key="dash.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card shadow="hover" class="dashboard-card">
          <h3>{{ dash.name }}</h3>
          <div class="tag-row">
            <el-tag v-for="metric in dash.metricsJson" :key="metric" type="info" size="small">
              {{ metricLabels[metric] ?? metric }}
            </el-tag>
          </div>
          <div class="actions">
            <el-button size="mini" type="primary" @click="viewDashboard(dash)">View</el-button>
            <el-button size="mini" type="warning" @click="editDashboard(dash)">Edit</el-button>
            <el-button size="mini" type="danger" @click="deleteDashboard(dash)">Delete</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-else description="No dashboards saved yet." />
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.dashboard-card {
  min-height: 180px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 12px 0;
}

.actions {
  display: flex;
  gap: 6px;
}

.muted {
  color: #6b7280;
}
</style>
