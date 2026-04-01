<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import adminApi from '@/api/admin';

const categories = ref([]);
const loading = ref(false);
const dialog = reactive({ visible: false, id: null, name: '', description: '' });

const loadCategories = async () => {
  loading.value = true;
  try {
    const { data } = await adminApi.getAdminCategories();
    categories.value = data;
  } finally {
    loading.value = false;
  }
};

onMounted(loadCategories);

const openDialog = (category) => {
  if (category) {
    dialog.id = category.id;
    dialog.name = category.name;
    dialog.description = category.description;
  } else {
    dialog.id = null;
    dialog.name = '';
    dialog.description = '';
  }
  dialog.visible = true;
};

const saveCategory = async () => {
  if (dialog.id) {
    await adminApi.updateCategory(dialog.id, { name: dialog.name, description: dialog.description });
    ElMessage.success('Category updated');
  } else {
    await adminApi.createCategory({ name: dialog.name, description: dialog.description });
    ElMessage.success('Category created');
  }
  dialog.visible = false;
  loadCategories();
};

const deactivate = async (category) => {
  try {
    await ElMessageBox.confirm(`Deactivate ${category.name}?`, 'Deactivate Category');
  } catch {
    return;
  }
  try {
    await adminApi.deleteCategory(category.id);
    ElMessage.success('Category updated');
  } catch (error) {
    ElMessage.error(error.response?.data?.message ?? 'Unable to deactivate');
  } finally {
    loadCategories();
  }
};

const statusTag = (category) => category.active ? 'success' : 'info';
</script>

<template>
  <section class="page-card">
    <div class="toolbar">
      <div>
        <h1>Job Categories</h1>
        <p class="muted">Control the taxonomy employers choose from.</p>
      </div>
      <el-button type="primary" @click="openDialog(null)">Add Category</el-button>
    </div>
    <el-table :data="categories" v-loading="loading">
      <el-table-column label="Name" prop="name" />
      <el-table-column label="Description" prop="description" />
      <el-table-column label="Active">
        <template #default="{ row }"><el-tag :type="statusTag(row)">{{ row.active ? 'Active' : 'Inactive' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="Active Postings" prop="activePostings" />
      <el-table-column label="Created">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleDateString() }}</template>
      </el-table-column>
      <el-table-column label="Actions" width="220">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDialog(row)">Edit</el-button>
          <el-button v-if="row.active" type="danger" link @click="deactivate(row)">Deactivate</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? 'Edit Category' : 'Add Category'" width="420px">
      <el-form label-position="top">
        <el-form-item label="Name">
          <el-input v-model="dialog.name" />
        </el-form-item>
        <el-form-item label="Description">
          <el-input type="textarea" rows="3" v-model="dialog.description" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">Cancel</el-button>
        <el-button type="primary" @click="saveCategory">Save</el-button>
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

.muted {
  color: #6b7280;
}
</style>
