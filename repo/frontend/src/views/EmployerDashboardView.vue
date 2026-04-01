<script setup>
import { onMounted, ref } from 'vue';
import jobsApi from '@/api/jobs';

const loading = ref(false);
const summary = ref({ total: 0, published: 0, pendingReview: 0, rejected: 0 });
const recent = ref([]);

const statusType = {
  DRAFT: 'info',
  PENDING_REVIEW: 'warning',
  APPROVED: 'success',
  PUBLISHED: 'success',
  REJECTED: 'danger',
  UNPUBLISHED: 'info',
  TAKEN_DOWN: 'danger',
  APPEAL_PENDING: 'warning'
};

const formatLocation = (_, __, row) => `${row.locationCity}, ${row.locationState}`;
const formatDate = (_, __, row) => new Date(row.createdAt).toLocaleDateString();

const loadSummary = async () => {
  loading.value = true;
  try {
    const { data } = await jobsApi.fetchSummary();
    summary.value = data;
    recent.value = data.recent ?? [];
  } finally {
    loading.value = false;
  }
};

onMounted(loadSummary);
</script>

<template>
  <section class="page-card">
    <div class="dashboard-header">
      <div>
        <h1>Employer Control Center</h1>
        <p class="muted">Monitor submissions and keep upcoming shifts on schedule.</p>
      </div>
      <el-button type="primary" round @click="$router.push('/employer/create')">
        New Posting
      </el-button>
    </div>
    <el-row :gutter="16" class="metric-row">
      <el-col :xs="12" :md="6">
        <div class="metric-card">
          <span>Total Postings</span>
          <strong>{{ summary.total }}</strong>
        </div>
      </el-col>
      <el-col :xs="12" :md="6">
        <div class="metric-card">
          <span>Published</span>
          <strong>{{ summary.published }}</strong>
        </div>
      </el-col>
      <el-col :xs="12" :md="6">
        <div class="metric-card">
          <span>Pending Review</span>
          <strong>{{ summary.pendingReview }}</strong>
        </div>
      </el-col>
      <el-col :xs="12" :md="6">
        <div class="metric-card">
          <span>Rejected</span>
          <strong>{{ summary.rejected }}</strong>
        </div>
      </el-col>
    </el-row>
    <h3>Recent Postings</h3>
    <el-table :data="recent" v-loading="loading">
      <el-table-column label="Title" prop="title" />
      <el-table-column label="Category" prop="categoryName" />
      <el-table-column label="Location" :formatter="formatLocation" />
      <el-table-column label="Status">
        <template #default="{ row }">
          <el-tag :type="statusType[row.status] ?? 'info'" effect="light">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Created" prop="createdAt" :formatter="formatDate" />
      <el-table-column label="Actions">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/employer/postings/${row.id}`)">View</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.metric-row {
  margin-bottom: 24px;
}

.metric-card {
  background: #0a1d37;
  color: #f5f7ff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.1);
}

.metric-card span {
  display: block;
  font-size: 13px;
  opacity: 0.8;
}

.metric-card strong {
  font-size: 28px;
}
</style>
