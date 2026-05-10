# PR-H4b Designer — `shared-realtime` BE 13 service 적용 매트릭스 + 상태별 잠금 정책 일람표

> Phase 12 Step 4b — BE realtime rollout 슬라이스의 Designer 산출물.
> PR-H4a 에서 추출한 `shared-realtime` + `shared-edit-request` 모듈을
> **slip-service 외 13 backend service 가 의존 추가만으로 일괄 도입** 하기 위한
> 적용 매트릭스 + 도메인별 상태/잠금 정책 표 + roll-out 우선순위 가이드.
> 본 PR-H4b 는 BE 단일 PR 이지만, Designer 측은 **도메인별 상태값/잠금 분류/event name 의 시각·UX 일관성**
> 을 미리 명세하여 PR-H4c (50+ page UI 통합) 단계 진입 전에 BE/FE 합의를 확보한다.

## 0. 본 가이드의 위상

| PR | 책임 | 산출 |
| --- | --- | --- |
| PR-H4a (완료, 머지됨) | shared 모듈 추출 + 시드 가이드 | `services/shared-realtime`, `services/shared-edit-request`, H4a Designer/DevOps/QA 가이드 |
| **PR-H4b (본 PR)** | 13 service BE 일괄 도입 | shared 모듈 의존 추가 + audit/edit-request 도메인 specialization + 본 Designer/DevOps/QA docs |
| PR-H4c | 50+ page UI 통합 (desktop / mobile-staff) | 본 가이드 § 3 / § 4 / § 5 따르기 |

> **원칙**: 본 PR-H4b 는 코드 변경이 **BE 의존 추가 + 도메인별 audit/lock specialization 클래스 추가** 만 — 기존 endpoint 표면 변경 0건. FE 회귀 0건이 필수 게이트.

## 1. 13 service 일괄 적용 매트릭스 (BE rollout 우선순위)

> **slip-service** 는 PR-H4a 에서 이미 shared 모듈로 마이그레이트 완료 (시드). 본 PR-H4b 는 나머지 13 service 가 대상.
> **api-gateway / eureka-server** 는 인프라 layer — audit/edit-request 도입 대상 외 (총 15 service 중 도입 후보 = 13 service).

### 1.1 13 service × shared 모듈 적용 매트릭스

| Backend Service | audit overlay 도입 | edit-request 도입 | shared-realtime broker 의존 | shared-edit-request 의존 | 본 PR-H4b 우선순위 |
| --- | :-: | :-: | :-: | :-: | :-: |
| **partner-service** | O (사업자명/대표자/연락처/주소) | O (마스터 데이터 변경 보호) | O | O | 🔴 1순위 (사용 빈도 최고) |
| **inventory-service** | O (조정 사유/수량) | O (회계 무결성 의무) | O | O | 🔴 1순위 (한국 회계 감사 의무) |
| **accounting-service** | O (적요/금액/계정) | O (한국 일반기업회계기준 감사) | O | O | 🔴 1순위 (한국 회계 감사 의무) |
| **arologis-service** | O (기사/차량/예정시각) | O (배차 변경 SMS 알림) | O | O | 🟠 2순위 |
| **product-service** | O (단가/규격/SKU) | O (단가 변경 보호) | O | O | 🟠 2순위 |
| **dc-config-service** | O (할인 정책 본문) | O (정책 변경 추적 의무) | O | O | 🟠 2순위 |
| **partner-order-service** | O (주문 본문) | O (주문 확정 후 보호) | O | O | 🟠 2순위 |
| **user-service** | O (이름/연락처/소속) | X (자기 정보 자유 수정) | O | X | 🟡 3순위 |
| **groupware-service** | O (메모/공지 본문) | X (작성자 자유 수정) | O | X | 🟡 3순위 |
| **dashboard-service** | X (read-only) | X | broker only (push) | X | 🟢 broker only |
| **notification-service** | X (전송 로그 — append-only) | X | broker only (push) | X | 🟢 broker only |
| **partner-auth-service** | X (인증/세션) | X | X | X | 🟢 적용 제외 |
| **auth-service** | X (인증) | X | X | X | 🟢 적용 제외 |
| **logging-service** | X (read-only / append-only) | X | X | X | 🟢 적용 제외 |

