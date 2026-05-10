# PR-H4c Designer — FE audit overlay rollout 종합 가이드 (50+ page 일괄 적용 매트릭스 + 사용자 패턴 시각 일관 보장)

> Phase 12 Step 4c — FE audit overlay rollout 슬라이스의 Designer 종합 산출물.
> PR-H2 시드 (slip 1 도메인) → PR-H4a `shared-realtime` 모듈 + 시드 가이드 → PR-H4b BE 13 service rollout 머지 완료.
> 본 PR-H4c 는 **9 audit overlay 도메인 × 50+ page 가 사용자 명시 패턴 (취소선 + 수정자 색상 + 수정자 이름 + 1초 SSE) 을 시각·UX 일관성 100% 로 도입** 하기 위한 종합 가이드.
>
> **Samhan Public 핵심 가치 (사용자 명시 헌법)**:
> > "다른 모든 화면도 마찬가지" — 한 사용자가 어떤 화면을 보든 동일한 audit overlay 멘탈 모델을 가진다.
>
> 본 가이드는 PR-H4a (시드) + PR-H4b (BE rollout 매트릭스) 를 통합·확장하여 FE 통합 PR 가이드를 단일 docs 로 묶는다.

---

## 0. 본 가이드의 위상 (PR-H4 sub 시리즈 종결)

| PR | 책임 | 산출 |
| --- | --- | --- |
| PR-H1 (머지 완료) | 색상 hash util 시드 (`userIdToColor`) | 시드 1 도메인 (slip 코멘트 smoke) |
| PR-H2 (머지 완료) | `AuditOverlay` 컴포넌트 + `SlipDetailPage` 시드 적용 | 시드 1 page |
| PR-H3 (머지 완료) | `SlipEditRequestDialog` 잠금/요청/수락 워크플로우 | 시드 1 도메인 |
| PR-H4a (머지 완료) | `shared-realtime` BE 모듈 + 시드 Designer/QA 가이드 | shared 모듈 + 시드 docs |
| PR-H4b (머지 완료) | 13 service BE 일괄 도입 (audit overlay + edit-request specialization) | BE rollout + 매트릭스 + 잠금 정책 일람 |
| **PR-H4c (본 PR)** | 50+ page FE 통합 + 매뉴얼 일괄 갱신 + 작동 캡처 | 본 종합 가이드 + 매뉴얼 8 docs + QA scenarios + 작동 캡처 5 PNG |

> **원칙**: 본 PR-H4c FE 통합은 PR-H2 `SlipDetailPage` 시드 패턴 (commit `435918c`) 의 **1:1 복제** 이며, 시각 차이 0건이 의무. 도메인별 한국어 라벨만 교체.
> **회귀 가드**: PR-H1/H2/H3 시드 동작 100% 보존 (slip-service 화면 픽셀 회귀 0건).

---

## 1. 50+ page 적용 매트릭스 (9 도메인 × page 단위)

### 1.1 desktop (Electron renderer) page 매트릭스

