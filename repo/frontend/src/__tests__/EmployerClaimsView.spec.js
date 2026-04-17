import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

const { mockGetClaims, mockGetTickets, mockCreateClaim, mockCreateTicket } = vi.hoisted(() => ({
  mockGetClaims: vi.fn(),
  mockGetTickets: vi.fn(),
  mockCreateClaim: vi.fn(),
  mockCreateTicket: vi.fn()
}));

vi.mock('@/api/admin', () => ({
  default: {
    getClaims: mockGetClaims,
    getTickets: mockGetTickets,
    createClaim: mockCreateClaim,
    createTicket: mockCreateTicket
  }
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) }
}));

import EmployerClaimsView from '@/views/EmployerClaimsView.vue';

const globalStubs = {
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div><slot :row="{id:1}" /></div>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-tag': { template: '<span />' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' }
};

describe('EmployerClaimsView', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls getClaims and getTickets on mount', async () => {
    mockGetClaims.mockResolvedValue({ data: [] });
    mockGetTickets.mockResolvedValue({ data: [] });
    mount(EmployerClaimsView, { global: { stubs: globalStubs } });
    await flushPromises();
    expect(mockGetClaims).toHaveBeenCalledOnce();
    expect(mockGetTickets).toHaveBeenCalledOnce();
  });

  it('populates claims from API response', async () => {
    mockGetClaims.mockResolvedValue({
      data: [{ id: 1, description: 'Claim 1', status: 'OPEN' }]
    });
    mockGetTickets.mockResolvedValue({ data: [] });
    const wrapper = mount(EmployerClaimsView, { global: { stubs: globalStubs } });
    await flushPromises();
    expect(wrapper.vm.claims).toHaveLength(1);
    expect(wrapper.vm.claims[0].description).toBe('Claim 1');
  });

  it('submitClaim calls createClaim with form data', async () => {
    mockGetClaims.mockResolvedValue({ data: [] });
    mockGetTickets.mockResolvedValue({ data: [] });
    mockCreateClaim.mockResolvedValue({ data: { id: 2 } });
    const wrapper = mount(EmployerClaimsView, { global: { stubs: globalStubs } });
    await flushPromises();
    wrapper.vm.claimForm.jobPostingId = '5';
    wrapper.vm.claimForm.description = 'New claim';
    await wrapper.vm.submitClaim();
    expect(mockCreateClaim).toHaveBeenCalledWith({ jobPostingId: '5', description: 'New claim' });
  });

  it('submitTicket calls createTicket with form data', async () => {
    mockGetClaims.mockResolvedValue({ data: [] });
    mockGetTickets.mockResolvedValue({ data: [] });
    mockCreateTicket.mockResolvedValue({ data: { id: 3 } });
    const wrapper = mount(EmployerClaimsView, { global: { stubs: globalStubs } });
    await flushPromises();
    wrapper.vm.ticketForm.subject = 'Issue';
    wrapper.vm.ticketForm.description = 'Details here';
    wrapper.vm.ticketForm.priority = 'HIGH';
    await wrapper.vm.submitTicket();
    expect(mockCreateTicket).toHaveBeenCalledWith({ subject: 'Issue', description: 'Details here', priority: 'HIGH' });
  });
});
