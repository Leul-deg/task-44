import { defineStore } from 'pinia';
import api, { setCsrfTokenResolver, setUnauthorizedHandler } from '@/api/axios';
import { ElMessage } from 'element-plus';

const USER_KEY = 'shiftworks-user';
const CSRF_KEY = 'shiftworks-csrf';

const readCookie = (name) => {
  const match = document.cookie.split(';').map((item) => item.trim()).find((item) => item.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.split('=').slice(1).join('=')) : '';
};

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: JSON.parse(sessionStorage.getItem(USER_KEY) || 'null'),
    csrfToken: sessionStorage.getItem(CSRF_KEY) || '',
    initialized: false,
    interceptorsReady: false
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.user),
    role: (state) => state.user?.role ?? null,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isReviewer: (state) => state.user?.role === 'REVIEWER',
    isEmployer: (state) => state.user?.role === 'EMPLOYER',
    username: (state) => state.user?.username ?? '',
    initials: (state) => {
      if (!state.user?.username) return 'SW';
      return state.user.username
        .split(' ')
        .map((chunk) => chunk[0])
        .join('')
        .slice(0, 2)
        .toUpperCase();
    }
  },
  actions: {
    initInterceptors() {
      if (this.interceptorsReady) return;
      setCsrfTokenResolver(() => this.csrfToken);
      setUnauthorizedHandler(() => {
        this.clearSession();
        if (window.location.pathname !== '/login') {
          ElMessage.error('Session expired. Please log in again.');
          window.location.href = '/login';
        }
      });
      this.interceptorsReady = true;
    },
    async restoreSession() {
      this.initInterceptors();
      if (!this.csrfToken) {
        const csrfFromCookie = readCookie('XSRF-TOKEN');
        if (csrfFromCookie) {
          this.csrfToken = csrfFromCookie;
          sessionStorage.setItem(CSRF_KEY, csrfFromCookie);
        }
      }
      if (!this.user) {
        const storedUser = sessionStorage.getItem(USER_KEY);
        if (storedUser) {
          this.user = JSON.parse(storedUser);
        }
      }
      const hasSessionCookie = document.cookie.includes('SESSION_TOKEN=');
      if (hasSessionCookie || this.user) {
        try {
          await this.fetchMe();
        } catch (error) {
          // ignore, interceptor will handle redirect
        }
      }
      this.initialized = true;
    },
    setSession(response) {
      this.user = response.user;
      this.csrfToken = response.csrfToken;
      sessionStorage.setItem(USER_KEY, JSON.stringify(response.user));
      sessionStorage.setItem(CSRF_KEY, response.csrfToken);
    },
    clearSession() {
      this.user = null;
      this.csrfToken = '';
      sessionStorage.removeItem(USER_KEY);
      sessionStorage.removeItem(CSRF_KEY);
    },
    async fetchMe() {
      const { data } = await api.get('/auth/me');
      this.user = data;
      sessionStorage.setItem(USER_KEY, JSON.stringify(data));
    },
    async login(payload) {
      const { data } = await api.post('/auth/login', payload);
      this.setSession(data);
      return data;
    },
    async logout() {
      try {
        await api.post('/auth/logout');
      } finally {
        this.clearSession();
        window.location.href = '/login';
      }
    },
    async register(payload) {
      await api.post('/auth/register', payload);
    },
    async changePassword(payload) {
      await api.post('/auth/change-password', payload);
      this.clearSession();
    }
  }
});
