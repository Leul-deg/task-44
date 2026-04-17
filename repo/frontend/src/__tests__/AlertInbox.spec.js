import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import AlertInbox from '@/views/AlertInbox.vue';

const { mockGetAlerts, mockMarkAlertRead, mockAcknowledgeAlert } = vi.hoisted(() => ({
  mockGetAlerts: vi.fn(),
  mockMarkAlertRead: vi.fn(),
  mockAcknowledgeAlert: vi.fn(),
}));

vi.mock('@/api/alerts', () => ({
  default: {
    getAlerts: mockGetAlerts,
    markAlertRead: mockMarkAlertRead,
    acknowledgeAlert: mockAcknowledgeAlert,
  },
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {} }),
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('@element-plus/icons-vue', () => ({}));

const stubs = {
  'el-button': { template: '<button><slot/></button>', props: ['type', 'loading', 'link', 'size'] },
  'el-table': { template: '<div class="el-table"><slot/></div>', props: ['data', 'style'] },
  'el-table-column': { template: '<div></div>', props: ['label', 'prop', 'width'] },
  'el-tag': { template: '<span><slot/></span>', props: ['type', 'effect'] },
  'el-tabs': { template: '<div><slot/></div>', props: ['modelValue', 'gutter'] },
  'el-tab-pane': { template: '<div><slot/></div>', props: ['label', 'name'] },
  'el-badge': { template: '<span><slot/></span>', props: ['value', 'type'] },
  'el-icon': { template: '<span></span>', props: ['size', 'color'] },
};

describe('AlertInbox', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockGetAlerts.mockReset();
    mockMarkAlertRead.mockReset();
    mockAcknowledgeAlert.mockReset();
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('calls getAlerts on mount', async () => {
    mockGetAlerts.mockResolvedValue({ data: { content: [] } });

    mount(AlertInbox, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(mockGetAlerts).toHaveBeenCalledTimes(1);
  });

  it('populates alerts from API response', async () => {
    mockGetAlerts.mockResolvedValue({
      data: {
        content: [{ id: 1, alertType: 'TAKEDOWN_SPIKE', severity: 'WARNING', read: false, message: 'High rate of takedowns', createdAt: new Date().toISOString() }],
      },
    });

    const wrapper = mount(AlertInbox, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    expect(wrapper.vm.alerts.length).toBeGreaterThanOrEqual(1);
  });

  it('markRead calls API and reloads', async () => {
    mockGetAlerts.mockResolvedValue({ data: { content: [] } });
    mockMarkAlertRead.mockResolvedValue({});

    const wrapper = mount(AlertInbox, { global: { plugins: [createPinia()], stubs } });
    await flushPromises();

    mockGetAlerts.mockClear();
    await wrapper.vm.markRead(1);
    await flushPromises();

    expect(mockMarkAlertRead).toHaveBeenCalledWith(1);
    expect(mockGetAlerts).toHaveBeenCalledTimes(1);
  });

  it('starts with empty alerts before load', () => {
    mockGetAlerts.mockReturnValue(new Promise(() => {}));

    const wrapper = mount(AlertInbox, { global: { plugins: [createPinia()], stubs } });

    expect(wrapper.vm.alerts.length).toBe(0);
  });
});
