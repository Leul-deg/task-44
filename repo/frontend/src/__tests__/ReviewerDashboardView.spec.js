import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import ReviewerDashboardView from '@/views/ReviewerDashboardView.vue';

const { mockFetchDashboard } = vi.hoisted(() => ({
  mockFetchDashboard: vi.fn()
}));

vi.mock('@/api/review', () => ({ default: { fetchDashboard: mockFetchDashboard } }));
vi.mock('@/constants/statuses', () => ({ REVIEW_ACTION_TYPE: {} }));
vi.mock('element-plus', () => ({}));

const stubs = {
  'el-row': { template: '<div class="el-row"><slot/></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot/></div>', props: ['xs', 'md'] },
  'el-table': { template: '<div class="el-table"></div>', props: ['data', 'empty-text'] },
  'el-table-column': { template: '<div class="el-table-column"></div>', props: ['label', 'prop', 'width'] },
  'el-tag': { template: '<span class="el-tag"><slot/></span>', props: ['type'] }
};

describe('ReviewerDashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls fetchDashboard on mount', async () => {
    mockFetchDashboard.mockResolvedValue({
      data: { pendingReviews: 8, pendingAppeals: 2, reviewedToday: 4, recentActions: [] }
    });
    mount(ReviewerDashboardView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();
    expect(mockFetchDashboard).toHaveBeenCalledTimes(1);
  });

  it('displays metrics from API response', async () => {
    mockFetchDashboard.mockResolvedValue({
      data: { pendingReviews: 8, pendingAppeals: 2, reviewedToday: 4, recentActions: [] }
    });
    const wrapper = mount(ReviewerDashboardView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();
    expect(wrapper.vm.metrics.pendingReviews).toBe(8);
    expect(wrapper.vm.metrics.pendingAppeals).toBe(2);
  });

  it('starts with zero metrics', () => {
    mockFetchDashboard.mockReturnValue(new Promise(() => {}));
    const wrapper = mount(ReviewerDashboardView, { global: { plugins: [createPinia()], stubs } });
    expect(wrapper.vm.metrics.pendingReviews).toBe(0);
  });

  it('populates recentActions', async () => {
    mockFetchDashboard.mockResolvedValue({
      data: {
        pendingReviews: 1,
        pendingAppeals: 0,
        reviewedToday: 1,
        recentActions: [{ id: 1, action: 'APPROVE', rationale: 'Approved', jobTitle: 'SomeJob' }]
      }
    });
    const wrapper = mount(ReviewerDashboardView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();
    expect(wrapper.vm.metrics.recentActions.length).toBe(1);
  });
});
