<script setup>
import { ref, reactive, onMounted, watch } from 'vue';
import { getAuditLogs, getAuditLogDetail } from '../api/audit';
import { ElMessage } from 'element-plus';

const loading = ref(false);
const logs = ref([]);
const total = ref(0);
const page = ref(0);
const size = ref(20);

const filters = reactive({
  entityType: '',
  action: '',
  userId: null,
  from: null,
  to: null
});

const entityTypes = ['USER', 'JOB_POSTING', 'APPEAL', 'CLAIM', 'TICKET', 'FILE', 'REPORT'];
const actions = [
  'USER_LOGIN', 'USER_LOGIN_FAILED', 'USER_LOGOUT', 'USER_CREATED',
  'USER_ROLE_CHANGED', 'USER_STATUS_CHANGED', 'PASSWORD_CHANGED',
  'JOB_CREATED', 'JOB_EDITED', 'JOB_SUBMITTED', 'JOB_APPROVED',
  'JOB_REJECTED', 'JOB_PUBLISHED', 'JOB_UNPUBLISHED', 'JOB_TAKEN_DOWN',
  'APPEAL_CREATED', 'APPEAL_PROCESSED',
  'CLAIM_CREATED', 'CLAIM_UPDATED',
  'TICKET_CREATED',
  'FILE_UPLOADED', 'FILE_QUARANTINED',
  'DATA_EXPORTED'
];

const detailDialog = reactive({ visible: false, loading: false, data: null });

async function loadLogs() {
  loading.value = true;
  try {
    const params = { page: page.value, size: size.value };
    if (filters.entityType) params.entityType = filters.entityType;
    if (filters.action) params.action = filters.action;
    if (filters.userId) params.userId = filters.userId;
    if (filters.from) params.from = filters.from.toISOString();
    if (filters.to) params.to = filters.to.toISOString();
    const { data } = await getAuditLogs(params);
    logs.value = data.items;
    total.value = data.totalElements;
  } catch {
    ElMessage.error('Failed to load audit logs');
  } finally {
    loading.value = false;
  }
}

function handlePageChange(newPage) {
  page.value = newPage - 1;
  loadLogs();
}

function resetFilters() {
  filters.entityType = '';
  filters.action = '';
  filters.userId = null;
  filters.from = null;
  filters.to = null;
  page.value = 0;
  loadLogs();
}

async function showDetail(row) {
  detailDialog.visible = true;
  detailDialog.loading = true;
  try {
    const { data } = await getAuditLogDetail(row.id);
    detailDialog.data = data;
  } catch (error) {
    ElMessage.error('Failed to load audit detail');
  } finally {
    detailDialog.loading = false;
  }
}

function formatJson(value) {
  if (!value) return '—';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch (error) {
    return value;
  }
}

function actionTagType(action) {
  if (action.includes('LOGIN_FAILED')) return 'danger';
  if (action.includes('LOGIN') || action.includes('CREATED')) return 'success';
  if (action.includes('TAKEN_DOWN') || action.includes('REJECTED') || action.includes('QUARANTINED')) return 'danger';
  if (action.includes('APPROVED') || action.includes('PUBLISHED')) return 'success';
  if (action.includes('CHANGED') || action.includes('EDITED') || action.includes('UPDATED')) return 'warning';
  return 'info';
}

function formatDate(val) {
  if (!val) return '—';
  return new Date(val).toLocaleString();
}

watch([() => filters.entityType, () => filters.action], () => {
  page.value = 0;
  loadLogs();
});

onMounted(loadLogs);
</script>

<template>
  <section class="page-card">
    <h1>Audit Log</h1>
    <p class="subtitle">Immutable record of all sensitive actions.</p>

    <div class="toolbar">
      <el-select v-model="filters.entityType" placeholder="Entity Type" clearable style="width: 160px">
        <el-option v-for="t in entityTypes" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="filters.action" placeholder="Action" clearable filterable style="width: 200px">
        <el-option v-for="a in actions" :key="a" :label="a" :value="a" />
      </el-select>
      <el-input-number v-model="filters.userId" placeholder="User ID" :min="1" :controls="false" style="width: 120px" />
      <el-date-picker v-model="filters.from" type="datetime" placeholder="From" style="width: 200px" @change="loadLogs" />
      <el-date-picker v-model="filters.to" type="datetime" placeholder="To" style="width: 200px" @change="loadLogs" />
      <el-button @click="loadLogs" type="primary">Search</el-button>
      <el-button @click="resetFilters">Reset</el-button>
    </div>

    <el-table :data="logs" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="createdAt" label="Timestamp" width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="username" label="User" width="140">
        <template #default="{ row }">{{ row.username || '—' }}</template>
      </el-table-column>
      <el-table-column prop="action" label="Action" width="200">
        <template #default="{ row }">
          <el-tag :type="actionTagType(row.action)" size="small">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="entityType" label="Entity Type" width="140" />
      <el-table-column prop="entityId" label="Entity ID" width="100" />
      <el-table-column prop="ipAddress" label="IP" width="140" />
      <el-table-column label="Actions" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="showDetail(row)">Details</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > size"
      layout="prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page + 1"
      @current-change="handlePageChange"
      class="pagination"
    />

    <el-dialog v-model="detailDialog.visible" title="Audit Log Detail" width="720px">
      <div v-loading="detailDialog.loading">
        <template v-if="detailDialog.data">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="ID">{{ detailDialog.data.id }}</el-descriptions-item>
            <el-descriptions-item label="User">{{ detailDialog.data.username || '—' }}</el-descriptions-item>
            <el-descriptions-item label="Action">
              <el-tag :type="actionTagType(detailDialog.data.action)" size="small">{{ detailDialog.data.action }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Entity">{{ detailDialog.data.entityType }} #{{ detailDialog.data.entityId }}</el-descriptions-item>
            <el-descriptions-item label="IP">{{ detailDialog.data.ipAddress || '—' }}</el-descriptions-item>
            <el-descriptions-item label="Timestamp">{{ formatDate(detailDialog.data.createdAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="diff-panels">
            <div class="diff-panel">
              <h4>Before</h4>
              <pre class="json-block">{{ formatJson(detailDialog.data.beforeValue) }}</pre>
            </div>
            <div class="diff-panel">
              <h4>After</h4>
              <pre class="json-block">{{ formatJson(detailDialog.data.afterValue) }}</pre>
            </div>
          </div>
        </template>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.page-card { padding: 24px; }
.subtitle { color: #909399; margin-bottom: 16px; }
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 16px;
}
.pagination { margin-top: 16px; justify-content: center; }
.diff-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 16px;
}
.diff-panel h4 { margin-bottom: 8px; font-weight: 600; }
.json-block {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  font-size: 12px;
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
