# A2-2 — 출고전표 accept/inspect 결재자 enforcement 설계

> A2 결재라인 에픽의 enforcement 슬라이스. A2-1/A2-1b/A2-1c 가 **선언적 결재라인**(역할·순서·라벨·다중 결재자 그룹∪개인)을 구축했고, 본 슬라이스가 그것을 **출고전표 처리 액션에 실제 강제**한다.
>
> 선행: [A2-1c spec](2026-06-21-approval-multi-approver-a2-1c-design.md)(action_key 앵커·approval_line_approver). 에픽 E12 = B 게이트 모델(자동채움 유지 + 권한 게이트만 추가).

## 목표

출고전표(`SlipType.OUTBOUND`)의 `accept`(출고 처리)·`inspect`(검수) 액션을, 결재라인 설정의 해당 역할(출고인/검수인)에 지정된 **결재자(권한 그룹 ∪ 개인 사원)만** 수행할 수 있게 한다. B 모델 자동채움(dispatcherUserId/inspectorUserId)은 그대로. **입고(INBOUND) 무영향**.

## 개발책임자 결정 (brainstorming 2026-06-21)

| 결정 | 내용 |
|---|---|
| **아키텍처** | **동적 config 조회**(E8 page-code grant 폐기 — 개인이 grant 부적합). accept/inspect 시점에 결재자 집합을 읽어 검증. |
| **미지정 fallback** | **opt-in** — 결재자 0개면 기존 `slip.transfer.process` 권한자 유지(무중단). |
| **4-eye** | **권장만**(강제 X). 출고인=검수인 동일인 허용. |
| **입고 격리** | 출고 게이트는 `slipType==OUTBOUND` 만. 입고 accept/inspect 무영향. |

## 아키텍처

### auth — 결재자 인가 내부 엔드포인트 (신규)
`POST /internal/approval-line/authorize` (X-Internal-Token 가드, [[project_estimate_auth_dc_key_decisions]] 패턴):
- 요청 `{ documentType, actionKey, userId }`.
- 처리: `approval_line_config` 에서 `(documentType, action_key)` 활성 역할 1건 조회 → 그 역할의 `approval_line_approver` 결재자 집합 조회. **configured** = 결재자 ≥1. **allowed** = userId 가 USER 결재자에 포함 OR userId 의 활성 그룹(account_groups) ∩ GROUP 결재자 ≠ ∅.
- 응답 `{ configured: boolean, allowed: boolean }`.
- 서비스 `ApprovalLineAuthorizationService.authorize(documentType, actionKey, userId)`.

### slip-service — accept/inspect 게이트 (service 단)
`ApprovalLineAuthorizeClient`(RestClient + X-Internal-Token, 기존 *InternalClient 패턴):
- `SlipService.accept(id, acceptorUserId)`: 슬립 로드 후 **`slip.slipType==OUTBOUND` && acceptorUserId 가 실 사용자('system' 아님)** 이면 `authorize(SLIP_OUTBOUND, OUTBOUND_DISPATCH, acceptorUserId)` 호출 → `configured && !allowed` 면 `BusinessException(FORBIDDEN, "출고 수락 권한이 없습니다 — 출고인 결재자(그룹/개인)만 처리할 수 있습니다")`. (자동채움·inventory.reserve 전에 가드.)
- `SlipService.inspect(id, inspectorUserId)`: 동일하게 `OUTBOUND` && 실 사용자면 `authorize(SLIP_OUTBOUND, OUTBOUND_INSPECT, inspectorUserId)` → `configured && !allowed` 면 FORBIDDEN("출고 검수 권한이 없습니다 …").
- **위치**: 컨트롤러는 슬립 slipType 미지 → service(슬립 로드 후)에서 가드. 기존 `@RequirePermission(slip.transfer.process)`(컨트롤러)는 유지(opt-in 베이스 게이트). 기존 inspect 의 `checkEditPermission(inbound.inspection)`(입고 검수)는 유지(INBOUND 가드).
- **'system' bypass**: acceptorUserId='system'(내부/무사용자 폴백)이면 결재자 검증 skip(내부 연산). 실 사용자는 게이트웨이 X-User-Id 로 식별.

### action_key 앵커 (A2-1c)
출고인=`OUTBOUND_DISPATCH`, 검수인=`OUTBOUND_INSPECT`(라벨/순서 무관 안정 매핑). accept→DISPATCH, inspect→INSPECT.

## 데이터 모델
변경 없음. A2-1c 의 `approval_line_config`(action_key) + `approval_line_approver`(GROUP|USER) + `account_groups`(auth) 조회만. **Flyway 신규 없음**.

## opt-in 무중단 동작
- 결재자 미지정(configured=false): authorize 가 `{configured:false}` → slip-service 가 게이트 skip → 기존 `slip.transfer.process` 권한자가 그대로 처리(무중단 도입).
- 결재자 지정(configured=true): 그 그룹∪개인만 accept/inspect. 비결재자 → 403.

## 테스트
- **auth 단위**(`ApprovalLineAuthorizationServiceTest`): authorize — 결재자 0개(configured=false) / USER 결재자 일치(allowed) / GROUP 결재자에 user 소속(allowed) / 비결재자(configured=true·allowed=false) / 미존재 action_key(configured=false).
- **auth IT**(`ApprovalLineAuthorizeControllerIT`, X-Internal-Token): 200 응답 형태 + 토큰 없으면 401/403.
- **slip 단위/IT**(`SlipApprovalEnforcementIT`, 실HTTP·@MockBean 금지 [[restclient-contract-test-false-green]]):
  - **OUTBOUND accept**: 결재자 지정 후 비결재자 → 403, 결재자 → 200(자동채움 dispatcherUserId 유지).
  - **OUTBOUND inspect**: 동일.
  - **INBOUND accept/inspect 회귀**: 출고 게이트 미적용 200(입고 무영향) — **핵심 회귀**.
  - **opt-in**: 결재자 0개면 기존 권한자 200.
  - ApprovalLineAuthorizeClient 는 MockRestServiceServer 또는 실 auth(Testcontainers) — @MockBean 우회 금지.
- **🐳 라이브 QA**(매 라운드): 결재라인 설정에서 출고인=특정 그룹/개인 지정 → 그 사용자로 출고전표 accept 200, 비결재 사용자로 accept 403 캡처. 입고 전표 accept 무영향 캡처.

## 범위 밖
- 입고(INBOUND) 결재라인(별 documentType), process/ship/deliver/confirm 등 타 전이 게이트, 그룹웨어/주문 등 타 문서 enforcement, 4-eye 강제, 결재 알림.

## 워크플로우
Codex 초기 구현 → 🔵Opus 5-agent+QA(순차) → 🟣Codex 5-agent+QA(cross-check) → 라운드 fix(Opus=Opus직접/Codex=Codex) → **양쪽 blocking 0 수렴까지**(병렬 금지) → 머지. 매 라운드 라이브 캡처.
