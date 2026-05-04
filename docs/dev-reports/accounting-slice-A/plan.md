# Accounting Slice A — Plan (FE 작업 기준)

> 본 문서는 Phase 4 회계 첫 슬라이스의 FE 산출물 정의서. BE/Designer/QA/DevOps
> 동시 산출물은 별도 PR/리포트에서 다룬다. 본 슬라이스는 회계 도메인 신규
> 라우트 5종, 디자인 시스템 신규 컴포넌트 4종, mock 모드 시드 데이터 확장을
> 포함한다.

## 1. 범위

### 1.1 신규 도메인
- 한국 일반기업회계기준 표준 계정과목 (100/200/300/400/500/800/900)
- 분개장 (Journal) — 헤더 + 라인 (차변/대변 동등 검증)
- 시산표 (Trial Balance) — 월별 (YYYYMM) 계정별 잔액

### 1.2 사용자 권한
- ACCOUNTANT — 분개 작성/조회/마감, 시산표 조회
- MASTER     — 모든 작업 (재무 결산 포함)
- 그 외 role — 회계 메뉴 미노출 + 라우트 직접 진입 시 redirect

## 2. 디자인 시스템 신규 컴포넌트 4종

| 이름 | 용도 | 핵심 props |
| --- | --- | --- |
| `AccountCodeSelect` | 계정과목 검색 가능 select (autocomplete + 트리 표시) | `value` `onChange` `accounts` `category?` `required` `error` |
| `JournalStatusBadge` | DRAFT / POSTED / REVERSED 3 variants (SlipStatusBadge 패턴) | `status` |
| `MoneyInput` | 통화 포맷팅 입력 (자동 콤마, KRW only) | `value:number` `onChange:(n)=>void` `placeholder` `disabled` `max` `min` `error` |
| `JournalLineRow` | 분개 라인 (계정 select + 차변 + 대변 + 거래처 + 메모 + 삭제) | `line` `accounts` `onChange` `onRemove` `index` `disabled` |

각 컴포넌트는 `<Name>.tsx` + `.module.css` + `.stories.tsx` + `index.ts`
4 파일 셋트. `src/index.ts` 에 export 추가.

## 3. desktop 라우트 5종

| 경로 | 컴포넌트 | 화면명 (헤더) |
| --- | --- | --- |
| `/accounting/accounts` | `AccountTreePage` | 계정과목 |
| `/accounting/journals` | `JournalListPage` | 분개장 |
| `/accounting/journals/new` | `JournalFormPage` | 분개 작성 |
| `/accounting/journals/:id` | `JournalDetailPage` | 분개 상세 |
| `/accounting/journals/:id/edit` | `JournalFormPage` (edit 모드) | 분개 편집 |
| `/accounting/balances` | `TrialBalancePage` | 시산표 |

`AppLayout` 사이드바 새 그룹 "회계" 추가 (계정과목 / 분개장 / 시산표).
ACCOUNTANT/MASTER 권한 보유자만 그룹 가시.

## 4. API 클라이언트 (`api/accounting.ts`)

```
GET    /accounting/accounts              listAccounts()
GET    /accounting/journals              listJournals(opts)
GET    /accounting/journals/{id}         getJournal(id)
POST   /accounting/journals              createJournal(body)
POST   /accounting/journals/{id}/post    postJournal(id)
POST   /accounting/journals/{id}/reverse reverseJournal(id, reason)
GET    /accounting/balances?period=YYYYMM getTrialBalance(period)
```

TypeScript interfaces: `Account` / `Journal` / `JournalLine` /
`TrialBalanceRow` (모두 export).

## 5. mock 모드 확장

- 표준 계정 ~50개 시드 (BE 와 동일 — 7 카테고리)
- 분개 sample 5+ rows (DRAFT 1, POSTED 3, REVERSED 1)
- TrialBalance period 1개 (202605) sample

## 6. 검증 의무

- `cd clients/web/design-system && npm run build` PASS
- `cd clients/desktop && npm run typecheck` PASS
- `cd clients/desktop && npm run lint` PASS

## 7. 회귀 가드

- `feedback_uuid_no_user_visibility.md` — Journal/Account UUID 화면 미노출
- `feedback_role_naming_full.md` — RouteGuard 풀네임 (ACCOUNTANT/MASTER)
- `feedback_function_documentation.md` — 한국어 JSDoc + dev-reports
- `feedback_korean_commits.md` — 커밋 메시지 한국어
