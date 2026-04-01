<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import adminApi from '@/api/admin';
import FileUpload from '@/components/common/FileUpload.vue';

const statuses = ['ALL', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const priorities = ['ALL', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const filters = reactive({ status: 'ALL', priority: 'ALL' });
const pagination = reactive({ page: 1, size: 10, total: 0 });
const tickets = ref([]);
const loading = ref(false);
const adminUsers = ref([]);

const dialog = reactive({ visible: false, ticket: null, status: 'OPEN', priority: 'LOW', resolution: '', assignedTo: null });

const loadTickets = async () => {
  loading.value = true;
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      status: filters.status !== 'ALL' ? filters.status : undefined,
      priority: filters.priority !== 'ALL' ? filters.priority : undefined
    };
    const { data } = await adminApi.getTickets(params);
    tickets.value = data.items;
    pagination.total = data.totalElements;
  } finally {
    loading.value = false;
  }
};

const loadAdmins = async () => {
  const { data } = await adminApi.getUsers({ role: 'ADMIN', page: 0, size: 100 });
  adminUsers.value = data.items;
};

watch(() => [filters.status, filters.priority], () => {
  pagination.page = 1;
  loadTickets();
});

watch(() => pagination.page, loadTickets);

onMounted(() => {
  loadTickets();
  loadAdmins();
});

const openDialog = (ticket) => {
  dialog.visible = true;
  dialog.ticket = ticket;
  dialog.status = ticket.status;
  dialog.priority = ticket.priority;
  dialog.resolution = ticket.resolution || '';
  dialog.assignedTo = ticket.assignedTo || null;
};

const saveTicket = async () => {
  await adminApi.updateTicket(dialog.ticket.id, {
    status: dialog.status,
    priority: dialog.priority,
    resolution: dialog.resolution,
    assignedTo: dialog.assignedTo
  });
  ElMessage.success('Ticket updated');
  dialog.visible = false;
  loadTickets();
};

const statusTag = (status) => ({
  OPEN: 'warning',
  IN_PROGRESS: 'info',
  RESOLVED: 'success',
  CLOSED: 'default'
}[status] || 'info');

const priorityTag = (priority) => ({
  LOW: 'info',
  MEDIUM: 'warning',
  HIGH: 'danger',
  CRITICAL: 'danger'
}[priority] || 'info');

const priorityLabel = (priority) => (priority === 'CRITICAL' ? 'CRITICAL ⚠' : priority);
</script>

<template>
  <section class="page-card">
    <div class="toolbar">
      <div>
        <h1>Support Tickets</h1>
        <p class="muted">Track product feedback and operational issues.</p>
      </div>
      <div class="filters">
        <el-select v-model="filters.status" placeholder="Status">
          <el-option v-for="status in statuses" :key="status" :label="status.replace('_', ' ')" :value="status" />
        </el-select>
        <el-select v-model="filters.priority" placeholder="Priority">
          <el-option v-for="priority in priorities" :key="priority" :label="priority" :value="priority" />
        </el-select>
      </div>
    </div>
    <el-table :data="tickets" v-loading="loading">
      <el-table-column label="Subject" prop="subject" />
      <el-table-column label="Reporter" prop="reporterUsername" />
      <el-table-column label="Priority">
        <template #default="{ row }"><el-tag :type="priorityTag(row.priority)">{{ priorityLabel(row.priority) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="Status">
        <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="Assigned To">
        <template #default="{ row }">{{ row.assignedToUsername || 'Unassigned' }}</template>
      </el-table-column>
      <el-table-column label="Created">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="Actions" width="120">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDialog(row)">View</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="No tickets found" /></template>
    </el-table>
    <div class="table-footer">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="pagination.page"
        :total="pagination.total"
        :page-size="pagination.size"
        @current-change="(val) => (pagination.page = val)"
      />
    </div>

    <el-dialog v-model="dialog.visible" title="Ticket Details" width="600px">
      <template v-if="dialog.ticket">
        <p><strong>Reporter:</strong> {{ dialog.ticket.reporterUsername }}</p>
        <p><strong>Description:</strong></p>
        <p class="muted">{{ dialog.ticket.description }}</p>
        <div class="attachments">
          <h4>Attachments</h4>
          <FileUpload entityType="TICKET" :entityId="dialog.ticket.id" />
        </div>
        <el-form label-position="top">
          <el-form-item label="Status">
            <el-select v-model="dialog.status">
              <el-option v-for="status in statuses.slice(1)" :key="status" :label="status.replace('_', ' ')" :value="status" />
            </el-select>
          </el-form-item>
          <el-form-item label="Priority">
            <el-select v-model="dialog.priority">
              <el-option v-for="priority in priorities.slice(1)" :key="priority" :label="priority" :value="priority" />
            </el-select>
          </el-form-item>
          <el-form-item label="Assigned To">
            <el-select v-model="dialog.assignedTo" clearable>
              <el-option v-for="user in adminUsers" :key="user.id" :label="user.username" :value="user.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="Resolution">
            <el-input type="textarea" rows="3" v-model="dialog.resolution" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="dialog.visible = false">Close</el-button>
        <el-button type="primary" @click="saveTicket">Save</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 16px;
}

.filters {
  display: flex;
  gap: 12px;
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
