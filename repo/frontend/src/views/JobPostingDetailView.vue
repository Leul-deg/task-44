<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import jobsApi from '@/api/jobs';
import appealsApi from '@/api/appeals';
import StepUpVerification from '@/components/common/StepUpVerification.vue';

const route = useRoute();
const router = useRouter();
const job = ref(null);
const history = ref([]);
const loading = ref(false);
const stepUpVisible = ref(false);
const stepUpLoading = ref(false);
const appealVisible = ref(false);
const appealReason = ref('');
const jobId = route.params.id;

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

const canEdit = computed(() => job.value?.status === 'DRAFT');
const canSubmit = computed(() => job.value?.status === 'DRAFT');
const canPublish = computed(() => job.value?.status === 'APPROVED');
const canUnpublish = computed(() => job.value?.status === 'PUBLISHED');
const canAppeal = computed(() => job.value?.status === 'TAKEN_DOWN');

const loadJob = async () => {
  loading.value = true;
  try {
    const [jobRes, historyRes] = await Promise.all([jobsApi.getJob(jobId), jobsApi.fetchHistory(jobId)]);
    job.value = jobRes.data;
    history.value = historyRes.data;
  } finally {
    loading.value = false;
  }
};

const submit = async () => {
  try {
    await ElMessageBox.confirm('Submit this posting for review?', 'Submit', { confirmButtonText: 'Submit', cancelButtonText: 'Cancel' });
  } catch {
    return;
  }
  await jobsApi.submitJob(jobId);
  ElMessage.success('Submitted for review');
  loadJob();
};

const unpublish = async () => {
  try {
    await ElMessageBox.confirm('Unpublish this posting?', 'Unpublish', { confirmButtonText: 'Yes', cancelButtonText: 'No' });
  } catch {
    return;
  }
  await jobsApi.unpublishJob(jobId);
  ElMessage.success('Posting unpublished');
  loadJob();
};

const startPublish = () => {
  stepUpVisible.value = true;
};

const confirmPublish = async (password) => {
  stepUpLoading.value = true;
  try {
    await jobsApi.publishJob(jobId, { stepUpPassword: password });
    ElMessage.success('Job published');
    stepUpVisible.value = false;
    loadJob();
  } finally {
    stepUpLoading.value = false;
  }
};

const submitAppeal = async () => {
  if (!appealReason.value.trim()) {
    ElMessage.warning('Appeal reason is required');
    return;
  }
  await appealsApi.createAppeal({ jobPostingId: Number(jobId), appealReason: appealReason.value });
  ElMessage.success('Appeal filed');
  appealVisible.value = false;
  appealReason.value = '';
  loadJob();
};

onMounted(loadJob);
</script>

<template>
  <section class="page-card" v-loading="loading">
    <div v-if="job">
      <div class="detail-header">
        <div>
          <h1>{{ job.title }}</h1>
          <el-tag :type="statusType[job.status] ?? 'info'">{{ job.status }}</el-tag>
        </div>
        <div class="actions">
          <el-button v-if="canEdit" @click="router.push(`/employer/postings/${jobId}/edit`)">Edit</el-button>
          <el-button v-if="canSubmit" type="primary" @click="submit">Submit</el-button>
          <el-button v-if="canPublish" type="primary" @click="startPublish">Publish</el-button>
          <el-button v-if="canUnpublish" type="warning" @click="unpublish">Unpublish</el-button>
          <el-button v-if="canAppeal" type="primary" @click="appealVisible = true">File Appeal</el-button>
        </div>
      </div>
      <p class="muted">{{ job.categoryName }} · {{ job.locationCity }}, {{ job.locationState }}</p>
      <p class="key-line">${{ job.payAmount }} / {{ job.payType === 'HOURLY' ? 'hour' : 'flat' }} — {{ job.settlementType }}</p>
      <p class="muted">Headcount {{ job.headcount }} · Weekly Hours {{ job.weeklyHours }}</p>
      <p class="muted">Validity {{ job.validityStart }} → {{ job.validityEnd }}</p>
      <p class="muted">Contact {{ job.contactPhone ?? job.contactPhoneMasked }}</p>
      <p class="description">{{ job.description }}</p>
      <div class="tag-section">
        <el-tag v-for="tag in job.tags" :key="tag" type="success">{{ tag }}</el-tag>
      </div>
      <div v-if="job.reviewerNotes" class="notes">
        <h3>Reviewer Notes</h3>
        <p>{{ job.reviewerNotes }}</p>
      </div>
      <div v-if="job.takedownReason" class="notes">
        <h3>Takedown Reason</h3>
        <p>{{ job.takedownReason }}</p>
      </div>
      <h3>Status Timeline</h3>
      <el-timeline>
        <el-timeline-item v-for="entry in history" :key="entry.createdAt" :timestamp="new Date(entry.createdAt).toLocaleString()">
          <p><strong>{{ entry.previousStatus }} → {{ entry.newStatus }}</strong></p>
          <p class="muted">by {{ entry.changedBy }}</p>
          <p v-if="entry.changeReason">{{ entry.changeReason }}</p>
        </el-timeline-item>
      </el-timeline>
    </div>
    <StepUpVerification
      v-model="stepUpVisible"
      :loading="stepUpLoading"
      @verified="confirmPublish"
    />
    <el-dialog v-model="appealVisible" title="File Appeal" width="480px">
      <el-input type="textarea" rows="4" v-model="appealReason" placeholder="Explain why the posting should return" />
      <template #footer>
        <el-button @click="appealVisible = false">Cancel</el-button>
        <el-button type="primary" @click="submitAppeal">Submit Appeal</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.actions {
  display: flex;
  gap: 12px;
}

.description {
  margin-top: 16px;
  line-height: 1.6;
  white-space: pre-line;
}

.tag-section {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.notes {
  background: #f5f7ff;
  padding: 16px;
  border-radius: 12px;
  margin-top: 16px;
}

.key-line {
  font-weight: 600;
  margin-top: 12px;
}
</style>
