# 슬1 — 판매전표 양식 통일 + 명칭 정정 Implementation Plan

> **For agentic workers:** 본 plan 은 프로젝트 워크플로우상 **Codex 가 구현**([[codex-implements-claude-reviews]]·[[temp-multimodel-workflow]])한다. Opus 는 계획/리뷰/PR. 실행 = Codex 디스패치 → Opus·Codex 듀얼 5-agent 0수렴 → Docker 라이브 실QA 캡처 → PR. Steps 는 체크박스(`- [ ]`)로 추적.

**Goal:** 출고 인쇄 산출물을 단일 양식(작업지시서)으로 통일하고 사용자 노출 명칭을 "판매전표"로 정정한다. 중복·고아인 `OutboundView`(금액 단 "출고전표")를 폐기한다.

**Architecture:** FE 전용(BE/Flyway 무변). `OutboundView`(인터랙티브 미리보기, 상세 인쇄 메뉴 미연결 = 고아 라우트)와 `/sales/:id/print/outbound` 라우트를 제거하고, 출고 인쇄 명칭(`DispatchView` 화면명·판매전표 상세 화면명·인쇄 메뉴 버튼·결재라인 설정 DOC 라벨)을 "판매전표"로 변경한다. 거래명세서(`/print/statement`)·세금계산서(`/print/invoice`)는 별도 유지.

**Tech Stack:** React 18 + TypeScript, Electron, @samhan/design-system, react-router(HashRouter), @tanstack/react-query, Vitest, Playwright.

## Global Constraints
- **기술 키 불변**: documentType `SLIP_OUTBOUND` 등 식별자·주석·코드 심볼은 변경 금지. **사용자 노출 텍스트(라벨/화면명/버튼)만** "판매전표"로 변경([[jeonpyo-not-slip]] 식 명칭 규칙 — 식별자≠라벨).
- **건드리지 말 것(오인 금지)**: groupware doc-ref enum 라벨 '출고전표'(`groupware-approval-templates-qa.spec.ts:126`, ApprovalReferenceDocType — 별개 시스템), phase-2-4 restore toast '출고전표' 문자열, purchase-query/d2-6d 주석. 본 슬라이스 대상 아님.
- **입고전표/주문 명칭 유지**: `SLIP_INBOUND`="입고전표", `PARTNER_ORDER`="주문" 라벨 현행(개발책임자 별도 지정 시 변경). 이번엔 `SLIP_OUTBOUND` 노출만.
- **print-renderer 비범위**: `print-renderer/PrintRendererApp.tsx`(헤드리스 사본 합성)는 OutboundView 레이아웃을 자체 복제(import 아님)하므로 OutboundView.tsx 삭제에 안 깨짐. 사본 양식 통일은 슬3 이연 — 본 슬라이스에서 건드리지 않음.
- **FE green = typecheck + lint + vitest 전부**([[desktop-typecheck-command]]), cwd `clients/desktop`. playwright 는 `node_modules/.bin/playwright`([[playwright-local-version-skew]]).
- **머지 전 Docker 라이브 실QA 의무**([[no-fake-data-ever]]·[[overnight-live-capture]]): 실 게이트웨이 :8080 + 실 로그인, VITE_MOCK_MODE off.

---

### Task 1: OutboundView(고아 라우트) 폐기

**Files:**
- Modify: `clients/desktop/src/renderer/routes/index.tsx:83`(import 제거), `:531-532`(라우트+주석 제거)
- Delete: `clients/desktop/src/renderer/print/OutboundView.tsx`
- Modify(테스트 참조 5곳, `/print/outbound` → `/print/dispatch` 전환 또는 해당 케이스 제거):
  - `clients/desktop/playwright/audit/full-screen-audit.spec.ts:112`
  - `clients/desktop/playwright/print-preview-standardization/print-preview-standardization-real-qa.spec.ts:185`
  - `clients/desktop/playwright/supplier-profile-bank-stamp-real-qa/print-supplement-real-qa.spec.ts:131,187`
  - `clients/desktop/playwright/supplier-profile-bank-stamp-real-qa/supplier-profile-bank-stamp-real-qa.spec.ts:1183`

