import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() }
}));
vi.mock('@/utils/logger', () => ({
  default: { error: vi.fn(), warn: vi.fn(), info: vi.fn(), debug: vi.fn() }
}));

describe('Session expiry / 401 handling', () => {
  it('response interceptor calls unauthorizedHandler on 401', async () => {
    const { setUnauthorizedHandler, default: api } = await import('@/api/axios');

    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    const responseInterceptor = api.interceptors.response.handlers[0];
    const fakeError = {
      response: { status: 401, data: {} },
      config: { method: 'get', url: '/api/auth/me' }
    };

    try {
      await responseInterceptor.rejected(fakeError);
    } catch {
      // interceptor re-throws
    }

    expect(handler).toHaveBeenCalled();
  });

  it('response interceptor does not call unauthorizedHandler on non-401 errors', async () => {
    const { setUnauthorizedHandler, default: api } = await import('@/api/axios');

    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    const responseInterceptor = api.interceptors.response.handlers[0];
    const fakeError = {
      response: { status: 403, data: { message: 'Forbidden' } },
      config: { method: 'get', url: '/api/admin/users' }
    };

    try {
      await responseInterceptor.rejected(fakeError);
    } catch {
      // expected
    }

    expect(handler).not.toHaveBeenCalled();
  });

  it('auth store clearSession removes session data from sessionStorage', async () => {
    const mockStorage = {};
    vi.stubGlobal('sessionStorage', {
      getItem: vi.fn((key) => mockStorage[key] || null),
      setItem: vi.fn((key, value) => { mockStorage[key] = value; }),
      removeItem: vi.fn((key) => { delete mockStorage[key]; })
    });

    vi.doMock('@/api/axios', () => ({
      default: { get: vi.fn(), post: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
      setCsrfTokenResolver: vi.fn(),
      setUnauthorizedHandler: vi.fn()
    }));

    const { useAuthStore } = await import('@/stores/auth');
    const { createPinia, setActivePinia } = await import('pinia');
    setActivePinia(createPinia());

    const store = useAuthStore();
    store.user = { id: 1, username: 'test' };
    store.csrfToken = 'token';

    store.clearSession();

    expect(store.user).toBeNull();
    expect(store.csrfToken).toBe('');
    expect(sessionStorage.removeItem).toHaveBeenCalledWith('shiftworks-user');
    expect(sessionStorage.removeItem).toHaveBeenCalledWith('shiftworks-csrf');
  });
});
