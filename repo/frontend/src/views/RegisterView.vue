<script setup>
import { reactive, computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue';

const router = useRouter();
const authStore = useAuthStore();

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
});

const loading = ref(false);
const errorMessage = ref('');

const checklist = computed(() => ({
  length: form.password.length >= 12,
  upper: /[A-Z]/.test(form.password),
  lower: /[a-z]/.test(form.password),
  digit: /\d/.test(form.password),
  special: /[^A-Za-z0-9]/.test(form.password)
}));

const checklistItems = [
  { key: 'length', label: 'At least 12 characters' },
  { key: 'upper', label: 'Contains uppercase letter' },
  { key: 'lower', label: 'Contains lowercase letter' },
  { key: 'digit', label: 'Contains a number' },
  { key: 'special', label: 'Contains special character' }
];

const handleRegister = async () => {
  errorMessage.value = '';
  if (form.password !== form.confirmPassword) {
    errorMessage.value = 'Passwords do not match';
    return;
  }
  if (Object.values(checklist.value).some((item) => !item)) {
    errorMessage.value = 'Password does not meet complexity requirements';
    return;
  }
  loading.value = true;
  try {
    await authStore.register({
      username: form.username,
      email: form.email,
      password: form.password
    });
    ElMessage.success('Account created! Please sign in.');
    router.push('/login');
  } catch (error) {
    errorMessage.value = error.response?.data?.message ?? 'Unable to register';
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="hover">
      <h1>Join ShiftWorks</h1>
      <p class="auth-card__subtitle">Create your employer profile to begin publishing shifts</p>
      <el-alert v-if="errorMessage" type="error" :closable="false" class="mb-16">
        {{ errorMessage }}
      </el-alert>
      <el-form label-position="top" @submit.prevent="handleRegister">
        <el-form-item label="Employer Username">
          <el-input v-model.trim="form.username" autocomplete="off" />
        </el-form-item>
        <el-form-item label="Work Email">
          <el-input v-model.trim="form.email" type="email" autocomplete="email" />
        </el-form-item>
        <el-form-item label="Password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="Confirm Password">
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
        <el-button type="primary" class="w-100" :loading="loading" native-type="submit">
          Create Account
        </el-button>
        <div class="auth-card__footer">
          <span>Already have an account?</span>
          <RouterLink to="/login">Sign in</RouterLink>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
