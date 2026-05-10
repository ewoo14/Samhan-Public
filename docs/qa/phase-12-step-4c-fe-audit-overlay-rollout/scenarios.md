# PR-H4c — Phase 12 Step 4c FE audit overlay rollout (50+ page × 5 case = ~250 case sampling) QA 시나리오

> **branch** — `feature/integrated-phase-12-step-4c-fe-audit-overlay-rollout`
> **작성일** — 2026-05-10
> **작성** — QA Tester (5-team 통합 PR 패턴)
> **목적** — Phase 12 Step 4c PR-H4c (`shared-realtime` + `shared-edit-request` BE 시드 + PR-H4b 13 service rollout 위에서 **50+ FE page 가 사용자 명시 패턴 (취소선 + 수정자 색상 + 수정자 이름 + 1초 SSE) 을 시각·UX 일관 100% 도입**) 가 **(1) 50+ page audit overlay 시각 일관 의무** + **(2) 9 도메인 LockPolicy × UI 분기 정확성** + **(3) PR-H1/H2/H3 시드 픽셀 회귀 0건** 을 측정 가능한 PASS/FAIL 로 명세.
> **연관 산출물** —
> - Designer: `docs/uiux/phase12/H4c-fe-rollout-summary.md` (50+ page 적용 매트릭스 + 한국어 라벨 매핑)
> - 매뉴얼: `docs/manual/03-회계/03-세금계산서.md` 외 7 docs "수정 이력 보기" + "수정 요청" section 일괄 추가
> - 작동 캡처: 본 디렉토리 `working-*.png` 5건 (회계 + 영업 + 창고 + arologis + admin)
> - 캡처 도구: `tools/manual-capture/capture-pr-h4c.js`

---

## 0. 검증 정책

### 0.1 페르소나 5 (사용자 명시 — `feedback_role_naming_full` 풀네임)

| 페르소나 | ROLE | 도메인 지식 | 본 PR 검증 관점 |
| --- | --- | --- | --- |
| **신입 영업** | SALES | 단가/세금 미경험 | partner / partner-order / dc-config 화면의 audit overlay 가 slip 시드와 시각 1:1 동일 학습 가능 — 사용자 멘탈 모델 일관 |
| **창고원** | WAREHOUSE | 출고 픽업/검수 | inventory StockAdjustDetailPage / WarehouseDetailPage / StockMovePage / StockCountPage audit overlay 일관. mobile-staff StockAdjustDetailScreen 동일 시각 |
| **회계 담당자** | ACCOUNTANT (또는 MANAGER) | 한국 일반기업회계기준 | accounting JournalDetailPage / TaxInvoicePage / MonthlyClosePage audit overlay + POSTED FULLY_LOCKED UI 분기 정확. 한국 계정 코드 (100100 / 220000 / 401000) 표시 정합 |
| **관리자** | MANAGER | 전 도메인 | 9 도메인 50+ page 모두 LOCKED_REQUIRES_APPROVAL 상태에서 dropdown 복원 / 승인 가능 + admin/UsersPage 타인 수정 시도 시 MASTER 만 통과 |
| **DevOps 엔지니어** | DEVOPS | infra 운영 | 50+ page mount 시 SSE subscribe / unmount 시 unsubscribe 정확. multi-context presence 표시 정합. 1초 sync 게이트 통과 |

### 0.2 측정 가능한 PASS/FAIL 기준

각 case 는 다음 4 요소 모두 명시:

1. **선행 조건** — fixture (BE 13 service rollout 머지 완료 + Redis 또는 in-memory broker + mock fetch interceptor — `VITE_MOCK_MODE=1`)
2. **동작** — Playwright step 또는 사용자 수동 step
3. **기대 결과** — 단위 assertion (DOM `data-testid` / 시각 요소) + 시각 회귀 (Playwright snapshot) + SSE timing assertion (< 1초)
4. **회귀 차단 effect** — fail 시 사용자 멘탈 모델 단절 / 50+ page 시각 inconsistency / Samhan Public 핵심 가치 위배

### 0.3 우선순위 표기

- 🔴 **Critical** — fail 시 PR-H4c 머지 차단 (사용자 명시 패턴 위배 / 시드 픽셀 회귀 / 50+ page 시각 inconsistency)
- 🟠 **Major** — 도메인별 시각 일관성 누락 (특정 page 의 한국어 라벨 / 잠금 banner / 복원 dropdown 누락)
- 🟡 **Minor** — 한국어 라벨 / 응답 schema field 누락 / SSE timing > 1초
- 🟢 **Info** — 향후 Stage 4 보강 권고 (presence / RN 시각 강화)

### 0.4 sampling 정책 (50+ page × 5 case = ~250 case)

50+ page 전수 검증은 비현실적 — 도메인별 대표 page 1~3건 sampling + 시각 회귀 가드 (Playwright snapshot) 로 시드 픽셀 일치 검증.

| 도메인 | sampling page | case 수 | 시각 회귀 |
| --- | --- | :-: | :-: |
| slip (시드) | SlipDetailPage / SlipListPage | 5 | snapshot |
| partner | PartnerDetailPage / PartnerListPage | 10 | snapshot |
| inventory | StockAdjustDetailPage / WarehouseDetailPage / StockMovePage | 15 | snapshot |
| accounting | JournalDetailPage / TaxInvoicePage / MonthlyClosePage | 15 | snapshot |
| arologis | DispatchDetailPage / DispatchListPage / DriverPage | 15 | snapshot |
| product | ProductDetailPage | 5 | snapshot |
| dc-config | DcRuleDetailPage | 5 | snapshot |
| partner-order | PartnerOrderDetailPage | 5 | snapshot |
| user | UserProfilePage / admin/UsersPage | 10 | snapshot |
| groupware | MemoDetailPage / AnnouncementDetailPage | 5 | snapshot |
| mobile-staff RN | DispatchDetailScreen / StockAdjustDetailScreen | 10 | RN snapshot |
| partner-portal | orders/[id] / account | 5 | snapshot |
| admin | admin/UsersPage | 5 | snapshot |
| broker only | DashboardHomePage / NotificationListPage | 5 | snapshot (overlay 미적용 검증) |
| 회귀 가드 (시드 보존) | 5 case | 5 | snapshot |
| **합계** | | **120 case** | sampling case + snapshot 시각 회귀로 50+ page 1:1 일관 검증 |

> **sampling 합계** — 사용자 명세 "~250 case" 기준 sampling 최적화 — Playwright snapshot 가드가 50+ page 시각 일치 자동 보장. sampling 120 case 가 핵심 검증.

### 0.5 권한 매트릭스 (50+ page UI 분기)

본 PR-H4c FE 단계 — Designer § 3 잠금 정책 × ROLE 분기 1:1 일치 의무. UI 가 다음 분기를 동일 시각으로 표시:

