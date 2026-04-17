import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import AnalyticsCenter from '@/views/AnalyticsCenter.vue';

const { mockGetDashboards, mockDeleteDashboard } = vi.hoisted(() => ({
  mockGetDashboards: vi.fn(),
  mockDeleteDashboard: vi.fn(),
}));

vi.mock('@/api/dashboards', () => ({
  default: { getDashboards: mockGetDashboards, deleteDashboard: mockDeleteDashboard },
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {} }),
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) },
}));
vi.mock('@element-plus/icons-vue', () => ({}));

const stubs = {
  'el-button': { template: '<button><slot/></button>', props: ['type', 'loading', 'link', 'round', 'size', 'plain', 'circle', 'disabled'] },
  'el-row': { template: '<div><slot/></div>', props: ['gutter'] },
  'el-col': { template: '<div><slot/></div>', props: ['xs', 'sm', 'md', 'lg'] },
  'el-tag': { template: '<span><slot/></span>', props: ['type', 'effect', 'size'] },
  'el-card': { template: '<div><slot/></div>', props: ['shadow'] },
  'el-empty': { template: '<div>empty</div>', props: ['description'] },
};

describe('AnalyticsCenter', () => {
  beforeEach(() => {
    mockGetDashboards.mockReset();
    mockDeleteDashboard.mockReset();
  });

  it('calls getDashboards on mount', async () => {
    mockGetDashboards.mockResolvedValue({ data: [{ id: 1, name: 'Board A', metricsJson: [] }, { id: 2, name: 'Board B', metricsJson: [] }] });

    mount(AnalyticsCenter, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(mockGetDashboards).toHaveBeenCalledTimes(1);
  });

  it('populates dashboards list from API', async () => {
    mockGetDashboards.mockResolvedValue({ data: [{ id: 1, name: 'Board A', metricsJson: [] }, { id: 2, name: 'Board B', metricsJson: [] }] });

    const wrapper = mount(AnalyticsCenter, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(wrapper.vm.dashboards.length).toBe(2);
    expect(wrapper.vm.dashboards[0].name).toBe('Board A');
  });

  it('starts with empty dashboards before load', () => {
    mockGetDashboards.mockReturnValue(new Promise(() => {}));

    const wrapper = mount(AnalyticsCenter, { global: { plugins: [createPinia()], stubs } });

    expect(wrapper.vm.dashboards.length).toBe(0);
  });

  it('delete calls deleteDashboard then reloads', async () => {
    mockGetDashboards.mockResolvedValue({ data: [] });
    mockDeleteDashboard.mockResolvedValue({});

    const wrapper = mount(AnalyticsCenter, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    const dash = { id: 1, name: 'Board A' };
    await wrapper.vm.deleteDashboard(dash);
    await flushPromises();

    expect(mockDeleteDashboard).toHaveBeenCalledWith(1);
    // getDashboards called once on mount + once after delete
    expect(mockGetDashboards).toHaveBeenCalledTimes(2);
  });
});
