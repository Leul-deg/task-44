<script setup>
import { onMounted, ref } from 'vue';
import reportApi from '@/api/reports';

const exportsList = ref([]);
const loading = ref(false);

const loadExports = async () => {
  loading.value = true;
  try {
    const { data } = await reportApi.getExports();
    exportsList.value = data;
  } finally {
    loading.value = false;
  }
};

const download = async (row) => {
  const { data } = await reportApi.downloadExport(row.id);
  const url = URL.createObjectURL(new Blob([data]));
  const link = document.createElement('a');
  link.href = url;
  link.download = `${row.dashboardName || 'export'}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

const formatDate = (value) => value ? new Date(value).toLocaleString() : '-';

const formatBytes = (bytes) => {
  if (bytes == null) return '-';
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = bytes;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size.toFixed(1)} ${units[index]}`;
};

onMounted(loadExports);
</script>

<template>
  <section class="page-card" v-loading="loading">
    <h1>Report Exports</h1>
    <el-table :data="exportsList">
      <el-table-column label="Dashboard" prop="dashboardName" />
      <el-table-column label="Generated At">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="Masked">
        <template #default="{ row }">
          <el-tag type="info" v-if="row.masked">Yes</el-tag>
          <el-tag type="success" v-else>No</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Size">
        <template #default="{ row }">{{ formatBytes(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="Actions" width="120">
        <template #default="{ row }">
          <el-button type="primary" link @click="download(row)">Download</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