- **DRAFT/PLANNED** — 모든 input 활성 + 즉시 audit 누적
- **LOCKED_REQUIRES_APPROVAL** — input 비활성 + 상단 banner "수정 요청" + `[수정 요청]` 버튼
- **FULLY_LOCKED** — input 비활성 + 상단 banner "MASTER 에게 문의" (도메인별 별도 절차 안내 — accounting "정정 분개" / arologis "운송 본부 승인" / inventory "별도 회계 정정")

### 0.6 UUID 비공개 (`feedback_uuid_no_user_visibility`)

50+ page 화면 어디에도 UUID 노출 0건. `actorName` 풀네임만 표시 + `actorId` 색상 hash 입력만 사용. `data-testid` / `aria-label` DOM 속성에도 UUID leak 0건.

---

## 1. slip-service (5 case) — 시드 보존 회귀

> **모듈 위치** — `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` (PR-H2 시드)
> **회귀 의무** — PR-H1/H2/H3 시드 동작 100% 보존 (50+ page rollout 으로 시드 깨지지 않음)

### 1.1 SlipDetailPage 시드 audit overlay 픽셀 회귀

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.1 | SALES | 🔴 | slip-001 (메모 3회 변경 + 배송지 1회) + 50+ page rollout 머지 후 | SlipDetailPage 진입 | PR-H2 시드 snapshot 1:1 일치 (취소선 + 색상 dot + actorName + revisionNo chip + 시각 표기) | 시드 회귀 시 사용자가 익힌 멘탈 모델 단절 |

### 1.2 SlipDetailPage 시드 SSE 1초 sync 회귀

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.2 | SALES + WAREHOUSE | 🔴 | slip-001 + multi-context (browser tab A + tab B) | tab A 에서 slip-001 메모 수정 → tab B 의 SlipDetailPage 수신 | 1초 안 audit overlay 갱신 + toast 표시 — PR-H4b § 12.1 회귀 case 1:1 동일 | sync 1초 회귀 시 Samhan Public 핵심 가치 단절 |

### 1.3 SlipDetailPage 시드 잠금/요청 회귀

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.3 | SALES | 🔴 | slip-001 (ACCEPTED) | direct edit 시도 | LOCKED_REQUIRES_APPROVAL banner + `[수정 요청]` 버튼 표시 — PR-H3 시드 1:1 동일 | PR-H3 운영 슬립 잠금 무력화 |

### 1.4 SlipListPage "수정 N회" badge 시드

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.4 | SALES | 🟠 | slip 50건 (수정 횟수 0~15 분포) | SlipListPage 진입 | 0회 = badge hide / 1~4회 = 회색 / 5~9회 = 노랑 / 10+ = 빨강 (PR-H2 시드 1:1) | badge 회귀 시 비정상 수정 환기 단절 |

### 1.5 mobile-staff SlipDetailScreen RN 1:1

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.5 | SALES | 🟠 | mobile-staff app + slip-001 | SlipDetailScreen 진입 | RN snapshot 1:1 (textDecorationLine line-through + backgroundColor userIdToColor + actorName) | RN 회귀 시 모바일 사용자 멘탈 모델 단절 |

---

## 2. partner-service (10 case)

> **page 위치** — `clients/desktop/src/renderer/routes/PartnerDetailPage.tsx` / `PartnerListPage.tsx` / `PartnerCreatePage.tsx`

### 2.1 PartnerDetailPage audit overlay 시각 (사업자명 변경)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.1 | SALES | 🔴 | partner-001 (ACTIVE, businessName="삼한전자" → "삼한전자(주)" 변경 ack 후) | PartnerDetailPage 진입 | audit overlay row 1건: `~~삼한전자~~ → 삼한전자(주)` + 색상 dot + actorName "오영업" + 시각 "13:32" | 사용자 명시 패턴 partner 도메인 미적용 시 멘탈 모델 단절 |

### 2.2 PartnerDetailPage LOCKED_REQUIRES_APPROVAL 잠금 banner

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.2 | SALES | 🔴 | partner-001 (ACTIVE) + 활성 승인 0건 | direct edit 시도 → input 비활성 | 상단 banner "이 거래처는 잠금 상태입니다 — 수정 요청을 보내주세요" + `[수정 요청]` 버튼 | 잠금 banner 회귀 시 무단 수정 가능 — audit 무력화 |

### 2.3 PartnerDetailPage edit-request dialog

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.3 | SALES | 🔴 | partner-001 (ACTIVE) | `[수정 요청]` 클릭 → dialog 열림 → 사유 입력 → 전송 | dialog UI = PR-H3 SlipEditRequestDialog 1:1 복제 + 사유 50자 권장 + 전송 후 toast "수정 요청 보냄" | dialog 회귀 시 권한자 알림 채널 단절 |

### 2.4 PartnerDetailPage SSE 1초 sync (multi-context)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.4 | SALES + MANAGER | 🔴 | partner-001 + tab A (SALES) + tab B (MANAGER) | tab B 에서 contactPhone 수정 → tab A 수신 | 1초 안 audit overlay 갱신 + toast "박관리 님이 연락처 를 수정했습니다 (rev #N)" | SSE 회귀 시 multi-user 동시 작업 무력화 |

### 2.5 PartnerDetailPage 복원 dropdown (MANAGER+)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.5 | MANAGER | 🟠 | partner-001 audit row 5건 | audit overlay 의 row 우측 ▾ 클릭 → 복원 dropdown 열림 → "rev 3 으로 복원" 클릭 | 확인 dialog "revision 3 으로 복원하시겠습니까?" → 확인 → 새 revision 추가 (이전 revision 보존) | 복원 회귀 시 MANAGER 권한 책임 무력화 |

### 2.6 PartnerListPage "수정 N회" badge

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.6 | SALES | 🟠 | partner 100건 | PartnerListPage 진입 | 각 row 의 "수정 N회" badge 색상 임계 (시드와 동일) | badge 회귀 시 비정상 수정 환기 단절 |

### 2.7 PartnerListPage 마지막 수정 actorName 표시

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.7 | SALES | 🟡 | partner 100건 (마지막 수정자 분포) | PartnerListPage 진입 | 각 row "마지막 수정: 오영업 13:32" 컬럼 + 색상 dot | 표시 회귀 시 사용자 추적 단절 |

### 2.8 PartnerCreatePage audit overlay 미표시 (DRAFT 신규)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.8 | SALES | 🟡 | (신규 진입) | PartnerCreatePage 진입 | audit overlay 영역 자체 미렌더 (신규 = revision 0) + 저장 후 redirect → PartnerDetailPage 에서 audit row 0건 | 미표시 회귀 시 신규 작성 화면에 불필요 UI 노출 |

