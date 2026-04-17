import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import RegisterView from '@/views/RegisterView.vue';

const mockRegister = vi.fn();
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    register: mockRegister,
    isLoggedIn: false,
    role: null
  })
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({})
}));
vi.mock('@element-plus/icons-vue', () => ({
  CircleCheckFilled: {},
  CircleCloseFilled: {}
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() }
}));

const stubs = {
  'el-form': { template: '<form @submit.prevent="$emit(\'submit\')"><slot/></form>' },
  'el-form-item': { template: '<div class="form-item"><slot/></div>' },
  'el-input': {
    template: '<input :type="type || \'text\'" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type']
  },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot/></button>', props: ['loading', 'type'] },
  'el-alert': { template: '<div class="alert"><slot/></div>', props: ['type'] },
  'el-card': { template: '<div class="card"><slot/></div>' },
  'el-icon': { template: '<span><slot/></span>', props: ['size', 'color'] },
  RouterLink: { template: '<a><slot/></a>', props: ['to'] }
};

describe('RegisterView', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('renders at least 3 input fields', () => {
    const wrapper = mount(RegisterView, { global: { stubs } });
    expect(wrapper.findAll('input').length).toBeGreaterThanOrEqual(3);
  });

  it('form initializes with empty fields', () => {
    const wrapper = mount(RegisterView, { global: { stubs } });
    expect(wrapper.vm.form.username).toBe('');
    expect(wrapper.vm.form.email).toBe('');
    expect(wrapper.vm.form.password).toBe('');
    expect(wrapper.vm.form.confirmPassword).toBe('');
  });

  it('shows validation error when password is weak', async () => {
    const wrapper = mount(RegisterView, { global: { stubs } });
    wrapper.vm.form.username = 'testuser';
    wrapper.vm.form.email = 'test@example.com';
    wrapper.vm.form.password = 'weak';
    wrapper.vm.form.confirmPassword = 'weak';
    await wrapper.vm.handleRegister();
    expect(wrapper.vm.errorMessage).toContain('complexity requirements');
  });
});