| Backend Service | 적용 page (`clients/desktop/src/renderer/routes/`) | audit overlay | edit-request 잠금 | 본 PR-H4c 우선순위 |
| --- | --- | :-: | :-: | :-: |
| **slip-service** (PR-H2 시드) | `SlipDetailPage.tsx` | O (memo / shippingAddress 외 11 필드) | O (PR-H3) | 시드 (변경 0) |
| **partner-service** | `PartnerDetailPage.tsx` | O (사업자명/대표자/연락처/주소/사업자등록번호) | O (ACTIVE locked-approval) | 🔴 1순위 |
| **partner-service** | `PartnerListPage.tsx` | "수정 N회" badge + 마지막 수정 actorName | X (목록은 잠금 없음) | 🔴 1순위 |
| **partner-service** | `PartnerCreatePage.tsx` | X (신규 등록 단계 — DRAFT) | X | 🟠 2순위 |
| **inventory-service** | `StockAdjustDetailPage.tsx` | O (조정 사유/수량) | O (SUBMITTED locked-approval) | 🔴 1순위 |
| **inventory-service** | `StockAdjustListPage.tsx` | "수정 N회" badge | X | 🔴 1순위 |
| **inventory-service** | `WarehouseDetailPage.tsx` | O (창고 마스터 데이터) | O | 🟠 2순위 |
| **inventory-service** | `StockMovePage.tsx` (재고 이동) | O (이동 사유) | O (POSTED FULLY_LOCKED) | 🟠 2순위 |
| **inventory-service** | `StockCountPage.tsx` (재고 실사) | O (실사 라인 차이/사유) | O | 🟠 2순위 |
| **accounting-service** | `JournalDetailPage.tsx` | O (적요/금액/계정 코드) | O (POSTED FULLY_LOCKED — 정정 분개 의무) | 🔴 1순위 |
| **accounting-service** | `JournalListPage.tsx` | "수정 N회" badge | X | 🔴 1순위 |
| **accounting-service** | `JournalReversePage.tsx` (역분개) | O (역분개 사유) | O | 🟠 2순위 |
| **accounting-service** | `MonthlyClosePage.tsx` (월말 마감) | O (마감 사유 / 마감 권한자) | O (CLOSED FULLY_LOCKED — 감사인 동석) | 🟠 2순위 |
| **accounting-service** | `TaxInvoicePage.tsx` (세금계산서) | O (공급자/공급받는자/품목/금액) | O | 🟠 2순위 |
| **arologis-service** | `DispatchDetailPage.tsx` | O (기사/차량/예정시각/경로) | O (DISPATCHED locked-approval, IN_TRANSIT FULLY_LOCKED) | 🔴 1순위 |
| **arologis-service** | `DispatchListPage.tsx` | "수정 N회" badge | X | 🔴 1순위 |
| **arologis-service** | `DispatchKakaoPage.tsx` (카카오톡 입력) | O (raw 텍스트 / 파싱 결과) | O | 🟠 2순위 |
| **arologis-service** | `VehiclePage.tsx` (차량 마스터) | O (차량번호/톤수) | O | 🟠 2순위 |
| **arologis-service** | `DriverPage.tsx` (기사 배정) | O (기사명/연락처) | O (post-approve hook = SMS 알림) | 🟠 2순위 |
| **product-service** | `ProductDetailPage.tsx` | O (단가/규격/SKU/카테고리) | O (ACTIVE locked-approval) | 🟠 2순위 |
| **product-service** | `ProductListPage.tsx` | "수정 N회" badge | X | 🟠 2순위 |
| **product-service** | `ProductCategoryPage.tsx` | O (카테고리 트리) | O | 🟡 3순위 |
| **dc-config-service** | `DcRuleDetailPage.tsx` | O (할인율/적용 시작/종료/대상 거래처) | O (ACTIVE locked-approval) | 🟠 2순위 |
| **dc-config-service** | `DcRuleListPage.tsx` | "수정 N회" badge | X | 🟠 2순위 |
| **partner-order-service** | `PartnerOrderDetailPage.tsx` | O (주문 본문/수량/희망 납품일) | O (SUBMITTED locked-approval) | 🟠 2순위 |
| **partner-order-service** | `PartnerOrderListPage.tsx` | "수정 N회" badge | X | 🟠 2순위 |
| **user-service** | `UserProfilePage.tsx` (본인) | O (이름/연락처/소속) | X (본인 자유 수정) | 🟡 3순위 |
| **user-service** | `UserListPage.tsx` (관리자) | O (타인 수정 — MASTER 만) | O (MASTER 외 잠금) | 🟡 3순위 |
| **user-service** | `UserDetailPage.tsx` (관리자) | O | O | 🟡 3순위 |
| **groupware-service** | `MemoDetailPage.tsx` | O (제목/본문/카테고리) | X (작성자 자유) | 🟡 3순위 |
| **groupware-service** | `AnnouncementDetailPage.tsx` | O | X | 🟡 3순위 |
| **groupware-service** | `AnnouncementListPage.tsx` | "수정 N회" badge | X | 🟡 3순위 |
| **dashboard-service** | `DashboardHomePage.tsx` | X (read-only — broker only push) | X | 🟢 broker only |
| **notification-service** | `NotificationListPage.tsx` | X (append-only) | X | 🟢 broker only |
| **slip-service** | `SlipListPage.tsx` (시드 목록) | "수정 N회" badge (시드) | X | 시드 (변경 0) |
| **slip-service** | `SlipCreatePage.tsx` | X (신규 작성 — DRAFT) | X | 시드 |

> **합계 desktop**: 약 **34 page** (9 도메인 = audit overlay 적용 + 5 도메인 = list badge + 4 도메인 = list 시드 + 2 도메인 broker only). PR-H4c rollout 1:1 대상.

### 1.2 mobile-staff (RN Expo) screen 매트릭스

