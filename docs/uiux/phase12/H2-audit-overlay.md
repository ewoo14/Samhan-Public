# PR-H2 Designer — AuditOverlay UX wireframe + 사용자 명시 패턴

> Phase 12 Step 2 — 슬립 audit overlay 슬라이스의 Designer 산출물.
> PR-H1 fda4d8f 의 `userIdToColor` util 을 본격적으로 소비하여
> "취소선 + 수정자 색상 dot + 수정자 이름" overlay 를 시드한다.

## 1. 사용자 명시 패턴 (개발 책임자 원안 그대로)

> "이전 품목에서 취소선을 긋고 새로운 데이터로 바로 위에 재표기
> (단 색상은 수정자마다 랜덤하게 자동 설정되며,
> 우측에 수정자 이름이 해당 색상으로 같이 표시)."

본 패턴은 PR-H2 의 헌법이며, 모든 시각적 결정은 본 문장에 부합해야 한다.

세부 해석:

| 요구 | 구현 매핑 |
| --- | --- |
| "이전 품목에서 취소선" | `.before` 클래스 — `text-decoration: line-through` (색상 = 빨강 `--color-danger`, 두께 1.5px) |
| "새로운 데이터로 바로 위에 재표기" | 같은 row 안에서 현재 값(`.current`, 검정) 좌 / 이전 값(`.before`, 회색+취소선) 우 — 한 줄 inline |
| "수정자마다 랜덤하게 자동 설정" | `userIdToColor(actorId)` (deterministic — 동일 user 동일 색상 보장. PR-H1 시드) |
| "우측에 수정자 이름" | `.actor` (margin-left: auto) — dot + 이름 + 시각 가로 배치 |
| "해당 색상으로 같이 표시" | `.actorDot` 의 `background` inline style 로 hash 색상 주입 (이름 텍스트는 가독성 검정 유지, 식별은 dot 색상으로) |

## 2. Wireframe — 단일 변경

```
+----------------------------------------------------------------+
|  메모                                                          |
+----------------------------------------------------------------+
|  긴급 출고 요청 (오늘 마감 전)   ~~오전 배송 부탁드립니다~~  ● 김영업 14:32 |
+----------------------------------------------------------------+
   ^현재값(검정)                  ^이전값(취소선+회색)         ^색상dot+이름+시각(우측)
```

## 3. Wireframe — 다중 revision (3+ 누적)

```
+----------------------------------------------------------------+
|  메모                                                          |
+----------------------------------------------------------------+
|  익일 오전 배송으로 확정   ~~오후 배송으로 변경 요청~~   ● 박관리 16:48 |
|                                                                |
|  [이력 3개 보기] ◀ click                                       |
+----------------------------------------------------------------+

[이력 3개 보기] 클릭 시 expand:

+----------------------------------------------------------------+
|  익일 오전 배송으로 확정   ~~오후 배송으로 변경 요청~~   ● 박관리 16:48 |
|                                                                |
|  [이력 닫기]                                                    |
|  ┌────────────────────────────────────────────────────────┐    |
|  │ ~~오전 배송 부탁드립니다~~                ● 김영업 14:32 │    |
|  │ ~~(빈 값)~~                              ● 이작성 11:05 │    |
|  └────────────────────────────────────────────────────────┘    |
+----------------------------------------------------------------+
```

- expand 영역은 좌측에 `border-left: 2px solid` + 옅은 회색 배경으로 구분.
- 가장 최근(revisionNo 큰) → 과거 순으로 정렬.

## 4. Wireframe — 빈 이력

```
+----------------------------------------------------------------+
|  배송지                                                        |
+----------------------------------------------------------------+
|  서울특별시 강남구 테헤란로 152      변경 이력 없음            |
+----------------------------------------------------------------+
```

- 신규 작성 후 단 한 번도 수정되지 않은 필드.
- "변경 이력 없음" 라벨은 회색 12px italic.

## 5. 수정 횟수 카운트 (상단)

```
+----------------------------------------------------------------+
|  슬립 2026-05-09-001          [상태: 승인대기]   수정 5회      |
+----------------------------------------------------------------+
```

- 슬립 헤더 우측에 "수정 N회" badge 형태로 노출 (총 revision 합계).
- 0회 시 noop (badge 자체 hide).
- 5회 이상 → 노란색 (다중 수정 주의 환기), 10회 이상 → 빨간색.

