import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isLoggedIn: false,
    role: null,
    restoreSession: vi.fn()
  })
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} })
}));
vi.mock('@/api/auth', () => ({ default: { register: vi.fn() } }));

describe('RegisterView', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('renders registration fields', async () => {
    const RegisterView = (await import('@/views/RegisterView.vue')).default;
    const wrapper = mount(RegisterView, {
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
    expect(inputs.length).toBeGreaterThanOrEqual(3);
  });

  it('initializes empty form state', async () => {
    const RegisterView = (await import('@/views/RegisterView.vue')).default;
    const wrapper = mount(RegisterView, {
      global: {
        stubs: {
          'el-form': { template: '<form><slot/></form>' },
          'el-form-item': { template: '<div><slot/></div>' },
          'el-input': { template: '<input />', props: ['modelValue'] },
          'el-button': { template: '<button><slot/></button>' },
          'el-alert': true,
          'el-card': { template: '<div><slot/></div>' }
        }
      }
    });
    expect(wrapper.vm.form.username).toBe('');
    expect(wrapper.vm.form.email).toBe('');
    expect(wrapper.vm.form.password).toBe('');
  });
});
