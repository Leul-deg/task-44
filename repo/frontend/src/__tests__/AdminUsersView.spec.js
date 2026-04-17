import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const { mockGetUsers, mockCreateUser } = vi.hoisted(() => ({
  mockGetUsers: vi.fn(),
  mockCreateUser: vi.fn()
}));

vi.mock('@/api/admin', () => ({
  default: {
    getUsers: mockGetUsers,
    createUser: mockCreateUser,
    updateUser: vi.fn(),
    changeRole: vi.fn(),
    unlockUser: vi.fn(),
    resetPassword: vi.fn()
  }
}));

vi.mock('@/constants/statuses', () => ({
  validatePasswordComplexity: vi.fn(() => true),
  getPasswordChecklist: vi.fn(() => ({})),
  PASSWORD_RULES: [],
  USER_ROLE_TYPE: {},
  USER_STATUS_TYPE: {}
}));

vi.mock('@/components/common/StepUpVerification.vue', () => ({
  default: { template: '<div />', props: ['modelValue', 'loading'] }
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) }
}));

import AdminUsersView from '@/views/AdminUsersView.vue';

const globalStubs = {
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div><slot :row="{}" /></div>' },
  'el-pagination': { template: '<div />' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-input': { template: '<input />' },
  'el-select': { template: '<div />' },
  'el-option': { template: '<div />' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-tag': { template: '<span />' },
  'el-empty': { template: '<div />' },
  'el-icon': { template: '<span />' }
};

describe('AdminUsersView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls getUsers on mount', async () => {
    mockGetUsers.mockResolvedValue({ data: { items: [], totalElements: 0 } });
    mount(AdminUsersView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(mockGetUsers).toHaveBeenCalledOnce();
  });

  it('populates users ref from API response', async () => {
    const fakeUser = { id: 1, username: 'emp1', email: 'e@e.com', role: 'EMPLOYER', status: 'ACTIVE', createdAt: new Date().toISOString() };
    mockGetUsers.mockResolvedValue({ data: { items: [fakeUser], totalElements: 1 } });
    const wrapper = mount(AdminUsersView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(wrapper.vm.users).toHaveLength(1);
    expect(wrapper.vm.users[0].username).toBe('emp1');
  });

  it('sets pagination.total from API response', async () => {
    mockGetUsers.mockResolvedValue({ data: { items: [], totalElements: 42 } });
    const wrapper = mount(AdminUsersView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(wrapper.vm.pagination.total).toBe(42);
  });

  it('users is empty before data loads', () => {
    mockGetUsers.mockReturnValue(new Promise(() => {}));
    const wrapper = mount(AdminUsersView, { global: { stubs: globalStubs } });
    expect(wrapper.vm.users).toHaveLength(0);
  });
});
