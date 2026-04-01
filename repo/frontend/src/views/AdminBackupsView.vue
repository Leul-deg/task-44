<script setup>
import { ref, onMounted } from 'vue';
import { listBackups, triggerBackup, restoreBackup } from '../api/backup';
import { ElMessage, ElMessageBox } from 'element-plus';

const backups = ref([]);
const loading = ref(false);
const triggerLoading = ref(false);

async function loadBackups() {
  loading.value = true;
  try {
    const { data } = await listBackups();
    backups.value = data;
  } catch {
    ElMessage.error('Failed to load backups');
  } finally {
    loading.value = false;
  }
}

async function handleTrigger() {
  try {
    const { value: password } = await ElMessageBox.prompt(
      'Enter your password to trigger a backup.',
      'Step-Up Verification',
      { inputType: 'password', confirmButtonText: 'Trigger Backup', cancelButtonText: 'Cancel' }
    );
    triggerLoading.value = true;
    await triggerBackup(password);
    ElMessage.success('Backup triggered successfully');
    await loadBackups();
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || 'Backup failed');
  } finally {
    triggerLoading.value = false;
  }
}

async function handleRestore(row) {
  try {
    await ElMessageBox.confirm(
      `Restore from backup "${row.filename}"? This will overwrite current data.`,
      'Confirm Restore',
      { type: 'warning', confirmButtonText: 'Continue', cancelButtonText: 'Cancel' }
    );
    const { value: password } = await ElMessageBox.prompt(
      'Enter your password to confirm restore.',
      'Step-Up Verification',
      { inputType: 'password', confirmButtonText: 'Restore Now', cancelButtonText: 'Cancel' }
    );
    loading.value = true;
    await restoreBackup(row.id, password);
    ElMessage.success('Restore completed');
    await loadBackups();
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || 'Restore failed');
  } finally {
    loading.value = false;
  }
}

function formatSize(bytes) {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++; }
  return size.toFixed(i > 0 ? 1 : 0) + ' ' + units[i];
}

function formatDate(val) {
  if (!val) return '—';
  return new Date(val).toLocaleString();
}

function statusType(status) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED') return 'danger';
  return 'info';
}

onMounted(loadBackups);
</script>

<template>
  <section class="page-card">
    <div class="header-row">
      <div>
        <h1>Backups</h1>
        <p class="subtitle">Encrypted nightly backups with 30-day retention.</p>
      </div>
      <el-button type="primary" @click="handleTrigger" :loading="triggerLoading">Trigger Backup Now</el-button>
    </div>

    <el-table :data="backups" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="filename" label="Filename" min-width="260" />
      <el-table-column prop="fileSize" label="Size" width="100">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="Created" width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="expiresAt" label="Expires" width="180">
        <template #default="{ row }">{{ formatDate(row.expiresAt) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="Status" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="warning" size="small" @click="handleRestore(row)" :disabled="row.status !== 'COMPLETED'">
            Restore
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="No backups yet" />
      </template>
    </el-table>
  </section>
</template>

<style scoped>
.page-card { padding: 24px; }
.header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.subtitle { color: #909399; margin-top: 4px; }
</style>
