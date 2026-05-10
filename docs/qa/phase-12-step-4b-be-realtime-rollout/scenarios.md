# PR-H4b — Phase 12 Step 4b BE realtime rollout (13 service × 5 case = 65 case) QA 시나리오

> **branch** — `feature/integrated-phase-12-step-4b-be-realtime-rollout`
> **작성일** — 2026-05-10
> **작성** — QA Tester (5-team 통합 PR 패턴)
> **목적** — Phase 12 Step 4b PR-H4b (`shared-realtime` + `shared-edit-request` 모듈을 **slip-service 외 13 backend service** 가 의존 추가만으로 일괄 도입) 가 **(1) 도메인별 LockPolicy/EditRequestService specialization 동작 정확성** + **(2) 13 service 단일 ElastiCache 공유 환경 회귀 0건** + **(3) slip-service 시드 PR-H1/H2/H3 동작 100% 회귀 보존** 을 측정 가능한 PASS/FAIL 로 명세.
> **연관 산출물** —
> - BE: 9 service `build.gradle` shared 모듈 의존 추가 (partner / inventory / accounting / arologis / product / dc-config / partner-order / user / groupware)
> - BE: 7 service `LockPolicy<TStatus>` specialization 클래스 신규 (slip 제외 — partner / inventory / accounting / arologis / product / dc-config / partner-order)
> - BE: 7 service `EditRequestService<T>` specialization (위와 동일)
> - BE: 9 service audit overlay endpoint specialization (`GET /<domain>s/{id}/audit-logs` + `POST /audit/revert/{n}`)
> - BE: 2 service broker only 도입 (dashboard / notification)
> - BE: 13 service env 템플릿 라인 추가 (`SAMHAN_REALTIME_SERVICE_NAME` 등)
> - DevOps: `docs/devops/phase12-redis-multi-service.md` 신규 (단계적 cutover + 13 service 단일 ElastiCache 운영 가이드)
> - Designer: `docs/uiux/phase12/H4b-be-rollout-checklist.md` 신규 (13 service 적용 매트릭스 + 도메인별 상태/잠금 정책 일람)

---

## 0. 검증 정책

### 0.1 페르소나 5 (사용자 명시 — `feedback_role_naming_full` 풀네임)

| 페르소나 | ROLE | 도메인 지식 | 본 PR 검증 관점 |
| --- | --- | --- | --- |
| **신입 영업** | SALES | 단가/세금 미경험 | partner / partner-order / dc-config 도메인의 잠금 정책이 영업 작성 단계 (DRAFT/SAVED) 자유 수정 보장. 9 도메인 audit overlay 가 slip 시드와 시각 1:1 동일 |
| **창고원** | WAREHOUSE | 출고 픽업/검수 | inventory 의 StockAdjust DRAFT 자유 수정 + SUBMITTED 잠금 + POSTED FULLY_LOCKED 회계 무결성. arologis Dispatch 기사 변경 SMS 알림 |
| **회계 담당자** | ACCOUNTANT (또는 MANAGER) | 한국 일반기업회계기준 | accounting Journal 의 POSTED 상태 FULLY_LOCKED 정책 정확 적용. 정정 분개 의무 명시. 한국 계정 코드 (100/200/300/400/500/800/900) audit 표시 정합 |
| **관리자** | MANAGER | 전 도메인 | 9 도메인 모두 LOCKED_REQUIRES_APPROVAL 상태에서 승인 가능. 복원 dropdown / 수정 횟수 chip 모든 도메인 동일 동작 |
| **DevOps 엔지니어** | DEVOPS | infra 운영 | 13 service env 템플릿 라인 일관 + 단일 ElastiCache cutover 단계적 절차 안전. publishFailureCount metric 모든 service 0 유지 |

### 0.2 측정 가능한 PASS/FAIL 기준

각 case 는 다음 4 요소 모두 명시:

1. **선행 조건** — fixture (각 service migration 적용 + shared 모듈 의존 + service-name 환경변수 + Redis 또는 in-memory broker)
2. **동작** — Gradle test / curl / Playwright step
3. **기대 결과** — 단위 assertion (specialization 클래스 동작) + IT assertion (Redis testcontainer round-trip) + 회귀 assertion (slip-service 100% 통과)
4. **회귀 차단 effect** — fail 시 어떤 backend / frontend 증상이 production 에서 재현 가능한가

### 0.3 우선순위 표기

- 🔴 **Critical** — fail 시 PR-H4c 진입 차단 (도메인 specialization 회귀 / channel collision / slip 회귀)
- 🟠 **Major** — 도메인 specialization 단위 책임 누락 (특정 상태 잠금 분류 mismatch / event name 일관성 누락)
- 🟡 **Minor** — 한국어 라벨 / 응답 schema field 누락
- 🟢 **Info** — 향후 PR-H4c FE 도입 시 권고

### 0.4 권한 매트릭스 (도메인별 specialization)

본 PR-H4b BE 단계 — Designer § 2 잠금 정책 일람 1:1 일치 의무. 도메인별 `LockPolicy<TStatus>` 클래스가 본 표 그대로 구현:

