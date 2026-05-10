# PR-H4a Designer — `shared-realtime` audit overlay 적용 패턴 가이드 (14 service / 50+ page 일괄 확장)

> Phase 12 Step 4a — `shared-realtime` 공통 모듈 슬라이스의 Designer 산출물.
> PR-H1 `userIdToColor` 색상 hash + PR-H2 `AuditOverlay` 컴포넌트 + PR-H3 `SlipEditRequestDialog` 잠금/요청/수락 워크플로우를
> **slip-service 한 도메인에서만** 실증한 단계까지 완료한 위에서, 본 PR-H4a 는 **나머지 13 service / 50+ page 가
> 동일 패턴을 시각·UX 일관성으로 도입**하기 위한 가이드를 시드한다.
> 본 PR-H4a (BE-1 agent) 는 코드 변경 0 — 가이드 자산 (PR-H4b/H4c 의존)만 산출.

## 0. 본 가이드의 위상

| PR | 책임 | 산출 |
| --- | --- | --- |
| **PR-H4a (본 PR)** | shared 모듈 + 가이드 시드 | `shared-realtime` BE 모듈 (BE-1) + 본 Designer/DevOps/QA docs (본 agent) |
| PR-H4b | 5 backend (partner / inventory / accounting / arologis / dashboard) 일괄 도입 | 본 가이드 § 3 / § 4 / § 5 따르기 |
| PR-H4c | 50+ page UI 통합 (desktop / mobile-staff) | 본 가이드 § 6 / § 7 따르기 |

> **원칙**: 모든 도메인의 audit overlay 는 PR-H2 `SlipDetailPage` 시드 패턴 (commit `435918c`) 의 1:1 복제이며, 시각 차이 0건이 의무. 도메인별 "필드명" / "라벨" 만 한국어로 교체한다.

## 1. 사용자 명시 패턴 (PR-H2 헌법 → 14 service 확장)

> "이전 품목에서 취소선을 긋고 새로운 데이터로 바로 위에 재표기
> (단 색상은 수정자마다 랜덤하게 자동 설정되며,
> 우측에 수정자 이름이 해당 색상으로 같이 표시)."

본 문장은 PR-H2 단계에서 slip 도메인에 한정되어 실증되었으나, **본 PR-H4a 부터는 14 service 모든 도메인에 동일 적용** 한다. 사용자가 "내가 봤던 slip 화면" 의 시각 멘탈 모델 그대로 partner / inventory / accounting / arologis / dashboard 를 학습할 수 있어야 한다.

### 1.1 14 service × audit overlay 적용 매트릭스

| Backend Service | Audit overlay 적용 page (desktop) | mobile-staff 적용 | 본 PR-H4a 우선순위 |
| --- | --- | :-: | :-: |
| **slip-service** (PR-H2 시드) | `SlipDetailPage` (memo / shippingAddress 외 11 필드) | `SlipDetailScreen` (partnerName / status) | 시드 (변경 0) |
| **partner-service** | `PartnerDetailPage` (사업자명/대표자/연락처/주소) | — | 🔴 1순위 (사용 빈도 최고) |
| **inventory-service** | `StockAdjustPage` (조정 사유/수량) + `WarehouseDetailPage` | `StockAdjustScreen` | 🔴 1순위 (회계 무결성 의무) |
| **accounting-service** | `JournalDetailPage` (적요/금액) | — | 🔴 1순위 (한국 회계 감사 의무) |
| **arologis-service** | `DispatchDetailPage` (기사/차량/예정시각) | `DispatchScreen` (기사 변경) | 🟠 2순위 |
| **dashboard-service** | (보통 read-only — overlay 미적용 권고) | — | 🟢 적용 제외 |
| **product-service** | `ProductDetailPage` (단가/규격/SKU) | — | 🟠 2순위 |
| **dc-config-service** | `DcRuleDetailPage` (할인 정책 본문) | — | 🟠 2순위 (정책 변경 추적) |
| **partner-order-service** | `PartnerOrderDetailPage` (주문 본문) | — | 🟠 2순위 |
| **partner-auth-service** | (인증/세션 — overlay 미적용 권고) | — | 🟢 적용 제외 |
| **auth-service** | (인증 — overlay 미적용 권고) | — | 🟢 적용 제외 |
| **user-service** | `UserProfilePage` (이름/연락처/소속) | — | 🟡 3순위 |
| **notification-service** | (전송 로그 — overlay 미적용 권고) | — | 🟢 적용 제외 |
| **groupware-service** | `MemoDetailPage` / `AnnouncementDetailPage` | — | 🟡 3순위 |
| **logging-service** | (read-only — overlay 미적용 권고) | — | 🟢 적용 제외 |
| **eureka-server** | (인프라 — overlay 미적용) | — | 🟢 적용 제외 |

