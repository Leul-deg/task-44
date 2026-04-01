<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import adminApi from '@/api/admin';
import StepUpVerification from '@/components/common/StepUpVerification.vue';

const roles = ['EMPLOYER', 'REVIEWER', 'ADMIN'];
const statuses = ['ACTIVE', 'LOCKED', 'DISABLED'];

const filters = reactive({ search: '', role: '', status: '' });
const pagination = reactive({ page: 1, size: 10, total: 0 });
const users = ref([]);
const loading = ref(false);

const createDialog = reactive({ visible: false, username: '', email: '', password: '', role: 'EMPLOYER' });
const editDialog = reactive({ visible: false, id: null, email: '', status: 'ACTIVE' });
const roleDialog = reactive({ visible: false, id: null, role: 'EMPLOYER' });
const resetDialog = reactive({ visible: false, password: '' });
const showStepUp = ref(false);
const stepUpTarget = ref(null);
const stepUpLoading = ref(false);

const loadUsers = async () => {
  loading.value = true;
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      search: filters.search || undefined,
      role: filters.role || undefined,
      status: filters.status || undefined
    };
    const { data } = await adminApi.getUsers(params);
    users.value = data.items;
    pagination.total = data.totalElements;
  } finally {
    loading.value = false;
  }
};

watch(() => [filters.role, filters.status], () => {
  pagination.page = 1;
  loadUsers();
});

watch(() => filters.search, () => {
  pagination.page = 1;
  loadUsers();
});

watch(() => pagination.page, loadUsers);

onMounted(loadUsers);

const openCreate = () => {
  Object.assign(createDialog, { visible: true, username: '', email: '', password: '', role: 'EMPLOYER' });
};

const submitCreate = async () => {
  if (createDialog.password.length < 12) {
    ElMessage.warning('Password must be at least 12 characters');
    return;
  }
  await adminApi.createUser({
    username: createDialog.username,
    email: createDialog.email,
    password: createDialog.password,
    role: createDialog.role
  });
  ElMessage.success('User created');
  createDialog.visible = false;
  loadUsers();
};

const openEdit = (user) => {
  Object.assign(editDialog, {
    visible: true,
    id: user.id,
    email: user.email,
    status: user.status
  });
};

const submitEdit = async () => {
  await adminApi.updateUser(editDialog.id, {
    email: editDialog.email,
    status: editDialog.status
  });
  ElMessage.success('User updated');
  editDialog.visible = false;
  loadUsers();
};

const openRoleChange = (user) => {
  stepUpTarget.value = user;
  showStepUp.value = true;
};

const handleStepUp = (password) => {
  showStepUp.value = false;
  roleDialog.visible = true;
  roleDialog.id = stepUpTarget.value.id;
  roleDialog.role = stepUpTarget.value.role;
  roleDialog.stepUpPassword = password;
};

const submitRoleChange = async () => {
  await adminApi.changeRole(roleDialog.id, { role: roleDialog.role, stepUpPassword: roleDialog.stepUpPassword });
  ElMessage.success('Role updated');
  roleDialog.visible = false;
  loadUsers();
};

const unlock = async (user) => {
  await ElMessageBox.confirm(`Unlock ${user.username}?`, 'Unlock Account');
  await adminApi.unlockUser(user.id);
  ElMessage.success('Account unlocked');
  loadUsers();
};

const resetPassword = async (user) => {
  await ElMessageBox.confirm(`Reset password for ${user.username}?`, 'Reset Password');
  const { data } = await adminApi.resetPassword(user.id);
  resetDialog.visible = true;
  resetDialog.password = data.temporaryPassword;
};

const canUnlock = (user) => user.status === 'LOCKED';

const tableRoleTag = (role) => ({
  EMPLOYER: 'info',
  REVIEWER: 'warning',
  ADMIN: 'success'
}[role] || 'info');