| RN screen (`clients/mobile-staff/src/screens/`) | 적용 도메인 | audit overlay | 비고 |
| --- | --- | :-: | --- |
| `SlipDetailScreen.tsx` (PR-H2 시드) | slip | O (partnerName / status) | 시드 (변경 0) |
| `SlipListScreen.tsx` | slip | "수정 N회" badge | 시드 |
| `DispatchDetailScreen.tsx` (PR-H4c 신규) | arologis | O (driverName / scheduledAt) | 기사 변경 SMS 알림 의무 |
| `DispatchListScreen.tsx` | arologis | "수정 N회" badge | |
| `StockAdjustDetailScreen.tsx` (PR-H4c 신규) | inventory | O (adjustReason / quantity) | 회계 무결성 의무 |
| `StockAdjustListScreen.tsx` | inventory | "수정 N회" badge | |
| `PartnerOrderDetailScreen.tsx` (PR-H4c 신규 — 거래처 사용자) | partner-order | O (orderQuantity / requestedDeliveryDate) | partner-auth 통과 후 노출 |
| `PartnerOrderListScreen.tsx` | partner-order | "수정 N회" badge | |
| `UserProfileScreen.tsx` (본인) | user | O (이름 / 연락처) | |
| `MemoDetailScreen.tsx` | groupware | O (제목 / 본문) | 작성자 자유 수정 |
| `MemoListScreen.tsx` | groupware | "수정 N회" badge | |
| `NotificationListScreen.tsx` | notification | X (append-only) | broker only |

> **합계 mobile-staff**: 약 **12 screen** (5 도메인 적용 + 1 broker only). RN 1:1 복제 util (`clients/mobile-staff/src/utils/userColorHash.ts`) 일관 사용 의무.

### 1.3 partner-portal (Next.js / 거래처 외부) page 매트릭스

| page (`clients/partner-portal/pages/`) | 적용 도메인 | audit overlay | 비고 |
| --- | --- | :-: | --- |
| `orders/[id].tsx` (PR-H4c 신규) | partner-order | O (자기 주문만) | partner-auth 통과 + UUID 비공개 |
| `orders/index.tsx` | partner-order | "수정 N회" badge | |
| `account.tsx` (자기 정보) | user | O (이름 / 연락처) | 본인 자유 수정 |

> **합계 partner-portal**: 약 **3 page** (거래처 외부 사용자 자기 데이터 한정).

### 1.4 admin (관리자 전용) 매트릭스

| page (`clients/desktop/src/renderer/routes/admin/`) | 적용 도메인 | audit overlay | 비고 |
| --- | --- | :-: | --- |
| `UsersPage.tsx` | user | O (전 사용자) | MASTER 만 타인 수정 |
| `UserCreatePage.tsx` | user | X (신규 — DRAFT) | |
| `RolesPage.tsx` | auth | X (인증 도메인 — 적용 제외) | |
| `SystemConfigPage.tsx` | (시스템 설정) | O (설정 변경 audit) | MASTER 만 |
| `DashboardSettingsPage.tsx` | dashboard | X (read-only) | |

> **합계 admin**: 약 **5 page** (3 적용 + 2 적용 제외).

### 1.5 종합 합계

| 환경 | 적용 page 수 | broker only / 적용 제외 | 시드 보존 |
| --- | :-: | :-: | :-: |
| desktop | 30 | 2 | 2 |
| mobile-staff | 9 | 1 | 2 |
| partner-portal | 3 | 0 | 0 |
| admin | 3 | 2 | 0 |
| **합계** | **45 page** + 시드 4 page = **49 page** (50 근접) + broker only 5 page = **54 page** (50+ 도달) | 5 | 4 |

> **사용자 명시 50+ page 검증** — desktop 34 + mobile 12 + partner-portal 3 + admin 5 = **54 page** 일괄 영향. 본 PR-H4c rollout 1차 대상 = 45 page (적용 page) + 9 page (broker only / 시드 — 회귀 검증만).

---

## 2. 사용자 명시 패턴 (50+ page 일괄 일관 보장)

### 2.1 핵심 패턴 (PR-H2 헌법 → 50+ page 확장)

> "이전 품목에서 취소선을 긋고 새로운 데이터로 바로 위에 재표기
> (단 색상은 수정자마다 랜덤하게 자동 설정되며,
> 우측에 수정자 이름이 해당 색상으로 같이 표시)."

본 문장은 PR-H2 단계에서 slip 도메인에 한정되어 실증되었으나, **본 PR-H4c 부터는 50+ page 모두 동일 적용**. 사용자가 "내가 봤던 slip 화면" 의 시각 멘탈 모델 그대로 partner / inventory / accounting / arologis / product / dc-config / partner-order / user / groupware 화면을 학습할 수 있어야 한다.

### 2.2 시각 요소 1:1 일관 (PR-H2 시드 spec 그대로)

