import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import EmployerPostingsView from '@/views/EmployerPostingsView.vue';

const { mockFetchJobs } = vi.hoisted(() => ({
  mockFetchJobs: vi.fn(),
}));

vi.mock('@/api/jobs', () => ({
  default: { fetchJobs: mockFetchJobs, unpublishJob: vi.fn() },
}));
vi.mock('@/api/appeals', () => ({
  default: { createAppeal: vi.fn() },
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {} }),
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { prompt: vi.fn(), confirm: vi.fn() },
}));
vi.mock('@element-plus/icons-vue', () => ({}));
vi.mock('@/constants/statuses', () => ({
  JOB_STATUS_TYPE: { DRAFT: 'info', PENDING_REVIEW: 'warning', PUBLISHED: 'success', REJECTED: 'danger', TAKEN_DOWN: 'danger' },
}));

const stubs = {
  'el-button': { template: '<button><slot/></button>', props: ['type', 'loading', 'link', 'round', 'size', 'plain'] },
  'el-table': { template: '<div class="el-table"><slot/></div>', props: ['data', 'v-loading'] },
  'el-table-column': { template: '<div></div>', props: ['label', 'prop', 'formatter', 'width', 'type'] },
  'el-tag': { template: '<span><slot/></span>', props: ['type', 'effect'] },
  'el-pagination': { template: '<div></div>', props: ['total', 'current-page', 'page-size', 'layout', 'background', 'page-sizes'] },
  'el-tabs': { template: '<div><slot/></div>', props: ['modelValue'] },
  'el-tab-pane': { template: '<div><slot/></div>', props: ['label', 'name'] },
  'el-input': { template: '<input/>', props: ['modelValue', 'placeholder', 'clearable'] },
  'el-empty': { template: '<div>empty</div>', props: ['description'] },
};

describe('EmployerPostingsView', () => {
  beforeEach(() => {
    mockFetchJobs.mockReset();
  });

  it('calls fetchJobs on mount', async () => {
    mockFetchJobs.mockResolvedValue({ data: { items: [], totalElements: 0 } });

    mount(EmployerPostingsView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(mockFetchJobs).toHaveBeenCalledTimes(1);
  });

  it('populates postings from API response', async () => {
    mockFetchJobs.mockResolvedValue({
      data: { items: [{ id: 1, title: 'Dev Job', status: 'DRAFT', createdAt: new Date().toISOString() }], totalElements: 1 },
    });

    const wrapper = mount(EmployerPostingsView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(wrapper.vm.postings.length).toBe(1);
    expect(wrapper.vm.postings[0].title).toBe('Dev Job');
  });

  it('sets pagination total from API', async () => {
    mockFetchJobs.mockResolvedValue({ data: { items: [], totalElements: 30 } });

    const wrapper = mount(EmployerPostingsView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(wrapper.vm.pagination.total).toBe(30);
  });

  it('shows empty postings initially', () => {
    mockFetchJobs.mockReturnValue(new Promise(() => {}));

    const wrapper = mount(EmployerPostingsView, { global: { plugins: [createPinia()], stubs } });

    expect(wrapper.vm.postings.length).toBe(0);
  });
});
