import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import AppealDetailView from '@/views/AppealDetailView.vue';

const { mockGetAppeal, mockProcessAppeal } = vi.hoisted(() => ({
  mockGetAppeal: vi.fn(),
  mockProcessAppeal: vi.fn(),
}));

vi.mock('@/api/appeals', () => ({
  default: { getAppeal: mockGetAppeal, processAppeal: mockProcessAppeal },
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '5' } }),
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}));
vi.mock('@element-plus/icons-vue', () => ({}));
vi.mock('@/constants/statuses', () => ({
  APPEAL_STATUS_TYPE: { PENDING: 'warning', GRANTED: 'success', DENIED: 'danger' },
}));
vi.mock('@/components/common/FileUpload.vue', () => ({
  default: { template: '<div/>' },
}));

const stubs = {
  'el-button': { template: '<button><slot/></button>', props: ['type', 'loading', 'link', 'round', 'size'] },
  'el-tag': { template: '<span><slot/></span>', props: ['type', 'effect'] },
  'el-card': { template: '<div><slot/></div>', props: ['shadow'] },
  'el-dialog': { template: '<div v-if="modelValue"><slot/><slot name="footer"/></div>', props: ['modelValue', 'title', 'width'] },
  'el-input': { template: '<textarea/>', props: ['modelValue', 'type', 'rows', 'placeholder'] },
  'el-empty': { template: '<div>empty</div>', props: ['description'] },
  FileUpload: { template: '<div/>' },
};

const pendingAppeal = {
  id: 5,
  status: 'PENDING',
  appealReason: 'Appeal reason here',
  jobTitle: 'Test Job',
  employerUsername: 'emp1',
  createdAt: new Date().toISOString(),
  takedownReason: null,
  takedownRationale: null,
  takedownAt: null,
  categoryName: 'Tech',
  location: 'NYC',
  paySummary: '$20/hr',
};

describe('AppealDetailView', () => {
  beforeEach(() => {
    mockGetAppeal.mockReset();
    mockProcessAppeal.mockReset();
  });

  it('calls getAppeal with route id on mount', async () => {
    mockGetAppeal.mockResolvedValue({ data: pendingAppeal });

    mount(AppealDetailView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(mockGetAppeal).toHaveBeenCalledWith('5');
  });

  it('populates appeal data from API', async () => {
    mockGetAppeal.mockResolvedValue({ data: pendingAppeal });

    const wrapper = mount(AppealDetailView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(wrapper.vm.appeal.id).toBe(5);
    expect(wrapper.vm.appeal.jobTitle).toBe('Test Job');
  });

  it('starts with null appeal before load', () => {
    mockGetAppeal.mockReturnValue(new Promise(() => {}));

    const wrapper = mount(AppealDetailView, { global: { plugins: [createPinia()], stubs } });

    expect(wrapper.vm.appeal).toBeNull();
  });

  it('processAppeal calls API with decision', async () => {
    mockGetAppeal.mockResolvedValue({ data: pendingAppeal });
    mockProcessAppeal.mockResolvedValue({ data: {} });

    const wrapper = mount(AppealDetailView, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    // Set rationale long enough to pass the 10-char validation
    wrapper.vm.actionDialog.rationale = 'Granted because valid';
    wrapper.vm.actionDialog.decision = 'GRANTED';
    await wrapper.vm.processAppeal();
    await flushPromises();

    expect(mockProcessAppeal).toHaveBeenCalledWith(
      '5',
      expect.objectContaining({ decision: 'GRANTED' })
    );
  });
});