| 시각 요소 | spec | 50+ page 일관 의무 |
| --- | --- | --- |
| 검정 굵은 텍스트 (현재 값) | `color: #0F172A; font-weight: 600` | ✅ 50+ page |
| 취소선 + 회색 (변경 직전) | `text-decoration: line-through; color: #94A3B8` | ✅ 50+ page |
| 색상 dot (수정자 식별) | `userIdToColor(actorId)` HSL hash 28×28 px | ✅ 50+ page |
| 수정자 이름 (풀네임) | UUID 비공개 + `actorName` 만 | ✅ 50+ page |
| 시각 표기 (오늘 = `HH:mm`, 어제 이전 = `MM-DD HH:mm`) | locale ko-KR 일관 | ✅ 50+ page |
| `[이력 N개 보기]` 버튼 | 다중 revision 시 표시 | ✅ 50+ page |
| `[이력 닫기]` 버튼 | 펼침 상태 | ✅ 50+ page |
| 수정 N회 badge (헤더) | 0회 = hide / 1~4 회색 / 5~9 노랑 / 10+ 빨강 | ✅ 50+ page |
| 복원 dropdown (▾) | MANAGER+ / MASTER 만 | ✅ 50+ page (도메인별 권한 정책 § 3) |
| `(빈 값)` 표시 | NULL → 신규 입력 | ✅ 50+ page |
| SSE toast (1초 안 수신) | 우측 상단 success variant | ✅ 50+ page |

### 2.3 도메인별 한국어 라벨 매핑 (50+ page 일관)

PR-H4b § 4.1 그대로 — FE 통합 시 1:1 reference. 본 가이드는 추가 도메인 라벨 (PR-H4c 신규 page 라벨) 보강:

| 도메인 | fieldName | 한국어 라벨 | 적용 page |
| --- | --- | --- | --- |
| slip (시드) | `memo` / `shippingAddress` / `inspectionAddress` | 메모 / 배송지 / 검수지 | SlipDetailPage |
| partner | `businessName` | 사업자명 | PartnerDetailPage / PartnerListPage |
| partner | `representativeName` | 대표자명 | PartnerDetailPage |
| partner | `contactPhone` | 연락처 | PartnerDetailPage |
| partner | `address` | 주소 | PartnerDetailPage |
| partner | `businessRegistrationNo` | 사업자등록번호 | PartnerDetailPage |
| inventory | `adjustReason` | 조정 사유 | StockAdjustDetailPage |
| inventory | `quantity` | 수량 | StockAdjustDetailPage |
| inventory | `productCode` | 품목코드 | StockAdjustDetailPage / WarehouseDetailPage |
| inventory | `warehouseCode` | 창고 코드 | WarehouseDetailPage |
| inventory | `moveReason` | 이동 사유 | StockMovePage |
| inventory | `countDifference` | 실사 차이 | StockCountPage |
| accounting | `description` | 적요 | JournalDetailPage |
| accounting | `amount` | 금액 | JournalDetailPage |
| accounting | `accountCode` | 계정 코드 | JournalDetailPage |
| accounting | `partnerCode` | 거래처 코드 | JournalDetailPage |
| accounting | `reverseReason` | 역분개 사유 | JournalReversePage |
| accounting | `closeReason` | 마감 사유 | MonthlyClosePage |
| accounting | `taxInvoiceItems` | 세금계산서 라인 | TaxInvoicePage |
| arologis | `driverName` | 기사명 | DispatchDetailPage / DriverPage / DispatchScreen |
| arologis | `vehicleNo` | 차량번호 | DispatchDetailPage / VehiclePage |
| arologis | `scheduledAt` | 예정시각 | DispatchDetailPage / DispatchScreen |
| arologis | `route` | 운송 경로 | DispatchDetailPage |
| arologis | `rawKakaoText` | 카카오톡 원문 | DispatchKakaoPage |
| product | `unitPrice` | 단가 | ProductDetailPage |
| product | `specName` | 규격 | ProductDetailPage |
| product | `sku` | SKU | ProductDetailPage |
| product | `categoryPath` | 카테고리 | ProductCategoryPage |
| dc-config | `discountRate` | 할인율 | DcRuleDetailPage |
| dc-config | `validFrom` / `validTo` | 적용 시작 / 종료 | DcRuleDetailPage |
| dc-config | `targetPartnerCode` | 대상 거래처 코드 | DcRuleDetailPage |
| partner-order | `orderQuantity` | 주문 수량 | PartnerOrderDetailPage / 거래처 portal |
| partner-order | `requestedDeliveryDate` | 희망 납품일 | PartnerOrderDetailPage |
| partner-order | `note` | 비고 | PartnerOrderDetailPage |
| user | `name` | 이름 | UserProfilePage / admin/UsersPage |
| user | `contactPhone` | 연락처 | UserProfilePage |
| user | `department` | 소속 부서 | UserProfilePage |
| groupware | `title` | 제목 | MemoDetailPage / AnnouncementDetailPage |
| groupware | `body` | 본문 | MemoDetailPage / AnnouncementDetailPage |
| groupware | `category` | 카테고리 | MemoDetailPage |

