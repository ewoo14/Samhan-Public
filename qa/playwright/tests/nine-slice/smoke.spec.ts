import { test, expect } from '@playwright/test';
import { isBackendAvailable } from '../../fixtures/auth';

/**
 * 9 슬라이스 통합 PR — Playwright smoke (1건)
 *
 * 본 spec 은 Phase 10 Step 8 9 슬라이스 통합 PR 의 신규 acceptance 시나리오
 * (~160 case) 의 typecheck + backend health gate + testid spec 인덱싱 smoke 로,
 * 실제 case 별 spec 은 후속 DevOps PR 에서 .github/workflows/qa-e2e.yml matrix
 * 확장과 함께 점진 추가 (Detox android lane 동일).
 *
 * 의존:
 *   - docs/qa/integration-pr-9-slice/scenarios.md (160 case 명세)
 *   - tools/test-data/seed-9-slice-fixtures.ps1 (fixture seed)
 *   - tools/manual-capture/data-testid-required.md §우선순위 4 (60+ testid spec)
 *
 * skip 조건:
 *   - QA_API_BASE_URL backend 미가동 → 모든 test skip (CI green 유지)
 *   - QA_NINE_SLICE=skip 환경 변수 → 강제 skip (debug)
 */

const SLICES = [
  { id: 1, name: '비밀번호 재설정',     priority: 'P0-2', cases: 21, testIds: ['login-id-input', 'login-password-input', 'login-submit-button', 'account-locked-banner', 'password-policy-hint', 'password-reset-email-input', 'password-reset-submit-button', 'master-account-unlock-button'] },
  { id: 2, name: '세금계산서',           priority: 'P0-4', cases: 15, testIds: ['tax-invoice-form-partner-search', 'tax-invoice-form-vat-auto', 'tax-invoice-form-issue-button', 'tax-invoice-cancel-button'] },
  { id: 3, name: '인쇄 5건',             priority: 'P0-4', cases: 30, testIds: ['print-frame-a4', 'print-frame-88mm', 'print-company-logo', 'print-company-seal', 'print-page-counter'] },
  { id: 4, name: '관리자 UI',            priority: 'P0-5', cases: 28, testIds: ['users-admin-table', 'users-admin-disable-button', 'users-admin-enable-button', 'users-admin-role-select', 'role-matrix-table', 'org-chart-tree'] },
  { id: 5, name: 'arologis 수동 배차',   priority: 'P1-5', cases: 16, testIds: ['dispatch-manual-form', 'dispatch-stop-add-button', 'dispatch-driver-auto-match-button', 'arologis-kakao-preview-frame'] },
  { id: 6, name: '모바일 사진 (Detox)',  priority: 'P1-8', cases: 11, testIds: ['mobile-photo-camera-button', 'mobile-photo-gallery-button', 'mobile-photo-preview-list', 'mobile-photo-upload-progress'] },
  { id: 7, name: '견적서',               priority: 'P2-1', cases: 11, testIds: ['estimate-form-line-add', 'estimate-form-send-button', 'estimate-detail-accept-button', 'estimate-detail-convert-to-slip-button'] },
  { id: 8, name: '매출 마감',            priority: 'P2-4', cases: 10, testIds: ['period-lock-sales-month-select', 'period-lock-sales-lock-button', 'period-lock-sales-unlock-button', 'period-lock-banner-locked'] },
  { id: 9, name: '재고 실사',            priority: 'P2-6', cases: 18, testIds: ['stock-take-form-warehouse-select', 'stock-take-line-barcode-input', 'stock-take-line-counted-qty', 'stock-take-start-button', 'stock-take-complete-button'] },
] as const;

test.describe('9 슬라이스 통합 PR — smoke', () => {
  test.beforeEach(async () => {
    if (process.env.QA_NINE_SLICE === 'skip') {
      test.skip(true, 'QA_NINE_SLICE=skip 강제 skip');
    }
  });

  test('slice spec inventory — 9 슬라이스 + 160 case 인덱싱', () => {
    const totalCases = SLICES.reduce((sum, s) => sum + s.cases, 0);
    expect(totalCases).toBe(160);

    const totalTestIds = SLICES.reduce((sum, s) => sum + s.testIds.length, 0);
    expect(totalTestIds).toBeGreaterThanOrEqual(40);

    // 각 슬라이스가 testid 최소 1개 이상 정의
    for (const slice of SLICES) {
      expect(slice.testIds.length, `슬라이스 ${slice.id} (${slice.name}) testid 누락`).toBeGreaterThan(0);
    }
  });

  test('backend health smoke — actuator/health 200 (가동 시) 또는 skip', async () => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, '14 backend 미가동 — fixture seed + e2e skip (CI dry-run 모드)');
    expect(ok).toBe(true);
  });

  test('fixture seed 스크립트 존재 검증 (tools/test-data/seed-9-slice-fixtures.ps1)', async () => {
    const fs = await import('node:fs/promises');
    const path = await import('node:path');
    // qa/playwright/ 기준 → ../../ 로 repo root
    const scriptPath = path.resolve(process.cwd(), '../../tools/test-data/seed-9-slice-fixtures.ps1');
    const stat = await fs.stat(scriptPath).catch(() => null);
    expect(stat, `seed 스크립트 누락: ${scriptPath}`).not.toBeNull();
    expect(stat!.size).toBeGreaterThan(1000);
  });

  test('scenarios.md 존재 검증 (docs/qa/integration-pr-9-slice/scenarios.md)', async () => {
    const fs = await import('node:fs/promises');
    const path = await import('node:path');
    const scenariosPath = path.resolve(process.cwd(), '../../docs/qa/integration-pr-9-slice/scenarios.md');
    const content = await fs.readFile(scenariosPath, 'utf8').catch(() => null);
    expect(content, `scenarios.md 누락: ${scenariosPath}`).not.toBeNull();
    // 9 슬라이스 모두 본문에 포함
    for (const slice of SLICES) {
      expect(content!).toContain(slice.name);
    }
    // 합계 160 case 명세
    expect(content!).toContain('160');
  });
});
