import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { createPinia } from 'pinia';
import { useAuthStore } from './stores/auth';

import 'element-plus/dist/index.css';
import './styles/main.css';

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);

const authStore = useAuthStore();
authStore.restoreSession().then(() => {
  app.use(router);
  app.mount('#app');
});
