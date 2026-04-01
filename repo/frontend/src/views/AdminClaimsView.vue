<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import adminApi from '@/api/admin';
import FileUpload from '@/components/common/FileUpload.vue';

const statuses = ['ALL', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const filters = reactive({ status: 'ALL' });
const pagination = reactive({ page: 1, size: 10, total: 0 });
const claims = ref([]);
const loading = ref(false);
const adminUsers = ref([]);

const dialog = reactive({
  visible: false,
  claim: null,
  status: 'OPEN',
  resolution: '',
  assignedTo: null
});

const loadClaims = async () => {
  loading.value = true;
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      status: filters.status !== 'ALL' ? filters.status : undefined
    };
    const { data } = await adminApi.getClaims(params);
    claims.value = data.items;
    pagination.total = data.totalElements;
  } finally {
    loading.value = false;
  }
};

const loadAdmins = async () => {
  const { data } = await adminApi.getUsers({ role: 'ADMIN', page: 0, size: 100 });
  adminUsers.value = data.items;
};

watch(() => filters.status, () => {
  pagination.page = 1;
  loadClaims();
});

watch(() => pagination.page, loadClaims);

onMounted(() => {
  loadClaims();
  loadAdmins();
});

const openDialog = (claim) => {
  dialog.visible = true;
  dialog.claim = claim;
  dialog.status = claim.status;
  dialog.resolution = claim.resolution || '';
  dialog.assignedTo = claim.assignedTo || null;
};

const saveClaim = async () => {
  await adminApi.updateClaim(dialog.claim.id, {
    status: dialog.status,
    resolution: dialog.resolution,
    assignedTo: dialog.assignedTo
  });
  ElMessage.success('Claim updated');
  dialog.visible = false;
  loadClaims();
};

const statusTag = (status) => ({
  OPEN: 'warning',
  IN_PROGRESS: 'info',
  RESOLVED: 'success',
  CLOSED: 'default'
}[status] || 'info');
</script>

<template>
  <section class="page-card">
    <div class="toolbar">
      <div>
        <h1>Claims Desk</h1>
        <p class="muted">Investigate and resolve disputes from employers.</p>
      </div>
    </div>
    <el-tabs v-model="filters.status" class="status-tabs">
      <el-tab-pane v-for="status in statuses" :key="status" :label="status.replace('_', ' ')" :name="status" />
    </el-tabs>
    <el-table :data="claims" v-loading="loading">
      <el-table-column label="ID" prop="id" width="80" />
      <el-table-column label="Job Title" prop="jobTitle" />
      <el-table-column label="Claimant" prop="claimantUsername" />
      <el-table-column label="Status">
        <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="Assigned To">
        <template #default="{ row }">{{ row.assignedToUsername || 'Unassigned' }}</template>
      </el-table-column>
      <el-table-column label="Created">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="Actions" width="140">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDialog(row)">View</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="No claims found" /></template>
    </el-table>
    <div class="table-footer">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="pagination.page"
        :page-size="pagination.size"
        :total="pagination.total"
        @current-change="(val) => (pagination.page = val)"
      />
    </div>

    <el-dialog v-model="dialog.visible" title="Claim Details" width="600px">
      <template v-if="dialog.claim">
        <p><strong>Job:</strong> {{ dialog.claim.jobTitle }}</p>
        <p><strong>Claimant:</strong> {{ dialog.claim.claimantUsername }}</p>
        <p><strong>Description:</strong></p>
        <p class="muted">{{ dialog.claim.description }}</p>
        <div class="attachments">
          <h4>Attachments</h4>
          <FileUpload entityType="CLAIM" :entityId="dialog.claim.id" />
        </div>
        <el-form label-position="top">
          <el-form-item label="Status">
            <el-select v-model="dialog.status">
              <el-option v-for="status in statuses.slice(1)" :key="status" :label="status.replace('_', ' ')" :value="status" />
            </el-select>
          </el-form-item>
          <el-form-item label="Assigned To">
            <el-select v-model="dialog.assignedTo" clearable>
              <el-option v-for="user in adminUsers" :key="user.id" :label="user.username" :value="user.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="Resolution Notes">
            <el-input type="textarea" rows="3" v-model="dialog.resolution" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="dialog.visible = false">Close</el-button>
        <el-button type="primary" @click="saveClaim">Save</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.status-tabs {
  margin-bottom: 12px;
}

.table-footer {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.attachments {
  margin: 16px 0;
}

.muted {
  color: #6b7280;
}
</style>
