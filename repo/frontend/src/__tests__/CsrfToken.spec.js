import { describe, it, expect, vi } from 'vitest';

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() }
}));
vi.mock('@/utils/logger', () => ({
  default: { error: vi.fn(), warn: vi.fn(), info: vi.fn(), debug: vi.fn() }
}));

describe('CSRF token handling', () => {
  it('attaches X-XSRF-TOKEN header on POST/PUT/DELETE/PATCH requests', async () => {
    const { setCsrfTokenResolver } = await import('@/api/axios');
    setCsrfTokenResolver(() => 'csrf-token-abc');

    const { default: api } = await import('@/api/axios');

    for (const method of ['post', 'put', 'delete', 'patch']) {
      const config = {
        method,
        headers: {},
        url: '/test'
      };
      const interceptor = api.interceptors.request.handlers[0];
      const result = interceptor.fulfilled(config);
      expect(result.headers['X-XSRF-TOKEN']).toBe('csrf-token-abc');
    }
  });

  it('does not attach CSRF header on GET requests', async () => {
    const { setCsrfTokenResolver } = await import('@/api/axios');
    setCsrfTokenResolver(() => 'csrf-token-abc');

    const { default: api } = await import('@/api/axios');

    const config = {
      method: 'get',
      headers: {},
      url: '/test'
    };
    const interceptor = api.interceptors.request.handlers[0];
    const result = interceptor.fulfilled(config);
    expect(result.headers['X-XSRF-TOKEN']).toBeUndefined();
  });
});
