import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

function createSidebar(role) {
  vi.resetModules();
  vi.doMock('@/stores/auth', () => ({
    useAuthStore: () => ({
      isLoggedIn: true,
      role,
      user: { id: 1, username: 'testuser' },
      logout: vi.fn()
    })
  }));
  vi.doMock('vue-router', () => ({
    useRouter: () => ({ push: vi.fn() }),
    useRoute: () => ({ path: '/' })
  }));
}

describe('Sidebar role-based menus', () => {
  it('renders employer menu items', async () => {
    setActivePinia(createPinia());
    createSidebar('EMPLOYER');
    const Sidebar = (await import('@/components/Sidebar.vue')).default;
    const wrapper = mount(Sidebar, {
      global: {
        plugins: [createPinia()],
        stubs: {
          'el-menu': { template: '<nav><slot/></nav>' },
          'el-menu-item': { template: '<div class="menu-item"><slot/></div>' },
          'el-sub-menu': { template: '<div class="sub-menu"><slot/></div>' },
          'el-icon': true
        }
      }
    });
    expect(wrapper.text()).toContain('Postings');
  });

  it('renders admin menu items', async () => {
    setActivePinia(createPinia());
    createSidebar('ADMIN');
    const Sidebar = (await import('@/components/Sidebar.vue')).default;
    const wrapper = mount(Sidebar, {
      global: {
        plugins: [createPinia()],
        stubs: {
          'el-menu': { template: '<nav><slot/></nav>' },
          'el-menu-item': { template: '<div class="menu-item"><slot/></div>' },
          'el-sub-menu': { template: '<div class="sub-menu"><slot/></div>' },
          'el-icon': true
        }
      }
    });
    expect(wrapper.text()).toContain('Users');
  });
});
