import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import ReviewerQueueView from '@/views/ReviewerQueueView.vue';

const { mockFetchQueue } = vi.hoisted(() => ({
  mockFetchQueue: vi.fn()
}));

vi.mock('@/api/review', () => ({ default: { fetchQueue: mockFetchQueue } }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('element-plus', () => ({}));

const stubs = {
  'el-table': { template: '<div class="el-table"></div>', props: ['data'] },
  'el-table-column': { template: '<div class="el-table-column"></div>', props: ['label', 'prop', 'width'] },
  'el-button': { template: '<button><slot/></button>', props: ['type', 'link'] },
  'el-empty': { template: '<div class="el-empty"></div>', props: ['description'] },
  'el-pagination': { template: '<div class="el-pagination"></div>', props: ['background', 'layout', 'currentPage', 'total', 'pageSize'] }
};

describe('ReviewerQueueView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls fetchQueue on mount', async () => {
    mockFetchQueue.mockResolvedValue({
      data: { items: [], totalElements: 0 }
    });
    mount(ReviewerQueueView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();
    expect(mockFetchQueue).toHaveBeenCalledTimes(1);
  });

  it('populates postings from API response', async () => {
    mockFetchQueue.mockResolvedValue({
      data: {
        items: [{ id: 5, title: 'Queue Job', status: 'PENDING_REVIEW' }],
        totalElements: 1
      }
    });
    const wrapper = mount(ReviewerQueueView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();
    expect(wrapper.vm.postings.length).toBe(1);
    expect(wrapper.vm.postings[0].title).toBe('Queue Job');
  });

  it('sets pagination total from API', async () => {
    mockFetchQueue.mockResolvedValue({
      data: { items: [], totalElements: 42 }
    });
    const wrapper = mount(ReviewerQueueView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();
    expect(wrapper.vm.pagination.total).toBe(42);
  });

  it('shows empty list initially before load', () => {
    mockFetchQueue.mockReturnValue(new Promise(() => {}));
    const wrapper = mount(ReviewerQueueView, { global: { plugins: [createPinia()], stubs } });
    expect(wrapper.vm.postings.length).toBe(0);
  });
});
