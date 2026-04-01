<script setup>
import { reactive, computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue';

const router = useRouter();
const authStore = useAuthStore();

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const loading = ref(false);
const errorMessage = ref('');

const checklist = computed(() => ({
  length: form.newPassword.length >= 12,
  upper: /[A-Z]/.test(form.newPassword),
  lower: /[a-z]/.test(form.newPassword),
  digit: /\d/.test(form.newPassword),
  special: /[^A-Za-z0-9]/.test(form.newPassword)
}));

const checklistItems = [
  { key: 'length', label: 'At least 12 characters' },
  { key: 'upper', label: 'Contains uppercase letter' },
  { key: 'lower', label: 'Contains lowercase letter' },
  { key: 'digit', label: 'Contains a number' },
  { key: 'special', label: 'Contains special character' }
];

const handleChange = async () => {
  errorMessage.value = '';
  if (form.newPassword !== form.confirmPassword) {
    errorMessage.value = 'New passwords do not match';
    return;
  }
  if (Object.values(checklist.value).some((item) => !item)) {
    errorMessage.value = 'Password does not meet policy requirements';
    return;
  }
  loading.value = true;
  try {
    await authStore.changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    });
    ElMessage.success('Password updated. Please sign in again.');
    router.push('/login');
  } catch (error) {
    errorMessage.value = error.response?.data?.message ?? 'Unable to change password';
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <section class="page-card narrow">
    <h1>Security Checkpoint</h1>
    <p class="muted">Your password expired. Update it to continue using ShiftWorks.</p>
    <el-alert v-if="errorMessage" type="error" :closable="false" class="mb-16">
      {{ errorMessage }}
    </el-alert>
    <el-form label-position="top" @submit.prevent="handleChange">
      <el-form-item label="Current Password">
        <el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="New Password">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="Confirm New Password">
        <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <ul class="password-rules">
        <li v-for="item in checklistItems" :key="item.key" :class="{ pass: checklist[item.key] }">
          <el-icon v-if="checklist[item.key]" :size="16" color="#13ce66">
            <CircleCheckFilled />
          </el-icon>
          <el-icon v-else :size="16" color="#f56c6c">
            <CircleCloseFilled />
          </el-icon>
          {{ item.label }}
        </li>
      </ul>
      <el-button type="primary" :loading="loading" native-type="submit">
        Update Password
      </el-button>
    </el-form>
  </section>
</template>