> **요약**: 14 backend 중 **9 service / 약 30~40 page** 가 audit overlay 1차 대상. dashboard / auth / notification / logging / eureka 5건은 overlay 미적용 (read-only / infra / 단방향 전송).

## 2. SlipDetailPage 시드 패턴 (PR-H2 commit `435918c` 1:1 복제)

본 절은 다른 Detail page 가 본 시드를 그대로 따라가도록 단계별로 명시.

### 2.1 import — `@samhan/design-system` 1줄

```tsx
import {
  AuditOverlay,
  type AuditLogEntry,
} from '@samhan/design-system'
```

> **주의**: `AuditOverlay` 는 **`design-system` barrel export 의무** (PR-H2 `clients/web/design-system/src/index.ts` 시드 — 직접 path import 금지). 이렇게 해야 향후 컴포넌트 본체 수정 시 14 service 일괄 반영.

### 2.2 audit-logs API client (도메인별 신규)

PR-H2 시드 `clients/desktop/src/renderer/api/slipAudit.ts` 를 1:1 복제:

```
clients/desktop/src/renderer/api/<domain>Audit.ts
```

| 도메인 | api 파일 신규 | endpoint (BE 도메인 controller) |
| --- | --- | --- |
| partner | `partnerAudit.ts` | `GET /partners/{id}/audit-logs` + `POST /partners/{id}/audit/revert/{n}` |
| inventory (조정) | `stockAdjustAudit.ts` | `GET /stock-adjusts/{id}/audit-logs` + `revert` |
| accounting (분개) | `journalAudit.ts` | `GET /journals/{id}/audit-logs` + `revert` |
| arologis (배차) | `dispatchAudit.ts` | `GET /dispatches/{id}/audit-logs` + `revert` |
| product | `productAudit.ts` | `GET /products/{id}/audit-logs` + `revert` |

> **응답 schema 일관 의무**: 모든 audit-logs 응답은 PR-H2 `SlipAuditLogResponse` schema 와 1:1 동일 — `revisionNo` (int) + `actorId` (UUID string) + `actorName` (string) + `actorColor` (string|null) + `fieldName` (string) + `oldValue` (string|null) + `newValue` (string|null) + `changedAt` (ISO-8601). FE 가 단일 generic `<AuditOverlay logs={...}>` 호출만으로 동작 가능.

### 2.3 useQuery + AuditOverlay 합류 (도메인 Detail page)

```tsx
const auditLogsQuery = useQuery({
  queryKey: ['<domain>AuditLogs', id],
  queryFn: () => listAuditLogs(id),
  enabled: !!id,
})

// 필드 단위 overlay
<div data-testid={`<domain>-detail-audit-overlay-memo`}>
  <AuditOverlay
    logs={auditLogsQuery.data?.filter(l => l.fieldName === 'memo') ?? []}
    currentValue={detail?.memo ?? ''}
    fieldLabel="메모"
  />
</div>
```

> 본 PR-H4a 가이드는 **`testid` prefix 만 도메인별 다르게**. 컴포넌트 호출 인자/style 은 1:1 동일.

### 2.4 SSE event 수신 → cache invalidate (PR-H2 시드 `useEffect`)

```tsx
useEffect(() => {
  const client = new <Domain>RealtimeClient(id, {
    onEdit: () => {
      queryClient.invalidateQueries({ queryKey: ['<domain>AuditLogs', id] })
      queryClient.invalidateQueries({ queryKey: ['<domain>', id] })
    },
    onReverted: () => { /* 동일 */ },
  })
  client.connect()
  return () => client.disconnect()
}, [id])
```

> **shared-realtime 모듈 의무**: PR-H4a BE-1 가 추출하는 `shared-realtime` BE 모듈 + FE 측 `clients/web/design-system/src/realtime/RealtimeClient.ts` (PR-H4c 신규 권고) 가 `<Domain>RealtimeClient` 기반 클래스 제공. 도메인별 client 는 **event name 만** 다르게 (`partner:edit` / `journal:edit` / `dispatch:edit`...).

### 2.5 수정 횟수 chip (도메인별 라벨)

```tsx
<Badge data-testid={`<domain>-detail-revision-count`} tone={revisionTone(count)}>
  수정 {count}회
</Badge>
```

> 0회 → hide / 5회+ → 노랑 / 10회+ → 빨강. 슬립과 동일 임계값.

### 2.6 복원 dropdown (MANAGER+ / MASTER 만)