> **요약**: 13 service 중 **9 service** (partner / inventory / accounting / arologis / product / dc-config / partner-order / user / groupware) 가 `shared-realtime` 의존 도입. 그 중 **7 service** (user / groupware 제외) 가 `shared-edit-request` 의존도 도입. 4 service (auth / partner-auth / logging) + 2 인프라 (api-gateway / eureka-server) 는 적용 제외.

### 1.2 본 PR-H4b BE 도입 단계 (commit 단위 권고)

> 13 service 동시 commit 회피 — review 부담 + roll-back 단위 분리 의무.

| commit 단계 | 대상 service | 이유 |
| --- | --- | --- |
| commit 1 | partner-service + inventory-service + accounting-service | 🔴 1순위 (한국 회계 + 사용 빈도 최고) |
| commit 2 | arologis-service + product-service + dc-config-service + partner-order-service | 🟠 2순위 (영업 운영) |
| commit 3 | user-service + groupware-service | 🟡 3순위 (audit only — edit-request 미도입) |
| commit 4 | dashboard-service + notification-service | 🟢 broker only (push 수단) |
| commit 5 | shared 모듈 의존 추가만 (auth/partner-auth/logging 적용 제외 명시) | docs/build.gradle 표기 |

> 또는 본 PR 가 단일 commit 일 경우 위 5 그룹을 **commit message body 의 절** 로 명시.

## 2. 도메인별 상태값 + 잠금 정책 일람표 (`LockPolicy<TStatus>` specialization)

> PR-H3 시드의 `SlipLockPolicy implements LockPolicy<SlipStatus>` 패턴을 **9 service** 에 일관 적용. 도메인별 enum 값과 잠금 분류 (`FREE_DIRECT_EDIT` / `LOCKED_REQUIRES_APPROVAL` / `FULLY_LOCKED`) 를 사전 합의하여 BE rollout 시 specialization 클래스가 본 표를 1:1 reference.

### 2.1 슬립 (slip-service) — PR-H3 시드 (변경 0)

| 상태값 | 분류 | 직접 수정 가능 ROLE | 수정 요청 가능 ROLE | 승인 가능 ROLE |
| --- | --- | --- | --- | --- |
| `DRAFT` | FREE_DIRECT_EDIT | 작성자 (SALES) + MANAGER + MASTER | — | — |
| `SAVED` | FREE_DIRECT_EDIT | 작성자 (SALES) + MANAGER + MASTER | — | — |
| `ACCEPTED` | LOCKED_REQUIRES_APPROVAL | 활성 승인 보유 시 1회 (SALES) + MANAGER + MASTER 항상 | SALES (작성자) | WAREHOUSE / MANAGER / MASTER |
| `INSPECTING` | FULLY_LOCKED | MASTER 만 (별도 SQL audit) | — | — |
| `DELIVERED` (softDelete) | FULLY_LOCKED | MASTER 만 | — | — |

### 2.2 거래처 (partner-service) — 본 PR-H4b 신규

| 상태값 | 분류 | 직접 수정 가능 ROLE | 수정 요청 가능 ROLE | 승인 가능 ROLE |
| --- | --- | --- | --- | --- |
| `DRAFT` | FREE_DIRECT_EDIT | SALES + MANAGER + MASTER | — | — |
| `ACTIVE` | LOCKED_REQUIRES_APPROVAL | 활성 승인 1회 (SALES) + MANAGER + MASTER 항상 | SALES | MANAGER / MASTER |
| `SUSPENDED` | FULLY_LOCKED | MASTER 만 | — | — |
| `INACTIVE` (softDelete) | FULLY_LOCKED | MASTER 만 | — | — |

### 2.3 재고조정 (inventory-service / `StockAdjust`) — 본 PR-H4b 신규

