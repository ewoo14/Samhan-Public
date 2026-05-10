# PR-H3 Designer — 슬립 수정/삭제 요청 워크플로우 + 잠금 정책 시각화

> Phase 12 Step 3 — 슬립 권한 / 수락 / 거절 워크플로우 슬라이스의 Designer 산출물.
> PR-H1 의 SSE 양방향 push 인프라와 PR-H2 의 audit log overlay 위에서,
> "이미 진행된 슬립을 함부로 수정·삭제하지 못하도록 잠그되,
> 정당한 사유가 있을 때 권한자에게 명시적 승인을 받아 풀 수 있는" 흐름을 시드한다.

## 1. 사용자 명시 잠금 정책 (개발 책임자 원안)

> "DRAFT 단계에서는 자유롭게 수정·삭제, CONFIRMED 부터는 잠금.
> 수정/삭제가 필요하면 권한자(MANAGER+)에게 요청 → 권한자 수락 시에만 임시로 풀어 주고
> 수정 후 다시 잠금. IN_INSPECTION/OUT_FOR_DELIVERY/DELIVERED 는 더 강한 잠금
> (MASTER 만 권한자, 사유 필수, audit log 영구)."

본 문장이 PR-H3 의 헌법이며, 모든 잠금/요청/수락 UX 결정은 본 정책에 부합해야 한다.

## 2. 잠금 정책 표 (status × 수정 가능성)

| 슬립 상태 | 영문 | 본인 작성자 | SALES 다른 직원 | MANAGER | MASTER | 비고 |
| --- | --- | :-: | :-: | :-: | :-: | --- |
| 작성중 | `DRAFT` | 자유 수정/삭제 | ❌ | 자유 수정/삭제 | 자유 수정/삭제 | 잠금 없음 |
| 저장완료 | `SAVED` | 자유 수정/삭제 | ❌ | 자유 수정/삭제 | 자유 수정/삭제 | 잠금 없음 |
| 전송완료 | `SENT` | 요청 후 수락 시 | ❌ | 요청 수락 + 직접 수정 | 직접 수정 | 1차 잠금 |
| 수락 | `ACCEPTED` | 요청 후 수락 시 | ❌ | 요청 수락 + 직접 수정 | 직접 수정 | 1차 잠금 |
| 처리중 | `PROCESSING` | 요청 후 수락 시 | ❌ | 요청 수락 + 직접 수정 | 직접 수정 | 1차 잠금 |
| **검수중** | `INSPECTING` | 요청 (사유 필수) | ❌ | 요청 검토 (수락은 MASTER) | 직접 수정 (사유 필수) | 2차 잠금 |
| 처리완료 | `COMPLETED` | 요청 (사유 필수) | ❌ | 요청 검토 (수락은 MASTER) | 직접 수정 (사유 필수) | 2차 잠금 |
| **출하중** | `SHIPPING` | 요청 (사유 필수) | ❌ | 요청 검토 (수락은 MASTER) | 직접 수정 (사유 필수) | 2차 잠금 |
| **배송완료** | `DELIVERED` | 요청 (사유 필수) | ❌ | 요청 검토 (수락은 MASTER) | 직접 수정 (사유 필수) | 2차 잠금 |
| **확정** | `CONFIRMED` | 요청 불가 (역분개 절차) | ❌ | 요청 불가 | 역분개 신청 (회계) | 회계 영역 |
| 반려 | `REJECTED` | 요청 후 수락 시 (재기안) | ❌ | 직접 수정 | 직접 수정 | 1차 잠금 |
| 취소 | `CANCELED` | ❌ (영구 잠금) | ❌ | ❌ | 신규 슬립 발행만 | 영구 잠금 |

### 잠금 단계 요약

