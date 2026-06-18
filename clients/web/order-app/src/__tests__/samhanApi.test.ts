import { beforeEach, describe, expect, it, vi } from 'vitest';

const sessionStore = new Map<string, string>();

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  requestUse: vi.fn(),
}));

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      get: mocks.get,
      post: mocks.post,
      patch: mocks.patch,
      interceptors: {
        request: {
          use: mocks.requestUse,
        },
      },
    })),
  },
}));

import { samhanApi } from '../samhanApi';

vi.stubGlobal('sessionStorage', {
  getItem: vi.fn((key: string) => sessionStore.get(key) ?? null),
  setItem: vi.fn((key: string, value: string) => {
    sessionStore.set(key, value);
  }),
  clear: vi.fn(() => sessionStore.clear()),
});

describe('samhanApi.fetchBootstrap', () => {
  beforeEach(() => {
    mocks.get.mockReset();
  });

  it('ApiResponse BootstrapResponse envelope 에서 payloads 를 legacy bootstrap shape 로 반환한다', async () => {
    mocks.get.mockResolvedValue({
      data: {
        data: {
          payloads: {
            homemulti: [{ model: 'HM-1' }],
          },
        },
      },
    });

    const bootstrap = await samhanApi.fetchBootstrap();

    expect(mocks.get).toHaveBeenCalledWith('/partner-orders/bootstrap', { timeout: 8000 });
    expect(bootstrap).toEqual({
      homemulti: [{ model: 'HM-1' }],
    });
  });
});

describe('samhanApi.call', () => {
  beforeEach(() => {
    mocks.get.mockReset();
    mocks.post.mockReset();
    mocks.patch.mockReset();
    sessionStore.clear();
    sessionStorage.clear();
  });

  it('인증 상태 RPC 는 ApiResponse envelope 의 data 만 legacy handler 로 전달한다', async () => {
    mocks.get.mockResolvedValue({
      data: {
        success: true,
        code: 'OK',
        data: {
          status: 'LOGIN_REQUIRED',
          config: { homeDiscount: 0.45 },
        },
      },
    });

    const result = await samhanApi.call('checkAuthStatus', ['1234567890']);

    expect(mocks.get).toHaveBeenCalledWith('/auth/partner-status', {
      params: { bizNo: '1234567890' },
    });
    expect(result).toEqual({
      status: 'LOGIN_REQUIRED',
      config: { homeDiscount: 0.45 },
    });
  });

  it('로그인 RPC 는 envelope 언랩 후 token 과 config 를 캐싱한다', async () => {
    mocks.post.mockResolvedValue({
      data: {
        success: true,
        code: 'OK',
        data: {
          status: 'OK',
          token: 'partner-token',
          config: { commDiscount: 0.42 },
        },
      },
    });

    const result = await samhanApi.call('tryLogin', ['1234567890', '1234']);

    expect(mocks.post).toHaveBeenCalledWith('/auth/partner-login', {
      bizNo: '1234567890',
      password: '1234',
    });
    expect(result).toEqual({
      status: 'OK',
      token: 'partner-token',
      config: { commDiscount: 0.42 },
    });
    expect(sessionStorage.getItem('samhan-partner-token')).toBe('partner-token');
    expect(sessionStorage.getItem('samhan-partner-config')).toBe(
      JSON.stringify({ commDiscount: 0.42 }),
    );
  });

  it('비밀번호 설정 RPC 는 partner-auth SetPasswordRequest 의 newPassword 필드로 전송한다', async () => {
    mocks.patch.mockResolvedValue({
      data: {
        success: true,
        code: 'OK',
        data: {
          result: 'OK',
        },
      },
    });

    const result = await samhanApi.call('setAuthPassword', ['1234567890', '4321']);

    expect(mocks.patch).toHaveBeenCalledWith('/auth/partner-password', {
      bizNo: '1234567890',
      newPassword: '4321',
    });
    expect(result).toEqual({ result: 'OK' });
  });

  it('주문 이력 RPC 는 envelope 의 배열 data 를 그대로 반환한다', async () => {
    mocks.get.mockResolvedValue({
      data: {
        success: true,
        code: 'OK',
        data: [{ orderNo: '2026/06/18-1' }],
      },
    });

    const result = await samhanApi.call('getOrderHistory', [
      '1234567890',
      { type: '주문일시' },
    ]);

    expect(result).toEqual([{ orderNo: '2026/06/18-1' }]);
  });
});
