<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { diffWords } from 'diff';
import reviewApi from '@/api/review';
import StepUpVerification from '@/components/common/StepUpVerification.vue';
import DOMPurify from 'dompurify';

const route = useRoute();
const router = useRouter();
const jobId = route.params.id;
const job = ref(null);
const diffData = ref(null);
const actions = ref([]);
const loading = ref(false);

const rationaleDialog = reactive({ visible: false, type: 'APPROVE', rationale: '', notes: '' });
const takedownState = reactive({ password: '', dialogVisible: false });
const stepUpVisible = ref(false);
const stepUpLoading = ref(false);

const loadData = async () => {
  loading.value = true;
  try {
    const [jobRes, diffRes, actionsRes] = await Promise.all([
      reviewApi.getReviewJob(jobId),
      reviewApi.getReviewDiff(jobId),
      reviewApi.getReviewActions(jobId)
    ]);
    job.value = jobRes.data;
    diffData.value = diffRes.data;
    actions.value = actionsRes.data;
  } finally {
    loading.value = false;
  }
};

onMounted(loadData);

const descriptionDiff = computed(() => {
  if (!diffData.value || !diffData.value.description || !job.value) return null;
  const oldText = diffData.value.description.oldValue || '';
  const newText = diffData.value.description.newValue || '';
  return diffWords(oldText, newText);
});

const changedFields = computed(() => {
  if (!diffData.value) return [];
  return Object.entries(diffData.value)
    .filter(([key]) => key !== 'description')
    .map(([field, value]) => ({ field, value }));
});

const openDialog = (type) => {
  rationaleDialog.type = type;
  rationaleDialog.rationale = '';
  rationaleDialog.notes = '';
  rationaleDialog.visible = true;
};

const submitAction = async () => {
  if (rationaleDialog.rationale.length < 10) {
    ElMessage.warning('Rationale must be at least 10 characters');
    return;
  }
  try {
    if (rationaleDialog.type === 'APPROVE') {
      await reviewApi.approveJob(jobId, { rationale: rationaleDialog.rationale });
      ElMessage.success('Posting approved');
    } else if (rationaleDialog.type === 'REJECT') {
      await reviewApi.rejectJob(jobId, { rationale: rationaleDialog.rationale, reviewerNotes: rationaleDialog.notes });
      ElMessage.success('Posting rejected');
    } else if (rationaleDialog.type === 'TAKEDOWN') {
      await reviewApi.takedownJob(jobId, { rationale: rationaleDialog.rationale, stepUpPassword: takedownState.password });
      ElMessage.success('Posting taken down');
    }
    rationaleDialog.visible = false;
    stepUpVisible.value = false;
    loadData();
  } finally {
    takedownState.password = '';
  }
};

const startTakedown = () => {
  stepUpVisible.value = true;
};

const handleStepUp = (password) => {
  takedownState.password = password;
  rationaleDialog.type = 'TAKEDOWN';
  rationaleDialog.rationale = '';
  rationaleDialog.visible = true;
  stepUpVisible.value = false;
};

const statusBadges = {
  PENDING_REVIEW: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  PUBLISHED: 'success',
  TAKEN_DOWN: 'danger'
};
</script>

