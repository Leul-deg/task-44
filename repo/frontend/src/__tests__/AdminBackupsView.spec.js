import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

const { mockListBackups, mockTriggerBackup, mockRestoreBackup } = vi.hoisted(() => ({
  mockListBackups: vi.fn(),
  mockTriggerBackup: vi.fn(),
  mockRestoreBackup: vi.fn()
}));

vi.mock('@/api/backup', () => ({
  listBackups: mockListBackups,
  triggerBackup: mockTriggerBackup,
  restoreBackup: mockRestoreBackup
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { prompt: vi.fn(), confirm: vi.fn() }
}));

import AdminBackupsView from '@/views/AdminBackupsView.vue';

const globalStubs = {
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div><slot :row="{status:\'COMPLETED\',filename:\'backup.sql\',fileSize:1024,createdAt:null,expiresAt:null,id:1}" /></div>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-tag': { template: '<span />' },
  'el-empty': { template: '<div />' }
};

describe('AdminBackupsView', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls listBackups on mount', async () => {
    mockListBackups.mockResolvedValue({ data: [] });
    mount(AdminBackupsView, { global: { stubs: globalStubs } });
    await flushPromises();
    expect(mockListBackups).toHaveBeenCalledOnce();
  });

  it('populates backups from API response', async () => {
    mockListBackups.mockResolvedValue({
      data: [{ id: 1, filename: 'backup-2026.sql', fileSize: 2048, status: 'COMPLETED' }]
    });
    const wrapper = mount(AdminBackupsView, { global: { stubs: globalStubs } });
    await flushPromises();
    expect(wrapper.vm.backups).toHaveLength(1);
    expect(wrapper.vm.backups[0].filename).toBe('backup-2026.sql');
  });

  it('starts with empty backups list', () => {
    mockListBackups.mockResolvedValue({ data: [] });
    const wrapper = mount(AdminBackupsView, { global: { stubs: globalStubs } });
    expect(wrapper.vm.backups).toHaveLength(0);
  });

  it('formatSize returns correct string for bytes', () => {
    mockListBackups.mockResolvedValue({ data: [] });
    const wrapper = mount(AdminBackupsView, { global: { stubs: globalStubs } });
    expect(wrapper.vm.formatSize(0)).toBe('0 B');
    expect(wrapper.vm.formatSize(1024)).toBe('1.0 KB');
  });
});
