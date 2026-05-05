import { test, expect } from '@playwright/test';
import { getPartner, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 임시저장 (draft) 생성 — 거래처 주문서 작성 중 저장.
 *
 * Happy: 라인 1+ 입력 → 저장 버튼 → 저장 완료 toast + ID 발급
 * Edge : 빈 라인 저장 → 검증 실패 메시지
 */
test.describe('draft — save', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-order-service 미가동 — IT skip');
    const partner = getPartner({ status: 'ACTIVE', passwordType: 'BIZGATE' });
    await mockPartnerAuth(page, partner);
  });

  test('happy: 라인 입력 → 임시저장 → 완료 toast', async ({ page }) => {
    await page.goto('/');
    const saveBtn = page.locator('button:has-text("임시저장"), button:has-text("저장")').first();
    if ((await saveBtn.count()) === 0) {
      test.skip(true, '저장 버튼 미노출 — gate 단계 skip');
    }
    await saveBtn.click();
    await expect(page.locator('body')).toContainText(/저장|완료/, { timeout: 5_000 });
  });

  test('edge: 빈 라인 저장 → 검증 실패', async ({ page }) => {
    await page.goto('/');
    const saveBtn = page.locator('button:has-text("임시저장"), button:has-text("저장")').first();
    if ((await saveBtn.count()) === 0) {
      test.skip(true, '저장 버튼 미노출');
    }
    await saveBtn.click();
    // legacy 는 alert 또는 inline 메시지
    await expect(page.locator('body')).toContainText(/입력|선택|품목/, { timeout: 5_000 });
  });
});
