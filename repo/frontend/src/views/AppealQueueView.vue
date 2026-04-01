<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import appealsApi from '@/api/appeals';

const router = useRouter();
const appeals = ref([]);
const loading = ref(false);
const statusTab = ref('ALL');
const pagination = reactive({ page: 1, size: 10, total: 0 });

const tabs = [
  { label: 'All', value: 'ALL' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Granted', value: 'GRANTED' },
  { label: 'Denied', value: 'DENIED' }
];

const statusType = {
  PENDING: 'warning',
  GRANTED: 'success',
  DENIED: 'danger'
};

const fetchAppeals = async () => {
  loading.value = true;
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size
    };
    if (statusTab.value !== 'ALL') {
      params.status = statusTab.value;
    }
    const { data } = await appealsApi.fetchAppeals(params);
    appeals.value = data.items;
    pagination.total = data.totalElements;
  } finally {
    loading.value = false;
  }
};

watch(statusTab, () => {
  pagination.page = 1;
  fetchAppeals();
});

watch(() => pagination.page, fetchAppeals);

onMounted(fetchAppeals);
</script>

<template>
  <section class="page-card" v-loading="loading">
    <div class="list-header">
      <div>
        <h1>Appeal Queue</h1>
        <p class="muted">Review employer appeals and restore qualified shifts.</p>
      </div>
    </div>
    <el-tabs v-model="statusTab" class="status-tabs">
      <el-tab-pane v-for="tab in tabs" :key="tab.value" :label="tab.label" :name="tab.value" />
    </el-tabs>
    <el-table :data="appeals">
      <el-table-column label="Job" prop="jobTitle" />
      <el-table-column label="Employer" prop="employerUsername" />
      <el-table-column label="Appeal">
        <template #default="{ row }">{{ row.appealReason.slice(0, 100) }}{{ row.appealReason.length > 100 ? '…' : '' }}</template>
      </el-table-column>
      <el-table-column label="Filed">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="Status">
        <template #default="{ row }"><el-tag :type="statusType[row.status] || 'info'">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="Actions" width="220">
        <template #default="{ row }">
          <el-button type="primary" link @click="router.push(`/reviewer/appeals/${row.id}`)">
            {{ row.status === 'PENDING' ? 'Process' : 'View' }}
          </el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="No appeals" /></template>
    </el-table>
    <div class="table-footer">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="pagination.page"
        :total="pagination.total"
        :page-size="pagination.size"
        @current-change="(val) => (pagination.page = val)"
      />
    </div>
  </section>
</template>

<style scoped>
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.status-tabs {
  margin-bottom: 12px;
}

.table-footer {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