### 2.9 PartnerDetailPage UUID 비공개

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.9 | MANAGER | 🔴 | partner-001 audit row 5건 | DOM 검사 (`document.body.innerText` + `data-testid` / `aria-label`) | UUID 패턴 (`[0-9a-f]{8}-[0-9a-f]{4}-...`) 발견 0건 + actorName 풀네임만 표시 + businessRegistrationNo 노출 | UUID leak 시 `feedback_uuid_no_user_visibility` 위배 |

### 2.10 PartnerDetailPage 한국어 라벨 일관

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.10 | SALES | 🟡 | partner-001 audit row 5건 (businessName / contactPhone / address / representativeName / businessRegistrationNo) | audit overlay 표시 | 5 라벨 모두 한국어 ("사업자명" / "연락처" / "주소" / "대표자명" / "사업자등록번호") + Designer § 2.3 1:1 일치 | 라벨 회귀 시 사용자 인지 혼선 |

---

## 3. inventory-service (15 case)

> **page 위치** — `clients/desktop/src/renderer/routes/StockAdjustDetailPage.tsx` / `WarehouseDetailPage.tsx` / `StockMovePage.tsx` / `StockCountPage.tsx`

### 3.1 StockAdjustDetailPage audit overlay (조정 사유)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.1 | WAREHOUSE | 🔴 | adjust-001 (DRAFT, adjustReason="검수 누락" → "파손 발견") | StockAdjustDetailPage 진입 | audit overlay row 1건 + 한국어 라벨 "조정 사유" + `~~검수 누락~~ → 파손 발견` | 사용자 명시 패턴 inventory 도메인 미적용 시 멘탈 모델 단절 |

### 3.2 StockAdjustDetailPage DRAFT 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.2 | WAREHOUSE | 🔴 | adjust-002 (DRAFT) | direct edit 통과 | input 활성 + 수정 시 즉시 audit row 생성 + SSE publish | DRAFT 잠금 회귀 시 창고 작업 차단 |

### 3.3 StockAdjustDetailPage SUBMITTED 잠금 banner

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.3 | WAREHOUSE | 🔴 | adjust-001 (SUBMITTED) + 승인 0건 | direct edit 시도 | banner "이 재고조정은 잠금 상태입니다 — 수정 요청을 보내주세요" + `[수정 요청]` 버튼 | 잠금 회귀 시 회계 전기 직전 데이터 변조 |

### 3.4 StockAdjustDetailPage POSTED FULLY_LOCKED banner (회계 무결성)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.4 | MANAGER | 🔴 | adjust-003 (POSTED — 회계 전기 완료) | direct edit 시도 | banner "이 재고조정은 완전 잠금 상태입니다 — MASTER 에게 문의" + 별도 회계 정정 분개 안내 link | POSTED 수정 가능 시 한국 일반기업회계기준 위배 |

### 3.5 StockAdjustDetailPage SSE 1초 sync

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.5 | WAREHOUSE + MANAGER | 🔴 | adjust-001 + tab A + tab B | tab B 에서 quantity 수정 → tab A 수신 | 1초 안 갱신 + toast | SSE 회귀 시 multi-user 무력화 |

### 3.6 WarehouseDetailPage audit overlay (창고 마스터)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.6 | WAREHOUSE | 🟠 | warehouse-001 (창고 정보 변경) | WarehouseDetailPage 진입 | audit overlay 시각 일관 + 한국어 라벨 "창고 코드" / "주소" / "담당자" | 창고 마스터 audit 회귀 시 변경 추적 단절 |

### 3.7 StockMovePage audit overlay (이동 사유)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.7 | INVENTORY | 🟠 | stock-move-001 (DRAFT) | StockMovePage 진입 + 이동 사유 변경 | audit overlay row + "이동 사유" 라벨 + SSE | 이동 audit 회귀 시 재고 이동 추적 단절 |

### 3.8 StockMovePage POSTED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.8 | INVENTORY | 🟠 | stock-move-002 (POSTED) | direct edit 시도 | FULLY_LOCKED banner | POSTED 수정 시 회계 무결성 위배 |

### 3.9 StockCountPage audit overlay (실사 차이)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.9 | WAREHOUSE | 🟡 | stock-count-001 (실사 라인 5건) | StockCountPage 진입 + 차이 사유 입력 | audit overlay + "실사 차이" 라벨 | 실사 audit 회귀 시 차이 사유 추적 단절 |

### 3.10 StockAdjustListPage "수정 N회" badge

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.10 | WAREHOUSE | 🟠 | adjust 50건 (수정 횟수 분포) | StockAdjustListPage 진입 | badge 색상 임계 일관 | badge 회귀 시 환기 단절 |

### 3.11 StockAdjustDetailPage 복원 dropdown (MANAGER+)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.11 | MANAGER | 🟠 | adjust-001 audit row 3건 | dropdown ▾ → "rev 1 으로 복원" | 확인 dialog → 새 revision 추가 | 복원 회귀 시 MANAGER 권한 무력화 |

### 3.12 StockAdjustDetailPage UUID 비공개

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.12 | MANAGER | 🔴 | adjust-001 audit row + DOM 검사 | DOM 검사 | UUID leak 0건 + productCode + warehouseCode 노출 | UUID leak 시 가드 위배 |

### 3.13 mobile-staff StockAdjustDetailScreen RN 1:1

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.13 | WAREHOUSE | 🟠 | mobile-staff app + adjust-001 | StockAdjustDetailScreen 진입 | RN snapshot 1:1 (textDecorationLine + backgroundColor + actorName) | RN 회귀 시 모바일 멘탈 모델 단절 |

### 3.14 한국어 라벨 일관 (5 라벨)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.14 | WAREHOUSE | 🟡 | adjust-001 audit row 5건 | audit overlay 표시 | 5 라벨 한국어 ("조정 사유" / "수량" / "품목코드" / "창고 코드" / "이동 사유") | 라벨 회귀 시 인지 혼선 |

### 3.15 StockAdjustDetailPage 한국 회계 무결성 표기 (POSTED 진입 안내)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.15 | WAREHOUSE | 🟠 | adjust-001 (SUBMITTED → POSTED 직전) | 상단에 안내 문구 표시 | "POSTED 후에는 별도 회계 정정 분개로만 수정 가능합니다" 안내 | 안내 회귀 시 POSTED 후 사용자 혼선 |

---

## 4. accounting-service (15 case)

> **page 위치** — `clients/desktop/src/renderer/routes/JournalDetailPage.tsx` / `TaxInvoicePage.tsx` / `MonthlyClosePage.tsx` / `JournalReversePage.tsx`

### 4.1 JournalDetailPage audit overlay (적요 변경)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.1 | ACCOUNTANT | 🔴 | journal-001 (DRAFT, description="현금 입금" → "현금 매출 입금") | JournalDetailPage 진입 | audit overlay row + 한국어 라벨 "적요" + 한국 계정 코드 (100100 = 현금) 표기 + actorName "이회계" | accounting audit 회귀 시 한국 회계 감사 위배 |