- **partner-service** — DRAFT free / ACTIVE locked-approval / SUSPENDED-INACTIVE fully-locked
- **inventory-service** — DRAFT free / SUBMITTED locked-approval / POSTED-VOIDED fully-locked
- **accounting-service** — DRAFT free / POSTED-CLOSED-VOIDED fully-locked (LOCKED_REQUIRES_APPROVAL 미사용)
- **arologis-service** — PLANNED free / DISPATCHED locked-approval / IN_TRANSIT-DELIVERED-CANCELED fully-locked
- **product-service** — DRAFT free / ACTIVE locked-approval / DISCONTINUED-INACTIVE fully-locked
- **dc-config-service** — DRAFT free / ACTIVE locked-approval / EXPIRED-INACTIVE fully-locked
- **partner-order-service** — DRAFT free / SUBMITTED locked-approval / CONFIRMED-FULFILLED-CANCELED fully-locked
- **user-service** — ACTIVE free (audit only — edit-request 미도입) / SUSPENDED-INACTIVE fully-locked
- **groupware-service** — DRAFT-PUBLISHED free (audit only) / ARCHIVED fully-locked

### 0.5 UUID 비공개 (`feedback_uuid_no_user_visibility`)

본 PR-H4b BE 단계 — 응답 schema 의 `actorId` 가 색상 hash 입력 전용 / 화면 표시 = `actorName` 만. 도메인 본체 식별자 (partnerId / journalId / ...) 도 UUID 비공개 — 비즈니스 식별자 (사업자명 / 분개번호 / 배차번호 등) 만 노출. specialization 응답 controller 가 UUID leak 0건 검증 의무.

---

## 1. partner-service (5 case)

> **모듈 위치** — `services/partner-service/`
> **specialization** — `PartnerLockPolicy implements LockPolicy<PartnerStatus>` + `PartnerEditRequestService extends EditRequestService<Partner>` + `PartnerAuditController`

### 1.1 audit overlay 자동 기록

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.1 | SALES | 🔴 | partner-001 (status=ACTIVE, businessName="삼한전자") + 활성 승인 1건 | `PATCH /partners/partner-001 {businessName: "삼한전자(주)"}` | shared `EditAuditService.record` 위임 → `partner_audit_logs` 1행 (`fieldName=businessName`, `oldValue="삼한전자"`, `newValue="삼한전자(주)"`, `revisionNo=1`) + `partners.revision_count=1` + Redis publish `samhan:partner:partner:edit:{partnerId}` 1회 | partner audit 회귀 시 사용자 수정 추적 단절 — 마스터 데이터 무결성 위배 |

### 1.2 LockPolicy — DRAFT 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.2 | SALES | 🔴 | partner-002 (status=DRAFT) | `PATCH /partners/partner-002` direct | `PartnerLockPolicy.classify(DRAFT)` = `FREE_DIRECT_EDIT` → mutation 통과 | DRAFT 잠금 회귀 시 신규 거래처 등록 차단 |

### 1.3 LockPolicy — ACTIVE 잠금 + 수정 요청

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.3 | SALES | 🔴 | partner-001 (status=ACTIVE) + 활성 승인 0건 | `PATCH /partners/partner-001` direct | `PartnerLockPolicy.classify(ACTIVE)` = `LOCKED_REQUIRES_APPROVAL` → `BusinessException(CONFLICT, "수정 요청 후 권한자 수락 필요")` | ACTIVE 잠금 회귀 시 무단 수정 가능 — audit 무력화 |

### 1.4 EditRequestService — request → approve → consume

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.4 | SALES + MANAGER | 🔴 | partner-001 (ACTIVE) | (1) SALES `POST /partners/partner-001/edit-request` (2) MANAGER `POST /partners/partner-001/edit-request/{rid}/approve` (3) SALES `PATCH /partners/partner-001` (4) SALES `PATCH /partners/partner-001` 두 번째 시도 | (1) PENDING row + Redis publish `samhan:partner:partner:edit-request:created:{partnerId}` + NotificationClient 호출 (2) APPROVED + Redis publish `samhan:partner:partner:edit-request:decided:{partnerId}` (3) `findActiveApproval` 통과 + mutation 정상 + `consumeApproval` (row soft-delete) (4) `LOCKED_REQUIRES_APPROVAL` CONFLICT | partner edit-request 회귀 시 작성자가 권한자에게 알릴 수단 단절 |

### 1.5 LockPolicy — SUSPENDED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.5 | MASTER | 🔴 | partner-003 (status=SUSPENDED) | MASTER `PATCH /partners/partner-003` direct | `PartnerLockPolicy.classify(SUSPENDED)` = `FULLY_LOCKED` → MASTER 만 통과 (다른 ROLE = CONFLICT) | SUSPENDED 정책 회귀 시 정지 거래처 무단 수정 가능 |

---

## 2. inventory-service (5 case)