| 상태값 | 분류 | 직접 수정 가능 ROLE | 수정 요청 가능 ROLE | 승인 가능 ROLE |
| --- | --- | --- | --- | --- |
| `DRAFT` | FREE_DIRECT_EDIT | WAREHOUSE + MANAGER + MASTER | — | — |
| `SUBMITTED` | LOCKED_REQUIRES_APPROVAL | 활성 승인 1회 (WAREHOUSE) + MANAGER + MASTER 항상 | WAREHOUSE | MANAGER / MASTER |
| `POSTED` (회계 전기 완료) | FULLY_LOCKED | MASTER 만 (별도 회계 정정 분개) | — | — |
| `VOIDED` (softDelete) | FULLY_LOCKED | MASTER 만 | — | — |

### 2.4 분개 (accounting-service / `Journal`) — 본 PR-H4b 신규

> 한국 일반기업회계기준 감사 대응 — 가장 엄격한 잠금 정책.

| 상태값 | 분류 | 직접 수정 가능 ROLE | 수정 요청 가능 ROLE | 승인 가능 ROLE |
| --- | --- | --- | --- | --- |
| `DRAFT` | FREE_DIRECT_EDIT | ACCOUNTANT + MANAGER + MASTER | — | — |
| `POSTED` (전기 완료) | FULLY_LOCKED | MASTER 만 (별도 정정 분개 의무) | — | — |
| `CLOSED` (월/연 마감) | FULLY_LOCKED | MASTER 만 (감사인 동석 의무) | — | — |
| `VOIDED` (softDelete) | FULLY_LOCKED | MASTER 만 | — | — |

> **POSTED 상태에서도 LOCKED_REQUIRES_APPROVAL 미적용** — 한국 회계 표준 (계정과목 코드 100/200/300/400/500/800/900) 의무 보존. POSTED 후 정정 = 별도 정정 분개 의무.

### 2.5 배차 (arologis-service / `Dispatch`) — 본 PR-H4b 신규

| 상태값 | 분류 | 직접 수정 가능 ROLE | 수정 요청 가능 ROLE | 승인 가능 ROLE |
| --- | --- | --- | --- | --- |
| `PLANNED` | FREE_DIRECT_EDIT | DISPATCHER + MANAGER + MASTER | — | — |
| `DISPATCHED` (기사 출발) | LOCKED_REQUIRES_APPROVAL | 활성 승인 1회 (DISPATCHER) + MANAGER + MASTER 항상 | DISPATCHER | MANAGER / MASTER |
| `IN_TRANSIT` (운송 중) | FULLY_LOCKED | MASTER 만 (실시간 변경 = 운송 사고 위험) | — | — |
| `DELIVERED` | FULLY_LOCKED | MASTER 만 | — | — |
| `CANCELED` (softDelete) | FULLY_LOCKED | MASTER 만 | — | — |

> **기사 변경 시 SMS 알림 의무**: arologis-service specialization 의 `EditRequestService` post-approve hook 에서 `NotificationClient.sendSms(driverPhone, ...)` 호출.

### 2.6 품목 (product-service) — 본 PR-H4b 신규

| 상태값 | 분류 | 직접 수정 가능 ROLE | 수정 요청 가능 ROLE | 승인 가능 ROLE |
| --- | --- | --- | --- | --- |
| `DRAFT` | FREE_DIRECT_EDIT | MASTER + MANAGER | — | — |
| `ACTIVE` | LOCKED_REQUIRES_APPROVAL | 활성 승인 1회 (MANAGER) + MASTER 항상 | MANAGER | MASTER |
| `DISCONTINUED` (단종) | FULLY_LOCKED | MASTER 만 | — | — |
| `INACTIVE` (softDelete) | FULLY_LOCKED | MASTER 만 | — | — |

### 2.7 할인 정책 (dc-config-service / `DcRule`) — 본 PR-H4b 신규

