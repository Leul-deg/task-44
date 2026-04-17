import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const { mockGetScheduledReports, mockCreateScheduledReport, mockUpdateScheduledReport, mockDeleteScheduledReport } = vi.hoisted(() => ({
  mockGetScheduledReports: vi.fn(),
  mockCreateScheduledReport: vi.fn(),
  mockUpdateScheduledReport: vi.fn(),
  mockDeleteScheduledReport: vi.fn()
}));

const { mockGetDashboards } = vi.hoisted(() => ({
  mockGetDashboards: vi.fn()
}));

vi.mock('@/api/reports', () => ({
  default: {
    getScheduledReports: mockGetScheduledReports,
    createScheduledReport: mockCreateScheduledReport,
    updateScheduledReport: mockUpdateScheduledReport,
    deleteScheduledReport: mockDeleteScheduledReport,
    getExports: vi.fn(),
    downloadExport: vi.fn()
  }
}));

vi.mock('@/api/dashboards', () => ({
  default: {
    getDashboards: mockGetDashboards,
    getDashboard: vi.fn(),
    createDashboard: vi.fn(),
    updateDashboard: vi.fn(),
    deleteDashboard: vi.fn()
  }
}));

vi.mock('@/utils/logger', () => ({ default: { info: vi.fn(), error: vi.fn(), warn: vi.fn() } }));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) }
}));

import ReportScheduler from '@/views/ReportScheduler.vue';

const globalStubs = {
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div><slot :row="{id:1,cronExpression:\'0 0 2 * * *\',isActive:true}" /></div>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-switch': { template: '<input type="checkbox" />' },
  'el-input': { template: '<input />' },
  'el-tag': { template: '<span />' }
};

describe('ReportScheduler', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls getScheduledReports and getDashboards on mount', async () => {
    mockGetScheduledReports.mockResolvedValue({ data: [] });
    mockGetDashboards.mockResolvedValue({ data: [] });
    mount(ReportScheduler, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(mockGetScheduledReports).toHaveBeenCalledOnce();
    expect(mockGetDashboards).toHaveBeenCalledOnce();
  });

  it('populates schedules from API response', async () => {
    mockGetScheduledReports.mockResolvedValue({
      data: [{ id: 1, cronExpression: '0 0 2 * * *', isActive: true, dashboardConfigId: 5 }]
    });
    mockGetDashboards.mockResolvedValue({ data: [] });
    const wrapper = mount(ReportScheduler, { global: { stubs: globalStubs } });
    await vi.runAllMicrotasks();
    expect(wrapper.vm.schedules).toHaveLength(1);
    expect(wrapper.vm.schedules[0].cronExpression).toBe('0 0 2 * * *');
  });

  it('openDialog resets fields for new schedule', () => {
    mockGetScheduledReports.mockResolvedValue({ data: [] });
    mockGetDashboards.mockResolvedValue({ data: [] });
    const wrapper = mount(ReportScheduler, { global: { stubs: globalStubs } });
    wrapper.vm.openDialog(null);
    expect(wrapper.vm.dialog.scheduleId).toBeNull();
    expect(wrapper.vm.dialog.visible).toBe(true);
  });

  it('openDialog populates fields for existing schedule', () => {
    mockGetScheduledReports.mockResolvedValue({ data: [] });
    mockGetDashboards.mockResolvedValue({ data: [] });
    const wrapper = mount(ReportScheduler, { global: { stubs: globalStubs } });
    wrapper.vm.openDialog({ id: 2, dashboardConfigId: 5, cronExpression: '0 0 8 * * MON', isActive: false });
    expect(wrapper.vm.dialog.scheduleId).toBe(2);
    expect(wrapper.vm.dialog.dashboardConfigId).toBe(5);
  });
});
