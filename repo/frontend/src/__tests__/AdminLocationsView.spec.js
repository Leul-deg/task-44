import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

const { mockGetAdminLocations, mockCreateLocation, mockUpdateLocation, mockDeleteLocation } = vi.hoisted(() => ({
  mockGetAdminLocations: vi.fn(),
  mockCreateLocation: vi.fn(),
  mockUpdateLocation: vi.fn(),
  mockDeleteLocation: vi.fn()
}));

vi.mock('@/api/admin', () => ({
  default: {
    getAdminLocations: mockGetAdminLocations,
    createLocation: mockCreateLocation,
    updateLocation: mockUpdateLocation,
    deleteLocation: mockDeleteLocation
  }
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) }
}));

import AdminLocationsView from '@/views/AdminLocationsView.vue';

const globalStubs = {
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div><slot :row="{id:1,state:\'TX\',city:\'Austin\',active:true}" /></div>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-tag': { template: '<span />' }
};

describe('AdminLocationsView', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls getAdminLocations on mount', async () => {
    mockGetAdminLocations.mockResolvedValue({ data: [] });
    mount(AdminLocationsView, { global: { stubs: globalStubs } });
    await flushPromises();
    expect(mockGetAdminLocations).toHaveBeenCalledOnce();
  });

  it('populates locations from API response', async () => {
    mockGetAdminLocations.mockResolvedValue({
      data: [{ id: 1, state: 'TX', city: 'Austin', active: true }]
    });
    const wrapper = mount(AdminLocationsView, { global: { stubs: globalStubs } });
    await flushPromises();
    expect(wrapper.vm.locations).toHaveLength(1);
    expect(wrapper.vm.locations[0].city).toBe('Austin');
  });

  it('openDialog sets id null for new location', () => {
    mockGetAdminLocations.mockResolvedValue({ data: [] });
    const wrapper = mount(AdminLocationsView, { global: { stubs: globalStubs } });
    wrapper.vm.openDialog(null);
    expect(wrapper.vm.dialog.id).toBeNull();
    expect(wrapper.vm.dialog.visible).toBe(true);
  });

  it('openDialog populates fields for existing location', () => {
    mockGetAdminLocations.mockResolvedValue({ data: [] });
    const wrapper = mount(AdminLocationsView, { global: { stubs: globalStubs } });
    wrapper.vm.openDialog({ id: 2, state: 'CA', city: 'Los Angeles' });
    expect(wrapper.vm.dialog.id).toBe(2);
    expect(wrapper.vm.dialog.state).toBe('CA');
    expect(wrapper.vm.dialog.city).toBe('Los Angeles');
  });

  it('saveLocation calls createLocation when id is null', async () => {
    mockGetAdminLocations.mockResolvedValue({ data: [] });
    mockCreateLocation.mockResolvedValue({});
    const wrapper = mount(AdminLocationsView, { global: { stubs: globalStubs } });
    await flushPromises();
    wrapper.vm.dialog.id = null;
    wrapper.vm.dialog.state = 'WA';
    wrapper.vm.dialog.city = 'Seattle';
    await wrapper.vm.saveLocation();
    expect(mockCreateLocation).toHaveBeenCalledWith({ state: 'WA', city: 'Seattle' });
  });

  it('saveLocation calls updateLocation when id is set', async () => {
    mockGetAdminLocations.mockResolvedValue({ data: [] });
    mockUpdateLocation.mockResolvedValue({});
    const wrapper = mount(AdminLocationsView, { global: { stubs: globalStubs } });
    await flushPromises();
    wrapper.vm.dialog.id = 3;
    wrapper.vm.dialog.state = 'TX';
    wrapper.vm.dialog.city = 'Houston';
    await wrapper.vm.saveLocation();
    expect(mockUpdateLocation).toHaveBeenCalledWith(3, { state: 'TX', city: 'Houston' });
  });
});
