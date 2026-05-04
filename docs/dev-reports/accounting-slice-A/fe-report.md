# Accounting Slice A — FE Report

> Phase 4 회계 첫 슬라이스의 FE 산출 요약. BE/Designer/QA/DevOps 동시 산출은
> 별도 PR / report 에서 다룬다. 본 보고서는 디자인 시스템 신규 컴포넌트 4종,
> desktop 라우트 5종, mock 모드 시드 확장, 검증 결과를 정리한다.

## 1. 산출물 요약

### 1.1 디자인 시스템 신규 컴포넌트 4종 (21 → 25)

| 이름 | 파일 셋트 | 설명 |
| --- | --- | --- |
| `AccountCodeSelect` | `.tsx` + `.module.css` + `.stories.tsx` + `index.ts` | 계정과목 검색 가능 select. Autocomplete + 카테고리 그룹 트리 dropdown. UUID 미사용 (4자리 code 기반) |
| `JournalStatusBadge` | 위와 동일 | DRAFT / POSTED / REVERSED 3 variants. SlipStatusBadge 패턴 (pill + tier 색상) |
| `MoneyInput` | 위와 동일 | KRW 정수 (number) 입출력. 자동 콤마, 음수/소수 차단, max/min cap |
| `JournalLineRow` | 위와 동일 | 분개 라인 1건 (계정 + 차변 + 대변 + 거래처 + 메모 + 삭제). AccountCodeSelect + MoneyInput 컴포지트 |

`clients/web/design-system/src/index.ts` 에 4개 export 추가
(accounting-slice-A 코멘트 블록).

### 1.2 Desktop 라우트 신규 5종

| 경로 | 컴포넌트 | RoleGuard |
| --- | --- | --- |
| `/accounting/accounts` | `AccountTreePage` | ACCOUNTANT / MASTER |
| `/accounting/journals` | `JournalListPage` | ACCOUNTANT / MASTER |
| `/accounting/journals/new` | `JournalFormPage` (create) | ACCOUNTANT / MASTER |
| `/accounting/journals/:id/edit` | `JournalFormPage` (edit) | ACCOUNTANT / MASTER |
| `/accounting/journals/:id` | `JournalDetailPage` | ACCOUNTANT / MASTER |
| `/accounting/balances` | `TrialBalancePage` | ACCOUNTANT / MASTER |

신규 컴포넌트 `RoleGuard` (`components/RoleGuard.tsx`) — role 화이트리스트 기반
라우트 가드. 풀네임 표기 (M/M/D 약어 금지) + 미허용 시 안내 문구 + 대시보드
복귀 버튼.

### 1.3 사이드바 메뉴 그룹 "회계"

`AppLayout.tsx` 갱신 — `canAccessAccounting(role)` 헬퍼 통과 시에만 회계 그룹
가시. 그룹 헤더 + 3 NavLink (계정과목 / 분개장 / 시산표).

### 1.4 API 클라이언트 신규 — `api/accounting.ts`

함수:
- `listAccounts()` — GET /accounting/accounts
- `listJournals(opts)` — GET /accounting/journals (period / status 필터)
- `getJournal(id)` — GET /accounting/journals/{id}
- `createJournal(body)` — POST /accounting/journals
- `postJournal(id)` — POST /accounting/journals/{id}/post
- `reverseJournal(id, reason)` — POST /accounting/journals/{id}/reverse
- `getTrialBalance(period)` — GET /accounting/balances?period=YYYYMM

타입: `Account` (`@samhan/design-system` 재수출), `Journal`, `JournalLine`,
`JournalSummary`, `CreateJournalRequest`, `TrialBalance`, `TrialBalanceRow`.

권한 헬퍼: `canAccessAccounting`, `canCreateJournal`, `canPostJournal`. 풀네임
ACCOUNTANT/MASTER 만 매핑.

### 1.5 Mock 모드 시드 확장 — `api/mock.ts`

- 표준 계정 시드 50개 (한국 일반기업회계기준 7 카테고리)
- 분개 sample 5건 (POSTED 3 / DRAFT 1 / REVERSED 1) + 라인 13개
- 시산표 sample 1건 (period=202605, 5 row)
- 7 endpoint mock router (GET accounts/journals/journal 단건, POST journals/post/reverse, GET balances)

## 2. 검증 결과

| 검증 | 상태 | 비고 |
| --- | --- | --- |
| `cd clients/web/design-system && npm run build` | PASS | 87 modules, vite 2.12s, dts 1.84s. dist/index.d.ts 에 4개 신규 컴포넌트 export 24회 등장 |
| `cd clients/desktop && npm run typecheck` | PASS | tsc-node + tsc-web 양쪽 0 error |
| `cd clients/desktop && npm run lint` | PASS (0 error) | 1 warning 은 기존 SlipDetailPage.tsx (본 슬라이스 무관) |

## 3. 회귀 가드 준수