| 잠금 단계 | 적용 status | 수정 경로 |
| --- | --- | --- |
| **0차 (잠금 없음)** | DRAFT / SAVED | 본인 + MANAGER+ 즉시 수정 |
| **1차 잠금** | SENT / ACCEPTED / PROCESSING / REJECTED | 본인 → 권한자 요청 → MANAGER 수락 시 24h temp unlock |
| **2차 잠금 (사유 필수)** | INSPECTING / COMPLETED / SHIPPING / DELIVERED | 본인 → 권한자 요청 (사유 200자+) → MASTER 수락 시 1h temp unlock + audit log 영구 |
| **영구 잠금** | CONFIRMED / CANCELED | 회계 역분개 또는 신규 슬립 발행 (수정 불가) |

> ⚠️ **2차 잠금 사유 200자 제한** — 단순 오타/실수성 사유는 거절. "거래처 요청 변경", "재고 차질", "법적 사유" 등 명확한 비즈니스 사유 권고. audit log `editRequestReason` 컬럼에 영구 보존 (감사 시점 추적 가능).

## 3. Flow chart — 수정 요청 → 알림 → 수락/거절

### 3-1. 1차 잠금 (SENT~PROCESSING) — 본인 영업 → MANAGER

```
[본인 영업: 김영업]                  [MANAGER: 박관리]                 [SSE 채널]
     │                                       │                              │
     │ 슬립 상세 → [✏ 수정 요청] 클릭        │                              │
     ├─────────────────────────────────────→ │                              │
     │   SlipEditRequestDialog 띄우기        │                              │
     │   - 사유 입력 (50자 이상 권장)         │                              │
     │   - 수정 항목 선택 (메모/품목/배송지)   │                              │
     │   - [요청 보내기]                     │                              │
     │                                       │                              │
     │ POST /slips/{id}/edit-request         │                              │
     │ → 201 Created (editRequestId)         │                              │
     │                                       │                              │
     │                                       │  SSE event: edit.requested   │
     │                                       │ ←─────────────────────────── │
     │                                       │  알림 토스트:                │
     │                                       │  "김영업이 슬립 #001 수정    │
     │                                       │   요청 (사유: ...)"           │
     │                                       │                              │
     │                                       │ 슬립 상세 → [수정 요청 검토]  │
     │                                       │ ┌─────────────────────────┐ │
     │                                       │ │ 사유: 거래처에서 품목  │ │
     │                                       │ │ 수량 변경 요청 (4→6)   │ │
     │                                       │ │ 요청 항목: 품목         │ │
     │                                       │ │  [수락]   [거절]        │ │
     │                                       │ └─────────────────────────┘ │
     │                                       │                              │
     │                                       │ POST /slips/{id}/edit-       │
     │                                       │       request/{rid}/accept   │
     │                                       │ → 200 OK (unlock 24h)         │
     │                                       │                              │
     │  SSE event: edit.accepted             │                              │
     │ ←──────────────────────────────────── │                              │
     │  알림 토스트:                         │                              │
     │  "수정 요청이 수락되었습니다           │                              │
     │   (24시간 내 수정 가능)"              │                              │
     │                                       │                              │
     │ 슬립 상세 → 수정 모드 진입             │                              │
     │ - 헤더 노란 banner: "수정 가능 23:48 남음"│                          │
     │ - 모든 필드 편집 가능                 │                              │
     │ - 저장 시 PR-H2 audit log 누적        │                              │
     │                                       │                              │
     │ [저장] → POST /slips/{id} (PATCH)     │                              │
     │ → 200 OK + 자동 잠금 복귀              │                              │
     │                                       │                              │
     │  SSE event: slip.updated              │  SSE event: slip.updated     │
     │  (audit log 추가)                      │  (audit log 추가)            │
     │                                       │                              │
```

### 3-2. 거절 분기

```
     │                                       │ [거절] 클릭                  │
     │                                       │ ┌─────────────────────────┐ │
     │                                       │ │ 거절 사유 입력           │ │
     │                                       │ │ [   재고 변동 없음    ] │ │
     │                                       │ │  [거절 보내기]           │ │
     │                                       │ └─────────────────────────┘ │
     │                                       │                              │
     │                                       │ POST /slips/{id}/edit-       │
     │                                       │       request/{rid}/reject   │
     │                                       │ → 200 OK                      │
     │                                       │                              │
     │  SSE event: edit.rejected             │                              │
     │ ←──────────────────────────────────── │                              │
     │  알림 토스트 (빨강):                  │                              │
     │  "수정 요청이 거절되었습니다           │                              │
     │   사유: 재고 변동 없음"                │                              │
     │                                       │                              │
     │ 슬립 상세 → 수정 잠금 유지              │                              │
     │ - 거절 사유는 PR-H2 audit log         │                              │
     │   editRequestRejection 컬럼에 영구 기록│                              │
```

