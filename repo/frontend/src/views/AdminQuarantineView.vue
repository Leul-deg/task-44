<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import fileApi from '@/api/files';

const files = ref([]);
const loading = ref(false);

const fetchFiles = async () => {
  loading.value = true;
  try {
    const res = await fileApi.getQuarantinedFiles();
    files.value = res.data || [];
  } finally {
    loading.value = false;
  }
};

const confirmAction = (message, type = 'info') =>
  ElMessageBox.confirm(message, 'Confirm', { confirmButtonText: 'Continue', cancelButtonText: 'Cancel', type });

const handleRelease = async (id) => {
  await confirmAction('Release this file from quarantine?');
  try {
    await fileApi.releaseFile(id);
    ElMessage.success('File released');
    fetchFiles();
  } catch (e) {
    ElMessage.error(e.response?.data?.message || 'Failed to release file');
  }
};

const handleDelete = async (id) => {
  await confirmAction('Permanently delete this quarantined file?', 'warning');
  try {
    await fileApi.deleteFile(id);
    ElMessage.success('File deleted');
    fetchFiles();
  } catch (e) {
    ElMessage.error(e.response?.data?.message || 'Failed to delete file');
  }
};

onMounted(fetchFiles);
</script>

<template>
  <section class="page-card">
    <div class="header">
      <div>
        <h2>Quarantined Files</h2>
        <p class="muted">Files that failed validation are held here for admin review.</p>
      </div>
      <el-button size="small" :loading="loading" @click="fetchFiles">Refresh</el-button>
    </div>

    <el-table :data="files" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="entityType" label="Entity Type" width="120" />
      <el-table-column prop="entityId" label="Entity ID" width="100" />
      <el-table-column prop="originalFilename" label="Filename" show-overflow-tooltip />
      <el-table-column prop="fileSize" label="Size" width="120">
        <template #default="{ row }">
          {{ row.fileSize ? (row.fileSize / 1024).toFixed(1) + ' KB' : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="Quarantined At" width="180" />
      <el-table-column label="Actions" width="220">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="handleRelease(row.id)">Release</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">Delete</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && files.length === 0" description="No quarantined files" />
  </section>
</template>

<style scoped>
.page-card {
  padding: 24px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(8, 23, 52, 0.08);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.muted {
  color: #6b7280;
}
</style>