> **모듈 위치** — `services/inventory-service/`
> **specialization** — `StockAdjustLockPolicy implements LockPolicy<StockAdjustStatus>` + `StockAdjustEditRequestService` + `StockAdjustAuditController`

### 2.1 audit overlay — 조정 사유 변경

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.1 | WAREHOUSE | 🔴 | adjust-001 (status=DRAFT, adjustReason="검수 누락") | `PATCH /stock-adjusts/adjust-001 {adjustReason: "파손 발견"}` | shared 위임 → `stock_adjust_audit_logs` 1행 + Redis publish `samhan:inventory:stock-adjust:edit:{adjustId}` | 회계 무결성 의무 — 조정 사유 audit 누락 시 한국 회계 감사 위배 |

### 2.2 LockPolicy — DRAFT 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.2 | WAREHOUSE | 🔴 | adjust-002 (DRAFT) | direct `PATCH` | `FREE_DIRECT_EDIT` → mutation 통과 | DRAFT 잠금 회귀 시 창고 작업 차단 |

### 2.3 LockPolicy — SUBMITTED 잠금

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.3 | WAREHOUSE | 🔴 | adjust-001 (SUBMITTED) + 승인 0건 | direct `PATCH` | `LOCKED_REQUIRES_APPROVAL` → CONFLICT | SUBMITTED 무단 수정 시 회계 전기 직전 데이터 변조 |

### 2.4 LockPolicy — POSTED FULLY_LOCKED (한국 회계 무결성)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.4 | MANAGER | 🔴 | adjust-003 (POSTED — 회계 전기 완료) | MANAGER `PATCH` direct | `FULLY_LOCKED` → CONFLICT (MASTER 만 별도 정정 분개 채널) | POSTED 수정 가능 시 한국 일반기업회계기준 보존 의무 위배 → 세무 조사 위험 |

### 2.5 EditRequestService — request → approve → consume

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.5 | WAREHOUSE + MANAGER | 🔴 | adjust-001 (SUBMITTED) | (1) WAREHOUSE request (2) MANAGER approve (3) WAREHOUSE mutation 1회 (4) WAREHOUSE 두 번째 mutation | (1) PENDING + SSE + NotificationClient (2) APPROVED + SSE (3) consume → row soft-delete (4) CONFLICT (1회 한정 소진) | 1회 한정 소진 회귀 시 한 번 승인으로 무한 수정 — audit 무력화 |

---

## 3. accounting-service (5 case)

> **모듈 위치** — `services/accounting-service/`
> **specialization** — `JournalLockPolicy implements LockPolicy<JournalStatus>` + `JournalAuditController` (edit-request specialization 미도입 — § 0.4)

### 3.1 audit overlay — 적요 변경 (한국 계정 코드 표기)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.1 | ACCOUNTANT | 🔴 | journal-001 (DRAFT, accountCode="100100", description="현금 입금") | `PATCH /journals/journal-001 {description: "현금 매출 입금"}` | shared 위임 → `journal_audit_logs` 1행 + 응답 schema 에 한국 계정 코드 (100100 = 현금) 표기 + Redis publish `samhan:accounting:journal:edit:{journalId}` | 계정 코드 누락 시 회계 감사 추적 불가 |

### 3.2 LockPolicy — DRAFT 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.2 | ACCOUNTANT | 🔴 | journal-002 (DRAFT) | direct `PATCH` | `FREE_DIRECT_EDIT` → mutation 통과 | DRAFT 잠금 회귀 시 회계 작성 단계 차단 |

### 3.3 LockPolicy — POSTED FULLY_LOCKED (LOCKED_REQUIRES_APPROVAL 미사용)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.3 | MANAGER | 🔴 | journal-003 (POSTED — 전기 완료) | MANAGER `PATCH` direct | `FULLY_LOCKED` → CONFLICT (MASTER 만 별도 정정 분개 의무 — 본 endpoint 차단) + 응답 메시지 "전기된 분개는 정정 분개로만 수정 가능합니다" | POSTED 수정 가능 시 한국 일반기업회계기준 위배 |

### 3.4 LockPolicy — CLOSED 월/연 마감 FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.4 | MASTER | 🔴 | journal-004 (CLOSED — 월 마감) | MASTER `PATCH` direct | `FULLY_LOCKED` → CONFLICT (MASTER 도 차단 — 감사인 동석 의무) + 응답 메시지 "마감된 분개는 감사인 동석 별도 절차 의무" | 마감 분개 수정 시 회계 무결성 + 감사 보고 변조 |

### 3.5 audit-logs — actorId 비공개 + actorName 만 노출

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.5 | MANAGER | 🟠 | journal-001 audit row 3건 | `GET /journals/journal-001/audit-logs` | 응답 3건 + 각 row 의 `actorId` UUID 표기 (색상 hash 입력) + `actorName` 풀네임 + UUID 가 user-facing 메시지에 leak 0건 | UUID leak 시 `feedback_uuid_no_user_visibility` 위배 |

---

## 4. arologis-service (5 case)

