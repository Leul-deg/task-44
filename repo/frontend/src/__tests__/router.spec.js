import { describe, it, expect, vi } from 'vitest';

vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn()
}));

import { useAuthStore } from '@/stores/auth';

describe('Router guard behavior', () => {
  it('redirects unauthenticated users to /login for protected routes', async () => {
    const mockStore = {
      initialized: true,
      isLoggedIn: false,
      role: null,
      restoreSession: vi.fn()
    };
    useAuthStore.mockReturnValue(mockStore);

    const { default: router } = await import('@/router/index.js');
    await router.push('/employer/dashboard');
    await router.isReady();

    expect(router.currentRoute.value.path).toBe('/login');
  });
});
