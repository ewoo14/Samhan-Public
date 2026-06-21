# A2-4 주문 출고전환 enforcement Implementation Plan

> Codex 초기 구현, 리뷰-라운드 fix 는 리뷰 모델 직접. 슬립(A2-2/A2-3) 미러 + partner-order-service 신규 client.

**Goal:** 주문 convert-to-slip 을 PARTNER_ORDER 승인자 결재자만 수행(opt-in, 슬립 동일 B-게이트).

**Architecture:** V64 PARTNER_ORDER config 시드(action_key PARTNER_ORDER_CONVERT) + partner-order-service ApprovalLineAuthorizeClient(slip 미러·@Autowired DI 가드) + PartnerOrderConvertService 게이트 + FE 주문 전표종류. auth authorize generic 무변경.

## Global Constraints
- 실HTTP IT client @MockBean 격리 + MockRestServiceServer 계약 + **DI 가드 테스트**([[restclient-contract-test-false-green]] — @MockBean 이 빈 생성 우회 → 라이브 부팅/DI 가드 필수). 운영 생성자 **@Autowired** 명시.
- V61~V63 불변, V64 fresh probe([[migration-fresh-postgres-probe]]). 변경 모듈(auth+partner-order) **전체 test 완주 후 push**([[changed-module-full-test-before-push]]). opt-in·system bypass.
- spec: docs/superpowers/specs/2026-06-22-approval-order-convert-enforcement-a2-4-design.md

---

### Task 1: auth — V64 PARTNER_ORDER config 시드

**Files:** Create `V64__approval_line_partner_order_seed.sql`, `it/AuthFlywayV64SeedIT.java`. Modify `ApprovalLineAuthorizationServiceTest`(PARTNER_ORDER 케이스).

- [ ] **Step 1:** V64 — INSERT approval_line_config PARTNER_ORDER 2행: (작성자,CREATOR,seq0,action_key NULL)·(승인자,GROUP,seq1,action_key='PARTNER_ORDER_CONVERT'), required=TRUE, created_by='v64-seed', gen_random_uuid(). WHERE NOT EXISTS 멱등. V61~V63 불변.
- [ ] **Step 2:** fresh probe(V1..V64) — PARTNER_ORDER 2역할·action_key 확인.
- [ ] **Step 3:** AuthFlywayV64SeedIT(2역할+action_key 단언) + ServiceTest authorize("PARTNER_ORDER","PARTNER_ORDER_CONVERT") allowed=true 케이스.
- [ ] **Step 4:** commit `feat(auth): A2-4 V64 PARTNER_ORDER 결재라인 시드(승인자=PARTNER_ORDER_CONVERT)`.

---

### Task 2: partner-order-service — convert 게이트

**Files:**
- Create: `services/partner-order-service/.../client/ApprovalLineAuthorizeClient.java`(slip 미러: loadBalanced RestClient http://auth-service + X-Internal-Token, **운영 생성자 @Autowired**, 테스트 생성자), `.../client/ApprovalLineAuthorizeResult.java`, `.../client/ApprovalLineAuthorizeClientTest.java`(MockRestServiceServer 계약), `.../client/ApprovalLineAuthorizeClientDiGuardTest.java`(ApplicationContextRunner 실 빈 생성)
- Modify: `.../service/PartnerOrderConvertService.java`(convert+병합 게이트)
- Test: `.../it/...ConvertApprovalEnforcementIT.java` 또는 기존 convert IT 확장 + 외부 client 공유 @MockBean(AbstractIT)

**Interfaces:** `ApprovalLineAuthorizeClient.authorize(documentType, actionKey, userId): {configured, allowed}`. 상수 PARTNER_ORDER_DOC_TYPE="PARTNER_ORDER"·PARTNER_ORDER_CONVERT_ACTION_KEY="PARTNER_ORDER_CONVERT".

- [ ] **Step 1: 실패 테스트(IT)** — convert: 승인자 지정 후 비결재자 403("주문 출고전환 권한이 없습니다 …")·결재자 200(전환 정상)·opt-in(미설정) 200·병합전환 동일. **기존 convert IT 회귀 0**. client=MockRestServiceServer/공유 @MockBean(configured=false 기본) — @MockBean 누락 시 실 RestClient 호출→500 회귀 주의([[it-mockbean-external-clients]]).
- [ ] **Step 2: FAIL 확인.**
- [ ] **Step 3: 구현** — ApprovalLineAuthorizeClient(slip 1:1 미러, partner-order RestClientConfig loadBalancedRestClientBuilder + InternalAuthProperties). PartnerOrderConvertService.convert(개별)+병합전환: **전환 로직 전** `if (isRealUser(actorUuid)) { var r=client.authorize("PARTNER_ORDER","PARTNER_ORDER_CONVERT", actorUuid); if (r.configured() && !r.allowed()) throw BusinessException(FORBIDDEN, "주문 출고전환 권한이 없습니다 — 승인자 결재자(그룹/개인)만 전환할 수 있습니다"); }`. isRealUser=null/'system'/non-UUID skip. 컨트롤러 @RequirePermission 유지.
- [ ] **Step 4: PASS — partner-order 전체 test 완주(convert 회귀 0).** DI 가드/ClientTest green.
- [ ] **Step 5: commit** `feat(partner-order): A2-4 주문 출고전환 결재자 게이트(opt-in·system bypass)`.

---

### Task 3: FE — 주문 전표종류

**Files:** Modify `api/approvalLineConfigApi.ts`(DOC_TYPES), `api/mock.ts`(PARTNER_ORDER 2행), `api/approvalLineConfigApi.test.ts`.

- [ ] **Step 1:** DOC_TYPES 에 `{value:'PARTNER_ORDER', label:'주문'}` + mock _mockApprovalLineConfigRoles 에 PARTNER_ORDER 2행(작성자 CREATOR/승인자 GROUP). vitest 단언.
- [ ] **Step 2:** typecheck + lint + vitest.
- [ ] **Step 3:** commit `feat(desktop): A2-4 결재라인 설정 주문 전표종류 추가`.

---

## Self-Review
- 스펙 커버: V64 시드(T1)·convert 게이트(T2)·신규 client+DI 가드(T2)·FE+mock(T3)·convert 회귀(T2). ✅
- 타입 일치: PARTNER_ORDER_CONVERT actionKey ↔ V64 seed. authorize generic 재사용.
- 마이그: V64 신규, V61~V63 불변.
- placeholder: 없음.

## 검증 게이트
auth+partner-order 전체 test(convert 회귀+enforcement) + V64 probe + FE → 🐳 라이브 QA(주문 승인자 지정→convert 200·비결재 403·opt-in·슬립 무회귀) → 🔵Opus 5-agent+QA(순차) → 🟣Codex 5-agent+QA → 양쪽 0 → 머지.
