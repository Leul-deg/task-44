<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import jobsApi from '@/api/jobs';
import { JOB_STATUS_TYPE } from '@/constants/statuses';

const jobItems = ref([]);
const loading = ref(false);
const search = ref('');
const activeTab = ref('ALL');
const pagination = reactive({ page: 1, size: 10, total: 0 });

const tabs = [
  { label: 'All', value: 'ALL' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Pending Review', value: 'PENDING_REVIEW' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Published', value: 'PUBLISHED' },
  { label: 'Unpublished', value: 'UNPUBLISHED' },
  { label: 'Taken Down', value: 'TAKEN_DOWN' },
  { label: 'Appeal Pending', value: 'APPEAL_PENDING' }
];

const statusType = JOB_STATUS_TYPE;

const loadJobItems = async () => {
  loading.value = true;
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      search: search.value || undefined,
      status: activeTab.value !== 'ALL' ? activeTab.value : undefined
    };
    const { data } = await jobsApi.fetchJobs(params);
    jobItems.value = data.items;
    pagination.total = data.totalElements;
  } finally {
    loading.value = false;
  }
};

watch(search, () => {
  pagination.page = 1;
  loadJobItems();
});

watch(() => pagination.page, loadJobItems);
watch(() => pagination.size, loadJobItems);
watch(activeTab, () => {
  pagination.page = 1;
  loadJobItems();
});

onMounted(loadJobItems);

const formatCreated = (_, __, row) => new Date(row.createdAt).toLocaleDateString();
</script>

<template>
  <section class="page-card">
    <div class="list-header">
      <div>
        <h1>Job Items</h1>
        <p class="muted">Review all job postings across employers from a dedicated admin workspace.</p>
      </div>
    </div>
    <el-input v-model="search" placeholder="Search title, description, or employer" clearable />
    <el-tabs v-model="activeTab" class="status-tabs">
      <el-tab-pane v-for="tab in tabs" :key="tab.value" :label="tab.label" :name="tab.value" />
    </el-tabs>
    <el-table :data="jobItems" v-loading="loading">
      <el-table-column label="Title" prop="title" min-width="220" />
      <el-table-column label="Employer" prop="employerUsername" min-width="140" />
      <el-table-column label="Category" prop="categoryName" min-width="140" />
      <el-table-column label="Location" min-width="160">
        <template #default="{ row }">
          {{ row.locationCity }}, {{ row.locationState }}
        </template>
      </el-table-column>
      <el-table-column label="Status" min-width="150">
        <template #default="{ row }">
          <el-tag :type="statusType[row.status] ?? 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Created" :formatter="formatCreated" min-width="120" />
      <el-table-column label="Actions" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/employer/postings/${row.id}`)">View</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="No job items found" />
      </template>
    </el-table>
    <div class="table-footer">
      <el-pagination
        background
        layout="prev, pager, next, sizes, total"
        :current-page="pagination.page"
        :page-sizes="[10, 20, 50]"
        :page-size="pagination.size"
        :total="pagination.total"
        @size-change="(val) => { pagination.size = val; pagination.page = 1; }"
        @current-change="(val) => { pagination.page = val; }"
      />
    </div>
  </section>
</template>

<style scoped>
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.status-tabs {
  margin: 16px 0;
}

.table-footer {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