> **모듈 위치** — `services/arologis-service/`
> **specialization** — `DispatchLockPolicy` + `DispatchEditRequestService` (post-approve hook = SMS 알림) + `DispatchAuditController`

### 4.1 audit overlay — 기사 변경 + SMS 알림

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.1 | DISPATCHER + MANAGER | 🔴 | dispatch-001 (DISPATCHED, driverName="홍길동", driverPhone="010-1234-5678") + MANAGER 승인 | (1) DISPATCHER request → MANAGER approve → DISPATCHER `PATCH /dispatches/dispatch-001 {driverName: "김철수", driverPhone: "010-9999-8888"}` | shared 위임 audit row 2행 (driverName + driverPhone) + Redis publish `samhan:arologis:dispatch:edit:{dispatchId}` + post-approve hook 의 `NotificationClient.sendSms(oldDriverPhone, "배차 변경되었습니다")` 1회 + 새 기사에게도 SMS 1회 | 기사 변경 SMS 누락 시 운송 사고 위험 |

### 4.2 LockPolicy — PLANNED 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.2 | DISPATCHER | 🔴 | dispatch-002 (PLANNED) | direct `PATCH` | `FREE_DIRECT_EDIT` → mutation 통과 | PLANNED 잠금 회귀 시 배차 사전 작성 차단 |

### 4.3 LockPolicy — IN_TRANSIT FULLY_LOCKED (운송 중 잠금)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.3 | MANAGER | 🔴 | dispatch-003 (IN_TRANSIT) | MANAGER `PATCH` direct | `FULLY_LOCKED` → CONFLICT (MASTER 만 운송 사고 회귀 절차) + 응답 메시지 "운송 중 배차는 본부 승인 별도 절차 필요" | IN_TRANSIT 수정 시 기사 혼선 + 운송 사고 직결 |

### 4.4 EditRequestService — DISPATCHED request

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.4 | DISPATCHER | 🔴 | dispatch-001 (DISPATCHED) | DISPATCHER `POST /dispatches/dispatch-001/edit-request {reason: "기사 변경 요청"}` | PENDING row + Redis publish `samhan:arologis:dispatch:edit-request:created:{dispatchId}` + NotificationClient.notify(MANAGER) | request 회귀 시 배차 변경 채널 단절 |

### 4.5 audit-logs — UUID 비공개 + 비즈니스 식별자 (배차번호) 노출

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.5 | MANAGER | 🟠 | dispatch-001 (dispatchNo="D-2026-0001") | `GET /dispatches/dispatch-001/audit-logs` | 응답 row 의 dispatchId UUID 노출 0 + `dispatchNo` "D-2026-0001" 노출 | UUID leak 시 사용자 식별 혼선 |

---

## 5. product-service (5 case)

> **모듈 위치** — `services/product-service/`
> **specialization** — `ProductLockPolicy` + `ProductEditRequestService` + `ProductAuditController`

### 5.1 audit overlay — 단가 변경

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.1 | MANAGER | 🔴 | product-001 (ACTIVE, unitPrice=10000) + MASTER 승인 | MANAGER `PATCH /products/product-001 {unitPrice: 12000}` (consume approval) | shared 위임 audit row 1건 (unitPrice 변경) + Redis publish `samhan:product:product:edit:{productId}` + consumeApproval | 단가 audit 누락 시 영업 단가 협상 추적 불가 |

### 5.2 LockPolicy — DRAFT 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.2 | MASTER | 🔴 | product-002 (DRAFT) | MASTER direct `PATCH` | `FREE_DIRECT_EDIT` → 통과 | DRAFT 잠금 회귀 시 신규 품목 등록 차단 |

### 5.3 LockPolicy — ACTIVE 잠금

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.3 | MANAGER | 🔴 | product-001 (ACTIVE) + 승인 0건 | MANAGER direct `PATCH` | `LOCKED_REQUIRES_APPROVAL` → CONFLICT | ACTIVE 단가 무단 수정 시 영업 단가 무결성 위배 |

### 5.4 LockPolicy — DISCONTINUED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.4 | MANAGER | 🔴 | product-003 (DISCONTINUED — 단종) | MANAGER `PATCH` | `FULLY_LOCKED` → CONFLICT | 단종 품목 수정 시 영업 단가 혼선 |

### 5.5 EditRequestService — ACTIVE request → approve

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.5 | MANAGER + MASTER | 🔴 | product-001 (ACTIVE) | (1) MANAGER request (2) MASTER approve | (1) PENDING + SSE (2) APPROVED + SSE | request 회귀 시 단가 변경 정식 절차 단절 |

---

## 6. dc-config-service (5 case)

> **모듈 위치** — `services/dc-config-service/`
> **specialization** — `DcRuleLockPolicy` + `DcRuleEditRequestService` + `DcRuleAuditController`

### 6.1 audit overlay — 할인율 변경

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.1 | MANAGER | 🔴 | rule-001 (ACTIVE, discountRate=10) + MASTER 승인 | MANAGER `PATCH /dc-rules/rule-001 {discountRate: 15}` | shared 위임 audit row + Redis publish `samhan:dc-config:dc-rule:edit:{ruleId}` | 할인율 audit 누락 시 정책 변경 추적 불가 |

