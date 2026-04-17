import { describe, it, expect, vi, beforeEach } from 'vitest';
import { nextTick } from 'vue';

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
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ params: {} })
}));
vi.mock('@/api/jobs', () => ({ default: { createJob: vi.fn(), getJob: vi.fn(), updateJob: vi.fn(), submitJob: vi.fn() } }));
vi.mock('@/api/admin', () => ({
  default: { getClaims: vi.fn() },
  getCategories: vi.fn().mockResolvedValue({ data: [] }),
  getLocations: vi.fn().mockResolvedValue({ data: [] })
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}));
vi.mock('@element-plus/icons-vue', () => ({}));

const formStubs = {
  'el-form': { template: '<form><slot/></form>', props: ['model', 'label-position', 'rules'], methods: { validateField: vi.fn(), validate: vi.fn().mockResolvedValue(true) } },
  'el-form-item': { template: '<div><slot/></div>', props: ['label', 'prop'] },
  'el-input': { template: '<input/>', props: ['modelValue', 'type', 'placeholder', 'clearable'] },
  'el-input-number': { template: '<input/>', props: ['modelValue', 'min', 'max', 'step'] },
  'el-select': { template: '<select><slot/></select>', props: ['modelValue', 'placeholder'] },
  'el-option': { template: '<option/>', props: ['value', 'label'] },
  'el-button': { template: '<button><slot/></button>', props: ['type', 'loading', 'link', 'round', 'size', 'plain', 'circle', 'disabled'] },
  'el-date-picker': { template: '<input/>', props: ['modelValue', 'type', 'placeholder', 'disabled-date'] },
  'el-alert': { template: '<div><slot/></div>', props: ['type', 'closable', 'title'] },
  'el-row': { template: '<div><slot/></div>', props: ['gutter'] },
  'el-col': { template: '<div><slot/></div>', props: ['xs', 'md', 'span'] },
  'el-tag': { template: '<span><slot/></span>', props: ['type', 'effect', 'size', 'closable'] },
  'el-checkbox': { template: '<input type="checkbox"/>', props: ['modelValue'] },
  'el-checkbox-group': { template: '<div><slot/></div>', props: ['modelValue'] },
  'el-radio-group': { template: '<div><slot/></div>', props: ['modelValue'] },
  'el-radio-button': { template: '<label><slot/></label>', props: ['label'] },
};

async function mountForm() {
  const { mount } = await import('@vue/test-utils');
  const { createPinia, setActivePinia } = await import('pinia');
  const JobPostingForm = (await import('@/views/JobPostingForm.vue')).default;

  const pinia = createPinia();
  setActivePinia(pinia);

  const wrapper = mount(JobPostingForm, {
    props: { mode: 'create' },
    global: { plugins: [pinia], stubs: formStubs }
  });
  return wrapper;
}

describe('JobPostingForm pay validation boundaries', () => {
  it('hourly pay bounds are $12–$75', async () => {
    const wrapper = await mountForm();

    const { payBounds } = wrapper.vm;
    expect(payBounds.min).toBe(12);
    expect(payBounds.max).toBe(75);
  });

  it('flat pay bounds are $12–$5000', async () => {
    const wrapper = await mountForm();

    wrapper.vm.form.payType = 'FLAT';
    await nextTick();

    expect(wrapper.vm.payBounds.min).toBe(12);
    expect(wrapper.vm.payBounds.max).toBe(5000);
  });
});

describe('JobPostingForm tag management', () => {
  it('addTag adds a tag to the list', async () => {
    const wrapper = await mountForm();

    wrapper.vm.newTag = 'remote';
    wrapper.vm.addTag();

    expect(wrapper.vm.form.tags).toContain('remote');
  });

  it('addTag does not add duplicate tags', async () => {
    const wrapper = await mountForm();

    wrapper.vm.newTag = 'remote';
    wrapper.vm.addTag();
    wrapper.vm.newTag = 'remote';
    wrapper.vm.addTag();

    expect(wrapper.vm.form.tags.filter(t => t === 'remote').length).toBe(1);
  });

  it('addTag does not add more than 10 tags', async () => {
    const wrapper = await mountForm();

    for (let i = 1; i <= 11; i++) {
      wrapper.vm.newTag = `tag${i}`;
      wrapper.vm.addTag();
    }

    expect(wrapper.vm.form.tags.length).toBeLessThanOrEqual(10);
  });

  it('removeTag removes a tag from the list', async () => {
    const wrapper = await mountForm();

    wrapper.vm.newTag = 'remote';
    wrapper.vm.addTag();
    expect(wrapper.vm.form.tags).toContain('remote');

    wrapper.vm.removeTag('remote');
    expect(wrapper.vm.form.tags).not.toContain('remote');
  });
});