| 상태값 | 분류 | 직접 수정 가능 ROLE | 수정 요청 가능 ROLE | 승인 가능 ROLE |
| --- | --- | --- | --- | --- |
| `DRAFT` | FREE_DIRECT_EDIT | MANAGER + MASTER | — | — |
| `ACTIVE` (적용 중) | LOCKED_REQUIRES_APPROVAL | 활성 승인 1회 (MANAGER) + MASTER 항상 | MANAGER | MASTER |
| `EXPIRED` (만료) | FULLY_LOCKED | MASTER 만 | — | — |
| `INACTIVE` (softDelete) | FULLY_LOCKED | MASTER 만 | — | — |

### 2.8 거래처 주문 (partner-order-service / `PartnerOrder`) — 본 PR-H4b 신규

| 상태값 | 분류 | 직접 수정 가능 ROLE | 수정 요청 가능 ROLE | 승인 가능 ROLE |
| --- | --- | --- | --- | --- |
| `DRAFT` | FREE_DIRECT_EDIT | PARTNER (자기 주문) + MANAGER + MASTER | — | — |
| `SUBMITTED` (제출) | LOCKED_REQUIRES_APPROVAL | 활성 승인 1회 (PARTNER) + MANAGER + MASTER 항상 | PARTNER | MANAGER / MASTER |
| `CONFIRMED` (확정) | FULLY_LOCKED | MASTER 만 | — | — |
| `FULFILLED` (출고 완료) | FULLY_LOCKED | MASTER 만 | — | — |
| `CANCELED` (softDelete) | FULLY_LOCKED | MASTER 만 | — | — |

### 2.9 사용자 (user-service) — audit only

| 상태값 | 분류 | 비고 |
| --- | --- | --- |
| `ACTIVE` | FREE_DIRECT_EDIT | 자기 정보 수정 + MASTER 의 모든 사용자 수정 |
| `SUSPENDED` | FULLY_LOCKED | MASTER 만 |
| `INACTIVE` (softDelete) | FULLY_LOCKED | MASTER 만 |

> **edit-request 미도입** — 자기 정보는 본인이 자유 수정 (HR 정책). audit overlay 만 도입 (감사 보존).

### 2.10 그룹웨어 (groupware-service / `Memo` / `Announcement`) — audit only

| 상태값 | 분류 | 비고 |
| --- | --- | --- |
| `DRAFT` | FREE_DIRECT_EDIT | 작성자 자유 수정 |
| `PUBLISHED` | FREE_DIRECT_EDIT | 작성자 + MANAGER + MASTER (감사 audit 보존) |
| `ARCHIVED` (softDelete) | FULLY_LOCKED | MASTER 만 |

> **edit-request 미도입** — 메모/공지는 작성자가 자유 수정 (audit overlay 로 변경 이력만 보존).

## 3. 도메인별 event name 합의 (Redis channel + SSE event)

PR-H4a `RedisSamhanRealtimeBroker` 의 channel prefix 규칙 = `samhan:<service>:<eventName>:{entityId}`. 13 service rollout 시 도메인별 event name 사전 합의 의무 (PR-H4c FE 가 동일 string 으로 listen).

### 3.1 audit overlay event name (도메인 9건)

| 도메인 | edit event name | reverted event name | Redis channel 예 |
| --- | --- | --- | --- |
| slip (시드) | `slip:edit` | `slip:reverted` | `samhan:slip:slip:edit:{slipId}` |
| partner | `partner:edit` | `partner:reverted` | `samhan:partner:partner:edit:{partnerId}` |
| inventory (조정) | `stock-adjust:edit` | `stock-adjust:reverted` | `samhan:inventory:stock-adjust:edit:{adjustId}` |
| accounting (분개) | `journal:edit` | `journal:reverted` | `samhan:accounting:journal:edit:{journalId}` |
| arologis (배차) | `dispatch:edit` | `dispatch:reverted` | `samhan:arologis:dispatch:edit:{dispatchId}` |
| product | `product:edit` | `product:reverted` | `samhan:product:product:edit:{productId}` |
| dc-config | `dc-rule:edit` | `dc-rule:reverted` | `samhan:dc-config:dc-rule:edit:{ruleId}` |
| partner-order | `partner-order:edit` | `partner-order:reverted` | `samhan:partner-order:partner-order:edit:{orderId}` |
| user | `user:edit` | `user:reverted` | `samhan:user:user:edit:{userId}` |
| groupware | `memo:edit` / `announcement:edit` | `memo:reverted` / `announcement:reverted` | `samhan:groupware:memo:edit:{memoId}` |

