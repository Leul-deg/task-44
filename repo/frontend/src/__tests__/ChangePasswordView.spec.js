import { mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import ChangePasswordView from '@/views/ChangePasswordView.vue';

const mockChangePassword = vi.fn();
const mockPush = vi.fn();
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ changePassword: mockChangePassword })
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({})
}));
vi.mock('@element-plus/icons-vue', () => ({
  CircleCheckFilled: {},
  CircleCloseFilled: {}
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() }
}));

describe('ChangePasswordView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows error when passwords do not match', async () => {
    const wrapper = mount(ChangePasswordView, { global: { plugins: [createPinia()] } });
    wrapper.vm.form.newPassword = 'NewPass123!';
    wrapper.vm.form.confirmPassword = 'DifferentPass!';
    await wrapper.vm.handleChange();
    expect(wrapper.vm.errorMessage).toBe('New passwords do not match');
  });

  it('shows error when password fails policy', async () => {
    const wrapper = mount(ChangePasswordView, { global: { plugins: [createPinia()] } });
    wrapper.vm.form.newPassword = 'short';
    wrapper.vm.form.confirmPassword = 'short';
    await wrapper.vm.handleChange();
    expect(wrapper.vm.errorMessage).toContain('policy requirements');
  });

  it('does not call changePassword if passwords do not match', async () => {
    const wrapper = mount(ChangePasswordView, { global: { plugins: [createPinia()] } });
    wrapper.vm.form.newPassword = 'NewPass123!';
    wrapper.vm.form.confirmPassword = 'DifferentPass!';
    await wrapper.vm.handleChange();
    expect(mockChangePassword).not.toHaveBeenCalled();
  });

  it('calls changePassword with correct payload when valid', async () => {
    mockChangePassword.mockResolvedValue(undefined);
    const wrapper = mount(ChangePasswordView, { global: { plugins: [createPinia()] } });
    wrapper.vm.form.currentPassword = 'OldPass!1';
    wrapper.vm.form.newPassword = 'NewValidPass1!';
    wrapper.vm.form.confirmPassword = 'NewValidPass1!';
    await wrapper.vm.handleChange();
    expect(mockChangePassword).toHaveBeenCalledWith({
      currentPassword: 'OldPass!1',
      newPassword: 'NewValidPass1!'
    });
  });

  it('password checklist reflects current input', async () => {
    const wrapper = mount(ChangePasswordView, { global: { plugins: [createPinia()] } });
    wrapper.vm.form.newPassword = 'Short';
    await wrapper.vm.$nextTick();
    expect(wrapper.vm.checklist.length).toBe(false);
    expect(wrapper.vm.checklist.upper).toBe(true);

    wrapper.vm.form.newPassword = 'ValidPassword1!';
    await wrapper.vm.$nextTick();
    expect(wrapper.vm.checklist.length).toBe(true);
  });
});
