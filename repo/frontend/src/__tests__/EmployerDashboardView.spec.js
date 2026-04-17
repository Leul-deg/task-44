import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import EmployerDashboardView from '@/views/EmployerDashboardView.vue';

const { mockFetchSummary } = vi.hoisted(() => ({
  mockFetchSummary: vi.fn()
}));

vi.mock('@/api/jobs', () => ({ default: { fetchSummary: mockFetchSummary } }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/constants/statuses', () => ({ JOB_STATUS_TYPE: {} }));
vi.mock('element-plus', () => ({}));

const stubs = {
  'el-button': { template: '<button><slot/></button>', props: ['type', 'loading', 'link', 'round'] },
  'el-row': { template: '<div class="el-row"><slot/></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot/></div>', props: ['xs', 'md'] },
  'el-table': { template: '<div class="el-table"></div>', props: ['data'] },
  'el-table-column': { template: '<div class="el-table-column"></div>', props: ['label', 'prop', 'formatter', 'width'] },
  'el-tag': { template: '<span class="el-tag"><slot/></span>', props: ['type', 'effect'] }
};

describe('EmployerDashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls fetchSummary on mount', async () => {
    mockFetchSummary.mockResolvedValue({
      data: { total: 7, published: 3, pendingReview: 2, rejected: 1, recent: [] }
    });
    mount(EmployerDashboardView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();
    expect(mockFetchSummary).toHaveBeenCalledTimes(1);
  });

  it('displays summary counts from API response', async () => {
    mockFetchSummary.mockResolvedValue({
      data: { total: 7, published: 3, pendingReview: 2, rejected: 1, recent: [] }
    });
    const wrapper = mount(EmployerDashboardView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();
    expect(wrapper.vm.summary.total).toBe(7);
    expect(wrapper.vm.summary.published).toBe(3);
  });

  it('initializes with zero counts before load', () => {
    mockFetchSummary.mockReturnValue(new Promise(() => {}));
    const wrapper = mount(EmployerDashboardView, { global: { plugins: [createPinia()], stubs } });
    expect(wrapper.vm.summary.total).toBe(0);
  });

  it('populates recent list from API data', async () => {
    mockFetchSummary.mockResolvedValue({
      data: {
        total: 1,
        published: 1,
        pendingReview: 0,
        rejected: 0,
        recent: [{ id: 10, title: 'Job A', status: 'DRAFT' }]
      }
    });
    const wrapper = mount(EmployerDashboardView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();
    expect(wrapper.vm.recent.length).toBe(1);
    expect(wrapper.vm.recent[0].title).toBe('Job A');
  });
});
