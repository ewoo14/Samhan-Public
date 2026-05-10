# PR-H1 Designer — 슬립 코멘트 UX wireframe (smoke)

> Phase 12 Step 1 — 코멘트 smoke 슬라이스의 Designer 산출물.
> 본 PR 에서는 mock wireframe + 한국어 라벨 + 회색 단색 만 시드한다.
> 색상 hash (userIdToColor) 적용은 PR-H2 audit overlay 진입 시점에 합류한다.

## 1. 디자인 원칙

- **위치**: 슬립 상세 화면의 보조 영역 (본문은 슬립 데이터 우선).
  - desktop (`SlipDetailPage`, 견적/주문/회계 web): 우측 사이드바 360px 고정.
  - mobile-staff (`SlipDetailScreen`, RN Expo native): 본문 하단 sticky bar + 펼침 패널.
- **컴포넌트 구성** (3 부):
  1. 코멘트 list — `authorName` + `body` + `createdAt`
  2. 입력창 (textarea)
  3. 전송 버튼
- **색상 정책 (본 PR)**:
  - author avatar / dot 색상은 **회색 단색** (`#9CA3AF`) 만 사용.
  - PR-H2 진입 시 `userIdToColor(userId)` 로 교체 — 본 PR 에서는 export 만 시드.
- **시간 표기**: `createdAt` 은 `HH:mm` (오늘) / `MM-DD HH:mm` (과거) 으로 압축.
- **빈 상태**: "아직 코멘트가 없습니다." 안내 문구 (회색 12px).

## 2. desktop wireframe — `SlipDetailPage` (web)

```
+--------------------------------------------------+----------------------+
| [슬립번호] 2026-05-09-001        [상태: 승인대기]  | 코멘트 (3)           |
+--------------------------------------------------+----------------------+
| 거래처      삼한공조 강남점                         | ●  김영업            |
| 작성자      김영업 (영업1팀)                        |     수량 확인 필요    |
| 출고예정일  2026-05-12                             |     14:22            |
+--------------------------------------------------+----------------------+
| 품목                                               | ●  이관리            |
|  - LG-MAX-2024 (12 EA)                            |     재고 OK           |
|  - SAM-COOL-2024 (4 EA)                           |     14:25            |
| ...                                                +----------------------+
|                                                    | ●  박배송            |
|                                                    |     출고 준비 완료    |
|                                                    |     14:31            |
|                                                    +----------------------+
|                                                    | [코멘트를 입력하세요] |
|                                                    | [                  ] |
|                                                    | [                  ] |
|                                                    |          [ 전송 ]    |
+--------------------------------------------------+----------------------+
```

- 우측 사이드바 폭: 360px (1280px 이하에서는 collapse 토글).
- list 는 위→아래 시간순 (오래된 순), 새 코멘트는 list 하단에 추가 후 입력창으로 스크롤.
- author dot: ● = 회색 (#9CA3AF), PR-H2 에서 `userIdToColor` 교체.

## 3. mobile-staff wireframe — `SlipDetailScreen` (RN Expo)

```
+----------------------------------+
| ← 슬립 2026-05-09-001            |
+----------------------------------+
| 거래처   삼한공조 강남점           |
| 작성자   김영업                   |
| 출고예정일 2026-05-12             |
+----------------------------------+
| 품목                             |
|  - LG-MAX-2024 (12 EA)          |
|  - SAM-COOL-2024 (4 EA)         |
+----------------------------------+
|  ...                             |
|                                  |
+----------------------------------+
| ▲ 코멘트 (3)            [펼치기] | ← sticky bottom bar
+----------------------------------+

[펼치기] 탭 시 상단으로 sliding sheet:

+----------------------------------+
| 코멘트 (3)              [닫기 ▼] |
+----------------------------------+
| ●  김영업               14:22    |
|    수량 확인 필요                 |
+----------------------------------+
| ●  이관리               14:25    |
|    재고 OK                       |
+----------------------------------+
| ●  박배송               14:31    |
|    출고 준비 완료                 |
+----------------------------------+
| [코멘트를 입력하세요         ]   |
|                                  |
|                       [ 전송 ]   |
+----------------------------------+
```

- sticky bar 높이: 48dp, 새 코멘트 도착 시 카운트 갱신 + 짧은 진동 (haptic light).
- sheet 펼침 시 키보드 회피 (KeyboardAvoidingView, padding behavior).
- 입력 후 전송 → optimistic append → server 확정 시 `createdAt` 정렬.

## 4. 한국어 라벨 사전

| 영문 키            | 한국어 라벨            | 비고                            |
| ------------------ | ---------------------- | ------------------------------- |
| `comments`         | 코멘트                 | 헤더 + sticky bar               |
| `commentsCount`    | 코멘트 (N)             | 괄호 안 숫자                    |
| `inputPlaceholder` | 코멘트를 입력하세요    | textarea placeholder            |
| `submitButton`     | 전송                   | 1차 액션 (primary)              |
| `emptyState`       | 아직 코멘트가 없습니다.| 회색 12px, 중앙 정렬            |
| `loadingState`     | 코멘트를 불러오는 중...| 초기 로드 spinner 옆            |
| `errorState`       | 코멘트를 불러오지 못했습니다 | 빨강 13px + 재시도 링크   |
| `sendingState`     | 전송 중...             | 전송 버튼 비활성 + spinner      |
| `sendError`        | 전송 실패. 다시 시도해 주세요 | 입력창 하단 빨강 12px      |

## 5. 본 PR 범위 (Designer)

본 PR (PR-H1) Designer 산출물은 **mock + 색상 hash util 시드** 까지.

- [x] `docs/uiux/phase12/H1-comment-smoke.md` — wireframe + 한국어 라벨
- [x] `clients/web/design-system/src/utils/userColorHash.ts` — deterministic HSL hash
- [x] `clients/web/design-system/src/utils/userColorHash.stories.tsx` — 5 userId swatch
- [x] `clients/web/design-system/src/index.ts` — utils export 추가
- [ ] (PR-H2) audit overlay 에서 `userIdToColor` 실 사용
- [ ] (PR-H3) 코멘트 author avatar 배경색 hash 적용 + 실제 `CommentList` / `CommentInput` 컴포넌트 구현

## 6. 참고

- 색상 hash 정책: HSL 70% saturation / 50% lightness — 흰 배경 + 검정 텍스트
  대비 균형 (WCAG AA contrast 만족 여부는 PR-H2 QA 검증 항목).
- 동일 userId 는 항상 동일 색상 (deterministic) — 페이지 새로고침/다른 화면
  에서도 일관성 보장.
- PR-H1 본 PR 의 회색 단색은 mock 단계의 placeholder, 추후 PR-H2 에서 즉시 교체.