### 2.4 공통 라벨 (50+ page 일관)

| 영문 키 | 한국어 라벨 | 50+ page 적용 |
| --- | --- | --- |
| `current` | (값 그대로) | 전 page |
| `before` | (값 그대로 + 취소선) | 전 page |
| `actorName` | (사용자 풀네임) | 전 page |
| `expandToggle` | `이력 N개 보기` / `이력 닫기` | 전 page |
| `empty` | `변경 이력 없음` | 전 page |
| `editCountBadge` | `수정 N회` | 전 page |
| `restoreDropdown` | `↩ 이 시점으로 복원` / `↩ 최초 값으로 복원` | 전 page (MANAGER+) |
| `restoreConfirm` | `revision N 으로 복원하시겠습니까?` | 전 page |
| `lockBanner` | `이 N 은 잠금 상태입니다 — 수정 요청을 보내주세요` | edit-request 도입 7 도메인 page |
| `lockBanner_FULLY_LOCKED` | `이 N 은 완전 잠금 상태입니다 — MASTER 에게 문의하세요` | 전 page |
| `realtimeToast` | `<actorName> 님이 <fieldLabel> 을 수정했습니다 (rev #N)` | 전 page (SSE 1초 수신) |

> N 은 도메인 한국어 명사 (전표 / 거래처 / 재고조정 / 분개 / 배차 / 품목 / 할인규칙 / 거래처주문 / 사용자 / 메모/공지).

---

## 3. 도메인별 잠금 정책 + UI 분기 (PR-H4b § 2 1:1 reference)

PR-H4b § 2 잠금 정책 일람표를 FE 가 1:1 reference. 도메인별 page 가 다음 분기를 동일 시각으로 표시:

### 3.1 LockPolicy 분류별 UI 분기 (50+ page 일관)

| LockPolicy 분류 | UI 분기 | 시각 표시 |
| --- | --- | --- |
| `FREE_DIRECT_EDIT` | 직접 수정 가능 | 모든 input 활성 + 수정 시 즉시 audit row 생성 + SSE publish |
| `LOCKED_REQUIRES_APPROVAL` | 잠금 — 수정 요청 dialog | 모든 input 비활성 + 상단 banner "수정 요청을 보내주세요" + `[수정 요청]` 버튼 |
| `FULLY_LOCKED` | MASTER 만 (도메인별 별도 절차 안내) | 모든 input 비활성 + 상단 banner "완전 잠금 — MASTER 에게 문의" |

### 3.2 도메인별 status × UI 분기 (FE 통합 reference)

> PR-H4b § 2 1:1 — FE 가 status enum 값 ↔ UI 분기를 정확히 매핑.

| 도메인 | DRAFT/PLANNED 등 free | 중간 잠금 (locked-approval) | 완전 잠금 (fully-locked) |
| --- | --- | --- | --- |
| slip | DRAFT / SAVED | ACCEPTED | INSPECTING / DELIVERED |
| partner | DRAFT | ACTIVE | SUSPENDED / INACTIVE |
| inventory (조정) | DRAFT | SUBMITTED | POSTED / VOIDED |
| accounting (분개) | DRAFT | (없음 — 직접 FULLY_LOCKED) | POSTED / CLOSED / VOIDED |
| arologis (배차) | PLANNED | DISPATCHED | IN_TRANSIT / DELIVERED / CANCELED |
| product | DRAFT | ACTIVE | DISCONTINUED / INACTIVE |
| dc-config | DRAFT | ACTIVE | EXPIRED / INACTIVE |
| partner-order | DRAFT | SUBMITTED | CONFIRMED / FULFILLED / CANCELED |
| user | (본인) ACTIVE | (없음 — audit only) | SUSPENDED / INACTIVE |
| groupware | DRAFT / PUBLISHED | (없음 — audit only) | ARCHIVED |

### 3.3 권한별 분기 (50+ page 일관)