### 6.2 LockPolicy — DRAFT 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.2 | MANAGER | 🔴 | rule-002 (DRAFT) | direct `PATCH` | `FREE_DIRECT_EDIT` → 통과 | DRAFT 잠금 회귀 시 신규 정책 작성 차단 |

### 6.3 LockPolicy — ACTIVE 잠금

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.3 | MANAGER | 🔴 | rule-001 (ACTIVE) + 승인 0건 | direct `PATCH` | `LOCKED_REQUIRES_APPROVAL` → CONFLICT | ACTIVE 정책 무단 수정 시 영업 단가 산정 무결성 위배 |

### 6.4 LockPolicy — EXPIRED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.4 | MANAGER | 🔴 | rule-003 (EXPIRED) | MANAGER `PATCH` | `FULLY_LOCKED` → CONFLICT (MASTER 만) | 만료 정책 수정 시 과거 적용 이력 변조 |

### 6.5 audit-logs — 기간 + 대상 거래처 코드 표기

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.5 | MANAGER | 🟡 | rule-001 audit row 2건 (validFrom + validTo + targetPartnerCode) | `GET /dc-rules/rule-001/audit-logs` | 응답 row 의 partnerCode 노출 (UUID 비공개) + 한국어 라벨 ("적용 시작" / "적용 종료" / "대상 거래처 코드") | UUID leak 또는 한국어 라벨 누락 시 사용자 인지 혼선 |

---

## 7. partner-order-service (5 case)

> **모듈 위치** — `services/partner-order-service/`
> **specialization** — `PartnerOrderLockPolicy` + `PartnerOrderEditRequestService` + `PartnerOrderAuditController`

### 7.1 audit overlay — 주문 수량 변경

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.1 | PARTNER | 🔴 | order-001 (DRAFT, orderQuantity=100) | PARTNER `PATCH /partner-orders/order-001 {orderQuantity: 150}` | shared 위임 audit row + Redis publish `samhan:partner-order:partner-order:edit:{orderId}` | 주문 수량 audit 누락 시 거래처-자사 분쟁 추적 불가 |

### 7.2 LockPolicy — DRAFT 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.2 | PARTNER | 🔴 | order-002 (DRAFT) | direct `PATCH` | `FREE_DIRECT_EDIT` → 통과 | DRAFT 잠금 회귀 시 거래처 주문 작성 차단 |

### 7.3 LockPolicy — SUBMITTED 잠금

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.3 | PARTNER | 🔴 | order-001 (SUBMITTED) + 승인 0건 | direct `PATCH` | `LOCKED_REQUIRES_APPROVAL` → CONFLICT | SUBMITTED 무단 수정 시 본사 확정 이전 데이터 변조 |

### 7.4 LockPolicy — CONFIRMED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.4 | MANAGER | 🔴 | order-003 (CONFIRMED — 본사 확정) | MANAGER `PATCH` | `FULLY_LOCKED` → CONFLICT | 확정 주문 수정 시 출고 일정 + 회계 전기 데이터 변조 |

### 7.5 EditRequestService — SUBMITTED request → approve

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.5 | PARTNER + MANAGER | 🔴 | order-001 (SUBMITTED) | (1) PARTNER request (2) MANAGER approve (3) PARTNER mutation (4) PARTNER 두 번째 mutation | (1) PENDING + SSE (2) APPROVED + SSE (3) consume → 통과 (4) CONFLICT | 1회 한정 소진 회귀 시 거래처가 한 번 승인으로 무한 수정 |

---

## 8. user-service (5 case) — audit only

> **모듈 위치** — `services/user-service/`
> **specialization** — `UserLockPolicy implements LockPolicy<UserStatus>` + `UserAuditController` (edit-request 미도입)

### 8.1 audit overlay — 자기 정보 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.1 | SALES | 🔴 | user-001 (ACTIVE, name="홍길동") | SALES (본인) `PATCH /users/user-001 {contactPhone: "010-9999-8888"}` | shared 위임 audit row + Redis publish `samhan:user:user:edit:{userId}` | 자기 정보 audit 누락 시 HR 변경 이력 추적 불가 |

### 8.2 LockPolicy — ACTIVE 자유 수정 (본인)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.2 | SALES | 🔴 | user-001 (ACTIVE) | SALES (본인) direct `PATCH` | `FREE_DIRECT_EDIT` (본인) → 통과 | 본인 수정 차단 시 HR 정책 위배 |

### 8.3 LockPolicy — 타인 수정 시 MASTER 만

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.3 | MANAGER | 🟠 | user-001 (ACTIVE), 다른 사용자가 수정 시도 | MANAGER `PATCH /users/user-001` (타인) | `MasterOnlyException` 또는 403 (MASTER 만) | 타인 수정 가능 시 HR 정보 무단 변조 |

