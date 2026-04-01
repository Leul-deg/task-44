<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import jobsApi from '@/api/jobs';

const route = useRoute();
const router = useRouter();
const preview = ref(null);
const loading = ref(false);
const jobId = route.params.id;

const loadPreview = async () => {
  loading.value = true;
  try {
    const { data } = await jobsApi.previewJob(jobId);
    preview.value = data;
  } finally {
    loading.value = false;
  }
};

const submit = async () => {
  try {
    await ElMessageBox.confirm('Submit this posting for review?', 'Submit for Review', {
      confirmButtonText: 'Submit',
      cancelButtonText: 'Cancel'
    });
  } catch {
    return;
  }
  await jobsApi.submitJob(jobId);
  ElMessage.success('Submitted for review');
  router.push(`/employer/postings/${jobId}`);
};

onMounted(loadPreview);
</script>

<template>
  <section class="page-card" v-loading="loading">
    <div v-if="preview">
      <div class="preview-header">
        <div>
          <p class="muted">Preview</p>
          <h1>{{ preview.title }}</h1>
          <div class="badge-row">
            <el-tag type="info">{{ preview.categoryName }}</el-tag>
            <el-tag>{{ preview.locationLabel }}</el-tag>
          </div>
        </div>
        <div class="preview-actions">
          <el-button @click="router.push(`/employer/postings/${jobId}/edit`)">Back to Edit</el-button>
          <el-button type="primary" @click="submit">Submit for Review</el-button>
        </div>
      </div>
      <p class="pay-line">{{ preview.paySummary }} · {{ preview.settlementSummary }}</p>
      <p class="meta-line">Headcount {{ preview.headcount }} · Weekly Hours {{ preview.weeklyHours }}</p>
      <p class="meta-line">Valid {{ preview.validityStart }} → {{ preview.validityEnd }}</p>
      <p class="meta-line">Contact {{ preview.contactPhoneMasked ?? 'On approval' }}</p>
      <p class="description">{{ preview.description }}</p>
      <div class="tag-section">
        <el-tag v-for="tag in preview.tags" :key="tag" type="success">{{ tag }}</el-tag>
      </div>
    </div>
  </section>
</template>

<style scoped>
.preview-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.badge-row {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.preview-actions {
  display: flex;
  gap: 12px;
}

.pay-line {
  font-size: 20px;
  font-weight: 600;
  margin-top: 16px;
}

.meta-line {
  color: #6b7280;
  margin-top: 4px;
}

.description {
  margin-top: 20px;
  line-height: 1.6;
  white-space: pre-line;
}

.tag-section {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
