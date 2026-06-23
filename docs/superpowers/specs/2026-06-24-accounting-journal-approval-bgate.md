# 슬라이스 ④ — 회계전표 B-게이트 결재 enforcement 설계서

> 작성 2026-06-24 (Opus 기획). 개발책임자 결정: **회계전표부터 B-게이트 확장(옵션 1)만 진행 후 세션 종료.**
> 선행: 출고#556/입고#558/주문#559 B-게이트 완료([[project_approval_enforcement_epic]]). 동일 패턴(ApprovalLineAuthorizeClient)을 회계전표 게시에 적용.

## 1. 배경 / 패턴

B-게이트 = 결재라인 결재자(그룹∪개인)를 액션 권한게이트로 강제. 패턴(slip-service/partner-order 동일):
- 서비스가 `ApprovalLineAuthorizeClient.authorize(documentType, actionKey, actorId)` → auth `POST /auth/internal/approval-line/authorize` → `{configured, allowed}`.
- 액션 직전 게이트: `if (result.configured() && !result.allowed()) → BusinessException(FORBIDDEN)`.
- **opt-in 핵심**: `configured=true` 는 결재자(approver)가 **≥1명 지정됐을 때만**(auth `ApprovalLineAuthorizationService.authorize` — config role 있어도 approver 0명이면 `configured=false`). → 구조만 시드하고 결재자 미시드면 게이트 통과(기존 게시 무영향). 회계 관리자가 admin UI(admin.approval-line-config)로 결재자 지정 시 enforced.

## 2. 게이트 대상

**`POST /accounting/journals/{id}/post`** (게시, DRAFT→POSTED) — 회계전표의 재무 확정 액션. `JournalService.post(id, caller)` 직전 게이트.
- 비대상: create(작성)/reverse(역분개)/조회. (게시 1개만 — 출고 confirm/주문 convert 와 동일 "확정" 시점.)

## 3. BE — accounting-service

### 3.1 ApprovalLineAuthorizeClient (신규, slip 패턴 복제)
`services/accounting-service/.../client/ApprovalLineAuthorizeClient.java` — slip-service 동일 구조: `@Autowired` 생성자(loadBalancedRestClientBuilder + SimpleClientHttpRequestFactory 2s/3s) + **테스트 전용 패키지-프라이빗 생성자**(MockRestServiceServer RestClient 직접 주입). `POST /auth/internal/approval-line/authorize` {documentType, actionKey, userId} → parse {configured, allowed}. `ApprovalLineAuthorizeResult`(record configured/allowed) 동반.

### 3.2 JournalService.post 게이트
- 상수: `APPROVAL_DOCUMENT_TYPE = "ACCOUNTING_JOURNAL"`, `APPROVAL_ACTION_KEY = "JOURNAL_POST"`.
- `post(id, caller)` 진입 직후 `enforceApprovalLine(actorId)`:
  - actorId 해석: caller(X-User-Id) → UUID. **real-user only** — system/null actor 는 skip(서비스 내부 호출 무영향, [[project_approval_enforcement_epic]] 패턴).
  - `result = client.authorize(ACCOUNTING_JOURNAL, JOURNAL_POST, actorId); if (result.configured() && !result.allowed()) throw BusinessException(FORBIDDEN, "결재라인 결재자만 회계전표를 게시할 수 있습니다.");`
- **DI 가드**: ApprovalLineAuthorizeClient @Autowired (없으면 빈 미생성 → 무관 서비스 회귀 방지는 accounting 자체라 무관). 기존 post 흐름·도메인 차/대 검증 무변경.

## 4. auth-service — V## 멱등 시드 (document type 구조)

`approval_line_config` 에 ACCOUNTING_JOURNAL 구조 등록(멱등, V61/V62 SLIP_OUTBOUND 패턴):
- seq 0: `작성자`, step_type=CREATOR, action_key=null
- seq 1: `결재자`, step_type=GROUP, action_key=`JOURNAL_POST`
- **approval_line_approver 미시드** → configured=false (opt-in, 기존 게시 무영향). admin UI 에서 회계 관리자가 결재자(그룹/개인) 지정 시 enforced.
- 멱등: `WHERE NOT EXISTS (document_type='ACCOUNTING_JOURNAL')`. 적용된 마이그 불변·신규 V## 만([[feedback_applied_migration_immutable]]). fresh Postgres probe 검증([[feedback_migration_fresh_postgres_probe]]).
- admin.approval-line-config UI 는 기존 재사용(신규 화면 0) — 구조 시드로 ACCOUNTING_JOURNAL 가 목록 노출.

## 5. IT (실 HTTP, false-green 가드)

- accounting `JournalApprovalGateIT`(MockRestServiceServer ApprovalLineAuthorizeClient):
  - configured=true & allowed=false → post **403** + auth /authorize 호출 단언(경로/헤더/바디 documentType=ACCOUNTING_JOURNAL actionKey=JOURNAL_POST).
  - configured=false → post **200**(opt-in 미설정 게시 통과).
  - configured=true & allowed=true → post **200**.
- 기존 Journal IT: `@MockBean ApprovalLineAuthorizeClient`(configured=false 기본) → 무영향([[feedback_preauth_migration_lessons]]).
- auth: V## 시드 fresh probe(ACCOUNTING_JOURNAL config 존재, approver 0) — 선택.

## 6. 비목표 (YAGNI)
- 견적/배차/그룹웨어 결재(별도 슬라이스). create/reverse 등 다른 액션. 결재자 설정 UI 신규(기존 admin.approval-line-config 재사용). FE 변경 최소(게시 403 시 기존 에러 토스트).

## 7. 라이브 QA
- accounting-service 재빌드. (a) 결재라인 미설정(approver 0) → 회계전표 게시 200(기존동작). (b) admin UI 로 ACCOUNTING_JOURNAL 결재자 지정 후 → 비결재자 게시 403, 결재자 게시 200. 실 게이트웨이:8080 + dev_master + 데스크톱 회계 화면(또는 API 직접). 가짜 캡처 금지.

## 8. 산출물
- BE: accounting ApprovalLineAuthorizeClient + ApprovalLineAuthorizeResult + JournalService.post 게이트 + JournalApprovalGateIT
- auth: V## ACCOUNTING_JOURNAL 시드
- dev-report `docs/dev-reports/2026-06-24-accounting-journal-approval-bgate.md`, 라이브 QA `docs/qa/accounting-journal-bgate/`