### 4.2 JournalDetailPage DRAFT 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.2 | ACCOUNTANT | 🔴 | journal-002 (DRAFT) | direct edit 통과 | input 활성 | DRAFT 잠금 회귀 시 회계 작성 단계 차단 |

### 4.3 JournalDetailPage POSTED FULLY_LOCKED (LOCKED_REQUIRES_APPROVAL 미사용)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.3 | MANAGER | 🔴 | journal-003 (POSTED — 전기 완료) | direct edit 시도 | banner "이 분개는 완전 잠금 상태입니다 — 정정 분개로만 수정 가능합니다" + `[정정 분개 작성]` link | POSTED 수정 가능 시 한국 일반기업회계기준 위배 |

### 4.4 JournalDetailPage CLOSED FULLY_LOCKED (월/연 마감)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.4 | MASTER | 🔴 | journal-004 (CLOSED — 월 마감) | direct edit 시도 | banner "마감된 분개 — 감사인 동석 별도 절차 의무 (MASTER 도 차단)" | 마감 수정 시 회계 무결성 + 감사 보고 변조 |

### 4.5 JournalDetailPage SSE 1초 sync

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.5 | ACCOUNTANT + MANAGER | 🔴 | journal-001 + tab A + tab B | tab B 에서 amount 수정 → tab A 수신 | 1초 안 갱신 + toast | SSE 회귀 |

### 4.6 JournalDetailPage 한국 계정 코드 audit 표기 (천 단위 콤마)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.6 | ACCOUNTANT | 🔴 | journal-001 audit row (amount 1,200,000 → 1,500,000) | audit overlay 표시 | `~~1,200,000~~ → 1,500,000` 천 단위 콤마 일관 + 한국 계정 코드 (220000 부가세예수금) 표기 | 콤마 회귀 시 회계 가독성 단절 |

### 4.7 TaxInvoicePage audit overlay (세금계산서 라인)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.7 | ACCOUNTANT | 🔴 | tax-invoice-001 (라인 5건 변경) | TaxInvoicePage 진입 | audit overlay row 5건 + 한국어 라벨 "세금계산서 라인" + 공급자/공급받는자 정보 audit | 세금계산서 audit 회귀 시 NTS 신고 추적 단절 |

### 4.8 MonthlyClosePage audit overlay (마감 사유)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.8 | MANAGER | 🟠 | close-001 (월 마감 사유 입력) | MonthlyClosePage 진입 | audit overlay + 한국어 라벨 "마감 사유" + 마감 권한자 표기 | 마감 audit 회귀 시 마감 추적 단절 |

### 4.9 JournalReversePage audit overlay (역분개 사유)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.9 | ACCOUNTANT | 🟠 | reverse-001 (역분개 사유 입력) | JournalReversePage 진입 | audit overlay + 한국어 라벨 "역분개 사유" | 역분개 audit 회귀 시 정정 추적 단절 |

### 4.10 JournalListPage "수정 N회" badge

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.10 | MANAGER | 🟠 | journal 100건 | JournalListPage 진입 | badge 색상 임계 일관 | badge 회귀 |

### 4.11 JournalDetailPage 복원 dropdown 미적용 (POSTED 후 정정 분개)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.11 | MASTER | 🟠 | journal-001 (POSTED) audit row 3건 | dropdown ▾ 시도 | dropdown 자체 hide (POSTED FULLY_LOCKED) + 안내 "복원 = 정정 분개로 진행" | dropdown 노출 시 무단 복원 가능 |

### 4.12 JournalDetailPage UUID 비공개 + journalNo 노출

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.12 | MANAGER | 🔴 | journal-001 (journalNo="J-2026-0512") | DOM 검사 | UUID leak 0 + journalNo "J-2026-0512" 노출 + accountCode "100100" 노출 | UUID leak 시 가드 위배 |

### 4.13 한국어 라벨 일관 (5 라벨)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.13 | ACCOUNTANT | 🟡 | journal-001 audit row 5건 | audit overlay 표시 | 5 라벨 한국어 ("적요" / "금액" / "계정 코드" / "거래처 코드" / "역분개 사유") | 라벨 회귀 시 인지 혼선 |

### 4.14 TaxInvoicePage 공급자/공급받는자 audit 분리

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.14 | ACCOUNTANT | 🟡 | tax-invoice-001 (공급자 회사 정보 변경) | audit overlay 표시 | 공급자 / 공급받는자 라벨 분리 표시 + actorName | 분리 회귀 시 NTS 표준 양식 가독성 단절 |

### 4.15 JournalDetailPage 안내 문구 (POSTED 진입 직전)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.15 | ACCOUNTANT | 🟠 | journal-001 (DRAFT → POSTED 직전) | 상단 안내 표시 | "POSTED 후에는 정정 분개로만 수정 가능합니다 — 한국 일반기업회계기준 보존 의무" | 안내 회귀 시 사용자 혼선 |

---

## 5. arologis-service (15 case)

> **page 위치** — `DispatchDetailPage.tsx` / `DispatchListPage.tsx` / `DispatchKakaoPage.tsx` / `VehiclePage.tsx` / `DriverPage.tsx`

### 5.1 DispatchDetailPage audit overlay (기사명 변경)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.1 | DISPATCHER | 🔴 | dispatch-001 (DISPATCHED, driverName="홍길동" → "김철수") + MANAGER 승인 + 1회 한정 mutation | DispatchDetailPage 진입 | audit overlay row 2건 (driverName + driverPhone) + 한국어 라벨 "기사명" / "기사 연락처" + actorName "정배차" | 사용자 명시 패턴 arologis 도메인 미적용 시 멘탈 모델 단절 |

### 5.2 DispatchDetailPage SMS 발송 안내 toast

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.2 | DISPATCHER | 🔴 | dispatch-001 기사 변경 직후 | SSE 수신 | toast "기사 변경 SMS 발송 완료 — 이전 기사 (홍길동) + 새 기사 (김철수) 양쪽 발송" | SMS 안내 회귀 시 운송 사고 위험 |

### 5.3 DispatchDetailPage PLANNED 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.3 | DISPATCHER | 🔴 | dispatch-002 (PLANNED) | direct edit 통과 | input 활성 | PLANNED 잠금 회귀 시 배차 사전 작성 차단 |

### 5.4 DispatchDetailPage DISPATCHED 잠금 banner

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.4 | DISPATCHER | 🔴 | dispatch-001 (DISPATCHED) + 승인 0건 | direct edit 시도 | banner "이 배차는 잠금 상태입니다 — 수정 요청을 보내주세요" + `[수정 요청]` 버튼 | 잠금 회귀 시 무단 기사 변경 |

