# SP-02 회계 마감 메뉴 gap 정합화 — dev-report

작성: 2026-05-16 | 브랜치: `codex/sp-02-samhan-public-ui-gap-audit`

---

## 1. 배경

SP-01 이후 Samhan Public 매뉴얼과 데스크톱 사이드바를 추가 대조했다. `월말 마감`은 정식 route(`/accounting/period-close`)와 화면(`PeriodCloseListPage`)이 이미 있었지만, 회계 사이드바에서 발견할 수 없었다.

동시에 `매출 마감` 매뉴얼은 정식 route를 `/sales/closing`으로 안내하지만, 회계 사이드바 entry는 legacy `/warehouse/closing`으로 이동하고 있었다. 사용자는 문서를 따라도 다른 화면에 도착하거나 월말 마감 화면을 찾지 못할 수 있었다.

---

## 2. 결정

| 항목 | 결정 |
|---|---|
| 매출 마감 정식 route | `/sales/closing`을 사용한다. 판매 그룹과 회계 그룹 양쪽에서 같은 route로 이동한다. |
| 월말 마감 정식 route | `/accounting/period-close`를 회계 그룹에 노출한다. |
| 권한 | 두 route 모두 기존 `ACCOUNTANT / MANAGER / MASTER` route guard를 유지한다. 실행 버튼은 각 화면 내부의 `canExecuteClosing` 정책을 따른다. |
| backend 조회 계약 | `GET /accounting/closings`와 마감 SSE `/accounting/closings/{id}/realtime`은 `ACCOUNTANT / MANAGER / MASTER` 조회 권한으로 맞춘다. 마감 실행은 `ACCOUNTANT / MASTER`, 역마감은 `MASTER`만 유지한다. |
| UUID 비공개 | 마감 row 내부 `id`는 path/action param 전용이며 메뉴/캡처/표시 텍스트에는 노출하지 않는다. |

---

## 3. 변경 요약

### Desktop

- `AppLayout.tsx`
  - 판매 그룹에 `매출 마감` entry 추가: `/sales/closing`.
  - 회계 그룹의 기존 `매출 마감` entry를 `/warehouse/closing`에서 `/sales/closing`으로 정정.
  - 회계 그룹에 `월말 마감` entry 추가: `/accounting/period-close`.
- `clients/desktop/playwright/accounting-close-menu-gap/accounting-close-menu-gap.spec.ts`
  - 판매/회계 `매출 마감` route, 회계 `월말 마감` route, `ACCOUNTING_ROLES` guard를 정적 계약으로 검증.

### Backend

- `MonthEndCloseController`
  - `GET /accounting/closings`를 `ACCOUNTANT / MANAGER / MASTER` 조회 전용으로 정렬.
  - `POST /accounting/closings`와 `POST /accounting/closings/{id}/reverse` 권한은 기존 정책 유지.
- `AccountingRealtimeController`
  - 마감 SSE 구독만 `ACCOUNTANT / MANAGER / MASTER`로 확장해 MANAGER 조회 화면의 audit panel 흐름을 막지 않는다.
- `AccountingPeriodRepository` / `MonthEndCloseService`
  - nullable JPQL 필터를 명시적 repository method 분기로 교체해 PostgreSQL/Testcontainers에서 `year` 필터 조회 500을 방지.
- `MonthEndCloseControllerIT`
  - RED: MANAGER 목록 조회가 403/500으로 실패하는 것을 확인.
  - GREEN: MANAGER 목록 조회 200, MANAGER 마감 실행 403을 검증.
- `AccountingRealtimeIT`
  - MANAGER 마감 SSE 구독 200 + `text/event-stream` 계약을 고정.
- `HometaxExportService` / `TaxInvoiceBatchService`
  - 배치 미리보기 저장 후 `save()` 반환 entity를 응답에 사용해 `batchId=null` 회귀를 제거.
- `SupplierProfileFEMatchIT` / `TaxInvoiceBatchEndToEndIT`
  - 기존 `@Disabled` 5건을 재활성화하고 외부 `SlipQueryClient`를 `@MockBean`으로 격리.
  - 홈택스 history gzip snapshot, 1~5행 안내문 포함 xlsx row count, 제외 거래처 적용 코드를 실제 API 계약 기준으로 검증.

### 문서/QA

- `docs/manual/03-회계/04-월말-마감.md`
  - MANAGER 조회 전용 진입 권한을 명시.
- `docs/qa/sp-02-accounting-closing-menu-gap-audit/**`
  - 시나리오, 정합성 체크, 캡처 체크리스트, QA 캡처 6장 추가.
- `migration/decisions/DECISIONS.md`
  - SP-02 메뉴/route 결정 추가.

---

## 4. 검증 대상

```powershell
cd clients\desktop
npm run typecheck
npm run lint
npm run build
npx playwright test playwright/accounting-close-menu-gap/accounting-close-menu-gap.spec.ts --reporter=line
```

```powershell
.\scripts\generate-sp-02-accounting-closing-menu-gap-screenshots.ps1
Get-ChildItem docs\qa\sp-02-accounting-closing-menu-gap-audit\screenshots -Filter *.png
```

```powershell
$env:DOCKER_HOST='tcp://localhost:2375'
.\gradlew.bat :services:accounting-service:test --tests "*MonthEndCloseControllerIT" --no-daemon --rerun-tasks
.\gradlew.bat :services:accounting-service:test --no-daemon --rerun-tasks
```

최종 Docker/Testcontainers 결과:

- `:services:accounting-service:test` — `tests=204 failures=0 errors=0 skipped=0`.
- 기존 disabled 회귀 제거 — `SupplierProfileFEMatchIT.spFe2_updateAndRefetch`, `TaxInvoiceBatchEndToEndIT` 4건 모두 활성 통과.
