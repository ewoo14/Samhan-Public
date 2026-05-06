import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';
import { ApiClient } from '../../utils/api-clients';

/**
 * 임시저장 (draft) 30일 TTL boundary — 29.9d / 30.1d 경계.
 *
 * Spec 의도:
 *  - 29.9d (30일 -2.4시간): draft 조회 정상 (200)
 *  - 30.1d (30일 +2.4시간): draft auto-purge 또는 410/404
 *
 * 환경변수:
 *  - QA_DRAFT_29D_ID : 29.9d 경과 draft id (seed 또는 fixture)
 *  - QA_DRAFT_31D_ID : 30.1d 경과 draft id
 *  미설정 시 skip.
 */
test.describe('edge — draft TTL 30d boundary', () => {
  let api: ApiClient;
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-order-service 미가동 — IT skip');
    api = new ApiClient({ baseUrl: apiBase });
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('boundary 29.9d: draft 조회 정상 (TTL 미만)', async () => {
    const id = process.env.QA_DRAFT_29D_ID;
    test.skip(!id, '29.9d sample draft id 미설정 — skip');
    const draft = await api.getDraft(id as string).catch((e: Error) => ({ error: e.message }));
    // TTL 미만 — 정상 200 응답이어야 함 (id 또는 status 필드 보유)
    expect((draft as Record<string, unknown>).error).toBeUndefined();
  });

  test('boundary 30.1d: draft auto-purge — 404 또는 410', async () => {
    const id = process.env.QA_DRAFT_31D_ID;
    test.skip(!id, '30.1d sample draft id 미설정 — skip');
    let httpStatus: number | null = null;
    try {
      await api.getDraft(id as string);
    } catch (e: unknown) {
      const msg = (e as Error).message ?? '';
      const m = msg.match(/→ (\d{3}):/);
      if (m) httpStatus = Number(m[1]);
    }
    // TTL 초과 — purge 되었으므로 4xx (404 Not Found 또는 410 Gone)
    expect(httpStatus).not.toBeNull();
    expect([404, 410]).toContain(httpStatus);
  });
});