### 3-3. 2차 잠금 (INSPECTING~DELIVERED) — 본인 영업 → MASTER (사유 필수)

```
[본인 영업: 김영업]                  [MANAGER: 박관리]              [MASTER: 김미선]
     │                                       │                              │
     │ 슬립 상세 → [✏ 수정 요청] (2차 잠금)   │                              │
     ├─────────────────────────────────────→ │                              │
     │   SlipEditRequestDialog (2차 모드)    │                              │
     │   - 사유 입력 (200자 이상 강제)        │                              │
     │   - 수정 항목 선택                     │                              │
     │   - 비즈니스 사유 카테고리 선택        │                              │
     │     (거래처/재고/법적/기타)            │                              │
     │   - [요청 보내기]                     │                              │
     │                                       │                              │
     │ POST /slips/{id}/edit-request         │                              │
     │ → 201 Created (lockTier=TIER_2)       │                              │
     │                                       │                              │
     │                                       │  SSE: edit.requested         │
     │                                       │ ←─────────────────────────── │
     │                                       │  알림 (검토만 가능, 수락 X)  │
     │                                       │                              │
     │                                       │  SSE: edit.requested        │
     │                                       │ ────────────────────────────→│
     │                                       │  알림 (수락 권한)            │
     │                                       │  "MASTER 수락 필요"           │
     │                                       │                              │
     │                                       │ MANAGER 검토 의견 첨부 (선택)│
     │                                       │ POST /slips/{id}/edit-       │
     │                                       │       request/{rid}/comment  │
     │                                       │                              │
     │                                       │                              │ MASTER 검토 → [수락]
     │                                       │                              │ POST .../accept
     │                                       │                              │ → 200 OK (unlock 1h)
     │                                       │                              │
     │  SSE: edit.accepted (2차)             │  SSE: edit.accepted (2차)    │
     │ ←──────────────────────────────────── │ ←─────────────────────────── │
     │  알림 (빨강 banner):                  │                              │
     │  "2차 잠금 해제 (1시간)"               │                              │
     │  "수정 후 audit log 영구 기록"         │                              │
     │                                       │                              │
```

### 3-4. 삭제 요청

```
[본인 영업]                          [MANAGER+]
     │                                       │
     │ 슬립 상세 → [⌫ 삭제 요청] 클릭        │
     ├─────────────────────────────────────→ │
     │   SlipDeleteRequestDialog            │
     │   - 사유 입력 (필수)                  │
     │   - 삭제는 soft delete (BaseEntity)   │
     │   - audit log 영구 보존              │
     │   - [요청 보내기]                     │
     │                                       │
     │ POST /slips/{id}/delete-request       │
     │                                       │
     │                                       │ [수락] → POST .../accept
     │                                       │ → soft delete 즉시 실행
     │                                       │ → 슬립 status = CANCELED
     │  SSE: delete.accepted                 │
     │ ←──────────────────────────────────── │
     │  알림: "삭제되었습니다.                │
     │  관련 분개는 회계팀에 통보됨"          │
```

> **삭제 = soft delete + CANCELED status** — `deleted_at` 컬럼 set + 화면 hide. audit log 는 영구 보존. 회계 분개 (CONFIRMED 후) 가 있다면 자동 역분개 큐에 등록 (accounting-service 연동).

## 4. SlipEditRequestDialog UX mock

### 4-1. 1차 잠금 모드 (SENT~PROCESSING)

