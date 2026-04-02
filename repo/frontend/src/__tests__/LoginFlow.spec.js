import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

const mockPush = vi.fn();
const mockLogin = vi.fn();
const mockRestoreSession = vi.fn();

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ query: {} }),
  createRouter: vi.fn(() => ({ addRoute: vi.fn(), beforeEach: vi.fn() })),
  createWebHistory: vi.fn()
}));

vi.mock('@/router', () => ({
  defaultRouteForRole: (role) => `/${role.toLowerCase()}/dashboard`,
  default: { push: vi.fn(), beforeEach: vi.fn(), isReady: vi.fn() }
}));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isLoggedIn: false,
    role: null,
    login: mockLogin,
    restoreSession: mockRestoreSession
  })
}));

vi.mock('@/api/axios', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: { captchaId: 'test', imageBase64: '' } }) },
  setCsrfTokenResolver: vi.fn(),
  setUnauthorizedHandler: vi.fn()
}));

const stubs = {
  'el-form': { template: '<div class="el-form"><slot/></div>' },
  'el-form-item': { template: '<div class="form-item"><slot/></div>' },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type']
  },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot/></button>', props: ['loading', 'type'] },
  'el-alert': { template: '<div class="alert"><slot/></div>', props: ['type'] },
  'el-card': { template: '<div class="card"><slot/></div>' }
};

describe('LoginView interaction flows', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('successful login redirects to role-based dashboard', async () => {
    mockLogin.mockResolvedValue({
      user: { id: 1, username: 'employer1', role: 'EMPLOYER' },
      csrfToken: 'abc',
      passwordExpired: false
    });
    const LoginView = (await import('@/views/LoginView.vue')).default;
    const wrapper = mount(LoginView, { global: { stubs } });

    wrapper.vm.form.username = 'employer1';
    wrapper.vm.form.password = 'Employer@12345';
    await wrapper.vm.handleLogin();
    await flushPromises();

    expect(mockLogin).toHaveBeenCalledWith(expect.objectContaining({
      username: 'employer1',
      password: 'Employer@12345'
    }));
    expect(mockPush).toHaveBeenCalledWith('/employer/dashboard');
  });

  it('redirects to change-password when passwordExpired is true', async () => {
    mockLogin.mockResolvedValue({
      user: { id: 1, username: 'admin', role: 'ADMIN' },
      csrfToken: 'xyz',
      passwordExpired: true
    });
    const LoginView = (await import('@/views/LoginView.vue')).default;
    const wrapper = mount(LoginView, { global: { stubs } });

    wrapper.vm.form.username = 'admin';
    wrapper.vm.form.password = 'Admin@123456789';
    await wrapper.vm.handleLogin();
    await flushPromises();

    expect(mockPush).toHaveBeenCalledWith('/change-password');
  });

  it('sets captchaRequired on failed login when server returns captchaRequired', async () => {
    const error = new Error('Login failed');
    error.response = { data: { message: 'Invalid credentials', captchaRequired: true } };
    mockLogin.mockRejectedValue(error);

    const LoginView = (await import('@/views/LoginView.vue')).default;
    const wrapper = mount(LoginView, { global: { stubs } });

    wrapper.vm.form.username = 'baduser';
    wrapper.vm.form.password = 'wrong';
    await wrapper.vm.handleLogin();
    await flushPromises();

    expect(wrapper.vm.captchaRequired).toBe(true);
    expect(wrapper.vm.errorMessage).toBe('Invalid credentials');
  });

  it('shows generic error on failed login without captcha trigger', async () => {
    const error = new Error('Login failed');
    error.response = { data: { message: 'Account locked', captchaRequired: false } };
    mockLogin.mockRejectedValue(error);

    const LoginView = (await import('@/views/LoginView.vue')).default;
    const wrapper = mount(LoginView, { global: { stubs } });

    wrapper.vm.form.username = 'lockeduser';
    wrapper.vm.form.password = 'wrong';
    await wrapper.vm.handleLogin();
    await flushPromises();

    expect(wrapper.vm.captchaRequired).toBe(false);
    expect(wrapper.vm.errorMessage).toBe('Account locked');
  });

  it('sets loading state during login attempt', async () => {
    let resolveLogin;
    mockLogin.mockImplementation(() => new Promise((resolve) => { resolveLogin = resolve; }));

    const LoginView = (await import('@/views/LoginView.vue')).default;
    const wrapper = mount(LoginView, { global: { stubs } });

    wrapper.vm.form.username = 'user';
    wrapper.vm.form.password = 'pass';
    const loginPromise = wrapper.vm.handleLogin();

    await vi.dynamicImportSettled();
    expect(wrapper.vm.loading).toBe(true);

    resolveLogin({ user: { role: 'EMPLOYER' }, csrfToken: 't', passwordExpired: false });
    await loginPromise;
    await flushPromises();
    expect(wrapper.vm.loading).toBe(false);
  });
});
