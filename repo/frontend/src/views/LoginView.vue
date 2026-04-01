<script setup>
import { reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import api from '@/api/axios';
import { defaultRouteForRole } from '@/router';

const authStore = useAuthStore();
const router = useRouter();

const form = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaAnswer: ''
});

const loading = ref(false);
const errorMessage = ref('');
const captchaRequired = ref(false);
const captchaImage = ref('');

const fetchCaptcha = async () => {
  const { data } = await api.get('/auth/captcha');
  form.captchaId = data.captchaId;
  captchaImage.value = data.imageBase64;
  form.captchaAnswer = '';
};

const handleLogin = async () => {
  errorMessage.value = '';
  loading.value = true;
  try {
    const payload = {
      username: form.username,
      password: form.password,
      captchaId: captchaRequired.value ? form.captchaId : undefined,
      captchaAnswer: captchaRequired.value ? form.captchaAnswer : undefined
    };
    const response = await authStore.login(payload);
    if (response.passwordExpired) {
      router.push('/change-password');
    } else {
      router.push(defaultRouteForRole(response.user.role));
    }
  } catch (error) {
    const data = error.response?.data;
    captchaRequired.value = Boolean(data?.captchaRequired);
    errorMessage.value = data?.message ?? 'Unable to sign in';
  } finally {
    loading.value = false;
  }
};

watch(captchaRequired, async (value) => {
  if (value) {
    await fetchCaptcha();
  }
});

</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="hover">
      <h1>ShiftWorks JobOps</h1>
      <p class="auth-card__subtitle">Secure access for employers, reviewers, and admins</p>
      <el-alert v-if="errorMessage" type="error" :closable="false" class="mb-16">
        {{ errorMessage }}
      </el-alert>
      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="Username">
          <el-input v-model.trim="form.username" autocomplete="username" placeholder="Enter your username" />
        </el-form-item>
        <el-form-item label="Password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <div v-if="captchaRequired" class="captcha-box">
          <label>Captcha Verification</label>
          <div class="captcha-box__content">
            <img :src="captchaImage" alt="captcha" />
            <el-button text type="primary" @click.prevent="fetchCaptcha">Refresh</el-button>
          </div>
          <el-input v-model="form.captchaAnswer" placeholder="Enter the characters" />
        </div>
        <el-button type="primary" round size="large" :loading="loading" class="w-100" native-type="submit">
          Sign In
        </el-button>
      </el-form>
      <div class="auth-card__footer">
        <span>Contact your administrator for account provisioning.</span>
      </div>
    </el-card>
  </div>
</template>