<template>
  <section class="page-card" v-loading="loading">
    <div v-if="job">
      <div class="detail-header">
        <div>
          <p class="muted">Reviewing</p>
          <h1>{{ job.title }}</h1>
          <el-tag :type="statusBadges[job.status] || 'info'">{{ job.status }}</el-tag>
          <p class="muted">{{ job.employerUsername }} · {{ job.categoryName }} · {{ job.locationCity }}, {{ job.locationState }}</p>
        </div>
      </div>
      <el-row :gutter="16" class="diff-grid">
        <el-col :md="12" :xs="24">
          <div class="panel">
            <h3>Current Submission</h3>
            <p><strong>Pay:</strong> ${{ job.payAmount }} / {{ job.payType === 'HOURLY' ? 'hour' : 'flat' }} · {{ job.settlementType }}</p>
            <p><strong>Headcount:</strong> {{ job.headcount }}</p>
            <p><strong>Weekly Hours:</strong> {{ job.weeklyHours }}</p>
            <p><strong>Validity:</strong> {{ job.validityStart }} → {{ job.validityEnd }}</p>
            <div class="description" v-html="DOMPurify.sanitize(job.description.replace(/\n/g, '<br>'))" />
            <div class="tag-section">
              <el-tag v-for="tag in job.tags" :key="tag" type="info">{{ tag }}</el-tag>
            </div>
          </div>
        </el-col>
        <el-col :md="12" :xs="24">
          <div class="panel muted-panel">
            <h3>Previous Version</h3>
            <div v-if="diffData">
              <div v-for="item in changedFields" :key="item.field" class="diff-row">
                <label>{{ item.field }}</label>
                <div class="diff-values">
                  <span class="diff-old">{{ item.value.oldValue }}</span>
                  <span class="diff-new">{{ item.value.newValue }}</span>
                </div>
              </div>
              <div v-if="descriptionDiff" class="description-diff">
                <label>Description</label>
                <p>
                  <template v-for="token in descriptionDiff" :key="token.value + token.added + token.removed">
                    <span v-if="token.added" class="diff-new">{{ token.value }}</span>
                    <span v-else-if="token.removed" class="diff-old">{{ token.value }}</span>
                    <span v-else>{{ token.value }}</span>
                  </template>
                </p>
              </div>
            </div>
            <el-empty v-else description="First submission" />
          </div>
        </el-col>
      </el-row>
      <h3>Past Review Actions</h3>
      <el-timeline>
        <el-timeline-item v-for="action in actions" :key="action.id" :timestamp="new Date(action.createdAt).toLocaleString()">
          <el-tag size="small" :type="statusBadges[action.action] || 'info'">{{ action.action }}</el-tag>
          <p>{{ action.rationale }}</p>
        </el-timeline-item>
      </el-timeline>
    </div>
    <div class="action-bar" v-if="job">
      <el-button type="success" @click="openDialog('APPROVE')">Approve</el-button>
      <el-button type="danger" @click="openDialog('REJECT')">Reject</el-button>
      <el-button type="danger" plain v-if="job.status === 'PUBLISHED'" @click="startTakedown">Takedown</el-button>
    </div>
    <el-dialog v-model="rationaleDialog.visible" :title="`${rationaleDialog.type} Posting`" width="480px">
      <el-input v-model="rationaleDialog.rationale" type="textarea" rows="4" placeholder="Enter rationale (10+ chars)" />
      <el-input
        v-if="rationaleDialog.type === 'REJECT'"
        v-model="rationaleDialog.notes"
        type="textarea"
        rows="3"
        placeholder="Optional reviewer notes for employer"
        class="mt-12"
      />
      <template #footer>
        <el-button @click="rationaleDialog.visible = false">Cancel</el-button>
        <el-button type="primary" @click="submitAction">Confirm</el-button>
      </template>
    </el-dialog>
    <StepUpVerification v-model="stepUpVisible" :loading="stepUpLoading" @verified="handleStepUp" />
  </section>
</template>

<style scoped>
.detail-header {
  margin-bottom: 16px;
}

.diff-grid {
  margin-top: 16px;
}

.panel {
  background: #fff;
  padding: 16px;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(10, 29, 55, 0.06);
}

.muted-panel {
  background: #f8faff;
}

.diff-row {
  margin-bottom: 12px;
}

.diff-values {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.diff-old {
  background: #fee2e2;
  padding: 2px 6px;
  border-radius: 6px;
}

.diff-new {
  background: #dcfce7;
  padding: 2px 6px;
  border-radius: 6px;
}

.tag-section {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.action-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  gap: 16px;
  background: rgba(255, 255, 255, 0.9);
  padding: 16px;
  border-top: 1px solid #e4e7ed;
  margin-top: 24px;
}

.mt-12 {
  margin-top: 12px;
}
</style>
