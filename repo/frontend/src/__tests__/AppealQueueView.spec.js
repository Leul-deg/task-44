import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import AppealQueueView from '@/views/AppealQueueView.vue';

const { mockFetchAppeals } = vi.hoisted(() => ({
  mockFetchAppeals: vi.fn(),
}));

vi.mock('@/api/appeals', () => ({
  default: { fetchAppeals: mockFetchAppeals },
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {} }),
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}));
vi.mock('@element-plus/icons-vue', () => ({}));
vi.mock('@/constants/statuses', () => ({
  APPEAL_STATUS_TYPE: { PENDING: 'warning', GRANTED: 'success', DENIED: 'danger' },
}));

const stubs = {
  'el-button': { template: '<button><slot/></button>', props: ['type', 'loading', 'link', 'round', 'size'] },
  'el-table': { template: '<div class="el-table"><slot/></div>', props: ['data', 'v-loading'] },
  'el-table-column': { template: '<div></div>', props: ['label', 'prop', 'formatter', 'width', 'type'] },
  'el-tag': { template: '<span><slot/></span>', props: ['type', 'effect'] },
  'el-pagination': { template: '<div></div>', props: ['total', 'current-page', 'page-size', 'layout', 'background'] },
  'el-tabs': { template: '<div><slot/></div>', props: ['modelValue'] },
  'el-tab-pane': { template: '<div><slot/></div>', props: ['label', 'name'] },
  'el-empty': { template: '<div>empty</div>', props: ['description'] },
};

describe('AppealQueueView', () => {
  beforeEach(() => {
    mockFetchAppeals.mockReset();
  });

  it('calls fetchAppeals on mount', async () => {
    mockFetchAppeals.mockResolvedValue({ data: { items: [], totalElements: 0 } });

    mount(AppealQueueView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(mockFetchAppeals).toHaveBeenCalledTimes(1);
  });

  it('populates appeals from API response', async () => {
    mockFetchAppeals.mockResolvedValue({
      data: { items: [{ id: 3, jobTitle: 'Job A', status: 'PENDING', appealReason: 'reason', employerUsername: 'emp', createdAt: new Date().toISOString() }], totalElements: 1 },
    });

    const wrapper = mount(AppealQueueView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(wrapper.vm.appeals.length).toBe(1);
    expect(wrapper.vm.appeals[0].jobTitle).toBe('Job A');
  });

  it('sets pagination total from API', async () => {
    mockFetchAppeals.mockResolvedValue({ data: { items: [], totalElements: 25 } });

    const wrapper = mount(AppealQueueView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(wrapper.vm.pagination.total).toBe(25);
  });

  it('shows empty list initially', () => {
    mockFetchAppeals.mockReturnValue(new Promise(() => {}));

    const wrapper = mount(AppealQueueView, { global: { plugins: [createPinia()], stubs } });

    expect(wrapper.vm.appeals.length).toBe(0);
  });
});
