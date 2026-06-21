# A2-3 입고 enforcement Implementation Plan

> Codex 초기 구현, 리뷰-라운드 fix 는 리뷰 모델 직접. A2-2 출고 미러링.

**Goal:** 입고전표 accept/inspect 를 SLIP_INBOUND 결재자(입고인/검수인)만 수행(opt-in, A2-2 동일 모델).

**Architecture:** V63 SLIP_INBOUND config 시드(action_key INBOUND_RECEIVE/INBOUND_INSPECT) + SlipService 게이트 slipType 일반화 + FE 입고전표 드롭다운. auth authorize/client 무변경(generic).

## Global Constraints
- 실HTTP IT @MockBean 격리 + ClientTest 계약([[restclient-contract-test-false-green]]). V61/V62 불변, V63 fresh probe([[migration-fresh-postgres-probe]]).
- 변경 모듈(slip+auth) **전체 test 완주 후 push**([[changed-module-full-test-before-push]]). opt-in·system bypass·INBOUND/OUTBOUND 양 게이트.
- spec: docs/superpowers/specs/2026-06-22-approval-inbound-enforcement-a2-3-design.md

---

### Task 1: auth — V63 SLIP_INBOUND config 시드

**Files:**
- Create: `services/auth-service/.../db/migration/V63__approval_line_inbound_seed.sql`
- Test: `.../it/AuthFlywayV63SeedIT.java`

- [ ] **Step 1:** V63 — INSERT approval_line_config SLIP_INBOUND 3행: (작성자,CREATOR,seq0,action_key NULL)·(입고인,GROUP,seq1,action_key='INBOUND_RECEIVE')·(검수인,GROUP,seq2,action_key='INBOUND_INSPECT'), required=TRUE, created_by='v63-seed', gen_random_uuid(). V61 INSERT...SELECT VALUES 패턴 재사용 + action_key 컬럼 포함. ON CONFLICT/멱등(동일 doctype 재시드 방지 — WHERE NOT EXISTS 또는 unique uq_..._doctype_seq_active 의존).
- [ ] **Step 2:** fresh Postgres probe — V1..V63 적용 → SLIP_INBOUND 3역할·action_key 2건(INBOUND_RECEIVE/INSPECT)·작성자 NULL 확인.
- [ ] **Step 3:** AuthFlywayV63SeedIT — SLIP_INBOUND 3역할 + action_key 매핑 단언(immutable seed, 공유DB 주의).
- [ ] **Step 4:** commit `feat(auth): A2-3 V63 SLIP_INBOUND 결재라인 시드(입고인=INBOUND_RECEIVE/검수인=INBOUND_INSPECT)`.

---

### Task 2: slip — accept/inspect 게이트 slipType 일반화

**Files:**
- Modify: `services/slip-service/.../service/SlipService.java`(enforceOutboundApprovalLine → enforceSlipApprovalLine 일반화 또는 INBOUND 분기 추가, INBOUND 상수)
- Test: `.../it/SlipOutboundApprovalEnforcementIT.java`(→ INBOUND 케이스 추가, skipOutboundGate 갱신) 또는 신규 `SlipInboundApprovalEnforcementIT.java`

**Interfaces:**
- INBOUND_RECEIVE_ACTION_KEY="INBOUND_RECEIVE", INBOUND_INSPECT_ACTION_KEY="INBOUND_INSPECT", SLIP_INBOUND_DOC_TYPE="SLIP_INBOUND".

- [ ] **Step 1: 실패 테스트(IT)** — INBOUND accept: 결재자(입고인=그룹) 지정 후 비결재자 403("입고 수령 권한이 없습니다 …")·결재자 200(자동채움 유지). INBOUND inspect 동일(INBOUND_INSPECT, "입고 검수 권한이 없습니다 …"). **OUTBOUND 회귀 무변**(기존 케이스 green). **기존 `inboundAcceptAndInspect_skipOutboundGate`(verifyNoInteractions) 는 INBOUND 미설정 opt-in 200 + authorize INBOUND_RECEIVE 호출되도록 갱신**(verifyNoInteractions 제거 또는 INBOUND actionKey 호출 단언).
- [ ] **Step 2: FAIL 확인.**
- [ ] **Step 3: 구현** — SlipService accept: `slip.slipType` 으로 (documentType, actionKey) 결정 — OUTBOUND→(SLIP_OUTBOUND,OUTBOUND_DISPATCH)/INBOUND→(SLIP_INBOUND,INBOUND_RECEIVE) → enforce(자동채움·reserve 전, 실사용자·opt-in·system bypass A2-2 동일). inspect 동일(OUTBOUND_INSPECT/INBOUND_INSPECT). 컨트롤러 inbound.inspection(INBOUND inspect) 가드 유지(건드리지 말 것). 기존 enforceOutboundApprovalLine 시그니처 일반화(documentType·actionKey·errorMsg 인자).
- [ ] **Step 4: PASS — slip 전체 test 완주(OUTBOUND+INBOUND, 회귀 0).**
- [ ] **Step 5: commit** `feat(slip): A2-3 입고 accept/inspect 결재자 게이트(slipType 일반화·opt-in)`.

---

### Task 3: FE — 입고전표 전표종류

**Files:**
- Modify: `clients/desktop/src/renderer/api/approvalLineConfigApi.ts`(DOC_TYPES)
- Test: `api/approvalLineConfigApi.test.ts`(SLIP_INBOUND 옵션)

- [ ] **Step 1:** DOC_TYPES 에 `{value:'SLIP_INBOUND', label:'입고전표'}` 추가. (칩/순서/라벨/필수 UI 는 documentType generic — 변경 불요.)
- [ ] **Step 2:** typecheck + lint + vitest(DOC_TYPES SLIP_INBOUND 단언).
- [ ] **Step 3:** commit `feat(desktop): A2-3 결재라인 설정 입고전표 전표종류 추가`.

---

## Self-Review
- 스펙 커버: V63 시드(T1)·게이트 일반화 INBOUND(T2)·FE 드롭다운(T3)·skipOutboundGate 갱신(T2)·OUTBOUND 회귀(T2). ✅
- 타입 일치: INBOUND_RECEIVE/INSPECT actionKey ↔ V63 action_key seed 정합.
- 마이그: V63 신규(fresh seed), V61/V62 불변.
- placeholder: 없음.

## 검증 게이트
auth+slip 전체 test(INBOUND+OUTBOUND 회귀) + V63 probe + FE typecheck/lint/vitest → 🐳 라이브 QA(입고 accept 결재자 200·비결재 403·검수·출고 무회귀) → 🔵Opus 5-agent+QA(순차) → 🟣Codex 5-agent+QA → 양쪽 0 → 머지.
