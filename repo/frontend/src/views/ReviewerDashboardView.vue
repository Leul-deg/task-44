<script setup>
import { onMounted, ref } from 'vue';
import reviewApi from '@/api/review';

const loading = ref(false);
const metrics = ref({ pendingReviews: 0, pendingAppeals: 0, reviewedToday: 0, recentActions: [] });

const loadDashboard = async () => {
  loading.value = true;
  try {
    const { data } = await reviewApi.fetchDashboard();
    metrics.value = data;
  } finally {
    loading.value = false;
  }
};

onMounted(loadDashboard);

const statusType = {
  APPROVE: 'success',
  REJECT: 'danger',
  TAKEDOWN: 'warning'
};
</script>

<template>
  <section class="page-card" v-loading="loading">
    <div class="dashboard-header">
      <div>
        <h1>Reviewer Mission Control</h1>
        <p class="muted">Check queues, appeals, and your latest calls.</p>
      </div>
    </div>
    <el-row :gutter="16" class="metric-row">
      <el-col :xs="24" :md="8">
        <div class="metric-card">
          <span>Pending Reviews</span>
          <strong>{{ metrics.pendingReviews }}</strong>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="metric-card">
          <span>Pending Appeals</span>
          <strong>{{ metrics.pendingAppeals }}</strong>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="metric-card">
          <span>Reviewed Today</span>
          <strong>{{ metrics.reviewedToday }}</strong>
        </div>
      </el-col>
    </el-row>
    <h3>Recent Decisions</h3>
    <el-table :data="metrics.recentActions" empty-text="No recent actions">
      <el-table-column label="Job" prop="jobTitle" />
      <el-table-column label="Action">
        <template #default="{ row }">
          <el-tag :type="statusType[row.action] || 'info'">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Rationale" prop="rationale" />
      <el-table-column label="Date">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.metric-row {
  margin-top: 16px;
  margin-bottom: 24px;
}

.metric-card {
  background: #0a1d37;
  color: #f5f7ff;
  border-radius: 16px;
  padding: 20px;
}

.metric-card span {
  display: block;
  font-size: 13px;
  opacity: 0.8;
}

.metric-card strong {
  font-size: 32px;
}
</style>