### 3.2 edit-request event name (도메인 7건)

| 도메인 | created event | decided event | Redis channel 예 |
| --- | --- | --- | --- |
| slip (시드) | `slip:edit-request:created` | `slip:edit-request:decided` | `samhan:slip:slip:edit-request:created:{slipId}` |
| partner | `partner:edit-request:created` | `partner:edit-request:decided` | `samhan:partner:partner:edit-request:created:{partnerId}` |
| inventory | `stock-adjust:edit-request:created` | `stock-adjust:edit-request:decided` | `samhan:inventory:stock-adjust:edit-request:...` |
| accounting | `journal:edit-request:created` | `journal:edit-request:decided` | `samhan:accounting:journal:edit-request:...` |
| arologis | `dispatch:edit-request:created` | `dispatch:edit-request:decided` | `samhan:arologis:dispatch:edit-request:...` |
| product | `product:edit-request:created` | `product:edit-request:decided` | `samhan:product:product:edit-request:...` |
| dc-config | `dc-rule:edit-request:created` | `dc-rule:edit-request:decided` | `samhan:dc-config:dc-rule:edit-request:...` |
| partner-order | `partner-order:edit-request:created` | `partner-order:edit-request:decided` | `samhan:partner-order:partner-order:edit-request:...` |

### 3.3 broker-only event (dashboard / notification)

| 도메인 | event name | 용도 |
| --- | --- | --- |
| dashboard | `dashboard:metric:updated` | 실시간 KPI 갱신 push |
| dashboard | `dashboard:alert:fired` | 임계 알람 push |
| notification | `notification:delivered` | 발송 완료 push (read-only 표시) |
| notification | `notification:failed` | 발송 실패 push (재시도 안내) |

## 4. 도메인별 한국어 라벨 매핑 (PR-H4c FE 통합 의무)

PR-H2 § 7 + PR-H4a § 4.1 시드 확장 — 본 PR-H4b BE rollout 단계에서 사전 합의하여 PR-H4c FE 가 1:1 reference.

### 4.1 13 service 필드 라벨 사전

| 도메인 | fieldName | 한국어 라벨 (UI 표시) | 비고 |
| --- | --- | --- | --- |
| partner | `businessName` | 사업자명 | 마스터 데이터 |
| partner | `representativeName` | 대표자명 | |
| partner | `contactPhone` | 연락처 | KOREAN_MOBILE_PHONE_PATTERN 검증 |
| partner | `address` | 주소 | |
| partner | `businessRegistrationNo` | 사업자등록번호 | 한국 표준 10자리 |
| inventory | `adjustReason` | 조정 사유 | 200자+ 권고 |
| inventory | `quantity` | 수량 | 양수/음수 표기 |
| inventory | `productCode` | 품목코드 | UUID 비공개 — 코드 노출 |
| inventory | `warehouseCode` | 창고 코드 | UUID 비공개 — 코드 노출 |
| accounting | `description` | 적요 | |
| accounting | `amount` | 금액 | 천 단위 콤마 표기 |
| accounting | `accountCode` | 계정 코드 | 한국 표준 (100/200/300/400/500/800/900) |
| accounting | `partnerCode` | 거래처 코드 | UUID 비공개 |
| arologis | `driverName` | 기사명 | SMS 알림 의무 |
| arologis | `vehicleNo` | 차량번호 | |
| arologis | `scheduledAt` | 예정시각 | YYYY-MM-DD HH:mm |
| arologis | `route` | 운송 경로 | |
| product | `unitPrice` | 단가 | 천 단위 콤마 |
| product | `specName` | 규격 | |
| product | `sku` | SKU | UUID 비공개 — SKU 노출 |
| dc-config | `discountRate` | 할인율 | % 표기 |
| dc-config | `validFrom` / `validTo` | 적용 시작 / 종료 | YYYY-MM-DD |
| dc-config | `targetPartnerCode` | 대상 거래처 코드 | UUID 비공개 |
| partner-order | `orderQuantity` | 주문 수량 | |
| partner-order | `requestedDeliveryDate` | 희망 납품일 | YYYY-MM-DD |
| partner-order | `note` | 비고 | |
| user | `name` | 이름 | |
| user | `contactPhone` | 연락처 | |
| user | `department` | 소속 부서 | |
| groupware | `title` | 제목 | |
| groupware | `body` | 본문 | |
| groupware | `category` | 카테고리 | enum 한국어 라벨 |