### 8.4 LockPolicy — SUSPENDED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.4 | MASTER | 🟠 | user-002 (SUSPENDED) | MASTER `PATCH` | `FULLY_LOCKED` → MASTER 만 통과 | SUSPENDED 무단 수정 시 정지 사용자 재활성 |

### 8.5 audit-logs — actorId 색상 hash 일관

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.5 | MANAGER | 🟢 | user-001 audit row 3건 (다른 사용자 2명이 수정) | `GET /users/user-001/audit-logs` | 응답 3건 + 동일 actorId → 동일 색상 hash (deterministic — `userIdToColor` 일관) | 색상 일관 회귀 시 cross-domain 사용자 식별 무력화 |

---

## 9. groupware-service (5 case) — audit only

> **모듈 위치** — `services/groupware-service/`
> **specialization** — `MemoLockPolicy` / `AnnouncementLockPolicy` + `GroupwareAuditController` (edit-request 미도입)

### 9.1 audit overlay — 메모 본문 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 9.1 | SALES | 🔴 | memo-001 (PUBLISHED, body="회의록 v1") | SALES (작성자) `PATCH /memos/memo-001 {body: "회의록 v2"}` | shared 위임 audit row + Redis publish `samhan:groupware:memo:edit:{memoId}` | 메모 audit 누락 시 변경 이력 보존 위배 |

### 9.2 LockPolicy — DRAFT/PUBLISHED 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 9.2 | SALES | 🔴 | memo-002 (DRAFT) + memo-003 (PUBLISHED) | SALES direct `PATCH` 양쪽 | 양쪽 `FREE_DIRECT_EDIT` → 통과 (작성자 자유 수정 정책) | 자유 수정 회귀 시 메모 작성 UX 차단 |

### 9.3 LockPolicy — ARCHIVED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 9.3 | MANAGER | 🟠 | memo-004 (ARCHIVED) | MANAGER `PATCH` | `FULLY_LOCKED` → CONFLICT (MASTER 만) | 보관 메모 수정 시 과거 기록 변조 |

### 9.4 audit-logs — 공지 본문 변경 timeline

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 9.4 | MANAGER | 🟡 | announcement-001 audit row 5건 | `GET /announcements/announcement-001/audit-logs` | 응답 5건 + DESC 정렬 (revisionNo + changedAt) + 한국어 라벨 ("제목" / "본문" / "카테고리") | timeline 정렬 회귀 시 사용자 인지 혼선 |

### 9.5 broker only — 공지 발행 push (audit + broker 동시)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 9.5 | MANAGER | 🟢 | announcement-002 신규 발행 | MANAGER `POST /announcements` | (1) `announcements` row INSERT (2) Redis publish `samhan:groupware:announcement:published:{id}` (audit overlay 시드 본PR 외 broker only) | broker only 회귀 시 신규 공지 실시간 push 단절 |

---

## 10. dashboard-service (5 case) — broker only

> **모듈 위치** — `services/dashboard-service/`
> **specialization** — broker only (audit / edit-request 미도입). `SamhanRealtimeBroker` 의존만 추가.

### 10.1 broker only — KPI metric publish

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 10.1 | MANAGER | 🔴 | dashboard-service 부팅 + ElastiCache 연결 | MANAGER 가 desktop dashboard 진입 → KPI 갱신 trigger | Redis publish `samhan:dashboard:dashboard:metric:updated:{dashboardId}` + desktop SSE 1초 안 수신 + KPI 차트 자동 refresh | KPI push 회귀 시 실시간 dashboard 단절 |

### 10.2 broker only — alert push

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 10.2 | MANAGER | 🟠 | 임계 알람 trigger (예: 출고 지연 5건+) | dashboard-service 가 임계 검사 후 alert 발생 | Redis publish `samhan:dashboard:dashboard:alert:fired:{alertId}` + desktop toast 표시 | alert push 회귀 시 운영 위험 알림 단절 |

### 10.3 audit endpoint 미적용 검증

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 10.3 | MASTER | 🟠 | dashboard-service 부팅 | `GET /dashboards/{id}/audit-logs` | 404 또는 endpoint 자체 미존재 (audit specialization 미도입 정책) | endpoint 잘못 노출 시 read-only 도메인에 audit 부담 |

### 10.4 broker subscriberCount metric

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 10.4 | DEVOPS | 🟡 | desktop multi-context 5개 KPI subscribe | actuator `/actuator/metrics/samhan.realtime.subscribe` 조회 | gauge=5 + service-name="dashboard" 태그 | metric 회귀 시 운영 모니터링 무력화 |

### 10.5 ApplicationContextLoadIT — broker bean 자동 등록

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 10.5 | DEVOPS | 🔴 | shared-realtime 의존 추가 + service-name 환경변수 + `@MockBean` 외부 client 격리 | `SpringBootTest` startup | `SamhanRealtimeBroker` bean 단일 등록 + `RealtimeBrokerAutoConfig` 자동 발동 + startup 정상 | bean 등록 회귀 시 dashboard-service 부팅 실패 |

---

## 11. notification-service (5 case) — broker only