```
+──────────────────────────────────────────────+
│  슬립 #2026-05-09-001 — 수정 요청              │
+──────────────────────────────────────────────+
│  현재 상태: 수락 (ACCEPTED)                    │
│  잠금 단계: 1차 (MANAGER 수락 필요)            │
│                                                │
│  수정 사유  *필수*                             │
│  ┌──────────────────────────────────────────┐ │
│  │ 거래처에서 품목 수량을 4 → 6 으로 변경   │ │
│  │ 요청. 재고 가능 확인 완료.                │ │
│  └──────────────────────────────────────────┘ │
│  56자 / 권장 50자 이상                         │
│                                                │
│  수정 항목 (복수 선택 가능)                    │
│  ☐ 메모        ☑ 품목/수량                    │
│  ☐ 배송지      ☐ 결제 정보                   │
│  ☐ 검수지      ☐ 거래처 정보                 │
│                                                │
│  수락자 알림 대상                               │
│  • 박관리 (MANAGER, 영업1팀) — 즉시 알림        │
│                                                │
+──────────────────────────────────────────────+
│                       [ 취소 ]   [ 요청 보내기 ]│
+──────────────────────────────────────────────+
```

### 4-2. 2차 잠금 모드 (INSPECTING~DELIVERED) — 사유 200자 + 카테고리 + 빨강 강조

```
+──────────────────────────────────────────────+
│  슬립 #2026-05-09-001 — 수정 요청  ⚠ 2차 잠금   │
+──────────────────────────────────────────────+
│  현재 상태: 검수중 (INSPECTING)                │
│  잠금 단계: 2차 (MASTER 수락 필요, 사유 필수)   │
│                                                │
│  비즈니스 사유 카테고리 *필수*                 │
│  ◉ 거래처 요청    ◯ 재고 차질                 │
│  ◯ 법적 사유      ◯ 기타                      │
│                                                │
│  상세 사유 *필수, 200자 이상*                  │
│  ┌──────────────────────────────────────────┐ │
│  │ 거래처 (삼한공조 강남점) 가 검수 도중    │ │
│  │ 품목 LG-MAX-2024 의 수량을 12 → 8 로 변경│ │
│  │ 요청. 사유는 거래처측 매장 진열 공간     │ │
│  │ 부족. 재고는 즉시 회수 가능 (창고 HQ-001)│ │
│  │ ...                                       │ │
│  └──────────────────────────────────────────┘ │
│  248자 / 200자 이상 ✓                          │
│                                                │
│  수정 항목                                     │
│  ☐ 메모        ☑ 품목/수량                    │
│  ☐ 배송지      ☐ 결제 정보                   │
│                                                │
│  수락자 알림 대상                               │
│  • 박관리 (MANAGER) — 검토 의견 첨부 가능       │
│  • 김미선 (MASTER, CEO) — 최종 수락 권한        │
│                                                │
│  ⚠ 본 요청은 audit log 에 영구 기록됩니다.     │
│  ⚠ 수락 후 1시간 이내 수정해야 하며,          │
│     수정 시 모든 변경이 영구 audit log 누적.   │
│                                                │
+──────────────────────────────────────────────+
│                       [ 취소 ]   [ 요청 보내기 ]│
+──────────────────────────────────────────────+
```

### 4-3. 검토 다이얼로그 (수락자 측)

```
+──────────────────────────────────────────────+
│  수정 요청 검토 — 슬립 #2026-05-09-001         │
+──────────────────────────────────────────────+
│  요청자  김영업 (영업1팀)                      │
│  요청 시각  2026-05-09 14:32                   │
│  잠금 단계  1차                                │
│                                                │
│  사유                                          │
│  ┌──────────────────────────────────────────┐ │
│  │ 거래처에서 품목 수량을 4 → 6 으로 변경   │ │
│  │ 요청. 재고 가능 확인 완료.                │ │
│  └──────────────────────────────────────────┘ │
│                                                │
│  수정 항목                                     │
│  • 품목/수량                                   │
│                                                │
│  검토 의견 (선택)                              │
│  ┌──────────────────────────────────────────┐ │
│  │ 재고 충분 확인. 수락합니다.               │ │
│  └──────────────────────────────────────────┘ │
│                                                │
+──────────────────────────────────────────────+
│  [ 거절 ]                  [ 수락 (24h 해제) ]│
+──────────────────────────────────────────────+
```

