# A2-2 출고전표 enforcement Implementation Plan

> **For agentic workers:** Codex 초기 구현([[feedback_codex_implements_claude_reviews]]), 리뷰-라운드 fix 는 리뷰 모델 직접. Steps `- [ ]`.

**Goal:** 출고전표 accept/inspect 를 결재라인 결재자(그룹∪개인)만 수행하도록 강제(opt-in, INBOUND 무영향).

**Architecture:** auth `POST /internal/approval-line/authorize`(결재자 집합 동적 검증) ← slip-service `ApprovalLineAuthorizeClient` 가 accept/inspect 게이트에서 호출(slipType==OUTBOUND·system bypass). Flyway 신규 없음.

**Tech Stack:** Spring Boot 3.3 / Java 17 / RestClient / X-Internal-Token / Testcontainers.

## Global Constraints
- 실HTTP IT @MockBean 금지([[feedback_restclient_contract_test_false_green]]) — slip→auth 계약은 MockRestServiceServer 또는 실 auth.
- BaseEntity·한국어 Javadoc·도메인 메서드 chain. UUID 비공개.
- opt-in(결재자 0개=무중단). INBOUND 회귀 0(핵심). 4-eye 없음.
- spec: docs/superpowers/specs/2026-06-21-approval-outbound-enforcement-a2-2-design.md

---

### Task 1: auth — 결재자 인가 엔드포인트

**Files:**
- Create: `services/auth-service/.../service/ApprovalLineAuthorizationService.java`
- Create: `services/auth-service/.../web/ApprovalLineAuthorizeController.java`(또는 기존 internal 컨트롤러 확장), `.../web/dto/ApprovalLineAuthorizeRequest.java`(`{String documentType, String actionKey, UUID userId}`), `.../web/dto/ApprovalLineAuthorizeResponse.java`(`{boolean configured, boolean allowed}`)
- Modify: `repository/ApprovalLineConfigRepository.java`(findByDocumentTypeAndActionKeyAndIsDeletedFalse), `repository/AccountGroupRepository.java`(필요 시 findGroupIds)
- Test: `.../service/ApprovalLineAuthorizationServiceTest.java`, `.../it/ApprovalLineAuthorizeControllerIT.java`

**Interfaces:**
- Produces: `ApprovalLineAuthorizeResponse authorize(String documentType, String actionKey, UUID userId)`.

- [ ] **Step 1: 실패 테스트(단위)** — authorize: 결재자0(configured=false,allowed=false) / USER 일치(true,true) / GROUP 소속(true,true) / 비결재자(true,false) / 미존재 action_key(false,false).
- [ ] **Step 2: FAIL 확인.**
- [ ] **Step 3: 구현** — service: `roleRepository.findByDocumentTypeAndActionKeyAndIsDeletedFalse(documentType, actionKey)` → 없으면 `{false,false}`. 있으면 `approverRepository.findByConfigRoleIdAndIsDeletedFalse(roleId)` → configured=approvers.nonEmpty. allowed= USER refId==userId 존재 OR (userId 의 활성 group id 집합 ∩ GROUP refId 집합 ≠ ∅; account_groups 조회). controller `POST /internal/approval-line/authorize` X-Internal-Token 가드(기존 internal 컨트롤러 보안 패턴 재사용).
- [ ] **Step 4: PASS(단위) + IT** — X-Internal-Token 200 + 토큰없음 4xx. @MockBean 없음.
- [ ] **Step 5: commit** `feat(auth): A2-2 결재자 인가 엔드포인트 /internal/approval-line/authorize`.

---

### Task 2: slip-service — accept/inspect 출고 게이트

**Files:**
- Create: `services/slip-service/.../client/ApprovalLineAuthorizeClient.java`(RestClient, X-Internal-Token; auth base url 설정), `.../client/dto/ApprovalLineAuthorizeResult.java`
- Modify: `service/SlipService.java`(accept/inspect 게이트), config(auth url·internal token 주입 — 기존 *InternalClient 설정 재사용)
- Test: `.../it/SlipOutboundApprovalEnforcementIT.java`(실HTTP)

**Interfaces:**
- Consumes: auth `POST /internal/approval-line/authorize`.
- Produces: `ApprovalLineAuthorizeClient.authorize(documentType, actionKey, userId): {configured, allowed}`.

- [ ] **Step 1: 실패 테스트(IT)** — OUTBOUND accept: 결재자 지정 후 비결재자 403·결재자 200(dispatcherUserId 자동채움 유지). OUTBOUND inspect 동일. **INBOUND accept/inspect 200(게이트 미적용, 회귀)**. opt-in(결재자0) 200. ApprovalLineAuthorizeClient=MockRestServiceServer 또는 실 auth(@MockBean 금지).
- [ ] **Step 2: FAIL 확인.**
- [ ] **Step 3: 구현** — `ApprovalLineAuthorizeClient`(RestClient.post, X-Internal-Token 헤더, base=auth). `SlipService.accept(id, acceptorUserId)`: 슬립 로드 후 `if (slip.getSlipType()==OUTBOUND && isRealUser(acceptorUserId))` → `authorize("SLIP_OUTBOUND","OUTBOUND_DISPATCH",acceptorUserId)` → `if (r.configured() && !r.allowed()) throw BusinessException(FORBIDDEN,"출고 수락 권한이 없습니다 — 출고인 결재자(그룹/개인)만 처리할 수 있습니다")`. **자동채움·inventory.reserve 전**에 가드. `inspect` 동일(OUTBOUND_INSPECT, "출고 검수 권한이 없습니다 …"). `isRealUser` = acceptorUserId 가 null/'system' 아님 + UUID 파싱 가능. 기존 컨트롤러 @RequirePermission·inspect 의 inbound.inspection 가드 유지.
- [ ] **Step 4: PASS(IT) — INBOUND 회귀 0 확인.**
- [ ] **Step 5: commit** `feat(slip): A2-2 출고 accept/inspect 결재자 게이트(OUTBOUND·opt-in·system bypass)`.

---

## Self-Review
- 스펙 커버: auth authorize(T1)·slip 게이트(T2)·opt-in(T1 configured/T2 skip)·INBOUND 회귀(T2)·system bypass(T2)·action_key 앵커(T1). ✅
- 타입 일치: authorize {documentType,actionKey,userId}→{configured,allowed} auth↔slip 정합.
- 마이그: 신규 없음(조회만).
- placeholder: 없음.

## 검증 게이트
auth+slip 전체 test(실HTTP, INBOUND 회귀) → 🐳 라이브 QA(출고인 결재자 지정→결재자 accept 200·비결재 403·입고 무영향) → 🔵Opus 5-agent+QA(순차) → 🟣Codex 5-agent+QA → 양쪽 blocking 0 → 머지.
