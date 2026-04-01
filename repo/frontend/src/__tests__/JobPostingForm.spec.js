import { describe, it, expect, vi } from 'vitest';

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ role: 'EMPLOYER', user: { id: 1 } })
}));
vi.mock('@/stores/dictionaries', () => ({
  useDictionaryStore: () => ({
    categories: [],
    ensureCategories: vi.fn().mockResolvedValue(undefined),
    ensureStates: vi.fn().mockResolvedValue(undefined),
    loadCities: vi.fn().mockResolvedValue([])
  })
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {} })
}));
vi.mock('@/api/jobs', () => ({ default: { createJob: vi.fn(), getJob: vi.fn(), updateJob: vi.fn() } }));
vi.mock('@/api/admin', () => ({
  getCategories: vi.fn().mockResolvedValue({ data: [] }),
  getLocations: vi.fn().mockResolvedValue({ data: [] })
}));

describe('JobPostingForm pay validation boundaries', () => {
  it('hourly pay bounds are $12–$75', async () => {
    const { mount } = await import('@vue/test-utils');
    const { createPinia, setActivePinia } = await import('pinia');
    const JobPostingForm = (await import('@/views/JobPostingForm.vue')).default;

    const pinia = createPinia();
    setActivePinia(pinia);

    const wrapper = mount(JobPostingForm, {
      props: { mode: 'create' },
      global: {
        plugins: [pinia],
        stubs: {
          'el-form': true,
          'el-form-item': true,
          'el-input': true,
          'el-input-number': true,
          'el-select': true,
          'el-option': true,
          'el-button': true,
          'el-date-picker': true,
          'el-alert': true,
          'el-row': true,
          'el-col': true,
          'el-tag': true,
          'el-checkbox': true,
          'el-checkbox-group': true,
          'el-radio-group': true,
          'el-radio-button': true
        }
      }
    });

    const { payBounds } = wrapper.vm;
    expect(payBounds.min).toBe(12);
    expect(payBounds.max).toBe(75);
  });
});
