<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppLayout from './layouts/AppLayout.vue';

const route = useRoute();
const authFreeRoutes = ['/login', '/register'];
const isAuthFree = computed(() => authFreeRoutes.includes(route.path));
</script>

<template>
  <AppLayout v-if="!isAuthFree">
    <RouterView />
  </AppLayout>
  <RouterView v-else />
</template>