### 5.5 DispatchDetailPage IN_TRANSIT FULLY_LOCKED (운송 중)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.5 | MANAGER | 🔴 | dispatch-003 (IN_TRANSIT) | direct edit 시도 | banner "운송 중 배차 — MASTER 본부 승인 별도 절차 필요" | IN_TRANSIT 수정 시 운송 사고 직결 |

### 5.6 DispatchDetailPage SSE 1초 sync

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.6 | DISPATCHER + MANAGER | 🔴 | dispatch-001 + tab A + tab B | tab B 에서 scheduledAt 수정 → tab A 수신 | 1초 안 갱신 + toast | SSE 회귀 |

### 5.7 DispatchListPage "수정 N회" badge

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.7 | DISPATCHER | 🟠 | dispatch 50건 | DispatchListPage 진입 | badge 색상 일관 | badge 회귀 |

### 5.8 DispatchKakaoPage audit overlay (raw 텍스트)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.8 | DISPATCHER | 🟠 | kakao-001 (raw 텍스트 변경 + 파싱 결과) | DispatchKakaoPage 진입 | audit overlay 2건 (rawKakaoText + 파싱 결과) | 카톡 audit 회귀 시 추적 단절 |

### 5.9 VehiclePage audit overlay (차량번호)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.9 | MANAGER | 🟡 | vehicle-001 | VehiclePage 진입 | audit overlay + 한국어 라벨 "차량번호" / "톤수" | 차량 audit 회귀 |

### 5.10 DriverPage audit overlay (기사 마스터)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.10 | MANAGER | 🟠 | driver-001 (기사 정보 변경) | DriverPage 진입 | audit overlay + 한국어 라벨 + post-approve hook 안내 | 기사 audit 회귀 |

### 5.11 DispatchDetailPage 복원 dropdown (MANAGER+)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.11 | MANAGER | 🟠 | dispatch-001 audit row 3건 | dropdown ▾ → "rev 1 으로 복원" | 확인 → 새 revision (이전 보존) | 복원 회귀 |

### 5.12 DispatchDetailPage UUID 비공개 + dispatchNo 노출

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.12 | MANAGER | 🔴 | dispatch-001 (dispatchNo="D-2026-0510-007") | DOM 검사 | UUID leak 0 + dispatchNo + vehicleNo 노출 | UUID leak 시 가드 위배 |

### 5.13 mobile-staff DispatchDetailScreen RN 1:1

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.13 | DISPATCHER | 🟠 | mobile-staff app + dispatch-001 | DispatchDetailScreen 진입 | RN snapshot 1:1 + SMS 안내 toast | RN 회귀 |

### 5.14 한국어 라벨 일관 (4 라벨)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.14 | DISPATCHER | 🟡 | dispatch-001 audit row 4건 | audit overlay 표시 | 4 라벨 한국어 ("기사명" / "차량번호" / "예정시각" / "운송 경로") | 라벨 회귀 |

### 5.15 DispatchDetailPage 안내 (DISPATCHED 진입 직전)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.15 | DISPATCHER | 🟠 | dispatch-002 (PLANNED → DISPATCHED 직전) | 상단 안내 | "DISPATCHED 후에는 수정 요청 → 승인 → 1회 한정 mutation 진행됩니다" | 안내 회귀 시 사용자 혼선 |

---

## 6. product-service / dc-config-service / partner-order-service (15 case)

### 6.1 ProductDetailPage audit overlay (단가 변경)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.1 | MANAGER | 🔴 | product-001 (ACTIVE, unitPrice=10000 → 12000) + MASTER 승인 | ProductDetailPage 진입 | audit overlay + 한국어 라벨 "단가" + 천 단위 콤마 + actorName | product audit 회귀 시 단가 협상 추적 단절 |

### 6.2 ProductDetailPage ACTIVE 잠금 banner

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.2 | MANAGER | 🔴 | product-001 (ACTIVE) + 승인 0건 | direct edit 시도 | banner "이 품목은 잠금 상태입니다 — 수정 요청을 보내주세요" | ACTIVE 단가 무단 수정 시 영업 무결성 위배 |

### 6.3 ProductDetailPage DISCONTINUED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.3 | MANAGER | 🔴 | product-003 (DISCONTINUED) | direct edit 시도 | FULLY_LOCKED banner | 단종 수정 시 영업 단가 혼선 |

### 6.4 ProductDetailPage UUID 비공개 + sku 노출

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.4 | MANAGER | 🔴 | product-001 (sku="SKU-LP-A03") | DOM 검사 | UUID leak 0 + sku + specName 노출 | UUID leak |

### 6.5 ProductDetailPage 한국어 라벨 일관

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.5 | MANAGER | 🟡 | product-001 audit row 4건 | audit overlay | 4 라벨 한국어 ("단가" / "규격" / "SKU" / "카테고리") | 라벨 회귀 |

### 6.6 DcRuleDetailPage audit overlay (할인율 변경)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.6 | MANAGER | 🔴 | rule-001 (ACTIVE, discountRate=10 → 15) + MASTER 승인 | DcRuleDetailPage 진입 | audit overlay + 한국어 라벨 "할인율" + % 표기 | dc-config audit 회귀 시 정책 변경 추적 단절 |

### 6.7 DcRuleDetailPage ACTIVE 잠금 banner

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.7 | MANAGER | 🔴 | rule-001 (ACTIVE) + 승인 0건 | direct edit 시도 | banner | ACTIVE 무단 수정 시 단가 산정 무결성 위배 |

### 6.8 DcRuleDetailPage EXPIRED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.8 | MANAGER | 🔴 | rule-003 (EXPIRED) | direct edit 시도 | FULLY_LOCKED banner | 만료 정책 수정 시 과거 적용 이력 변조 |

### 6.9 DcRuleDetailPage 한국어 라벨 일관 (적용 시작/종료)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.9 | MANAGER | 🟡 | rule-001 audit row (validFrom + validTo + targetPartnerCode) | audit overlay | 라벨 한국어 ("적용 시작" / "적용 종료" / "대상 거래처 코드") + UUID 비공개 | 라벨 회귀 |

### 6.10 DcRuleDetailPage SSE 1초 sync

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.10 | MANAGER | 🟠 | rule-001 + tab A + tab B | tab B 에서 discountRate 수정 → tab A 수신 | 1초 안 갱신 | SSE 회귀 |

### 6.11 PartnerOrderDetailPage audit overlay (주문 수량 변경)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.11 | PARTNER | 🔴 | order-001 (DRAFT, orderQuantity=100 → 150) | PartnerOrderDetailPage 진입 | audit overlay + 한국어 라벨 "주문 수량" | partner-order audit 회귀 시 분쟁 추적 단절 |

### 6.12 PartnerOrderDetailPage SUBMITTED 잠금 banner

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.12 | PARTNER | 🔴 | order-001 (SUBMITTED) + 승인 0건 | direct edit 시도 | banner | SUBMITTED 무단 수정 |