```tsx
{canRevert && (
  <Select data-testid={`<domain>-detail-revert-select`}>
    {revisionList.map(rev => (
      <option key={rev} value={rev}
        data-testid={`<domain>-detail-revert-button-${rev}`}>
        ↩ revision #{rev} 으로 복원
      </option>
    ))}
  </Select>
)}
```

> 권한 가드는 BE `@PreAuthorize("hasAnyRole('MANAGER','MASTER')")` 와 FE `currentUserRole in ['MANAGER','MASTER']` 양쪽 의무.

## 3. userColorHash util 재사용 (이미 design-system export — 추가 작업 0)

```tsx
import { userIdToColor } from '@samhan/design-system'

// AuditOverlay 컴포넌트 내부에서 자동 사용 — 외부 호출자 가 신경 X
// 다만 다른 컴포넌트 (CommentList / PresenceIndicator 등) 도 동일 색상 보장 의무
const dotColor = userIdToColor(actorId)
```

> **deterministic 보장**: PR-H1 시드 `userIdToColor` 가 hash → HSL hue 변환. 동일 userId → 동일 색상. partner / inventory / accounting / arologis / dashboard 모든 화면에서 같은 사용자 = 같은 색상 자동 일관.
> mobile-staff 는 `clients/mobile-staff/src/utils/userColorHash.ts` 에 1:1 복제 (PR-H2 시드, RN 환경) — design-system import 불가 환경 대응.

### 3.1 cross-domain 색상 일관 검증 (Storybook + QA)

- design-system Storybook `userColorHash.stories.tsx` `MultiUserShowcase` story — 동일 actorId 5건이 모든 도메인 컴포넌트에서 같은 hue 인지 시각 검증.
- QA 시나리오 (본 PR § 7) — partner / inventory / accounting 양쪽 화면에 같은 사용자 audit row 가 같은 색상으로 렌더되는지 multi-context 캡처.

## 4. 한국어 라벨 일관 (도메인별 매핑 표)

PR-H2 § 7 (SlipDetailPage 한국어 라벨 사전) 을 base 로 도메인별 fieldName → 한국어 라벨 매핑 의무.

### 4.1 필드 라벨 매핑 표 (도메인 5건 시범)

| 도메인 | fieldName | 한국어 라벨 (UI 표시) | 비고 |
| --- | --- | --- | --- |
| partner | `businessName` | 사업자명 | 마스터 데이터, 변경 빈도 낮음 |
| partner | `representativeName` | 대표자명 | |
| partner | `contactPhone` | 연락처 | KOREAN_MOBILE_PHONE_PATTERN 검증 |
| partner | `address` | 주소 | |
| inventory | `adjustReason` | 조정 사유 | 회계 감사 의무 — 200자+ 권고 |
| inventory | `quantity` | 수량 | 양수/음수 표기 |
| accounting | `description` | 적요 | |
| accounting | `amount` | 금액 | 천 단위 콤마 표기 |
| arologis | `driverName` | 기사명 | 기사 변경 시 SMS 알림 의무 |
| arologis | `vehicleNo` | 차량번호 | |
| arologis | `scheduledAt` | 예정시각 | YYYY-MM-DD HH:mm |
| product | `unitPrice` | 단가 | |
| product | `specName` | 규격 | |

> 도메인 추가 시 본 표에 row 추가 의무 (PR-H4b/H4c 시 본 가이드 PR 같이 보강).

### 4.2 공통 라벨 (PR-H2 § 7 그대로 재사용)

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
| `lockBanner` | `이 N 은 잠금 상태입니다 — 수정 요청을 보내주세요` | edit-request 도입 도메인 (PR-H3 패턴) |

> N 은 도메인 한국어 명사 (전표 / 거래처 / 재고조정 / 분개 / 배차).

## 5. UUID 비공개 가드 (전 도메인 의무)

PR-H2 시드 `feedback_uuid_no_user_visibility` 그대로 적용:

- `actorId` 는 색상 hash 입력으로만 사용 — **화면 텍스트 노출 0건**.
- `actorName` 만 화면 표시.
- `data-testid` / `aria-label` 등 DOM 속성에도 actorId 직접 노출 금지.
- 도메인 본체 식별자도 동일 — partnerId / journalId / dispatchId 등 **UUID 모두 비공개**, 비즈니스 식별자만 노출 (`partner-detail-business-name` testid OK / `partner-detail-id-${UUID}` testid 금지).

## 6. PR-H4c (50+ page UI 통합) 적용 체크리스트

본 절은 PR-H4c 의 FE 통합 PR 작성 시 도메인별 검수 의무 항목.

