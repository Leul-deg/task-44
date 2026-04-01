<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import reviewApi from '@/api/review';

const router = useRouter();
const postings = ref([]);
const loading = ref(false);
const pagination = reactive({ page: 1, size: 10, total: 0 });

const fetchQueue = async () => {
  loading.value = true;
  try {
    const { data } = await reviewApi.fetchQueue({ page: pagination.page - 1, size: pagination.size });
    postings.value = data.items;
    pagination.total = data.totalElements;
  } finally {
    loading.value = false;
  }
};

onMounted(fetchQueue);

const handlePageChange = (page) => {
  pagination.page = page;
  fetchQueue();
};

const formatDate = (value) => new Date(value).toLocaleString();
</script>

<template>
  <section class="page-card" v-loading="loading">
    <div class="list-header">
      <div>
        <h1>Approval Queue</h1>
        <p class="muted">Oldest submissions appear first. Keep the pipeline flowing.</p>
      </div>
    </div>
    <el-table :data="postings">
      <el-table-column label="Title" prop="title" />
      <el-table-column label="Employer" prop="employerUsername" />
      <el-table-column label="Category" prop="categoryName" />
      <el-table-column label="Location">
        <template #default="{ row }">{{ row.locationCity }}, {{ row.locationState }}</template>
      </el-table-column>
      <el-table-column label="Submitted">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="Actions" width="140">
        <template #default="{ row }">
          <el-button type="primary" link @click="router.push(`/reviewer/queue/${row.id}`)">Review</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="No postings pending review" /></template>
    </el-table>
    <div class="table-footer">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="pagination.page"
        :total="pagination.total"
        :page-size="pagination.size"
        @current-change="handlePageChange"
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

.table-footer {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
