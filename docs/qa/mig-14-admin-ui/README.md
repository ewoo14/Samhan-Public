# MIG-14 Admin UI QA

Playwright specs:

- `clients/desktop/playwright/mig-14-admin-ui/mig-14-order-admin.spec.ts`
- `clients/desktop/playwright/mig-14-admin-ui/mig-14-ledger-admin.spec.ts`

> ⚠️ 네이티브 편입 에픽으로 2개 spec 이 제거됨: `mig-14-aging-snapshot-admin.spec.ts`(슬1 PR #518 — 잔액 스냅샷 silo 폐기, 거래처 잔액은 네이티브 `/accounting/reports/partner-aging` 보고서로 대체) · `mig-14-cash-admin.spec.ts`(슬2 PR #520 — 현금 지출/입금 silo 폐기, 현금 자료는 네이티브 분개장/입금매칭/원장으로 대체). MIG-14 admin UI 는 4 → **2 화면**(Order / Ledger).

Screenshot target:

- `docs/qa/mig-14-admin-ui/screenshots/*.png`

Current status:

- `--list` 기준 17개 테스트 discover 완료.
- `VITE_MOCK_MODE=1` 용 `ecount.mig14.*` mock permissions seed 반영.
- Playwright runtime 의 `docs/qa` 직접 PNG 쓰기는 Windows EPERM 으로 차단되어,
  QA mock fallback PNG 4장을 `screenshots/` 아래 생성했다.
