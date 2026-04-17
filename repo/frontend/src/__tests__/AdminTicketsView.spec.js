import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const { mockGetTickets, mockGetUsers, mockUpdateTicket } = vi.hoisted(() => ({
  mockGetTickets: vi.fn(),
  mockGetUsers: vi.fn(),
  mockUpdateTicket: vi.fn()
}));

vi.mock('@/api/admin', () => ({
  default: {
    getTickets: mockGetTickets,
    getUsers: mockGetUsers,
    updateTicket: mockUpdateTicket
  }
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn() }
}));

vi.mock('@/components/common/FileUpload.vue', () => ({
  default: { template: '<div />', props: ['entityType', 'entityId'] }
}));

import AdminTicketsView from '@/views/AdminTicketsView.vue';

const globalStubs = {
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div><slot :row="{}" /></div>' },
  'el-pagination': { template: '<div />' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-select': { template: '<div />' },
  'el-option': { template: '<div />' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-tag': { template: '<span />' },
  'el-empty': { template: '<div />' }
};

describe('AdminTicketsView', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls getTickets and getUsers on mount', async () => {
    mockGetTickets.mockResolvedValue({ data: { items: [], totalElements: 0 } });
    mockGetUsers.mockResolvedValue({ data: { items: [] } });
    mount(AdminTicketsView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(mockGetTickets).toHaveBeenCalledOnce();
    expect(mockGetUsers).toHaveBeenCalledOnce();
  });

  it('populates tickets from API', async () => {
    const fakeTicket = { id: 1, subject: 'Bug', description: 'Details', status: 'OPEN', priority: 'HIGH', reporterUsername: 'user1', createdAt: new Date().toISOString() };
    mockGetTickets.mockResolvedValue({ data: { items: [fakeTicket], totalElements: 1 } });
    mockGetUsers.mockResolvedValue({ data: { items: [] } });
    const wrapper = mount(AdminTicketsView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(wrapper.vm.tickets).toHaveLength(1);
    expect(wrapper.vm.tickets[0].subject).toBe('Bug');
  });

  it('sets pagination.total from API', async () => {
    mockGetTickets.mockResolvedValue({ data: { items: [], totalElements: 15 } });
    mockGetUsers.mockResolvedValue({ data: { items: [] } });
    const wrapper = mount(AdminTicketsView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(wrapper.vm.pagination.total).toBe(15);
  });

  it('openDialog populates dialog fields', () => {
    mockGetTickets.mockResolvedValue({ data: { items: [], totalElements: 0 } });
    mockGetUsers.mockResolvedValue({ data: { items: [] } });
    const wrapper = mount(AdminTicketsView, { global: { stubs: globalStubs } });
    const ticket = { id: 3, subject: 'Bug', status: 'OPEN', priority: 'HIGH', resolution: 'none', assignedTo: null };
    wrapper.vm.openDialog(ticket);
    expect(wrapper.vm.dialog.visible).toBe(true);
    expect(wrapper.vm.dialog.ticket).toEqual(ticket);
    expect(wrapper.vm.dialog.status).toBe('OPEN');
  });

  it('saveTicket calls updateTicket with dialog values', async () => {
    mockGetTickets.mockResolvedValue({ data: { items: [], totalElements: 0 } });
    mockGetUsers.mockResolvedValue({ data: { items: [] } });
    mockUpdateTicket.mockResolvedValue({});
    const wrapper = mount(AdminTicketsView, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    wrapper.vm.dialog.ticket = { id: 5 };
    wrapper.vm.dialog.status = 'RESOLVED';
    wrapper.vm.dialog.priority = 'LOW';
    wrapper.vm.dialog.resolution = 'Fixed';
    wrapper.vm.dialog.assignedTo = 2;
    await wrapper.vm.saveTicket();
    expect(mockUpdateTicket).toHaveBeenCalledWith(5, {
      status: 'RESOLVED',
      priority: 'LOW',
      resolution: 'Fixed',
      assignedTo: 2
    });
  });
});