| 가드 | 적용 |
| --- | --- |
| `feedback_uuid_no_user_visibility.md` | 모든 회계 화면에서 journal.id / line.id / account UUID 미노출. 사용자 노출 식별자는 `journalNo` (예 `JV-2026/05-001`) 와 `accountCode` (4자리). 라우팅 path 만 UUID 사용 |
| `feedback_role_naming_full.md` | `RoleGuard` props 와 안내 메시지 모두 풀네임 (ACCOUNTANT / MASTER). `canAccessAccounting` / `canCreateJournal` / `canPostJournal` 헬퍼도 풀네임 비교 |
| `feedback_function_documentation.md` | 모든 신규 컴포넌트 / 라우트 / API 함수에 한국어 JSDoc 의무. dev-reports 본 문서 작성 |
| `feedback_korean_commits.md` | 커밋 메시지 한국어 작성 예정 (prefix + trailer 만 영문) |

## 4. Designer 충실도

본 슬라이스는 Designer spec 산출이 본 worktree 시점에 미존재 — 따라서 Plan
(`plan.md`) 의 자체 정의 component / 라우트 명세를 기준으로 작업. Designer
산출이 도입되면 다음 사항을 우선 정렬해야 한다:

1. 사이드바 회계 그룹 헤더 톤/위치 — Designer 통일 spec 도입 시 inline style 제거
2. JournalStatusBadge tier-3 hue — SlipStatusBadge 와 동일한 녹색 계열 사용 중,
   Designer 가 별도 hue 제안 시 token 변경
3. JournalLineRow grid 폭 (220 / 140 / 140 / 160) — Designer 의 12-col grid spec 도입 시 px → fr 단위 전환

## 5. 회귀 위험

| 영역 | 위험 | 완화 |
| --- | --- | --- |
| `mock.ts` | 신규 router clause 가 위쪽 slip / transfer router 와 endpoint 충돌 가능 (regex 순서 의존) | `/accounting/...` prefix 분리로 격리. 기존 `/slips/...`, `/inventory/...` regex 와 비충돌 확인 |
| `AppLayout` | 사이드바 conditional 그룹 — 비-ACCOUNTANT role 진입 시 회계 라우트 직접 입력 가능 | `RoleGuard` 이중 방어 — 라우트 진입 시도 시 안내 문구 + 대시보드 redirect |
| `JournalFormPage edit 모드` | 기존 분개 fetch → 폼 hydrate. 저장 시 PATCH 가 아닌 신규 createJournal 호출 (BE PATCH 미존재) | 본 슬라이스 한정 — BE 가 PATCH 도입 시 mutation 전환. 사용자 혼동 방지 헤더 라벨 "분개 편집" 유지 |
| `RouteGuard` 풀네임 prop | 풀네임 string 배열 직접 전달 — 오타 발생 시 컴파일러가 못 잡음 | 향후 `Role` literal type 도입 + `readonly Role[]` 시그니처로 강화 (다음 슬라이스) |

## 6. 다음 단계

1. **BE 슬라이스** — `accounting-service` Spring Boot 신규 서비스. 본 FE 의 7
   endpoint contract 와 동일 응답 envelope 으로 구현. mock 시드와 동일한 50
   계정 Flyway V1 시드 + journal/journal_line 테이블 + post/reverse RPC.
2. **Designer 슬라이스** — `docs/design/accounting-slice-A/` 6 spec 작성
   (README / wireframes / components / ux-flow / tokens / mobile-spec) +
   5 mock + 5 캡처. 본 FE 에서 inline style 로 자리 잡은 부분을 token 으로 흡수.
3. **QA 슬라이스** — `JournalFormPage` 차/대변 검증 + post/reverse 권한 매트릭스
   E2E. `feedback_pr_qa_screenshots.md` 의무 — `docs/qa/accounting-slice-A/*.png`.
4. **DevOps 슬라이스** — `accounting-service` Eureka 등록 + api-gateway route
   추가 (`/accounting/**` → accounting-service). docker-compose 신규 컨테이너.

## 7. 변경 파일 목록

### 신규 (16)
- `docs/dev-reports/accounting-slice-A/plan.md`
- `docs/dev-reports/accounting-slice-A/fe-report.md`
- `clients/web/design-system/src/components/AccountCodeSelect/{.tsx,.module.css,.stories.tsx,index.ts}`
- `clients/web/design-system/src/components/JournalStatusBadge/{.tsx,.module.css,.stories.tsx,index.ts}`
- `clients/web/design-system/src/components/MoneyInput/{.tsx,.module.css,.stories.tsx,index.ts}`
- `clients/web/design-system/src/components/JournalLineRow/{.tsx,.module.css,.stories.tsx,index.ts}`
- `clients/desktop/src/renderer/api/accounting.ts`
- `clients/desktop/src/renderer/components/RoleGuard.tsx`
- `clients/desktop/src/renderer/routes/AccountTreePage.tsx`
- `clients/desktop/src/renderer/routes/JournalListPage.tsx`
- `clients/desktop/src/renderer/routes/JournalFormPage.tsx`
- `clients/desktop/src/renderer/routes/JournalDetailPage.tsx`
- `clients/desktop/src/renderer/routes/TrialBalancePage.tsx`

### 수정 (3)
- `clients/web/design-system/src/index.ts` (4 export 추가)
- `clients/desktop/src/renderer/routes/index.tsx` (5 라우트 + RoleGuard 등록)
- `clients/desktop/src/renderer/components/AppLayout.tsx` (회계 그룹 + canAccessAccounting 가시 분기)
- `clients/desktop/src/renderer/api/mock.ts` (회계 mock router + 50 계정 + 5 분개 + 시산표 시드)