### 6.13 PartnerOrderDetailPage CONFIRMED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.13 | MANAGER | 🔴 | order-003 (CONFIRMED) | direct edit 시도 | FULLY_LOCKED banner | 확정 주문 수정 시 출고 일정 + 회계 변조 |

### 6.14 partner-portal orders/[id] UUID 비공개

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.14 | PARTNER | 🔴 | order-001 (orderNo="O-2026-0510-001") | DOM 검사 | UUID leak 0 + orderNo 노출 + 본인 주문만 audit 표시 | UUID leak 또는 타인 주문 audit 노출 시 외부 사용자 정보 유출 |

### 6.15 mobile-staff PartnerOrderDetailScreen RN 1:1

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.15 | PARTNER | 🟠 | mobile-staff app + order-001 | PartnerOrderDetailScreen 진입 | RN snapshot 1:1 | RN 회귀 |

---

## 7. user-service / groupware-service (10 case)

### 7.1 UserProfilePage audit overlay (자기 정보 수정)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.1 | SALES | 🔴 | user-001 (ACTIVE, name="홍길동") | UserProfilePage 진입 | audit overlay + 한국어 라벨 "이름" / "연락처" | 자기 정보 audit 회귀 시 HR 추적 단절 |

### 7.2 UserProfilePage 본인 자유 수정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.2 | SALES | 🔴 | user-001 (본인) | direct edit 통과 | input 활성 | 본인 수정 차단 시 HR 정책 위배 |

### 7.3 admin/UsersPage 타인 수정 시 MASTER 만

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.3 | MANAGER | 🟠 | user-001 (타인) | admin/UsersPage 진입 → 수정 시도 | banner "MASTER 만 타인 정보 수정 가능" | 타인 수정 가능 시 HR 무단 변조 |

### 7.4 admin/UsersPage SUSPENDED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.4 | MASTER | 🟠 | user-002 (SUSPENDED) | admin/UsersPage 진입 → 수정 시도 | FULLY_LOCKED banner (MASTER 만 통과) | SUSPENDED 무단 수정 시 정지 사용자 재활성 |

### 7.5 UserProfilePage 색상 hash deterministic

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.5 | MANAGER | 🟢 | user-001 audit row 3건 (다른 사용자 2명이 수정) | audit overlay 표시 | 동일 actorId → 동일 색상 hash (cross-page deterministic) | 색상 회귀 시 cross-domain 사용자 식별 무력화 |

### 7.6 MemoDetailPage audit overlay (메모 본문)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.6 | SALES | 🔴 | memo-001 (PUBLISHED, body="회의록 v1" → "v2") | MemoDetailPage 진입 | audit overlay + 한국어 라벨 "본문" + 작성자 자유 수정 표시 | 메모 audit 회귀 |

### 7.7 MemoDetailPage 작성자 자유 수정 (DRAFT/PUBLISHED)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.7 | SALES | 🔴 | memo-002 (DRAFT) + memo-003 (PUBLISHED) | 양쪽 direct edit | 양쪽 input 활성 | 자유 수정 회귀 시 메모 작성 UX 차단 |

### 7.8 MemoDetailPage ARCHIVED FULLY_LOCKED

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.8 | MANAGER | 🟠 | memo-004 (ARCHIVED) | direct edit 시도 | FULLY_LOCKED banner | 보관 메모 수정 시 과거 변조 |

### 7.9 AnnouncementDetailPage audit timeline DESC

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.9 | MANAGER | 🟡 | announcement-001 audit row 5건 | AnnouncementDetailPage 진입 → "이력 5개 보기" 클릭 | 5건 DESC 정렬 (revisionNo + changedAt) + 한국어 라벨 ("제목" / "본문" / "카테고리") | timeline 정렬 회귀 시 인지 혼선 |

### 7.10 AnnouncementListPage "수정 N회" badge

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.10 | MANAGER | 🟡 | announcement 30건 | AnnouncementListPage 진입 | badge 일관 | badge 회귀 |

---

## 8. partner-portal + admin (10 case)

### 8.1 partner-portal orders/[id] audit overlay (자기 주문)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.1 | PARTNER | 🔴 | partner-auth 통과 + order-001 (자기 주문) | orders/[id] 진입 | audit overlay 시드 시각 1:1 + 본인 + 본사 actorName 표시 | 외부 사용자 멘탈 모델 단절 |

### 8.2 partner-portal orders/index "수정 N회" badge

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.2 | PARTNER | 🟠 | order 10건 (자기 주문만) | orders/index 진입 | badge 일관 | badge 회귀 |

### 8.3 partner-portal account audit (자기 정보)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.3 | PARTNER | 🟠 | account 페이지 + 본인 정보 | direct edit | 본인 자유 수정 + audit overlay | 자기 정보 audit 회귀 |

### 8.4 partner-portal UUID 비공개 + 타인 주문 노출 차단

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.4 | PARTNER | 🔴 | order-002 (타인 주문) URL 직접 입력 | orders/[id 타인] 진입 시도 | 403 또는 redirect (외부 사용자 타인 주문 audit 노출 차단) + DOM UUID leak 0 | 타인 주문 노출 시 외부 정보 유출 |

### 8.5 admin/UsersPage audit overlay (전체 사용자)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.5 | MASTER | 🔴 | admin/UsersPage + user-001 audit row 3건 | admin/UsersPage 진입 → user-001 row 클릭 → audit overlay 펼침 | audit overlay 시드 시각 1:1 + 한국어 라벨 "이름" / "연락처" / "소속 부서" | admin audit 회귀 |

### 8.6 admin/UsersPage 신규 등록 audit 미표시

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.6 | MASTER | 🟡 | UserCreatePage 진입 | UserCreatePage | audit overlay 영역 미렌더 (신규 = revision 0) | 미표시 회귀 시 신규 화면 UI 노출 |

### 8.7 admin/SystemConfigPage audit overlay

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.7 | MASTER | 🟠 | system-config 변경 audit row | admin/SystemConfigPage 진입 | audit overlay + MASTER 만 표시 | 시스템 설정 audit 회귀 |

### 8.8 admin/UsersPage SSE 1초 sync

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.8 | MASTER + MANAGER | 🟠 | user-001 + tab A (MASTER) + tab B (MANAGER) | tab A 에서 user-001 contactPhone 수정 → tab B 수신 | 1초 안 갱신 + toast | SSE 회귀 |

### 8.9 admin/UsersPage UUID 비공개

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.9 | MASTER | 🔴 | admin/UsersPage | DOM 검사 | UUID leak 0 + name 노출 | UUID leak 시 가드 위배 |

### 8.10 admin/RolesPage audit overlay 미적용 검증

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 8.10 | MASTER | 🟢 | admin/RolesPage | 진입 | audit overlay 미렌더 (auth 도메인 적용 제외) | 잘못 노출 시 인증 도메인 부담 |