**Interfaces:**
- Produces: `/sales/:id/print/outbound` 라우트 제거(404). 출고 인쇄 경로 = `/print/dispatch`(작업지시서) 단일.
- Consumes: 없음(OutboundView 는 상세 인쇄 메뉴 미연결, production 링크 0 — grep 확인필).

- [ ] **Step 1: 제거 전 production 링크 재확인** — `/print/outbound` 가 src(비-test)에서 라우트 정의 외 참조 없는지 grep.

Run: `cd clients/desktop && grep -rn "print/outbound" src/`
Expected: `routes/index.tsx` 의 라우트 정의 1건만 (다른 production 링크 0).

- [ ] **Step 2: 라우트·import 제거** — `routes/index.tsx` 에서 `import { OutboundView } from '../print/OutboundView'`(line 83), 라우트 라인과 그 위 주석(`// P0-4 신규 — 출고전표 (88mm/A4 분기)...`, line 531-532) 삭제.

- [ ] **Step 3: 컴포넌트 파일 삭제** — `git rm clients/desktop/src/renderer/print/OutboundView.tsx`

- [ ] **Step 4: playwright 참조 5곳 정리** — 각 `/print/outbound` 케이스를 `/print/dispatch`(작업지시서/판매전표 양식)로 전환하거나, 그 케이스가 OutboundView 금액양식 특정(공급가/부가세/합계·88mm) 검증이면 케이스 삭제(판매전표는 금액 없음). audit full-screen-audit 의 `{ path: '/sales/slip-001/print/outbound' }` 항목은 제거.

- [ ] **Step 5: 빌드/타입 검증**