### 4.2 공통 라벨 (PR-H4a § 4.2 그대로 — 13 service 일관)

| 영문 키 | 한국어 라벨 | 적용 도메인 |
| --- | --- | --- |
| `current` | (값 그대로) | 전 도메인 |
| `before` | (값 그대로 + 취소선) | 전 도메인 |
| `actorName` | (사용자 풀네임) | 전 도메인 |
| `expandToggle` | `이력 N개 보기` / `이력 닫기` | 전 도메인 |
| `empty` | `변경 이력 없음` | 전 도메인 |
| `editCountBadge` | `수정 N회` | 전 도메인 |
| `restoreDropdown` | `↩ 이 시점으로 복원` / `↩ 최초 값으로 복원` | 전 도메인 (MANAGER+) |
| `restoreConfirm` | `revision N 으로 복원하시겠습니까?` | 전 도메인 |
| `lockBanner` | `이 N 은 잠금 상태입니다 — 수정 요청을 보내주세요` | edit-request 도입 7 도메인 |
| `lockBanner_FULLY_LOCKED` | `이 N 은 완전 잠금 상태입니다 — MASTER 에게 문의하세요` | 전 도메인 |

> N 은 도메인 한국어 명사 (전표 / 거래처 / 재고조정 / 분개 / 배차 / 품목 / 할인규칙 / 거래처주문 / 사용자 / 메모/공지).

## 5. UUID 비공개 가드 (13 service 일괄 의무)

PR-H4a § 5 그대로 13 service 일괄 적용:

- `actorId` 는 색상 hash 입력으로만 사용 — **화면 텍스트 노출 0건**.
- `actorName` 만 화면 표시.
- 도메인 본체 식별자 — `partnerId` / `journalId` / `dispatchId` / `productId` / `ruleId` / `orderId` / `userId` / `memoId` / `adjustId` 등 **UUID 모두 비공개**.
- 비즈니스 식별자만 노출:
  - partner: `businessName` (사업자명) / `businessRegistrationNo` (사업자등록번호)
  - inventory: `productCode` + `warehouseCode`
  - accounting: `journalNo` (분개번호) + `accountCode`
  - arologis: `dispatchNo` (배차번호) + `vehicleNo`
  - product: `sku` + `specName`
  - dc-config: `ruleName` (규칙명)
  - partner-order: `orderNo` (주문번호)
  - user: `name`
  - groupware: `title`
- `data-testid` / `aria-label` 등 DOM 속성에도 UUID 직접 노출 금지.

## 6. PR-H4c (50+ page UI 통합) 진입 조건

본 PR-H4b 머지 후 PR-H4c 진입 전 BE rollout 검증 의무:

### 6.1 BE rollout PASS 게이트

- [ ] 9 service 모두 `shared-realtime` 의존 추가 + bean 자동 등록 + `SamhanRealtimeBroker` interface 구현 자동 주입 PASS
- [ ] 7 service 모두 `shared-edit-request` 의존 추가 + `LockPolicy<TStatus>` specialization 클래스 등록 PASS
- [ ] 본 § 2 잠금 정책 일람표 1:1 일치 (정책 mismatch 0건)
- [ ] 본 § 3 event name 일치 (Redis channel collision 0건)
- [ ] 13 service ApplicationContextLoadIT 모두 GREEN
- [ ] 13 service `@MockBean` IT (외부 client 격리) 모두 GREEN

### 6.2 회귀 가드 (slip-service 보존)

