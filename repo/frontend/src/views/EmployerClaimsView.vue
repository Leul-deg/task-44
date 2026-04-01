<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import adminApi from '@/api/admin';

const activeTab = ref('claims');
const claims = ref([]);
const tickets = ref([]);
const loading = ref(false);

const showClaimDialog = ref(false);
const claimForm = ref({ jobPostingId: '', description: '' });

const showTicketDialog = ref(false);
const ticketForm = ref({ subject: '', description: '', priority: 'MEDIUM' });

const fetchData = async () => {
  loading.value = true;
  try {
    const [claimRes, ticketRes] = await Promise.all([
      adminApi.getClaims({ page: 0, size: 50 }),
      adminApi.getTickets({ page: 0, size: 50 })
    ]);
    claims.value = claimRes.data?.content ?? claimRes.data ?? [];
    tickets.value = ticketRes.data?.content ?? ticketRes.data ?? [];
  } finally {
    loading.value = false;
  }
};

const submitClaim = async () => {
  try {
    await adminApi.createClaim(claimForm.value);
    ElMessage.success('Claim submitted');
    showClaimDialog.value = false;
    claimForm.value = { jobPostingId: '', description: '' };
    fetchData();
  } catch (e) {
    ElMessage.error(e.response?.data?.message || 'Failed to submit claim');
  }
};

const submitTicket = async () => {
  try {
    await adminApi.createTicket(ticketForm.value);
    ElMessage.success('Ticket submitted');
    showTicketDialog.value = false;
    ticketForm.value = { subject: '', description: '', priority: 'MEDIUM' };
    fetchData();
  } catch (e) {
    ElMessage.error(e.response?.data?.message || 'Failed to submit ticket');
  }
};

onMounted(fetchData);
</script>

<template>
  <section class="page-card">
    <div class="header">
      <h2>My Claims & Tickets</h2>
      <div class="actions">
        <el-button type="primary" @click="showClaimDialog = true">File Claim</el-button>
        <el-button @click="showTicketDialog = true">Create Ticket</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="Claims" name="claims">
        <el-table :data="claims" v-loading="loading" stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="jobPostingId" label="Job ID" width="90" />
          <el-table-column prop="description" label="Description" show-overflow-tooltip />
          <el-table-column prop="status" label="Status" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === 'RESOLVED' ? 'success' : row.status === 'CLOSED' ? 'info' : 'warning'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="Filed" width="160" />
        </el-table>
        <el-empty v-if="!loading && !claims.length" description="No claims filed" />
      </el-tab-pane>

      <el-tab-pane label="Tickets" name="tickets">
        <el-table :data="tickets" v-loading="loading" stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="subject" label="Subject" show-overflow-tooltip />
          <el-table-column prop="priority" label="Priority" width="100">
            <template #default="{ row }">
              <el-tag :type="row.priority === 'CRITICAL' ? 'danger' : row.priority === 'HIGH' ? 'warning' : 'info'">{{ row.priority }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="Status" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === 'RESOLVED' ? 'success' : row.status === 'CLOSED' ? 'info' : 'warning'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="Filed" width="160" />
        </el-table>
        <el-empty v-if="!loading && !tickets.length" description="No tickets created" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showClaimDialog" title="File a Claim" width="500px">
      <el-form label-position="top">
        <el-form-item label="Job Posting ID">
          <el-input-number v-model="claimForm.jobPostingId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="claimForm.description" type="textarea" :rows="4" placeholder="Describe your claim..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showClaimDialog = false">Cancel</el-button>
        <el-button type="primary" @click="submitClaim">Submit Claim</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTicketDialog" title="Create a Ticket" width="500px">
      <el-form label-position="top">
        <el-form-item label="Subject">
          <el-input v-model="ticketForm.subject" placeholder="Brief summary..." />
        </el-form-item>
        <el-form-item label="Priority">
          <el-select v-model="ticketForm.priority" style="width: 100%">
            <el-option label="Low" value="LOW" />
            <el-option label="Medium" value="MEDIUM" />
            <el-option label="High" value="HIGH" />
            <el-option label="Critical" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="ticketForm.description" type="textarea" :rows="4" placeholder="Describe the issue..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTicketDialog = false">Cancel</el-button>
        <el-button type="primary" @click="submitTicket">Submit Ticket</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page-card {
  padding: 24px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.actions {
  display: flex;
  gap: 12px;
}
</style>
