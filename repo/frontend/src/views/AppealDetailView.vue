<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import appealsApi from '@/api/appeals';
import FileUpload from '@/components/common/FileUpload.vue';

const route = useRoute();
const router = useRouter();
const appealId = route.params.id;
const appeal = ref(null);
const loading = ref(false);
const actionDialog = reactive({ visible: false, decision: 'GRANTED', rationale: '' });

const loadAppeal = async () => {
  loading.value = true;
  try {
    const { data } = await appealsApi.getAppeal(appealId);
    appeal.value = data;
  } finally {
    loading.value = false;
  }
};

onMounted(loadAppeal);

const openDialog = (decision) => {
  actionDialog.visible = true;
  actionDialog.decision = decision;
  actionDialog.rationale = '';
};

const processAppeal = async () => {
  if (actionDialog.rationale.length < 10) {
    ElMessage.warning('Please provide rationale with at least 10 characters');
    return;
  }
  await appealsApi.processAppeal(appealId, {
    decision: actionDialog.decision,
    reviewerRationale: actionDialog.rationale
  });
  ElMessage.success(`Appeal ${actionDialog.decision.toLowerCase()}`);
  actionDialog.visible = false;
  loadAppeal();
};

const statusType = {
  PENDING: 'warning',
  GRANTED: 'success',
  DENIED: 'danger'
};
</script>

<template>
  <section class="page-card" v-loading="loading">
    <div v-if="appeal">
      <div class="detail-header">
        <div>
          <h1>Appeal from {{ appeal.employerUsername }}</h1>
          <el-tag :type="statusType[appeal.status] || 'info'">{{ appeal.status }}</el-tag>
          <p class="muted">Filed {{ new Date(appeal.createdAt).toLocaleString() }}</p>
        </div>
        <div v-if="appeal.status === 'PENDING'" class="actions">
          <el-button type="success" @click="openDialog('GRANTED')">Grant Appeal</el-button>
          <el-button type="danger" @click="openDialog('DENIED')">Deny Appeal</el-button>
        </div>
      </div>
      <el-card class="mb-16">
        <h3>Appeal Reason</h3>
        <p>{{ appeal.appealReason }}</p>
      </el-card>
      <el-card class="mb-16">
        <h3>Supporting Documents</h3>
        <FileUpload entityType="APPEAL" :entityId="appeal.id" />
      </el-card>
      <el-card class="mb-16">
        <h3>Takedown Context</h3>
        <p><strong>Reason:</strong> {{ appeal.takedownReason || 'N/A' }}</p>
        <p><strong>Reviewer Rationale:</strong> {{ appeal.takedownRationale || 'N/A' }}</p>
        <p><strong>Takedown Date:</strong> {{ appeal.takedownAt ? new Date(appeal.takedownAt).toLocaleString() : 'N/A' }}</p>
      </el-card>
      <el-card>
        <h3>Job Summary</h3>
        <p><strong>{{ appeal.jobTitle }}</strong> · {{ appeal.categoryName }} · {{ appeal.location }}</p>
        <p>{{ appeal.paySummary }}</p>
      </el-card>
      <el-card v-if="appeal.status !== 'PENDING'" class="mt-16">
        <h3>Decision</h3>
        <p><strong>{{ appeal.status }}</strong> — {{ appeal.reviewerRationale }}</p>
        <p>Processed {{ appeal.processedAt ? new Date(appeal.processedAt).toLocaleString() : '' }}</p>
      </el-card>
    </div>
    <el-dialog v-model="actionDialog.visible" :title="`${actionDialog.decision} Appeal`" width="480px">
      <el-input v-model="actionDialog.rationale" type="textarea" rows="4" placeholder="Rationale (10+ chars)" />
      <template #footer>
        <el-button @click="actionDialog.visible = false">Cancel</el-button>
        <el-button type="primary" @click="processAppeal">Submit</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.actions {
  display: flex;
  gap: 12px;
}

.mb-16 {
  margin-bottom: 16px;
}

.mt-16 {
  margin-top: 16px;
}
</style>
