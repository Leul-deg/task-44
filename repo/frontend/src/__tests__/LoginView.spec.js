import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import LoginView from '@/views/LoginView.vue';

const mockLogin = vi.fn();
const mockRestoreSession = vi.fn().mockResolvedValue(undefined);
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({})
}));
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    login: mockLogin,
    restoreSession: mockRestoreSession,
    isLoggedIn: false,
    role: null,
    csrfToken: null
  })
}));
vi.mock('@/api/axios', () => ({
  default: { get: vi.fn(), post: vi.fn() },
  setCsrfTokenResolver: vi.fn(),
  setUnauthorizedHandler: vi.fn()
}));
vi.mock('@/router', () => ({
  defaultRouteForRole: (role) => `/${role.toLowerCase()}/dashboard`,
  default: { push: vi.fn(), beforeEach: vi.fn(), isReady: vi.fn() }
}));
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }));

const stubs = {
  'el-form': { template: '<form @submit.prevent="$emit(\'submit\')"><slot/></form>' },
  'el-form-item': { template: '<div class="form-item"><slot/></div>' },
  'el-input': {
    template: '<input :type="type || \'text\'" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type']
  },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot/></button>', props: ['loading', 'type'] },
  'el-alert': { template: '<div class="alert"><slot/></div>', props: ['type'] },
  'el-card': { template: '<div class="card"><slot/></div>' }
};

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('renders username and password input fields', () => {
    const wrapper = mount(LoginView, { global: { stubs } });
    expect(wrapper.findAll('input').length).toBeGreaterThanOrEqual(2);
  });

  it('form initializes with empty credentials', () => {
    const wrapper = mount(LoginView, { global: { stubs } });
    expect(wrapper.vm.form.username).toBe('');
    expect(wrapper.vm.form.password).toBe('');
  });

  it('shows error message on failed login', async () => {
    mockLogin.mockRejectedValue({
      response: { data: { message: 'Bad credentials' } }
    });
    const wrapper = mount(LoginView, { global: { stubs } });
    wrapper.vm.form.username = 'bad';
    wrapper.vm.form.password = 'wrong';
    wrapper.vm.handleLogin();
    await flushPromises();
    expect(wrapper.vm.errorMessage).toBeTruthy();
  });
});