### 6.1 도메인별 신규 / 변경 파일 (예: partner)

```
clients/web/design-system/src/index.ts                  (변경 0 — AuditOverlay 이미 export)
clients/desktop/src/renderer/api/partnerAudit.ts        (신규 — slipAudit.ts 1:1 복제)
clients/desktop/src/renderer/realtime/PartnerRealtimeClient.ts  (신규 — SlipRealtimeClient 1:1 복제)
clients/desktop/src/renderer/routes/PartnerDetailPage.tsx        (변경 — useQuery + AuditOverlay + Badge + Select 추가)
```

### 6.2 시각 회귀 가드

- [ ] AuditOverlay 의 `.before` 클래스 = `text-decoration: line-through` + `color: var(--color-text-muted)` (slip 시드 동일)
- [ ] actorDot 의 `background` = `userIdToColor(actorId)` 인라인 style (slip 시드 동일)
- [ ] `actorName` 텍스트 색상 = 검정 (가독성 우선, 식별은 dot 색상)
- [ ] Badge tone 임계값 = 0 hide / 1~4 회색 / 5~9 노랑 / 10+ 빨강 (slip 시드 동일)
- [ ] 빈 history → "변경 이력 없음" 12px italic (slip 시드 동일)
- [ ] revision dropdown trigger = MANAGER + MASTER 만 가시 (slip 시드 동일)

### 6.3 한국어 라벨 회귀 가드

- [ ] § 4.1 필드 매핑 표 의 라벨이 화면에 노출되는 라벨과 1:1 일치
- [ ] § 4.2 공통 라벨이 PR-H2 SlipDetailPage 와 1:1 일치 (영문 leak 0건)
- [ ] `aria-label` 도 한국어 (스크린리더 일관)

## 7. mobile-staff 확장 가이드 (RN 환경)

mobile-staff 는 `@samhan/design-system` import 불가 (RN 별도 트리). PR-H2 시드 `clients/mobile-staff/src/components/AuditOverlay.tsx` 를 base 로 다른 RN screen 도 동일 적용:

| RN screen | 적용 fieldName | 비고 |
| --- | --- | --- |
| `SlipDetailScreen` (시드) | partnerName / status | PR-H2 시드 — 변경 0 |
| `DispatchScreen` (PR-H4c 신규) | driverName / scheduledAt | arologis-service 연동 |
| `StockAdjustScreen` (PR-H4c 신규) | adjustReason / quantity | inventory-service 연동 |

### 7.1 RN 시각 회귀 가드

- [ ] `Text` 의 `textDecorationLine: 'line-through'` 적용 (web `.before` 와 동일 시각)
- [ ] `View` dot 의 `backgroundColor` = `userIdToColor(actorId)` (RN 1:1 복제 util)
- [ ] `actorName` Text 색상 = `#1a1a1a` (검정 가독성)
- [ ] 빈 history → `<Text style={{fontStyle: 'italic'}}>변경 이력 없음</Text>`

## 8. 본 PR-H4a Designer 산출물 (가이드만 — 코드 0)

- [x] 본 문서 `docs/uiux/phase12/H4a-shared-realtime-pattern.md`
- [ ] (PR-H4b BE 가 도메인 5건 도입 시) 본 가이드 § 4.1 필드 매핑 표 row 추가
- [ ] (PR-H4c FE 가 50+ page 도입 시) 도메인별 wireframe doc 1건씩 (`docs/uiux/phase12/H4c-<domain>-audit-overlay.md`) 시드 권고 — 단순 fieldName/라벨 매핑만 (시각 wireframe 은 본 가이드 § 2 그대로)

## 9. 참고

- PR-H1 wireframe (코멘트 smoke): `docs/uiux/phase12/H1-comment-smoke.md`
- PR-H2 wireframe (audit overlay 시드): `docs/uiux/phase12/H2-audit-overlay.md`
- PR-H3 wireframe (잠금/요청/수락): `docs/uiux/phase12/H3-edit-request-workflow.md`
- userColorHash util (deterministic HSL): `clients/web/design-system/src/utils/userColorHash.ts`
- userColorHash util (RN 1:1): `clients/mobile-staff/src/utils/userColorHash.ts`
- AuditOverlay 컴포넌트 본체: `clients/web/design-system/src/components/AuditOverlay/`
- AuditOverlay Storybook (4 story): `AuditOverlay.stories.tsx` (Single / Multiple / Empty / MultiUserShowcase)
- SlipDetailPage 시드 (1:1 복제 base): `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` (commit `435918c`)
- shared-realtime BE 모듈 (PR-H4a BE-1): `services/shared-realtime/` (예정 — BE-1 agent 산출)