const tableStatusTag = (status) => ({
  ACTIVE: 'success',
  LOCKED: 'warning',
  DISABLED: 'danger'
}[status] || 'info');
</script>

<template>
  <section class="page-card">
    <div class="toolbar">
      <div>
        <h1>User Directory</h1>
        <p class="muted">Manage employers, reviewers, and administrators.</p>
      </div>
      <el-button type="primary" @click="openCreate">Create User</el-button>
    </div>
    <div class="filters">
      <el-input v-model="filters.search" placeholder="Search username or email" clearable />
      <el-select v-model="filters.role" placeholder="Role" clearable>
        <el-option v-for="role in roles" :key="role" :label="role" :value="role" />
      </el-select>
      <el-select v-model="filters.status" placeholder="Status" clearable>
        <el-option v-for="status in statuses" :key="status" :label="status" :value="status" />
      </el-select>
    </div>
    <el-table :data="users" v-loading="loading">
      <el-table-column label="Username" prop="username" />
      <el-table-column label="Email" prop="email" />
      <el-table-column label="Role">
        <template #default="{ row }">
          <el-tag :type="tableRoleTag(row.role)">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Status">
        <template #default="{ row }">
          <el-tag :type="tableStatusTag(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Created">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleDateString() }}</template>
      </el-table-column>
      <el-table-column label="Actions" width="320">
        <template #default="{ row }">
          <el-button type="primary" link @click="openEdit(row)">Edit</el-button>
          <el-button type="primary" link @click="openRoleChange(row)">Change Role</el-button>
          <el-button v-if="canUnlock(row)" type="warning" link @click="unlock(row)">Unlock</el-button>
          <el-button type="danger" link @click="resetPassword(row)">Reset Password</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="No users found" /></template>
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

    <el-dialog v-model="createDialog.visible" title="Create User" width="480px">
      <el-form label-position="top">
        <el-form-item label="Username">
          <el-input v-model="createDialog.username" />
        </el-form-item>
        <el-form-item label="Email">
          <el-input v-model="createDialog.email" />
        </el-form-item>
        <el-form-item label="Password">
          <el-input v-model="createDialog.password" type="password" show-password />
          <small class="muted">Min 12 chars with uppercase, lowercase, digit, special.</small>
        </el-form-item>
        <el-form-item label="Role">
          <el-select v-model="createDialog.role">
            <el-option v-for="role in roles" :key="role" :label="role" :value="role" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">Cancel</el-button>
        <el-button type="primary" @click="submitCreate">Create</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialog.visible" title="Edit User" width="420px">
      <el-form label-position="top">
        <el-form-item label="Email">
          <el-input v-model="editDialog.email" />
        </el-form-item>
        <el-form-item label="Status">
          <el-select v-model="editDialog.status">
            <el-option v-for="status in statuses" :key="status" :label="status" :value="status" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">Cancel</el-button>
        <el-button type="primary" @click="submitEdit">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog.visible" title="Change Role" width="360px">
      <el-select v-model="roleDialog.role" class="w-100">
        <el-option v-for="role in roles" :key="role" :label="role" :value="role" />
      </el-select>
      <template #footer>
        <el-button @click="roleDialog.visible = false">Cancel</el-button>
        <el-button type="primary" @click="submitRoleChange">Update Role</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialog.visible" title="Temporary Password" width="360px">
      <el-alert type="info" :closable="false">
        <strong>{{ resetDialog.password }}</strong>
        <p class="muted">Share this password securely; user will be prompted to change on next login.</p>
      </el-alert>
      <template #footer>
        <el-button type="primary" @click="resetDialog.visible = false">Close</el-button>
      </template>
    </el-dialog>

    <StepUpVerification v-model="showStepUp" :loading="stepUpLoading" @verified="handleStepUp" />
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.filters {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.table-footer {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.w-100 {
  width: 100%;
}

.muted {
  color: #6b7280;
}

small.muted {
  display: block;
  margin-top: 4px;
}
</style>