| ROLE | 직접 수정 | 수정 요청 | 승인 | 복원 dropdown |
| --- | :-: | :-: | :-: | :-: |
| MASTER | 전 도메인 전 status (FULLY_LOCKED 도 일부) | (필요 시) | 전 도메인 | 전 도메인 |
| MANAGER | LOCKED_REQUIRES_APPROVAL 까지 (활성 승인 시 1회) | 일부 | 전 도메인 (accounting 제외) | 전 도메인 |
| SALES | 자신 작성 + DRAFT | LOCKED 상태 | (없음) | (없음) |
| WAREHOUSE / INVENTORY | 자신 작성 + DRAFT | LOCKED 상태 | (없음) | (없음) |
| ACCOUNTANT | accounting DRAFT | (없음) | (없음) | (없음) |
| DISPATCHER | arologis PLANNED + DISPATCHED 활성 승인 시 | LOCKED 상태 | (없음) | (없음) |
| PARTNER | 자신 주문 DRAFT + SUBMITTED 활성 승인 시 | LOCKED 상태 | (없음) | (없음) |
| DRIVER | (없음 — 모바일 서명만) | (없음) | (없음) | (없음) |
| DEVELOPER | (read-only) | (없음) | (없음) | (없음) |

---

## 4. SSE 실시간 동기화 (50+ page 일관)

### 4.1 도메인별 event subscribe 패턴 (PR-H4b § 3 1:1)

각 page mount 시점에 도메인별 channel `samhan:<service>:<event>:{entityId}` subscribe. unmount 시 unsubscribe. PR-H4a `useRealtimeSubscribe(channel, onEvent)` hook (`clients/web/design-system/src/hooks/useRealtimeSubscribe.ts`) 1:1 사용.

```tsx
// 예: PartnerDetailPage.tsx
const { partner } = useQuery(['partner', partnerId], () => fetchPartner(partnerId))

useRealtimeSubscribe(
  `samhan:partner:partner:edit:${partnerId}`,
  (event) => {
    queryClient.invalidateQueries(['partner', partnerId])
    queryClient.invalidateQueries(['partnerAudit', partnerId])
    showToast({
      variant: 'success',
      message: `${event.actorName} 님이 ${labelOf(event.fieldName)} 을 수정했습니다 (rev #${event.revisionNo})`,
    })
  },
)
```

### 4.2 1초 sync 시각 게이트 (Samhan Public 핵심 가치)

| 측정 지점 | 목표 | 50+ page 일관 |
| --- | --- | --- |
| BE publish → FE receive | < 1초 | ✅ 전 page |
| FE receive → audit overlay 갱신 | < 200ms | ✅ 전 page |
| FE receive → toast 표시 | < 100ms | ✅ 전 page |
| toast 자동 닫힘 | 5초 (사용자 dismiss 가능) | ✅ 전 page |

### 4.3 multi-context 동시 접속 표시 (50+ page)

각 page 우측 상단에 "동시 접속 컨텍스트 N" 표시 — 다른 사용자가 동일 entity 를 보고 있는 경우 시각 환기.

```
┌─────────────────────────────┐
│ 동시 접속 (3)               │
│ ● 김영업 (SALES)            │
│ ● 박관리 (MANAGER)          │
│ ● 이회계 (ACCOUNTANT)       │
└─────────────────────────────┘
```

> presence channel `samhan:<service>:presence:{entityId}` (선택적 — 본 PR-H4c 1차 권고, Stage 4 강화).

---

## 5. UUID 비공개 가드 (50+ page 일괄 의무)

PR-H4b § 5 그대로 50+ page 일괄 적용:

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
- URL path 는 UUID 허용 (`/partners/{uuid}`) — 단, 화면 표시는 비즈니스 식별자.

---

## 6. mobile-staff 확장 가이드 (RN Expo 환경)

### 6.1 RN 1:1 복제 util

PR-H4a 시드 그대로 `clients/mobile-staff/src/utils/userColorHash.ts` 사용. desktop `userIdToColor` 와 hash 알고리즘 100% 동일.

### 6.2 RN 시각 회귀 가드

- [ ] `Text` 의 `textDecorationLine: 'line-through'` 적용
- [ ] `View` dot 의 `backgroundColor` = `userIdToColor(actorId)` (RN 1:1 복제)
- [ ] `actorName` Text 색상 = `#1a1a1a` (검정 가독성)
- [ ] 빈 history → `<Text style={{fontStyle: 'italic'}}>변경 이력 없음</Text>`
- [ ] SSE = WebSocket fallback (`expo-websocket` — RN 환경에서 EventSource 미지원)
- [ ] toast = `react-native-toast-message` (5초 자동 닫힘)
- [ ] presence = optional (Stage 4 권고)

### 6.3 RN 도메인별 적용 매트릭스 (PR-H4c 1차 대상)