## 5. 수정 가능 banner (수락 후 본인 영업 화면)

```
+──────────────────────────────────────────────+
│  ⚠ 수정 가능 시간: 23시간 47분 남음            │
│  잠금 단계: 1차 / 수락자: 박관리 (MANAGER)    │
│  남은 시간 안에 [저장] 누르지 않으면 자동 잠금 │
│                                                │
│  [ 수정 시작 ]   [ 요청 취소 ]                 │
+──────────────────────────────────────────────+
[슬립번호] 2026-05-09-001  [상태: 수락]  수정 5회
+──────────────────────────────────────────────+
| ... 슬립 본문 ...                              |
+──────────────────────────────────────────────+
```

| 시각 표기 | 색상 | 의미 |
| --- | --- | --- |
| 23:48 ~ 12:00 | 노랑 | 정상 범위 |
| 12:00 ~ 1:00 | 주황 | 마무리 권장 |
| 1:00 ~ 0:00 | 빨강 + 깜빡 | 임박 |
| 0:00 도달 | 빨강 banner → 회색 banner "잠금 복귀" | 자동 잠금 |

## 6. 한국어 라벨 사전

| 영문 키 | 한국어 라벨 | 비고 |
| --- | --- | --- |
| `editRequestButton` | `✏ 수정 요청` | 1차 잠금 status 에서 노출 |
| `editRequestButton2` | `✏ 수정 요청 (2차 잠금)` | 2차 잠금 status — 빨강 강조 |
| `deleteRequestButton` | `⌫ 삭제 요청` | DRAFT/SAVED 가 아닐 때 노출 |
| `dialogTitle` | `슬립 #N — 수정 요청` | 다이얼로그 헤더 |
| `dialogTitleTier2` | `슬립 #N — 수정 요청  ⚠ 2차 잠금` | 2차 모드 |
| `reasonLabel` | `수정 사유 *필수*` | 1차 |
| `reasonLabelTier2` | `상세 사유 *필수, 200자 이상*` | 2차 |
| `categoryLabel` | `비즈니스 사유 카테고리 *필수*` | 2차만 |
| `categoryOptions` | `거래처 요청` / `재고 차질` / `법적 사유` / `기타` | radio |
| `fieldsLabel` | `수정 항목 (복수 선택 가능)` | checkbox group |
| `fieldOptions` | `메모` / `품목/수량` / `배송지` / `결제 정보` / `검수지` / `거래처 정보` | checkbox |
| `recipientsLabel` | `수락자 알림 대상` | 다이얼로그 하단 |
| `submitButton` | `요청 보내기` | 1차 액션 (primary) |
| `cancelButton` | `취소` | 2차 액션 (secondary) |
| `acceptButton` | `수락 (24h 해제)` / `수락 (1h 해제)` | 검토 다이얼로그 — 시간 명시 |
| `rejectButton` | `거절` | 검토 다이얼로그 |
| `rejectReasonLabel` | `거절 사유 입력` | 거절 분기 |
| `unlockBannerTitle` | `⚠ 수정 가능 시간: HH시간 MM분 남음` | 수락 후 본인 영업 banner |
| `unlockBannerExpired` | `잠금 복귀` | 시간 만료 |
| `requestPendingBadge` | `수정 요청 검토 중` | 슬립 헤더 — 요청 발신 후 수락/거절 전 |
| `requestRejectedBadge` | `수정 요청 거절됨` | 거절 후 24h 표시 |
| `auditLogTier2Warning` | `⚠ 본 요청은 audit log 에 영구 기록됩니다.` | 2차 다이얼로그 하단 |
| `tier2TimeLimitNotice` | `⚠ 수락 후 1시간 이내 수정해야 합니다.` | 2차 다이얼로그 하단 |
| `confirmedLockNotice` | `확정된 슬립은 회계 역분개 절차로만 수정 가능합니다.` | CONFIRMED status 시 button 자체 회색 + tooltip |
| `canceledLockNotice` | `취소된 슬립은 수정할 수 없습니다. 신규 발행을 이용해 주세요.` | CANCELED status 시 |