> 본 PR-H2 단계에서는 AuditOverlay 컴포넌트 자체는 필드 단위 overlay 만 제공.
> "수정 5회" 슬립 헤더 badge 는 Desktop FE 슬라이스 (FE-1) 에서 SlipStatusBadge 옆에
> 일반 Badge 로 합류한다 (별도 컴포넌트 신설 X).

## 6. 복원 dropdown

```
+----------------------------------------------------------------+
|  익일 오전 배송으로 확정   ~~오후 배송으로 변경 요청~~   ● 박관리 16:48 ▾ |
+----------------------------------------------------------------+

▾ 클릭 시:

  ┌────────────────────────┐
  │ ↩ 이 시점으로 복원     │   ← rev 4 = 현재
  │ ↩ rev 3 으로 복원      │
  │ ↩ rev 2 으로 복원      │
  │ ↩ 최초 값으로 복원     │
  └────────────────────────┘
```

- 권한: MANAGER + MASTER 만 노출 (영업/창고 직원은 안 보임).
- 복원 클릭 시 confirm dialog → BE `POST /slips/{id}/restore?revisionNo={n}` (구현 예정 — H2 step-3).
- 본 PR-H2 디자인 단계에서는 dropdown trigger 만 시드, 복원 API 합류는 후속 PR.

## 7. 한국어 라벨 사전

| 영문 키 | 한국어 라벨 | 비고 |
| --- | --- | --- |
| `current` | (값 그대로) | 검정 본문 색 |
| `before` | (값 그대로 + 취소선) | 회색 (`--color-text-muted`) |
| `actorName` | (사용자 풀네임) | UUID 비공개 (`actorId` 는 dot 색상 hash 입력 전용) |
| `timestamp` | `HH:mm` | 같은 날짜는 시각만, 어제 이전은 `MM-DD HH:mm` |
| `expandToggle` | `이력 N개 보기` / `이력 닫기` | 다중 revision 시 |
| `empty` | `변경 이력 없음` | 회색 12px italic |
| `editCountBadge` | `수정 N회` | 슬립 헤더 (5회+ 노랑 / 10회+ 빨강) |
| `restoreDropdown` | `↩ 이 시점으로 복원` / `↩ 최초 값으로 복원` | MANAGER+ 권한 |
| `restoreConfirm` | `revision N 으로 복원하시겠습니까?` | confirm dialog 본문 |

## 8. 색상 정책 — userIdToColor

PR-H1 의 `userIdToColor(userId)` util 을 그대로 사용.

- `hash(actorId) → hue (0~360)` / `saturation 70%` / `lightness 50%`.
- 동일 사용자 = 동일 색상 (deterministic) — 다른 슬립/페이지에서도 일관 색상.
- 5명 이상의 수정자가 누적되어도 색상 다양성이 시각적으로 충분 (Storybook `MultiUserShowcase` story 로 검증).

## 9. UUID 비공개 가드

- `actorId` 는 색상 hash 입력으로만 사용 — **화면 텍스트로 노출 금지**.
- `actorName` (사용자 풀네임) 만 화면 표시.
- `data-testid` / `aria-label` 등 DOM 속성에도 actorId 직접 노출 금지.

## 10. 본 PR 범위 (Designer)

- [x] `docs/uiux/phase12/H2-audit-overlay.md` — wireframe + 사용자 패턴 명시 + 한국어 라벨
- [x] `clients/web/design-system/src/components/AuditOverlay/AuditOverlay.stories.tsx` — 4 story (단일 / 다중 / 빈 / 색상 다양성)
- [x] `docs/manual/02-창고/02-출고-처리.md` — "수정 이력 보기" section
- [ ] (FE-1) AuditOverlay 컴포넌트 본체 (.tsx + .module.css) — 본 PR 병렬 트랙
- [ ] (후속 PR-H2 step-2) "수정 N회" 슬립 헤더 badge 합류
- [ ] (후속 PR-H2 step-3) 복원 dropdown + BE `restore` endpoint 합류 (MANAGER+ 권한 게이트)

## 11. 참고

- PR-H1 wireframe (코멘트 smoke): `docs/uiux/phase12/H1-comment-smoke.md`
- userIdToColor util: `clients/web/design-system/src/utils/userColorHash.ts`
- 색상 swatch Storybook: `clients/web/design-system/src/utils/userColorHash.stories.tsx`
- BE `SlipAuditLog` 엔티티 (예정): slip-service revision 기록 — `slip_audit_logs` (V18 migration 예정).
