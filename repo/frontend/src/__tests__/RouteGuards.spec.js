import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useAuthStore } from '@/stores/auth';

vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn()
}));

vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart'
  }
}));

describe('Router guard role restrictions', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it('redirects employer away from admin routes to employer dashboard', async () => {
    useAuthStore.mockReturnValue({
      initialized: true,
      isLoggedIn: true,
      role: 'EMPLOYER',
      restoreSession: vi.fn()
    });

    const { default: router } = await import('@/router/index.js');
    await router.push('/admin/dashboard');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/employer/dashboard');
  });

  it('redirects reviewer away from employer routes to reviewer dashboard', async () => {
    useAuthStore.mockReturnValue({
      initialized: true,
      isLoggedIn: true,
      role: 'REVIEWER',
      restoreSession: vi.fn()
    });

    const { default: router } = await import('@/router/index.js');
    await router.push('/employer/postings');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/reviewer/dashboard');
  });

  it('allows admin to access admin routes', async () => {
    useAuthStore.mockReturnValue({
      initialized: true,
      isLoggedIn: true,
      role: 'ADMIN',
      restoreSession: vi.fn()
    });

    const { default: router } = await import('@/router/index.js');
    await router.push('/admin/job-items');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/admin/job-items');
  });

  it('redirects logged-in user from /login to their dashboard', async () => {
    useAuthStore.mockReturnValue({
      initialized: true,
      isLoggedIn: true,
      role: 'ADMIN',
      restoreSession: vi.fn()
    });

    const { default: router } = await import('@/router/index.js');
    await router.push('/login');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/admin/dashboard');
  });

  it('allows unauthenticated user to access /login', async () => {
    useAuthStore.mockReturnValue({
      initialized: true,
      isLoggedIn: false,
      role: null,
      restoreSession: vi.fn()
    });

    const { default: router } = await import('@/router/index.js');
    await router.push('/login');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/login');
  });
});