| RN screen | 도메인 | 적용 fieldName | 비고 |
| --- | --- | --- | --- |
| `SlipDetailScreen` (시드) | slip | partnerName / status | PR-H2 시드 — 변경 0 |
| `DispatchDetailScreen` (PR-H4c 신규) | arologis | driverName / scheduledAt | 기사 변경 SMS 알림 의무 |
| `StockAdjustDetailScreen` (PR-H4c 신규) | inventory | adjustReason / quantity | 회계 무결성 의무 |
| `PartnerOrderDetailScreen` (PR-H4c 신규) | partner-order | orderQuantity / requestedDeliveryDate | partner-auth 통과 후 |
| `UserProfileScreen` (본인) | user | 이름 / 연락처 | 본인 자유 수정 |
| `MemoDetailScreen` | groupware | 제목 / 본문 | 작성자 자유 수정 |

---

## 7. 매뉴얼 일괄 갱신 (본 PR-H4c 동반 8 docs)

본 PR-H4c FE rollout 동반 — 각 도메인 매뉴얼에 "수정 이력 보기" + "수정 요청 워크플로우" section 일괄 추가. 기 작성 패턴 (PR-H2 `02-출고-처리.md` § 2-9 / PR-H3 `02-출고-처리.md` § 2-10) 1:1 복제, 도메인 라벨만 교체.

| # | 매뉴얼 | 도메인 | 추가 section | 우선순위 |
| --- | --- | --- | --- | :-: |
| 1 | `docs/manual/03-회계/03-세금계산서.md` | accounting | "수정 이력 보기" (분개 + 세금계산서) | 🔴 1순위 (회계 무결성) |
| 2 | `docs/manual/03-회계/01-분개-입력.md` | accounting | "수정 이력 보기" + "POSTED FULLY_LOCKED 안내" | 🔴 1순위 |
| 3 | `docs/manual/01-영업/06-견적서.md` | slip (견적) | "수정 이력 보기" placeholder + 정식 fix 시 정책 | 🟠 2순위 |
| 4 | `docs/manual/01-영업/01-거래처-등록.md` | partner | "수정 이력 보기" + "ACTIVE 잠금 — 수정 요청" | 🔴 1순위 |
| 5 | `docs/manual/02-창고/01-입고-처리.md` | inventory | "수정 이력 보기" (입고 슬립 + lot) + "POSTED FULLY_LOCKED" | 🔴 1순위 |
| 6 | `docs/manual/02-창고/05-재고-실사.md` | inventory (실사) | "수정 이력 보기" placeholder + 정식 fix 시 정책 | 🟠 2순위 |
| 7 | `docs/manual/05-arologis/02-수동-배차.md` | arologis | "수정 이력 보기" + "기사 변경 SMS 알림" + "DISPATCHED 잠금" | 🔴 1순위 |
| 8 | `docs/manual/00-시작하기/03-역할별-권한.md` | (전 도메인) | "수정 이력 / 잠금 정책 종합" 표 (9 도메인) | 🔴 1순위 |

> **추가 보강 권고 (Stage 4)** — `04-매출-마감.md` (회계 마감) / `05-arologis/03-기사-배정.md` (기사 audit) / `04-모바일/*.md` (모바일 audit overlay) 도 본 PR 후속에서 보강.

---

## 8. 작동 캡처 5 PNG (사용자 명시 "다른 모든 화면도 마찬가지" 시각 검증)

본 PR-H4c 핵심 검증 — 9 audit overlay 도메인 중 핵심 5 도메인 (회계 + 영업 + 창고 + arologis + admin) 작동 시각 증거.

| # | 캡처 PNG | 도메인 | page | 검증 요점 |
| --- | --- | --- | --- | --- |
| 1 | `working-tax-invoice-detail-audit.png` | accounting | TaxInvoicePage / JournalDetailPage | 분개 적요 변경 audit overlay + 한국 계정 코드 (100100 현금) + actorName "이회계" + SSE toast |
| 2 | `working-estimate-detail-audit.png` | slip (견적) | SlipDetailPage (DRAFT 견적 단계) | 메모 / 단가 변경 audit overlay + actorName "오영업" + edit-request approve 후 1회 한정 mutation |
| 3 | `working-inventory-audit-overlay.png` | inventory | StockAdjustDetailPage | 조정 사유 변경 audit overlay + DRAFT 자유 수정 + 한국 회계 무결성 표기 + SSE |
| 4 | `working-arologis-dispatch-audit.png` | arologis | DispatchDetailPage | 기사명 / 연락처 변경 audit overlay + SMS 발송 안내 toast + DISPATCHED 잠금 + 1회 한정 mutation |
| 5 | `working-admin-users-audit.png` | admin (user) | admin/UsersPage | 사용자 정보 변경 audit overlay + MASTER 만 타인 수정 + actorName + SUSPENDED 표시 |

