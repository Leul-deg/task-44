import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const stubs = {
  'el-dialog': {
    template: '<div class="dialog" v-if="modelValue"><slot/><slot name="footer"/></div>',
    props: ['modelValue', 'title', 'width']
  },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'placeholder']
  },
  'el-button': {
    template: '<button @click="$emit(\'click\')"><slot/></button>',
    props: ['loading', 'type']
  },
  'el-alert': { template: '<div class="alert"><slot/>{{ $attrs.closable }}</div>', props: ['type'] }
};

describe('StepUpVerification', () => {
  let StepUpVerification;

  beforeEach(async () => {
    StepUpVerification = (await import('@/components/common/StepUpVerification.vue')).default;
  });

  it('renders dialog when modelValue is true', () => {
    const wrapper = mount(StepUpVerification, {
      props: { modelValue: true },
      global: { stubs }
    });
    expect(wrapper.find('.dialog').exists()).toBe(true);
  });

  it('does not render dialog when modelValue is false', () => {
    const wrapper = mount(StepUpVerification, {
      props: { modelValue: false },
      global: { stubs }
    });
    expect(wrapper.find('.dialog').exists()).toBe(false);
  });

  it('shows error when verify is clicked with empty password', async () => {
    const wrapper = mount(StepUpVerification, {
      props: { modelValue: true },
      global: { stubs }
    });

    const buttons = wrapper.findAll('button');
    const verifyButton = buttons[buttons.length - 1];
    await verifyButton.trigger('click');

    expect(wrapper.vm.errorMessage).toBe('Please enter your password');
    expect(wrapper.emitted('verified')).toBeUndefined();
  });

  it('emits verified with password when a password is entered', async () => {
    const wrapper = mount(StepUpVerification, {
      props: { modelValue: true },
      global: { stubs }
    });

    wrapper.vm.password = 'MySecretPass!';
    const buttons = wrapper.findAll('button');
    const verifyButton = buttons[buttons.length - 1];
    await verifyButton.trigger('click');

    expect(wrapper.emitted('verified')).toBeTruthy();
    expect(wrapper.emitted('verified')[0]).toEqual(['MySecretPass!']);
    expect(wrapper.vm.errorMessage).toBe('');
  });

  it('emits update:modelValue false when cancel is clicked', async () => {
    const wrapper = mount(StepUpVerification, {
      props: { modelValue: true },
      global: { stubs }
    });

    const cancelButton = wrapper.findAll('button')[0];
    await cancelButton.trigger('click');

    expect(wrapper.emitted('update:modelValue')).toBeTruthy();
    expect(wrapper.emitted('update:modelValue')[0]).toEqual([false]);
  });

  it('clears password and error on close', async () => {
    const wrapper = mount(StepUpVerification, {
      props: { modelValue: true },
      global: { stubs }
    });

    wrapper.vm.password = 'test';
    wrapper.vm.errorMessage = 'some error';

    const cancelButton = wrapper.findAll('button')[0];
    await cancelButton.trigger('click');

    expect(wrapper.vm.password).toBe('');
    expect(wrapper.vm.errorMessage).toBe('');
  });
});