## 7. SSE 이벤트 채널 (PR-H1 인프라 위에서)

| 이벤트 | 발행 시점 | 수신자 | 토스트 메시지 |
| --- | --- | --- | --- |
| `edit.requested` | POST `/edit-request` 성공 | 수락자 (MANAGER+/MASTER) | `김영업이 슬립 #N 수정 요청 (사유: ...)` |
| `edit.accepted` | POST `/edit-request/{id}/accept` 성공 | 요청자 | `수정 요청이 수락되었습니다 (24시간 내 수정 가능)` |
| `edit.rejected` | POST `/edit-request/{id}/reject` 성공 | 요청자 | `수정 요청이 거절되었습니다. 사유: ...` |
| `edit.unlock_expired` | 24h/1h 타이머 만료 (BE scheduled) | 요청자 | `수정 가능 시간이 만료되었습니다. 슬립이 다시 잠겼습니다.` |
| `delete.requested` | POST `/delete-request` 성공 | 수락자 | `김영업이 슬립 #N 삭제 요청 (사유: ...)` |
| `delete.accepted` | POST `/delete-request/{id}/accept` 성공 | 요청자 + 회계담당 | `슬립이 삭제되었습니다. 관련 분개는 회계팀에 통보됨` |
| `slip.updated` | PATCH `/slips/{id}` 성공 | 슬립 watcher 전원 | (audit overlay 자동 갱신, 토스트 X) |

> 본 PR-H3 단계에서는 SSE 채널 5종 (edit.requested/accepted/rejected/unlock_expired + delete.requested) 신규 추가. PR-H1 의 `SlipRealtimeClient` 가 자동 라우팅. 화면 토스트는 design-system `<Toast>` 재사용.

## 8. UUID 비공개 가드

- `requesterId`, `accepterId` 등 모든 user UUID 는 색상 hash + DB 식별 전용.
- 화면 텍스트로는 `requesterName` (풀네임) 만 노출.
- SSE 이벤트 payload 도 `actorName` 우선 — `actorId` 는 PR-H2 의 `userIdToColor` 입력 외 사용 금지.
- Toast 메시지의 모든 사용자 표기는 `풀네임 (ROLE, 부서명)` 형식.

## 9. 본 PR 범위 (Designer)

- [x] `docs/uiux/phase12/H3-edit-request-workflow.md` — 잠금 정책 표 + flow chart + UX mock + 한국어 라벨 + SSE 채널 정의
- [x] `clients/web/design-system/src/components/SlipEditRequestDialog/SlipEditRequestDialog.stories.tsx` — 3 story (1차 / 2차 / 검토 다이얼로그)
- [x] `docs/manual/02-창고/02-출고-처리.md` — "수정/삭제 요청" section 갱신
- [x] `docs/manual/00-시작하기/03-역할별-권한.md` — 잠금 정책 표 추가
- [ ] (FE-1) `SlipEditRequestDialog.tsx` 컴포넌트 본체 — 본 PR 병렬 트랙
- [ ] (BE-1) `POST /slips/{id}/edit-request` + `accept` / `reject` endpoint — 본 PR 병렬 트랙
- [ ] (후속 PR-H4) 슬립 외 inventory transfer / accounting journal 등 14 도메인 확장

## 10. 참고

- PR-H1 SSE infra: `services/slip-service/src/main/java/com/samhanair/logis/slip/realtime/`
- PR-H2 audit overlay (수정 시 자동 누적): `clients/web/design-system/src/components/AuditOverlay/`
- PR-H1 색상 hash util (수정자 dot): `clients/web/design-system/src/utils/userColorHash.ts`
- 슬립 11 status 정의: `services/slip-service/src/main/java/com/samhanair/logis/slip/domain/SlipStatus.java`
- ROLE 정의 (9종): `docs/manual/00-시작하기/03-역할별-권한.md` §1
- 메모리 가드 — UUID 비공개: `feedback_uuid_no_user_visibility.md`
- 메모리 가드 — ROLE 풀네임: `feedback_role_naming_full.md`