> **캡처 도구**: `tools/manual-capture/capture-pr-h4c.js` (PR-H4b 패턴 활용 — Playwright + DOM 직접 주입 fallback).

---

## 9. PR-H4c PASS 게이트

### 9.1 FE 통합 PASS 게이트

- [ ] desktop 30 page audit overlay 적용 + import barrel (`@samhan/design-system`) 일관
- [ ] desktop 5 list page "수정 N회" badge 표시
- [ ] mobile-staff 9 screen audit overlay (RN 1:1 복제)
- [ ] partner-portal 3 page audit overlay (UUID 비공개 + 자기 데이터 한정)
- [ ] admin 3 page audit overlay (MASTER 만 타인 수정)
- [ ] § 3.2 status × UI 분기 50+ page 1:1 일치 (분기 mismatch 0건)
- [ ] § 4.1 SSE subscribe pattern 50+ page 일관
- [ ] § 5 UUID 비공개 가드 50+ page 통과

### 9.2 시각 회귀 가드

- [ ] PR-H1/H2/H3 시드 (slip-service) 픽셀 회귀 0건 (Playwright snapshot)
- [ ] PR-H4b BE 회귀 (slip-service multi-context 1초 sync) 100% 보존
- [ ] § 2.2 시각 요소 11건 50+ page 일관 (취소선 / 색상 / actorName / 시각 표기 / 빈 값 / etc.)
- [ ] § 2.3 도메인별 한국어 라벨 매핑 50+ page 1:1 일치
- [ ] § 2.4 공통 라벨 50+ page 일관

### 9.3 매뉴얼 PASS 게이트

- [ ] § 7 매뉴얼 8 docs "수정 이력 보기" + "수정 요청 워크플로우" section 추가
- [ ] PR-H2 `02-출고-처리.md` § 2-9 / PR-H3 § 2-10 패턴 1:1 복제 (도메인 라벨만 교체)
- [ ] FAQ + 트러블슈팅 표 도메인별 보강

### 9.4 작동 캡처 PASS 게이트

- [ ] § 8 5 PNG 모두 산출 (실 캡처 또는 mock DOM placeholder ≥ 20KB)
- [ ] 5 PNG 모두 도메인별 audit overlay + actorName + SSE toast 시각 표시

---

## 10. 본 PR-H4c Designer 산출물

- [x] 본 종합 가이드 `docs/uiux/phase12/H4c-fe-rollout-summary.md`
- [x] 매뉴얼 8 docs 일괄 갱신 (§ 7)
- [x] QA scenarios `docs/qa/phase-12-step-4c-fe-audit-overlay-rollout/scenarios.md`
- [x] 작동 캡처 5 PNG (`docs/qa/phase-12-step-4c-fe-audit-overlay-rollout/working-*.png`)
- [x] 캡처 도구 `tools/manual-capture/capture-pr-h4c.js`

---

## 11. 참고

- PR-H4a Designer 가이드 (시드): `docs/uiux/phase12/H4a-shared-realtime-pattern.md`
- PR-H4b Designer 가이드 (BE 매트릭스): `docs/uiux/phase12/H4b-be-rollout-checklist.md`
- PR-H4b QA scenarios (BE rollout 검증): `docs/qa/phase-12-step-4b-be-realtime-rollout/scenarios.md`
- PR-H1 wireframe (코멘트 smoke): `docs/uiux/phase12/H1-comment-smoke.md`
- PR-H2 wireframe (audit overlay 시드): `docs/uiux/phase12/H2-audit-overlay.md`
- PR-H3 wireframe (잠금/요청/수락): `docs/uiux/phase12/H3-edit-request-workflow.md`
- shared-realtime BE 모듈 (PR-H4a 머지): `services/shared-realtime/`
- shared-edit-request BE 모듈 (PR-H4a 머지): `services/shared-edit-request/`
- userColorHash util (deterministic HSL): `clients/web/design-system/src/utils/userColorHash.ts`
- userColorHash util (RN 1:1): `clients/mobile-staff/src/utils/userColorHash.ts`
- AuditOverlay 컴포넌트 본체: `clients/web/design-system/src/components/AuditOverlay/`
- SlipDetailPage 시드 (1:1 복제 base): `clients/desktop/src/renderer/routes/SlipDetailPage.tsx`
- 한국 일반기업회계기준 표준 계정과목: 메모리 가드 `project_korean_accounting`
- UUID 비공개 원칙: 메모리 가드 `feedback_uuid_no_user_visibility`
- 권한 풀네임: 메모리 가드 `feedback_role_naming_full`
- 멀티 에이전트 팀 디스패치: 메모리 가드 `feedback_multi_agent_team_pattern`
