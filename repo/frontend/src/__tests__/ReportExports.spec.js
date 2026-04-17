import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const { mockGetExports, mockDownloadExport } = vi.hoisted(() => ({
  mockGetExports: vi.fn(),
  mockDownloadExport: vi.fn()
}));

vi.mock('@/api/reports', () => ({
  default: {
    getScheduledReports: vi.fn(),
    createScheduledReport: vi.fn(),
    updateScheduledReport: vi.fn(),
    deleteScheduledReport: vi.fn(),
    getExports: mockGetExports,
    downloadExport: mockDownloadExport
  }
}));

import ReportExports from '@/views/ReportExports.vue';

const globalStubs = {
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div><slot :row="{id:1,dashboardName:\'Board\',masked:true}" /></div>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-tag': { template: '<span />' }
};

describe('ReportExports', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls getExports on mount', async () => {
    mockGetExports.mockResolvedValue({ data: [] });
    mount(ReportExports, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(mockGetExports).toHaveBeenCalledOnce();
  });

  it('populates exportsList from API response', async () => {
    mockGetExports.mockResolvedValue({
      data: [{ id: 1, dashboardName: 'Monthly Board', masked: true, createdAt: null }]
    });
    const wrapper = mount(ReportExports, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(wrapper.vm.exportsList).toHaveLength(1);
    expect(wrapper.vm.exportsList[0].dashboardName).toBe('Monthly Board');
  });

  it('starts with empty exports list', () => {
    mockGetExports.mockResolvedValue({ data: [] });
    const wrapper = mount(ReportExports, { global: { stubs: globalStubs } });
    expect(wrapper.vm.exportsList).toHaveLength(0);
  });

  it('formatDate returns dash for null value', () => {
    mockGetExports.mockResolvedValue({ data: [] });
    const wrapper = mount(ReportExports, { global: { stubs: globalStubs } });
    expect(wrapper.vm.formatDate(null)).toBe('-');
  });
});
