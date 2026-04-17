import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const { mockGetAuditLogs, mockGetAuditLogDetail } = vi.hoisted(() => ({
  mockGetAuditLogs: vi.fn(),
  mockGetAuditLogDetail: vi.fn()
}));

vi.mock('@/api/audit', () => ({
  getAuditLogs: mockGetAuditLogs,
  getAuditLogDetail: mockGetAuditLogDetail
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) }
}));

import AdminAuditView from '@/views/AdminAuditView.vue';

const globalStubs = {
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-input-number': { template: '<input />' },
  'el-date-picker': { template: '<input />' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div><slot :row="{id:1,action:\'USER_LOGIN\',username:\'admin\',entityType:\'USER\',entityId:1,ipAddress:\'127.0.0.1\'}" /></div>' },
  'el-tag': { template: '<span />' },
  'el-pagination': { template: '<div />' },
  'el-dialog': { template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
  'el-descriptions': { template: '<div><slot /></div>' },
  'el-descriptions-item': { template: '<div><slot /></div>' }
};

describe('AdminAuditView', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls getAuditLogs on mount', async () => {
    mockGetAuditLogs.mockResolvedValue({ data: { items: [], totalElements: 0 } });
    mount(AdminAuditView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(mockGetAuditLogs).toHaveBeenCalledOnce();
  });

  it('populates logs from API response', async () => {
    mockGetAuditLogs.mockResolvedValue({
      data: {
        items: [{ id: 1, action: 'USER_LOGIN', username: 'admin', entityType: 'USER', entityId: 1 }],
        totalElements: 1
      }
    });
    const wrapper = mount(AdminAuditView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(wrapper.vm.logs).toHaveLength(1);
    expect(wrapper.vm.total).toBe(1);
  });

  it('showDetail calls getAuditLogDetail with row id', async () => {
    mockGetAuditLogs.mockResolvedValue({ data: { items: [], totalElements: 0 } });
    mockGetAuditLogDetail.mockResolvedValue({
      data: { id: 1, action: 'USER_LOGIN', entityType: 'USER', entityId: 1 }
    });
    const wrapper = mount(AdminAuditView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    await wrapper.vm.showDetail({ id: 1 });
    expect(mockGetAuditLogDetail).toHaveBeenCalledWith(1);
  });

  it('resetFilters clears filter state', async () => {
    mockGetAuditLogs.mockResolvedValue({ data: { items: [], totalElements: 0 } });
    const wrapper = mount(AdminAuditView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    wrapper.vm.filters.entityType = 'USER';
    wrapper.vm.filters.action = 'LOGIN';
    wrapper.vm.resetFilters();
    expect(wrapper.vm.filters.entityType).toBe('');
    expect(wrapper.vm.filters.action).toBe('');
  });
});
