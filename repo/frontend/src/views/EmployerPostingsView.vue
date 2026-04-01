<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import jobsApi from '@/api/jobs';
import appealsApi from '@/api/appeals';
const postings = ref([]);
const loading = ref(false);
const search = ref('');
const activeTab = ref('ALL');
const pagination = reactive({ page: 1, size: 10, total: 0 });

const tabs = [
  { label: 'All', value: 'ALL' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Pending', value: 'PENDING_REVIEW' },
  { label: 'Published', value: 'PUBLISHED' },
  { label: 'Rejected', value: 'REJECTED' },
  { label: 'Taken Down', value: 'TAKEN_DOWN' }
];

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

const fetchPostings = async () => {
  loading.value = true;
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      search: search.value || undefined,
      status: activeTab.value !== 'ALL' ? activeTab.value : undefined
    };
    const { data } = await jobsApi.fetchJobs(params);
    postings.value = data.items;
    pagination.total = data.totalElements;
  } finally {
    loading.value = false;
  }
};

const handleUnpublish = async (row) => {
  await jobsApi.unpublishJob(row.id);
  ElMessage.success('Posting unpublished');
  fetchPostings();
};

const handleAppeal = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('Provide a short summary for the appeal', 'Appeal', {
      confirmButtonText: 'Submit',
      cancelButtonText: 'Cancel'
    });
    await appealsApi.createAppeal({ jobPostingId: row.id, appealReason: value });
    ElMessage.success('Appeal submitted');
    fetchPostings();
  } catch (error) {
    if (error?.action) return;
    ElMessage.error('Unable to submit appeal');
  }
};

watch(search, () => {
  pagination.page = 1;
  fetchPostings();
});

watch(() => pagination.page, fetchPostings);
watch(() => pagination.size, fetchPostings);
watch(activeTab, () => {
  pagination.page = 1;
  fetchPostings();
});

onMounted(fetchPostings);

const formatCreated = (_, __, row) => new Date(row.createdAt).toLocaleDateString();
</script>

<template>
  <section class="page-card">
    <div class="list-header">
      <div>
        <h1>Job Postings</h1>
        <p class="muted">Manage drafts, pending submissions, and live shifts.</p>
      </div>
      <el-button type="primary" @click="$router.push('/employer/create')">Create Posting</el-button>
    </div>
    <el-input v-model="search" placeholder="Search title or description" clearable />
    <el-tabs v-model="activeTab" class="status-tabs">
      <el-tab-pane v-for="tab in tabs" :key="tab.value" :label="tab.label" :name="tab.value" />
    </el-tabs>
    <el-table :data="postings" v-loading="loading">
      <el-table-column label="Title" prop="title" />
      <el-table-column label="Category" prop="categoryName" />
      <el-table-column label="Location">
        <template #default="{ row }">
          {{ row.locationCity }}, {{ row.locationState }}
        </template>
      </el-table-column>
      <el-table-column label="Status">
        <template #default="{ row }">
          <el-tag :type="statusType[row.status] ?? 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Pay">
        <template #default="{ row }">
          ${{ row.payAmount }} / {{ row.payType === 'HOURLY' ? 'hour' : 'flat' }}
        </template>
      </el-table-column>
      <el-table-column label="Headcount" prop="headcount" />
      <el-table-column label="Created" :formatter="formatCreated" />
      <el-table-column label="Actions" width="260">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/employer/postings/${row.id}`)">View</el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="$router.push(`/employer/postings/${row.id}/edit`)">Edit</el-button>
          <el-button link type="primary" @click="$router.push(`/employer/postings/${row.id}/preview`)">Preview</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link type="danger" @click="handleUnpublish(row)">Unpublish</el-button>
          <el-button v-if="row.status === 'TAKEN_DOWN'" link type="warning" @click="handleAppeal(row)">Appeal</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="No job postings yet" /></template>
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
