import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isLoggedIn: false,
    role: null,
    login: vi.fn().mockRejectedValue(new Error('Invalid credentials')),
    restoreSession: vi.fn()
  })
}));
vi.mock('@/router', () => ({
  defaultRouteForRole: vi.fn(() => '/dashboard'),
  default: { push: vi.fn(), beforeEach: vi.fn(), isReady: vi.fn() }
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} }),
  createRouter: vi.fn(() => ({ addRoute: vi.fn(), beforeEach: vi.fn() })),
  createWebHistory: vi.fn()
}));

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('renders username and password fields', async () => {
    const LoginView = (await import('@/views/LoginView.vue')).default;
    const wrapper = mount(LoginView, {
      global: {
        stubs: {
          'el-form': { template: '<form><slot/></form>' },
          'el-form-item': { template: '<div class="form-item"><slot/></div>' },
          'el-input': { template: '<input />', props: ['modelValue'] },
          'el-button': { template: '<button><slot/></button>' },
          'el-alert': true,
          'el-card': { template: '<div><slot/></div>' }
        }
      }
    });
    const inputs = wrapper.findAll('input');
    expect(inputs.length).toBeGreaterThanOrEqual(2);
  });

  it('initializes form with empty credentials', async () => {
    const LoginView = (await import('@/views/LoginView.vue')).default;
    const wrapper = mount(LoginView, {
      global: {
        stubs: {
          'el-form': { template: '<form><slot/></form>' },
          'el-form-item': { template: '<div><slot/></div>' },
          'el-input': { template: '<input />', props: ['modelValue'] },
          'el-button': { template: '<button type="submit"><slot/></button>' },
          'el-alert': true,
          'el-card': { template: '<div><slot/></div>' }
        }
      }
    });
    expect(wrapper.vm.form.username).toBe('');
    expect(wrapper.vm.form.password).toBe('');
  });
});