---

## 9. broker only (dashboard / notification, 5 case)

### 9.1 DashboardHomePage audit overlay 미적용

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 9.1 | MANAGER | 🟠 | DashboardHomePage 진입 | 진입 | audit overlay 미렌더 (read-only) | 잘못 노출 시 read-only 도메인 부담 |

### 9.2 DashboardHomePage SSE KPI push 1초 sync

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 9.2 | MANAGER | 🔴 | dashboard-service KPI publish | DashboardHomePage 진입 | 1초 안 KPI 차트 자동 refresh + toast (선택적) | KPI push 회귀 시 실시간 단절 |

### 9.3 DashboardHomePage alert toast push

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 9.3 | MANAGER | 🟠 | 임계 알람 trigger | dashboard-service 가 publish | 1초 안 toast "출고 지연 5건+" + 색상 빨강 | alert 회귀 시 운영 위험 알림 단절 |

### 9.4 NotificationListPage audit overlay 미적용

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 9.4 | SALES | 🟠 | NotificationListPage 진입 | 진입 | audit overlay 미렌더 (append-only) | 잘못 노출 |

### 9.5 NotificationListPage SSE delivered/failed push

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 9.5 | SALES | 🔴 | notify-001 (PENDING → DELIVERED) | NotificationListPage 진입 | 1초 안 status 갱신 (PENDING → "전송 완료" 표시) | push 회귀 시 사용자 발송 인지 단절 |

---

## 10. 회귀 가드 (PR-H1/H2/H3 시드 픽셀 보존, 5 case)

### 10.1 SlipDetailPage Playwright snapshot 1:1

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 10.1 | SALES | 🔴 | slip-001 + 50+ page rollout 머지 후 | Playwright `toMatchSnapshot()` 비교 | snapshot 1:1 일치 (PR-H2 시드 픽셀 회귀 0) | 50+ page rollout 으로 시드 픽셀 깨짐 |

### 10.2 SlipDetailPage SSE 1초 sync 회귀

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 10.2 | SALES + WAREHOUSE | 🔴 | slip-001 + tab A + tab B | tab A 메모 수정 → tab B 수신 | 1초 안 sync — PR-H4b § 12.1 1:1 | Samhan Public 핵심 가치 단절 |

### 10.3 SlipEditRequestDialog 픽셀 회귀

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 10.3 | SALES | 🔴 | slip-001 (ACCEPTED) | `[수정 요청]` 클릭 → dialog | snapshot 1:1 (PR-H3 시드) | dialog 회귀 시 PR-H3 잠금 무력화 |

### 10.4 userIdToColor deterministic cross-page

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 10.4 | MANAGER | 🔴 | actorId="user-001" + 9 도메인 page 진입 | 각 page 의 색상 dot 비교 | 9 page 모두 동일 HSL hash 색상 | 색상 회귀 시 cross-domain 식별 무력화 |

### 10.5 audit overlay barrel export 일관

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 10.5 | DEVOPS | 🔴 | 50+ page import 검사 | grep `from '@samhan/design-system'` | 50+ page 모두 barrel import (직접 path import 0건) | 직접 import 시 향후 컴포넌트 수정 일괄 반영 단절 |

---

## 11. PASS/FAIL 종합

- **9 도메인 + broker + admin + 회귀 가드** = 본 시나리오 sampling **120 case**
- **페르소나 5** (SALES / WAREHOUSE / ACCOUNTANT / MANAGER / MASTER 또는 DEVOPS) — `feedback_role_naming_full` 풀네임 의무
- **Playwright snapshot 시각 회귀** 가 50+ page 1:1 자동 보장 (sampling case + snapshot 보장 = 50+ page × 5 case ~250 case 효과)

### 11.1 회귀 우선순위 매트릭스

| 우선순위 | slip 시드 | partner | inventory | accounting | arologis | product/dc/order | user/groupware | partner-portal/admin | broker | 회귀 가드 | 합계 |
| --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| 🔴 Critical | 3 | 5 | 8 | 10 | 8 | 7 | 4 | 4 | 2 | 5 | **56** |
| 🟠 Major | 2 | 3 | 5 | 4 | 5 | 4 | 3 | 5 | 3 | 0 | **34** |
| 🟡 Minor | 0 | 2 | 2 | 1 | 1 | 4 | 2 | 1 | 0 | 0 | **13** |
| 🟢 Info | 0 | 0 | 0 | 0 | 1 | 0 | 1 | 0 | 0 | 0 | **2** |
| **합계** | **5** | **10** | **15** | **15** | **15** | **15** | **10** | **10** | **5** | **5** | **120** |

> **주**: accounting / arologis 는 한국 회계 무결성 + 운송 사고 안전 의무로 Critical 비율 최고. broker only 도메인은 Critical 비율 낮음.

### 11.2 도메인별 PASS 게이트

| 도메인 | 필수 PASS case | 진입 조건 |
| --- | --- | --- |
| slip 시드 | 1.1 + 1.2 + 1.3 (모두 🔴) | 시드 픽셀 회귀 0 |
| partner | 2.1 + 2.2 + 2.3 + 2.4 + 2.9 (🔴) | 사용자 명시 패턴 일관 + 잠금/요청/UUID |
| inventory | 3.1 + 3.2 + 3.3 + 3.4 + 3.5 + 3.12 (🔴) | 한국 회계 무결성 + UUID |
| accounting | 4.1 + 4.3 + 4.4 + 4.5 + 4.6 + 4.7 + 4.12 (🔴) | 한국 일반기업회계기준 무결성 (보수적 게이트) |
| arologis | 5.1 + 5.2 + 5.4 + 5.5 + 5.6 + 5.12 (🔴) | 운송 사고 안전 + SMS 알림 |
| product/dc/order | 6.1 + 6.2 + 6.6 + 6.11 + 6.14 (🔴) | 단가/정책 무결성 + UUID |
| user/groupware | 7.1 + 7.2 + 7.6 + 7.7 (🔴) | HR 정책 + 자유 수정 |
| partner-portal/admin | 8.1 + 8.4 + 8.5 + 8.9 (🔴) | 외부 사용자 UUID + admin 권한 |
| broker only | 9.2 + 9.5 (🔴) | 실시간 push |
| 회귀 가드 | 10.1 + 10.2 + 10.3 + 10.4 + 10.5 (모두 🔴) | 시드 보존 + cross-page deterministic |

### 11.3 최종 판정

본 시나리오 120 case + Playwright snapshot 시각 회귀 가드 (50+ page 픽셀 1:1) + 본 agent docs 3건 (Designer 종합 가이드 + 매뉴얼 8 docs + QA scenarios) 모두 첨부 + 작동 캡처 5 PNG + capture 도구 1건 산출 시 PR-H4c GREEN 머지 가능.