> **모듈 위치** — `services/notification-service/`
> **specialization** — broker only

### 11.1 broker only — 발송 완료 push

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 11.1 | SALES | 🔴 | notify-001 (PENDING) | notification-service 가 외부 SMS gateway 호출 → 성공 → status=DELIVERED | Redis publish `samhan:notification:notification:delivered:{notifyId}` + desktop SSE 수신 → "전송 완료" 표시 | delivered push 회귀 시 사용자 발송 상태 인지 단절 |

### 11.2 broker only — 발송 실패 push + 재시도 안내

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 11.2 | SALES | 🔴 | notify-002 (PENDING) + 외부 gateway 5xx | notification-service retry 3회 모두 실패 → status=FAILED | Redis publish `samhan:notification:notification:failed:{notifyId}` + desktop toast "발송 실패 — 재시도 가능" | failed push 회귀 시 사용자가 발송 실패 인지 못 함 |

### 11.3 audit endpoint 미적용 검증

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 11.3 | MASTER | 🟠 | notification-service 부팅 | `GET /notifications/{id}/audit-logs` | 404 (append-only 도메인) | append-only 에 audit 부담 시 disk + 운영 비용 폭증 |

### 11.4 broker only — 다중 노드 fan-out

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 11.4 | DEVOPS | 🟠 | notification-service instance 2대 + `SAMHAN_REALTIME_BROKER=redis` | instance A 가 publish | instance B 의 SSE 구독자도 1초 안 수신 (Redis fan-out) | 다중 노드 회귀 시 instance 의존 발송 상태 inconsistency |

### 11.5 ApplicationContextLoadIT

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 11.5 | DEVOPS | 🔴 | shared 의존 + 외부 client `@MockBean` 격리 | `SpringBootTest` startup | broker bean 단일 + startup 정상 | startup 회귀 시 notification-service 부팅 실패 → 알림 채널 마비 |

---

## 12. 공통 회귀 가드 (slip-service 시드 보존, 5 case)

> **목적**: 본 PR-H4b 가 `shared-realtime` / `shared-edit-request` 모듈을 13 service 가 의존 추가한 결과로, slip-service 시드 동작 회귀 0건 검증.

### 12.1 slip-service multi-context 1초 sync 회귀 (Samhan Public 핵심 요구)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 12.1 | SALES + WAREHOUSE | 🔴 | slip-service `SAMHAN_REALTIME_BROKER=redis` + 13 service 동일 ElastiCache 공유 | SALES context A 에서 slip-001 memo 수정 → WAREHOUSE context B 의 SlipDetailPage 수신 | 1초 안 audit overlay 표시 (취소선 + 색상 + actorName) — PR-H4a 5.5.2 회귀 case 1:1 동일 | sync 1초 회귀 시 Samhan Public 핵심 가치 단절 |

### 12.2 slip-service 잠금 정책 회귀

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 12.2 | SALES | 🔴 | slip-001 (ACCEPTED) + 승인 0건 | direct `PATCH` | shared `LockPolicy` specialization (`SlipLockPolicy`) 위임 → `LOCKED_REQUIRES_APPROVAL` CONFLICT (PR-H3 회귀 1:1 동일) | 회귀 시 PR-H3 운영 슬립 잠금 무력화 |

### 12.3 slip-service edit-request 회귀

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 12.3 | SALES + WAREHOUSE | 🔴 | slip-001 (ACCEPTED) | (1) SALES request → (2) WAREHOUSE approve → (3) SALES mutation 1회 → (4) SALES 두 번째 mutation | (1)~(4) PR-H3 시나리오 1:1 동일 | edit-request 1회 한정 소진 회귀 시 무한 수정 가능 |

### 12.4 channel collision 검증

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 12.4 | DEVOPS | 🔴 | 13 service 모두 `SAMHAN_REALTIME_BROKER=redis` + ElastiCache MONITOR | 30초 sample → channel pattern 추출 | 모든 channel 이 `samhan:<serviceName>:*` 형식 + service-name 13개 모두 등장 + `partner` / `partner-order` collision 0 (full string equals 보장) | collision 회귀 시 service A 가 service B event 수신 |

### 12.5 publishFailureCount metric

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 12.5 | DEVOPS | 🔴 | 13 service 24h 운영 | 각 service `/actuator/metrics/samhan.realtime.publish.failure` 조회 | 13 service 모두 카운터 = 0 (정상 = 0~소수) | metric 증가 시 Redis 연결 단절 → 즉시 알람 + 진단 |

---

## 13. PASS/FAIL 종합

- **13 service × 5 case** = **65 case** (사용자 명세 일치)
- **공통 회귀 가드** = 5 case (별도 — slip-service 시드 보존)
- **총 70 case** (65 case 도메인별 + 5 case 회귀 가드)
- **페르소나 5** (SALES / WAREHOUSE / ACCOUNTANT / MANAGER / MASTER 또는 DEVOPS) — `feedback_role_naming_full` 풀네임 의무

### 13.1 회귀 우선순위 매트릭스