- [ ] PR-H1/H2/H3 시나리오 100% 회귀 보존 (PR-H4a § 5 / § 6 회귀 case 38 건 모두 PASS)
- [ ] 5.5.2 multi-context 1초 sync 회귀 case PASS (Samhan Public 핵심 요구)

### 6.3 도메인별 specialization 검증 (BE 단위)

- [ ] 9 도메인 LockPolicy specialization 단위 테스트 — 각 상태값 × 분류 매핑 1:1 일치
- [ ] 7 도메인 EditRequestService specialization 단위 테스트 — request/approve/reject/expirePending/consumeApproval 5 책임 1:1 동일
- [ ] 9 도메인 audit overlay endpoint (`GET /<domain>s/{id}/audit-logs` + `POST /<domain>s/{id}/audit/revert/{n}`) 단위 테스트 — 응답 schema 1:1 동일

## 7. mobile-staff 확장 가이드 (PR-H4c RN 환경)

PR-H4a § 7 시드 확장 — 본 PR-H4b BE 가 도입한 도메인 중 mobile-staff 가 사용하는 도메인은 다음:

| RN screen | 적용 도메인 | 적용 fieldName | 비고 |
| --- | --- | --- | --- |
| `SlipDetailScreen` (시드) | slip | partnerName / status | PR-H2 시드 — 변경 0 |
| `DispatchScreen` (PR-H4c 신규) | arologis | driverName / scheduledAt | 기사 변경 SMS 알림 의무 |
| `StockAdjustScreen` (PR-H4c 신규) | inventory | adjustReason / quantity | 회계 무결성 의무 |
| `PartnerOrderScreen` (PR-H4c 신규 — 거래처 사용자) | partner-order | orderQuantity / requestedDeliveryDate | partner-auth 통과 후 노출 |

### 7.1 RN 시각 회귀 가드 (PR-H4a § 7.1 그대로)

- [ ] `Text` 의 `textDecorationLine: 'line-through'` 적용
- [ ] `View` dot 의 `backgroundColor` = `userIdToColor(actorId)` (RN 1:1 복제 util)
- [ ] `actorName` Text 색상 = `#1a1a1a` (검정 가독성)
- [ ] 빈 history → `<Text style={{fontStyle: 'italic'}}>변경 이력 없음</Text>`

## 8. 본 PR-H4b Designer 산출물 (가이드만 — 코드 0)

- [x] 본 문서 `docs/uiux/phase12/H4b-be-rollout-checklist.md`
- [ ] (PR-H4c FE 가 도입 시) 도메인별 wireframe doc 1건씩 (`docs/uiux/phase12/H4c-<domain>-audit-overlay.md`) 시드 권고

## 9. 참고

- PR-H4a Designer 가이드 (시드 base): `docs/uiux/phase12/H4a-shared-realtime-pattern.md`
- PR-H4b DevOps 가이드 (본 PR 동반): `docs/devops/phase12-redis-multi-service.md`
- PR-H4b QA 시나리오 (본 PR 동반): `docs/qa/phase-12-step-4b-be-realtime-rollout/scenarios.md`
- PR-H1 wireframe (코멘트 smoke): `docs/uiux/phase12/H1-comment-smoke.md`
- PR-H2 wireframe (audit overlay 시드): `docs/uiux/phase12/H2-audit-overlay.md`
- PR-H3 wireframe (잠금/요청/수락): `docs/uiux/phase12/H3-edit-request-workflow.md`
- shared-realtime BE 모듈 (PR-H4a 머지 완료): `services/shared-realtime/`
- shared-edit-request BE 모듈 (PR-H4a 머지 완료): `services/shared-edit-request/`
- userColorHash util (deterministic HSL): `clients/web/design-system/src/utils/userColorHash.ts`
- userColorHash util (RN 1:1): `clients/mobile-staff/src/utils/userColorHash.ts`
- AuditOverlay 컴포넌트 본체: `clients/web/design-system/src/components/AuditOverlay/`
- SlipDetailPage 시드 (1:1 복제 base): `clients/desktop/src/renderer/routes/SlipDetailPage.tsx`