Run: `cd clients/desktop && npm run typecheck && npm run lint`
Expected: PASS (OutboundView 미참조로 인한 unresolved import 0).

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "refactor(desktop): OutboundView(중복 출고증) 폐기 + /print/outbound 라우트 제거 (슬1)"
```

---

### Task 2: 출고 인쇄 명칭 "판매전표" 정정

**Files:**
- Modify: `clients/desktop/src/renderer/print/DispatchView.tsx:105`
- Modify: `clients/desktop/src/renderer/routes/SlipDetailPage.tsx:412`(화면명), `:1077`(인쇄 메뉴 버튼 라벨)
- Modify: `clients/desktop/src/renderer/api/approvalLineConfigApi.ts:33`(DOC_TYPES 라벨)

**Interfaces:**
- Consumes: Task 1 완료(OutboundView 제거된 라우트 트리).
- Produces: 출고 사용자 노출 명칭 = "판매전표"(화면명·버튼·결재라인 설정 DOC 라벨). 기술 키 `SLIP_OUTBOUND` 불변.

- [ ] **Step 1: 인쇄 미리보기 화면명** — `DispatchView.tsx:105` `usePageTitle('출고전표 작업지시서', displaySlipNo)` → `usePageTitle('판매전표', displaySlipNo)`.

- [ ] **Step 2: 판매전표 상세 화면명** — `SlipDetailPage.tsx:412` `isOutbound ? '출고전표 상세' : '입고전표 상세'` → `isOutbound ? '판매전표 상세' : '입고전표 상세'`(OUTBOUND 분기만).

- [ ] **Step 3: 인쇄 메뉴 버튼 라벨** — `SlipDetailPage.tsx:1077` 버튼 텍스트 `작업지시서` → `판매전표`. (onClick `/print/dispatch` 유지.)

- [ ] **Step 4: 결재라인 설정 DOC 라벨** — `approvalLineConfigApi.ts:33` `{ value: 'SLIP_OUTBOUND', label: '출고전표' }` → `{ value: 'SLIP_OUTBOUND', label: '판매전표' }`. (`SLIP_INBOUND`/`PARTNER_ORDER` 라벨 불변.)

- [ ] **Step 5: 잔여 출고 노출 라벨 sweep** — 출고 인쇄/상세의 사용자 노출 "출고전표"/"작업지시서" 텍스트가 더 있는지 확인([[defect-family-sweep-fix]] 계열 전수).

Run: `cd clients/desktop && grep -rn "작업지시서\|출고전표" src/renderer/print/DispatchView.tsx src/renderer/routes/SlipDetailPage.tsx`
Expected: 잔여 사용자 노출 텍스트 0(주석·식별자·OUTBOUND 비교 로직은 유지 대상이므로 식별 후 제외).

- [ ] **Step 6: FE green 검증**

Run: `cd clients/desktop && npm run typecheck && npm run lint && npm run test`
Expected: PASS (구 라벨 단언 vitest 없음 — 전수 확인 완료).

- [ ] **Step 7: 커밋**

```bash
git add -A
git commit -m "refactor(desktop): 출고 인쇄/상세/결재설정 명칭 '판매전표' 정정 (작업지시서→판매전표, 슬1)"
```

---

### Task 3: Docker 라이브 실QA + PR

**Files:** 산출 — `docs/qa/sales-slip-form-unify-rename-s1/*.png`, PR 본문 인라인.

**Interfaces:** Consumes: Task 1·2 완료 브랜치. Produces: 머지 가능 슬1 PR.

- [ ] **Step 1: 스택 기동** — `docker compose up -d`(필요 서비스: gateway·auth·slip·inventory·desktop dev). VITE_MOCK_MODE off, 실 게이트웨이 :8080, 실 로그인 dev_master.

- [ ] **Step 2: 라이브 캡처 3종**
  1. 판매 전표 상세 → 인쇄 메뉴 버튼 **"판매전표"** 표기 캡처 + 클릭 → `/print/dispatch` 작업지시서 양식(금액 없음, 결재란) 화면명 "판매전표" 캡처.
  2. `/sales/{id}/print/outbound` 직접 접근 → 404/no-match(폐기 확인) 캡처.
  3. 거래명세서 출력(`/print/statement`) + 계산서 출력(`/print/invoice`) 별도 정상(금액 포함) 캡처.

- [ ] **Step 3: 결재라인 설정 DOC 라벨 캡처** — 결재라인 설정 메뉴 전표종류 드롭다운 = "판매전표 / 입고전표 / 주문" 캡처.

- [ ] **Step 4: PR 생성**([[open-pr-early]]·[[pr-title-caps-bracket]]) — `[REFACTOR] 판매전표 양식 통일 + 명칭 정정 (슬1, 동적 결재라인 에픽)`. 본문: spec 링크, 변경 요약, QA 캡처 인라인, 연관 메모리. CI watch 자동([[pr-ci-monitoring]]).

- [ ] **Step 5: 듀얼 5-agent 리뷰** — Opus 5-agent(게시) → Codex 5-agent cross-check(게시) → PM 종합. blocking 0 수렴까지([[temp-multimodel-workflow]], 병렬 금지·순차). 각 라운드 QA agent + 라이브 캡처 인라인.

---

## Self-Review (writing-plans 체크)
- **Spec coverage**: 슬1(spec §4) = OutboundView 폐기(Task1) + 판매전표 명칭(Task2, D5) + 라이브 QA(Task3). print-renderer·고아 InvoiceView 정리는 spec 상 슬3/별도 결정으로 명시 이연 — 본 plan 비범위 일치. ✅
- **Placeholder scan**: TBD/TODO 없음. 모든 step 에 정확 파일:라인 + 정확 변경 텍스트 + 검증 명령. ✅
- **Type/심볼 일관**: documentType 'SLIP_OUTBOUND' 식별자 전 task 불변, 라벨만 변경 — 일관. groupware enum '출고전표'와 우리 DOC_TYPES '출고전표' 혼동 가드 명시. ✅
