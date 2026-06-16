/**
 * G2 — directory 레이어 (partner-service/user-service → legacy getter shape) 단위 테스트.
 */

'use strict';

let mockGet;

jest.mock('axios', () => ({
  create: jest.fn(() => ({ get: (...args) => mockGet(...args) })),
}));

describe('directory → legacy 거래처/담당자 shape', () => {
  let directory;
  let warnSpy;

  beforeEach(() => {
    jest.resetModules();
    mockGet = jest.fn();
    warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    directory = require('../lib/directory');
  });

  afterEach(() => {
    warnSpy.mockRestore();
  });

  test('fetchPartners는 partner-service 응답을 getCustomers_ shape로 변환한다', async () => {
    mockGet.mockResolvedValueOnce({
      status: 200,
      data: {
        success: true,
        data: [{
          partnerId: '11111111-1111-1111-1111-111111111111',
          partnerCode: 'P-2026-0001',
          name: '(주)테스트',
          bizNo: '111-22-33333',
          representative: '홍길동',
          address: '서울시 테스트로 1',
          phone: '02-1234-5678',
          group: '일반거래처',
          note: '메모',
        }],
      },
    });

    const rows = await directory.fetchPartners('테스트');

    expect(mockGet).toHaveBeenCalledWith(
      expect.stringContaining('/internal/partners/list'),
      expect.objectContaining({
        params: { q: '테스트', limit: 5000, page: 0 },
        headers: expect.objectContaining({ 'X-Internal-Token': expect.any(String) }),
      }),
    );
    expect(rows).toEqual([{
      code: 'P-2026-0001',
      name: '(주)테스트',
      bizno: '1112233333',
      manager: '',
      managerTel: '',
      rep: '홍길동',
      addr: '서울시 테스트로 1',
      tel: '02-1234-5678',
      note: '메모',
      group: '일반거래처',
      singleDiscount: 0,
    }]);
  });

  test('fetchManagers는 user-service 응답을 getManagers_ shape로 변환한다', async () => {
    mockGet.mockResolvedValueOnce({
      status: 200,
      data: {
        success: true,
        data: [{
          userId: '22222222-2222-2222-2222-222222222222',
          fullName: '김담당',
          ecountCode: 'EMP-0007',
          departmentName: '행정팀',
        }],
      },
    });

    const rows = await directory.fetchManagers('김');

    expect(mockGet).toHaveBeenCalledWith(
      expect.stringContaining('/internal/users/employees'),
      expect.objectContaining({
        params: { q: '김', limit: 500 },
        headers: expect.objectContaining({ 'X-Internal-Token': expect.any(String) }),
      }),
    );
    expect(rows).toEqual([{
      '담당자명': '김담당',
      '담당자코드': 'EMP-0007',
      manager: '김담당',
      empCd: 'EMP-0007',
    }]);
  });

  test('HTTP 오류는 throw하지 않고 빈 배열을 반환한다', async () => {
    mockGet.mockResolvedValue({ status: 500, data: { success: false } });

    await expect(directory.fetchPartners()).resolves.toEqual([]);
    await expect(directory.fetchManagers()).resolves.toEqual([]);
    expect(warnSpy).toHaveBeenCalled();
  });

  test('fetchPartners는 5000건 페이지가 꽉 차면 다음 페이지까지 조회한다', async () => {
    const fullPage = Array.from({ length: directory.PARTNER_PAGE_LIMIT }, (_, idx) => ({
      partnerCode: `P-${idx}`,
      name: `거래처${idx}`,
      bizNo: `111-22-${String(idx).padStart(5, '0')}`,
    }));
    mockGet
      .mockResolvedValueOnce({ status: 200, data: { success: true, data: fullPage } })
      .mockResolvedValueOnce({ status: 200, data: { success: true, data: [{ partnerCode: 'P-last', name: '마지막' }] } });

    const rows = await directory.fetchPartners('');

    expect(mockGet).toHaveBeenNthCalledWith(
      1,
      expect.stringContaining('/internal/partners/list'),
      expect.objectContaining({ params: { q: '', limit: 5000, page: 0 } }),
    );
    expect(mockGet).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining('/internal/partners/list'),
      expect.objectContaining({ params: { q: '', limit: 5000, page: 1 } }),
    );
    expect(rows).toHaveLength(directory.PARTNER_PAGE_LIMIT + 1);
    expect(rows.at(-1).code).toBe('P-last');
  });
});