| 우선순위 | partner | inventory | accounting | arologis | product | dc-config | partner-order | user | groupware | dashboard | notification | 회귀 가드 | 합계 |
| --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| 🔴 Critical | 5 | 5 | 4 | 4 | 5 | 4 | 5 | 2 | 2 | 2 | 3 | 5 | **46** |
| 🟠 Major | 0 | 0 | 1 | 1 | 0 | 0 | 0 | 2 | 1 | 2 | 1 | 0 | **8** |
| 🟡 Minor | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 1 | 1 | 0 | 0 | **3** |
| 🟢 Info | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 | 0 | 1 | 0 | **3** |
| **합계** | **5** | **5** | **5** | **5** | **5** | **5** | **5** | **5** | **5** | **5** | **5** | **5** | **70** |

> **주**: dashboard / notification 은 audit/edit-request 미도입 — Critical case 비율 낮음. accounting 은 한국 회계 무결성 의무로 Critical 비율 높음.

### 13.2 도메인별 PASS 게이트

| 도메인 | 필수 PASS case | 진입 조건 |
| --- | --- | --- |
| partner | 1.1 + 1.2 + 1.3 + 1.4 + 1.5 (모두 🔴) | PR-H4c partner page 진입 가능 |
| inventory | 2.1 + 2.2 + 2.3 + 2.4 + 2.5 (모두 🔴) | PR-H4c StockAdjustPage + WarehouseDetailPage 진입 |
| accounting | 3.1~3.4 (🔴) | PR-H4c JournalDetailPage 진입 (감사 의무 — 보수적 게이트) |
| arologis | 4.1~4.4 (🔴) | PR-H4c DispatchDetailPage + DispatchScreen 진입 |
| product | 5.1~5.5 (🔴) | PR-H4c ProductDetailPage 진입 |
| dc-config | 6.1~6.4 (🔴) | PR-H4c DcRuleDetailPage 진입 |
| partner-order | 7.1~7.5 (🔴) | PR-H4c PartnerOrderDetailPage + 거래처 mobile 진입 |
| user | 8.1 + 8.2 (🔴) | PR-H4c UserProfilePage 진입 |
| groupware | 9.1 + 9.2 (🔴) | PR-H4c MemoDetailPage + AnnouncementDetailPage 진입 |
| dashboard | 10.1 (🔴) + 10.5 (🔴) | broker only 진입 가능 |
| notification | 11.1 + 11.2 (🔴) + 11.5 (🔴) | broker only 진입 가능 |

### 13.3 최종 판정

본 시나리오 70 case + BE 측 단위/IT 보강 (도메인별 specialization 단위 + ApplicationContextLoadIT) + 본 agent docs 3 건 (Designer + DevOps + QA) 모두 첨부 + DevOps § 3 단계적 cutover Day 1~Day 6 완료 시 PR-H4b GREEN 머지 가능.

**Samhan Public 핵심 요구 검증**: 12.1 multi-context 1초 sync 회귀 case (slip-service 시드 보존) 가 핵심 GREEN 게이트. 추가로 9 audit overlay 도메인 모두 동일 1초 sync 보장 의무 (PR-H4c FE 통합 시 검증).

**PR-H4c 진입 조건**:
- 본 PR-H4b 머지
- 13 service ApplicationContextLoadIT 모두 GREEN
- DevOps § 3 cutover Day 6 종합 모니터링 통과 (publishFailureCount = 0 유지)
- 본 시나리오 65 case + 회귀 가드 5 case 모두 PASS
- 채널 collision 0건 (12.4 case PASS)

---

## 14. 참고

- shared-realtime BE 모듈 (PR-H4a 머지 완료): `services/shared-realtime/`
- shared-edit-request BE 모듈 (PR-H4a 머지 완료): `services/shared-edit-request/`
- Designer 매트릭스 + 잠금 정책 일람 (본 PR 동반): `docs/uiux/phase12/H4b-be-rollout-checklist.md`
- DevOps 단계적 cutover (본 PR 동반): `docs/devops/phase12-redis-multi-service.md`
- PR-H4a 시드 시나리오 (shared module 단위 + slip 회귀): `docs/qa/phase-12-step-4a-shared-realtime-module/scenarios.md`
- PR-H2 시드 시나리오 (audit overlay base): `docs/qa/phase-12-step-2-slip-audit-overlay/scenarios.md`
- PR-H3 시드 시나리오 (edit-request base): `docs/qa/phase-12-step-3-slip-edit-permission/scenarios.md`
- userColorHash util (deterministic): `clients/web/design-system/src/utils/userColorHash.ts`
- AuditOverlay 컴포넌트: `clients/web/design-system/src/components/AuditOverlay/`
- SlipDetailPage 시드 (1:1 복제 base): `clients/desktop/src/renderer/routes/SlipDetailPage.tsx`
- 한국 일반기업회계기준 표준 계정과목: `project_korean_accounting`
- 멀티 에이전트 팀 디스패치 패턴: `feedback_multi_agent_team_pattern`
