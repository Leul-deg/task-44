<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import filesApi from '@/api/files';
import logger from '@/utils/logger';

const props = defineProps({
  entityType: { type: String, required: true },
  entityId: { type: Number, required: true }
});

const emit = defineEmits(['uploaded']);
const authStore = useAuthStore();
const files = ref([]);
const loading = ref(false);
const quarantineWarning = ref('');

const loadFiles = async () => {
  loading.value = true;
  try {
    const { data } = await filesApi.getEntityFiles(props.entityType, props.entityId);
    files.value = data;
  } catch (err) {
    logger.debug('FileUpload', `No files for ${props.entityType}/${props.entityId}: ${err?.message}`);
    files.value = [];
  } finally {
    loading.value = false;
  }
};

onMounted(loadFiles);

const handleUpload = async (options) => {
  quarantineWarning.value = '';
  try {
    await filesApi.uploadFile(options.file, props.entityType, props.entityId);
    ElMessage.success('File uploaded successfully');
    emit('uploaded');
    loadFiles();
  } catch (error) {
    const status = error.response?.status;
    if (status === 422) {
      quarantineWarning.value = error.response?.data?.message ?? 'File quarantined';
      loadFiles();
    } else if (status === 400) {
      ElMessage.error(error.response?.data?.message ?? 'Upload failed');
    } else {
      ElMessage.error('Upload failed');
    }
  }
};

const download = async (file) => {
  try {
    const { data } = await filesApi.downloadFile(file.id, file.fileType === 'pdf');
    const url = URL.createObjectURL(data);
    const link = document.createElement('a');
    link.href = url;
    link.download = file.originalFilename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  } catch (error) {
    ElMessage.error('Download failed');
  }
};

const remove = async (file) => {
  try {
    await filesApi.deleteFile(file.id);
    ElMessage.success('File deleted');
    loadFiles();
  } catch (error) {
    ElMessage.error('Delete failed');
  }
};

const formatSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
};
</script>

<template>
  <div class="file-upload-wrapper">
    <el-upload
      :http-request="handleUpload"
      :show-file-list="false"
      drag
      multiple
      accept=".pdf,.jpg,.jpeg,.png"
    >
      <div class="upload-zone">
        <el-icon style="font-size: 28px; color: #909399"><i class="el-icon-upload" /></el-icon>
        <p>Drop files here or <strong>click to upload</strong></p>
        <p class="hint">PDF, JPG, PNG (max 10 MB)</p>
      </div>
    </el-upload>

    <el-alert
      v-if="quarantineWarning"
      :title="quarantineWarning"
      type="warning"
      show-icon
      closable
      style="margin-top: 8px"
      @close="quarantineWarning = ''"
    />

    <el-table v-if="files.length > 0" :data="files" size="small" style="margin-top: 12px">
      <el-table-column label="File" prop="originalFilename" />
      <el-table-column label="Size" width="100">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="Uploaded" width="160">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="Status" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="160">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="download(row)">Download</el-button>
          <el-button v-if="authStore.isAdmin" type="danger" link size="small" @click="remove(row)">Delete</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.upload-zone {
  padding: 16px;
  text-align: center;
}

.upload-zone p {
  margin: 4px 0;
  color: #606266;
}

.hint {
  font-size: 12px;
  color: #909399;
}
</style>
