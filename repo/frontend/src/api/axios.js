import axios from 'axios';
import { ElMessage } from 'element-plus';
import logger from '@/utils/logger';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true
});

let csrfTokenResolver = () => '';
let unauthorizedHandler = () => {};

export const setCsrfTokenResolver = (fn) => {
  csrfTokenResolver = fn;
};

export const setUnauthorizedHandler = (fn) => {
  unauthorizedHandler = fn;
};

api.interceptors.request.use((config) => {
  const method = config.method?.toLowerCase();
  if (['post', 'put', 'delete', 'patch'].includes(method)) {
    const token = csrfTokenResolver();
    if (token) {
      config.headers['X-XSRF-TOKEN'] = token;
    }
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    if (status === 401) {
      logger.warn('API', `Unauthorized (401) on ${error.config?.method?.toUpperCase()} ${error.config?.url}`);
      unauthorizedHandler();
    } else {
      const message = error.response?.data?.message ?? 'Unexpected error';
      logger.error('API', `HTTP ${status} on ${error.config?.method?.toUpperCase()} ${error.config?.url}: ${message}`);
      ElMessage.error(message);
    }
    return Promise.reject(error);
  }
);

export default api;
