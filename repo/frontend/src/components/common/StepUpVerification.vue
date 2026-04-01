<script setup>
import { ref } from 'vue';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
});

const emit = defineEmits(['update:modelValue', 'verified']);

const password = ref('');
const errorMessage = ref('');

const closeDialog = () => {
  password.value = '';
  errorMessage.value = '';
  emit('update:modelValue', false);
};

const handleVerify = () => {
  if (!password.value) {
    errorMessage.value = 'Please enter your password';
    return;
  }
  errorMessage.value = '';
  emit('verified', password.value);
};
</script>

<template>
  <el-dialog :model-value="props.modelValue" title="Step-Up Verification" width="420px" @close="closeDialog">
    <p class="muted">Confirm your identity to continue with this sensitive action.</p>
    <el-alert v-if="errorMessage" type="error" :closable="false" class="mb-16">
      {{ errorMessage }}
    </el-alert>
    <el-input v-model="password" type="password" show-password placeholder="Enter your password" />
    <template #footer>
      <el-button @click="closeDialog">Cancel</el-button>
      <el-button type="primary" :loading="props.loading" @click="handleVerify">
        Verify
      </el-button>
    </template>
  </el-dialog>
</template>
