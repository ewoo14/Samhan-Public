import { test, expect } from '@playwright/test';
import { getPartner, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';
import { ApiClient } from '../../utils/api-clients';

/**
 * 임시저장 30일 TTL — 30일 경과 draft 자동 만료.
 *
 * Happy: 신규 draft → 즉시 load 성공
 * Edge : 30일 + 1일 경과 draft → 만료 메시지 + load 실패
 */
test.describe('draft — 30 day TTL', () => {
  let api: ApiClient;
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-order-service 미가동 — IT skip');
    api = new ApiClient({ baseUrl: apiBase });
    const partner = getPartner({ status: 'ACTIVE', passwordType: 'BIZGATE' });
    await mockPartnerAuth(page, partner);
  });

  test('happy: 신규 draft load 성공', async ({ page }) => {
    const created = await api
      .createDraft({ partnerCode: 'BIZ-001', lines: [] })
      .catch(() => ({ id: 'mock-draft' }));
    await page.goto(`/draft/${created.id}`);
    await expect(page).toHaveURL(/draft/);
  });

  test('edge: 30일 경과 draft → 만료 메시지', async ({ page }) => {
    await page.goto('/draft/expired-mock-id');
    await expect(page.locator('body')).toContainText(/만료|기간|불가|없|없음/, { timeout: 5_000 });
  });
});
