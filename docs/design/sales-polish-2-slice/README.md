# Sales Form UX Polish — 2nd Round (Slice A) — Designer 산출물

본 디렉토리는 SamhanLogis Sales Form Polish **2차 라운드 / Slice A** 의 **Designer (5-team)** 산출물입니다.
FE 팀은 본 산출물의 spec 을 인용해 구현하며, BE/QA/DevOps 팀도 wireframe / interaction flow / 신규 도메인 필드를 참고합니다.

> 사용자 (개발책임자) 강조: **"PR #20 의 디자인 polish 가 부족했으니 본 Slice A 의 spec 충실 적용 의무."**
> → 1차 슬라이스 (`sales-form-polish-slice/`) 결과물에 대한 12건의 사용자 피드백 중 8건 (Slice A 범위) 을 본 슬라이스에서 처리. 나머지 4건은 Slice B/C 후속.

---

## 0. 12건 사용자 피드백 — 본 Slice A 범위

### A. UI 정정 (5건 — 모두 Slice A)

| # | 피드백                                           | 처리 spec                                |
| - | ------------------------------------------------ | ---------------------------------------- |
| 1 | "라이프사이클" 표현 모호 → "전표 진행 단계"      | `<ProgressBar>` 신규 (10단계 + 분기 표시) |
| 2 | 상단 "업무 화면" → 현재 화면명 동적              | AppLayout 헤더 + `usePageTitle()` 훅      |
| 3 | 모델명/품목명 한 행 좌우 분리 (작업지시서)       | `<DispatchView>` 라인 표 7-col grid       |
| 4 | 상세에서 규격 입력 가능                          | `<LineRow>` 10-col + `SlipLine.specification` |
| 5 | 수량 옆 빈 열 제거 (작업지시서 마지막 빈 컬럼)   | `<DispatchView>` 라인 표 6-col 본문 (월/일/모델/품/규격/수량) |

### B. 작업지시서 디자인 정정 (3건 — 모두 Slice A)

| # | 피드백                                           | 처리 spec                                |
| - | ------------------------------------------------ | ---------------------------------------- |
| 6 | 배송지/연락처/특이사항 글자 크기 ↑ (12pt → 14pt) | `--print-text-base: 14pt` 신규 토큰      |
| 7 | 결재란 1×5 horizontal (담당부서/담당자/출고인/검수인/결재) | `.dispatch-roles` 5-col grid 38mm × 22mm |
| 8 | 용달기사/인수자 서명 잘리지 않도록 (A4 portrait 안) | A4 273mm 본문 영역 재배치 + 80mm × 35mm 서명박스 |

### C. 출고인/검수인 자동 서명 (1건 — Slice A 범위)

| # | 피드백                                           | 처리 spec                                |
| - | ------------------------------------------------ | ---------------------------------------- |
| 9 | 출고인 = ACCEPT 단계 수락자 자동 / 검수인 = INSPECTING 신규 단계 수락자 자동 | `Slip.dispatcherUserId` + `Slip.inspectorUserId` BE 필드 추가 + DispatchView 결재란 안 자동 표시 |

### Slice B/C 후속 (본 Slice A 범위 외)

- **모바일 서명** (용달기사 / 인수자) — Slice B
- **카톡 링크 발송** (서명 요청 deeplink) — Slice C
- **e-Sign 첨부 PDF 자동 생성** — Slice C
- **알림 (수락/처리/검수/완료 상태 변경)** — Slice C

---

## 1. 디자인 철학 (1차 슬라이스 계승)

본 슬라이스는 1차 (`sales-form-polish-slice/README.md`) 의 디자인 철학을 그대로 계승합니다.

- 모던 미니멀 + dense 정보 밀도 (Notion / Linear / 이카운트 영감)
- 4-base spacing scale (4 / 8 / 12 / 16 / 24 / 32 / 48)
- Pretendard 한국어 typography + tabular-nums 의무
- subtle elevation (`0 1px 3px rgba(0,0,0,0.04)` 카드)
- micro-interaction `120ms ease-out`
- 안티패턴: 둥근 12px 초과 / 진한 grey / 주황 primary / emoji / gradient

본 슬라이스는 1차 토큰을 **그대로 재사용** + Slice A 한정 신규 alias 만 추가합니다 (`tokens.md` §1).

---

## 2. 신규 도메인 변경 (BE 가 인용)

### 2.1 SlipStatus 단계 추가 (9 → 10)

```
DRAFT → SAVED → SENT → ACCEPTED → PROCESSING → INSPECTING(NEW) → COMPLETED → SHIPPING → DELIVERED → CONFIRMED
                              │                                 │
                              └─→ REJECTED                       │
                                                                  └─→ (REJECTED 가능 여부는 BE Q 결정)
```

- 신규 enum: `SlipStatus.INSPECTING("검수중")`
- 전이 규칙: `PROCESSING → INSPECTING → COMPLETED`
- 신규 도메인 메서드: `Slip#inspect(UserId inspector)` — `requireStatus(PROCESSING)` + `this.status = INSPECTING` + `this.inspectorUserId = inspector`
- 기존 `complete()` 의 `requireStatus(PROCESSING)` 을 `requireStatus(INSPECTING)` 로 변경

### 2.2 신규 영속 필드

| 필드                     | 타입         | nullable | 의미                                               |
| ------------------------ | ------------ | -------- | -------------------------------------------------- |
| `Slip.dispatcherUserId`  | `UUID`       | yes      | ACCEPTED 시점 수락자 user id (출고인)              |
| `Slip.dispatcherSignedAt`| `OffsetDateTime` | yes  | ACCEPTED 트랜지션 timestamp                        |
| `Slip.inspectorUserId`   | `UUID`       | yes      | INSPECTING 시점 검수자 user id (검수인)            |
| `Slip.inspectorSignedAt` | `OffsetDateTime` | yes  | INSPECTING 트랜지션 timestamp                      |
| `SlipLine.specification` | `varchar(50)`| yes      | 규격 (예: "220V", "4HP") — 사용자 직접 입력        |