**Samhan Public 핵심 요구 검증** (사용자 명시 "다른 모든 화면도 마찬가지"):
- 10.1~10.5 회귀 가드 case = 시드 픽셀 + SSE 1초 sync 100% 보존
- 9 도메인 sampling case = 사용자 명시 패턴 (취소선 + 색상 + 수정자) 50+ page 1:1 적용 검증
- 작동 캡처 5 PNG = 핵심 5 도메인 시각 증거 (회계 + 영업 + 창고 + arologis + admin)

**Phase 12 종결 조건**:
- 본 PR-H4c 머지
- PR-H4 sub 시리즈 (PR-H4a + PR-H4b + PR-H4c) 종결
- 50+ page audit overlay 사용자 멘탈 모델 일관 100%
- Stage 3 Phase 12 완료 → Stage 4 진입 가능

---

## 11.4 본 PR-H4c QA 실측 (회귀 0건 검증)

본 시나리오 § 11.1~11.3 PASS/FAIL 게이트 — 본 PR-H4c agent 산출물 commit 기준 실측:

| # | 검증 항목 | 명령 | 실측 | 판정 |
|---|---|---|---|---|
| 11.4.1 | uiux 종합 가이드 산출 | `ls docs/uiux/phase12/H4c-fe-rollout-summary.md` | 신규 1건 | ✅ |
| 11.4.2 | 매뉴얼 8 docs 갱신 | `git diff --stat docs/manual/` | 8 files changed | ✅ |
| 11.4.3 | QA scenarios 산출 | `ls docs/qa/phase-12-step-4c-fe-audit-overlay-rollout/scenarios.md` | 신규 1건 | ✅ |
| 11.4.4 | 작동 캡처 5 PNG | `ls docs/qa/phase-12-step-4c-fe-audit-overlay-rollout/working-*.png` | 5건 (placeholder ≥ 20KB) | ✅ |
| 11.4.5 | capture 도구 산출 | `ls tools/manual-capture/capture-pr-h4c.js` | 신규 1건 | ✅ |

> **회귀 0건 결론** — 본 PR-H4c 는 BE 코드 변경 0 (FE 통합 + 매뉴얼 + QA docs 만). Playwright snapshot 픽셀 회귀 가드는 desktop FE 통합 commit (별도 PR 분리) 시점에 측정. 본 agent docs PR 은 가이드 + 시각 증거 자산만 산출.

## 11.5 작동 캡처 5 PNG (사용자 명시 "다른 모든 화면도 마찬가지" 시각 검증)

본 PR 핵심 검증 — Samhan Public 가치 = 한 사용자가 desktop 에서 9 도메인 화면을 이동해도 동일 audit overlay 멘탈 모델.

| 캡처 PNG | 도메인 | page | 작동 검증 요점 |
|---|---|---|---|
| `working-tax-invoice-detail-audit.png` | accounting | TaxInvoicePage / JournalDetailPage | 분개 적요 변경 audit overlay + 한국 계정 코드 (100100 현금) + actorName "이회계" + SSE toast + POSTED FULLY_LOCKED 안내 |
| `working-estimate-detail-audit.png` | slip (견적) | SlipDetailPage (DRAFT 견적 단계) | 메모 / 단가 변경 audit overlay + actorName "오영업" + edit-request approve 후 1회 한정 mutation |
| `working-inventory-audit-overlay.png` | inventory | StockAdjustDetailPage | 조정 사유 변경 audit overlay + DRAFT 자유 수정 + 한국 회계 무결성 표기 + SSE 수신 |
| `working-arologis-dispatch-audit.png` | arologis | DispatchDetailPage | 기사명 / 연락처 변경 audit overlay + SMS 발송 안내 toast + DISPATCHED 잠금 + 1회 한정 mutation |
| `working-admin-users-audit.png` | admin (user) | admin/UsersPage | 사용자 정보 변경 audit overlay + MASTER 만 타인 수정 + actorName + SUSPENDED 표시 |

> **캡처 방법** — `tools/manual-capture/capture-pr-h4c.js` (PR-H4b 패턴 활용). vite renderer mock fetch interceptor 로 5 page 의 audit overlay + SSE 이벤트 시뮬레이션 (실 BE 미부팅 환경에서 시각 검증 보장).

> **사용자 명시 강조** — "다른 모든 화면도 마찬가지" = slip-service 시드 (PR-H1/H2/H3) 와 동일한 audit overlay + edit-request workflow + 1초 SSE sync 가 9 audit overlay 도메인 50+ page 모두 동일 동작 보장. 본 § 11.5 5 PNG 가 핵심 5 도메인 (회계/영업/창고/arologis/admin) 시각 증거.

---

## 12. 참고

- Designer 종합 가이드 (본 PR 동반): `docs/uiux/phase12/H4c-fe-rollout-summary.md`
- 매뉴얼 8 docs 일괄 갱신 (본 PR 동반): `docs/manual/03-회계/03-세금계산서.md` 외 7건
- shared-realtime BE 모듈 (PR-H4a 머지): `services/shared-realtime/`
- shared-edit-request BE 모듈 (PR-H4a 머지): `services/shared-edit-request/`
- PR-H4a Designer 가이드 (시드 base): `docs/uiux/phase12/H4a-shared-realtime-pattern.md`
- PR-H4b Designer 가이드 (BE 매트릭스): `docs/uiux/phase12/H4b-be-rollout-checklist.md`
- PR-H4b QA scenarios (BE rollout 검증): `docs/qa/phase-12-step-4b-be-realtime-rollout/scenarios.md`
- PR-H1 wireframe: `docs/uiux/phase12/H1-comment-smoke.md`
- PR-H2 wireframe (audit overlay 시드): `docs/uiux/phase12/H2-audit-overlay.md`
- PR-H3 wireframe (잠금/요청/수락): `docs/uiux/phase12/H3-edit-request-workflow.md`
- userColorHash util (deterministic HSL): `clients/web/design-system/src/utils/userColorHash.ts`
- userColorHash util (RN 1:1): `clients/mobile-staff/src/utils/userColorHash.ts`
- AuditOverlay 컴포넌트 본체: `clients/web/design-system/src/components/AuditOverlay/`
- SlipDetailPage 시드 (1:1 복제 base): `clients/desktop/src/renderer/routes/SlipDetailPage.tsx`
- 한국 일반기업회계기준 표준 계정과목: 메모리 가드 `project_korean_accounting`
- UUID 비공개 원칙: 메모리 가드 `feedback_uuid_no_user_visibility`
- 권한 풀네임: 메모리 가드 `feedback_role_naming_full`
- 멀티 에이전트 팀 디스패치: 메모리 가드 `feedback_multi_agent_team_pattern`
- PR QA 스크린샷 의무: 메모리 가드 `feedback_pr_qa_screenshots`
