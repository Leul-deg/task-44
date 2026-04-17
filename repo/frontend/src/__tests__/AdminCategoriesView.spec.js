import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

const { mockGetAdminCategories, mockCreateCategory, mockUpdateCategory, mockDeleteCategory } = vi.hoisted(() => ({
  mockGetAdminCategories: vi.fn(),
  mockCreateCategory: vi.fn(),
  mockUpdateCategory: vi.fn(),
  mockDeleteCategory: vi.fn()
}));

vi.mock('@/api/admin', () => ({
  default: {
    getAdminCategories: mockGetAdminCategories,
    createCategory: mockCreateCategory,
    updateCategory: mockUpdateCategory,
    deleteCategory: mockDeleteCategory
  }
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) }
}));

import AdminCategoriesView from '@/views/AdminCategoriesView.vue';

const globalStubs = {
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div><slot :row="{active:true,id:1,name:\'test\'}" /></div>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-tag': { template: '<span />' }
};

describe('AdminCategoriesView', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls getAdminCategories on mount', async () => {
    mockGetAdminCategories.mockResolvedValue({ data: [] });
    mount(AdminCategoriesView, { global: { stubs: globalStubs } });
    await flushPromises();
    expect(mockGetAdminCategories).toHaveBeenCalledOnce();
  });

  it('populates categories from API', async () => {
    mockGetAdminCategories.mockResolvedValue({
      data: [{ id: 1, name: 'Engineering', description: 'Tech', active: true, activePostings: 3 }]
    });
    const wrapper = mount(AdminCategoriesView, { global: { stubs: globalStubs } });
    await flushPromises();
    expect(wrapper.vm.categories).toHaveLength(1);
    expect(wrapper.vm.categories[0].name).toBe('Engineering');
  });

  it('openDialog sets id null for new category', () => {
    mockGetAdminCategories.mockResolvedValue({ data: [] });
    const wrapper = mount(AdminCategoriesView, { global: { stubs: globalStubs } });
    wrapper.vm.openDialog(null);
    expect(wrapper.vm.dialog.id).toBeNull();
    expect(wrapper.vm.dialog.visible).toBe(true);
  });

  it('openDialog populates fields for existing category', () => {
    mockGetAdminCategories.mockResolvedValue({ data: [] });
    const wrapper = mount(AdminCategoriesView, { global: { stubs: globalStubs } });
    wrapper.vm.openDialog({ id: 5, name: 'Retail', description: 'Shops' });
    expect(wrapper.vm.dialog.id).toBe(5);
    expect(wrapper.vm.dialog.name).toBe('Retail');
  });

  it('saveCategory calls createCategory when id is null', async () => {
    mockGetAdminCategories.mockResolvedValue({ data: [] });
    mockCreateCategory.mockResolvedValue({});
    const wrapper = mount(AdminCategoriesView, { global: { stubs: globalStubs } });
    await flushPromises();
    wrapper.vm.dialog.id = null;
    wrapper.vm.dialog.name = 'New Cat';
    wrapper.vm.dialog.description = 'Desc';
    await wrapper.vm.saveCategory();
    expect(mockCreateCategory).toHaveBeenCalledWith({ name: 'New Cat', description: 'Desc' });
  });

  it('saveCategory calls updateCategory when id is set', async () => {
    mockGetAdminCategories.mockResolvedValue({ data: [] });
    mockUpdateCategory.mockResolvedValue({});
    const wrapper = mount(AdminCategoriesView, { global: { stubs: globalStubs } });
    await flushPromises();
    wrapper.vm.dialog.id = 3;
    wrapper.vm.dialog.name = 'Updated';
    wrapper.vm.dialog.description = 'Desc';
    await wrapper.vm.saveCategory();
    expect(mockUpdateCategory).toHaveBeenCalledWith(3, { name: 'Updated', description: 'Desc' });
  });
});
