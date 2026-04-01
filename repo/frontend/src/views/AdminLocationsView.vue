<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import adminApi from '@/api/admin';

const locations = ref([]);
const loading = ref(false);
const stateFilter = ref('');
const dialog = reactive({ visible: false, id: null, state: '', city: '' });

const loadLocations = async () => {
  loading.value = true;
  try {
    const params = stateFilter.value ? { state: stateFilter.value } : {};
    const { data } = await adminApi.getAdminLocations(params);
    locations.value = data;
  } finally {
    loading.value = false;
  }
};

onMounted(loadLocations);
watch(stateFilter, loadLocations);

const openDialog = (location) => {
  if (location) {
    dialog.id = location.id;
    dialog.state = location.state;
    dialog.city = location.city;
  } else {
    dialog.id = null;
    dialog.state = '';
    dialog.city = '';
  }
  dialog.visible = true;
};

const saveLocation = async () => {
  const payload = { state: dialog.state, city: dialog.city };
  if (dialog.id) {
    await adminApi.updateLocation(dialog.id, payload);
    ElMessage.success('Location updated');
  } else {
    await adminApi.createLocation(payload);
    ElMessage.success('Location added');
  }
  dialog.visible = false;
  loadLocations();
};

const deactivate = async (location) => {
  try {
    await ElMessageBox.confirm(`Deactivate ${location.city}, ${location.state}?`, 'Deactivate Location');
  } catch {
    return;
  }
  try {
    await adminApi.deleteLocation(location.id);
    ElMessage.success('Location updated');
  } catch (error) {
    ElMessage.error(error.response?.data?.message ?? 'Unable to deactivate');
  } finally {
    loadLocations();
  }
};

const statusTag = (active) => (active ? 'success' : 'info');
</script>

<template>
  <section class="page-card">
    <div class="toolbar">
      <div>
        <h1>Service Locations</h1>
        <p class="muted">Govern which city/state pairs appear to employers.</p>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="stateFilter" placeholder="Filter by state" clearable>
          <el-option
            v-for="state in [...new Set(locations.map((loc) => loc.state))]"
            :key="state"
            :label="state"
            :value="state"
          />
        </el-select>
        <el-button type="primary" @click="openDialog(null)">Add Location</el-button>
      </div>
    </div>
    <el-table :data="locations" v-loading="loading">
      <el-table-column label="State" prop="state" />
      <el-table-column label="City" prop="city" />
      <el-table-column label="Active">
        <template #default="{ row }"><el-tag :type="statusTag(row.active)">{{ row.active ? 'Active' : 'Inactive' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="Created">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleDateString() }}</template>
      </el-table-column>
      <el-table-column label="Actions" width="200">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDialog(row)">Edit</el-button>
          <el-button v-if="row.active" type="danger" link @click="deactivate(row)">Deactivate</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? 'Edit Location' : 'Add Location'" width="420px">
      <el-form label-position="top">
        <el-form-item label="State">
          <el-input v-model="dialog.state" />
        </el-form-item>
        <el-form-item label="City">
          <el-input v-model="dialog.city" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">Cancel</el-button>
        <el-button type="primary" @click="saveLocation">Save</el-button>
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
}

.toolbar-actions {
  display: flex;
  gap: 12px;
}

.muted {
  color: #6b7280;
}
</style>