### 2.3 신규 응답 필드 (FE 가 인용)

`SlipDetailResponse` 추가:
```json
{
  "dispatcher": { "userId": "...", "fullName": "홍지수", "signedAt": "2026-05-04T14:32:18+09:00" },
  "inspector":  { "userId": "...", "fullName": "김기철", "signedAt": "2026-05-04T16:45:02+09:00" }
}
```

> Designer 의 BE/FE 가이드일 뿐, 실제 BE 구현은 BE 팀 Plan 단계에서 확정합니다.

---

## 3. 산출물 (본 디렉토리)

| 파일             | 내용                                                                |
| ---------------- | ------------------------------------------------------------------- |
| `README.md`      | 본 문서 — 12건 피드백 매핑 + Slice 범위 + 신규 도메인 요약          |
| `wireframes.md`  | Progress bar / AppLayout 헤더 / SlipFormPage 규격 컬럼 / DispatchView 7-col 라인 + 1×5 결재란 |
| `tokens.md`      | 신규 토큰 — progress / page-header / print 그룹                     |
| `components.md`  | `<ProgressBar>` 신규 + `<LineRow>` 갱신 (10 col) + `<DispatchView>` 갱신 + `<AppHeader>` 갱신 |
| `ux-flow.md`     | 헤더 화면명 동적 + 출고인/검수인 자동 기입 흐름 + INSPECTING 단계 사용자 시나리오 |
| `print-spec.md`  | A4 portrait mm spec 갱신 (결재란 38×22 / 서명 박스 80×35 / 본문 14pt) |

---

## 4. FE 가 인용해야 할 핵심 spec (top 5)

1. **`components.md` — `<ProgressBar>` props/states** — 10단계 + 분기 시각화 핵심 신규 컴포넌트
2. **`components.md` — `<AppHeader>` 갱신 + `usePageTitle()` 훅** — 라우트별 동적 화면명
3. **`components.md` — `<LineRow>` 10-col grid (규격 컬럼 추가)** — `SlipLine.specification` 입력
4. **`print-spec.md` — `.dispatch-roles` 1×5 grid + 출고인/검수인 자동 표시** — 결재란 신규 layout
5. **`print-spec.md` — A4 portrait 273mm 본문 영역 재배치** — 용달기사/인수자 서명 잘리지 않도록

---

## 5. 적용 범위 (Slice A 한정)

| 영역                          | Slice A | Slice B | Slice C |
| ----------------------------- | ------- | ------- | ------- |
| ProgressBar UI                | O       | -       | -       |
| AppLayout 헤더 동적 화면명    | O       | -       | -       |
| LineRow 규격 컬럼             | O       | -       | -       |
| DispatchView 1×5 결재란       | O       | -       | -       |
| DispatchView 7-col 라인 표    | O       | -       | -       |
| 출고인/검수인 자동 (서버 데이터) | O       | -       | -       |
| INSPECTING 단계 추가          | O       | -       | -       |
| 모바일 서명 (용달기사/인수자)  | -       | O       | -       |
| 카톡 링크 발송                | -       | -       | O       |
| e-Sign 첨부 PDF               | -       | -       | O       |
| 알림 (상태 변경)              | -       | -       | O       |

---

## 6. 검증 (QA 협조)

QA agent 가 본 슬라이스에서 검증해야 할 디자인 항목:

- [ ] ProgressBar 10단계 모두 표시 (DRAFT/SAVED/SENT/ACCEPTED/PROCESSING/INSPECTING/COMPLETED/SHIPPING/DELIVERED/CONFIRMED)
- [ ] 현재 단계 highlight (파란색 + bold)
- [ ] REJECTED 분기 빨간 ⊗, CANCELED 분기 회색 ⊗
- [ ] AppHeader 라우트 변경 시 화면명 자동 갱신 (`usePageTitle()` 훅)
- [ ] 화면명 포맷: `출고전표 상세 [2026/05/04-1]` (slipNo bracket)
- [ ] SlipFormPage LineRow 10 컬럼 (규격 컬럼 100px width)
- [ ] DispatchView 결재란 5칸 horizontal 38mm × 22mm 균등
- [ ] 결재란 출고인/검수인 셀 안에 이름 + 시각 (예: `오병승\n14:32`) — 22mm 안에 오버플로우 hidden
- [ ] DispatchView 라인 표 7-col (월/일/모델명/품목명/규격/수량) — 마지막 빈 열 없음
- [ ] DispatchView 모델명/품목명 한 행 좌우 (2줄 셀 X)
- [ ] DispatchView 배송지/연락처/특이사항 14pt 본문
- [ ] DispatchView A4 portrait 273mm 안에 모든 요소 (용달기사/인수자 서명 잘리지 않음)
- [ ] DispatchView 인쇄 시 1장 1전표 (라인 10건 이하)

---

## 7. 후속 슬라이스 (Slice B/C) 사전 디자인 노트

본 Slice A 의 결재란 5칸 layout 은 Slice B (모바일 서명) 후 Slice C (카톡 링크) 까지 그대로 유지합니다. 즉:

- Slice B: 결재란 5칸 layout 유지 + 용달기사/인수자 서명 박스 안에 모바일 서명 PNG 자동 삽입
- Slice C: 결재란 5칸 layout 유지 + 모바일 서명 호출용 카톡 deeplink (`https://app.samhan-air.com/sign/<token>`) 발송

따라서 본 Slice A 의 print-spec layout 은 후속 변경 최소 (mm 단위 그대로).
