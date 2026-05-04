# Notification Slice B — Designer 산출물 (배송기사 배치 링크 + 자동 SMS)

본 디렉토리는 SamhanLogis **Notification Slice B** 의 **Designer (5-team)** 산출물입니다.
FE / BE / QA / DevOps 팀은 본 spec 의 wireframe / token / component / UX flow 를 인용하여 구현·검증합니다.

> **상위 Plan**: `docs/dev-reports/notification-slice-B/plan.md` (PR #22 후보)
> **사용자 결정 (확정)**: SMS 트리거 = 관리자 수동 클릭 / 그룹 = driverPhone+date 자동 + 분리·병합 / 게이트웨이 = Solapi / 인수자 공유 = Web Share API / 토큰 만료 = batchDate +1일.

---

## 0. Slice B 디자인 요약

본 슬라이스는 **출고 배송 기사에게 배치 단위로 1건의 SMS 자동 발송 → 기사가 모바일 링크로 슬립 N건을 확인** 하는 워크플로의 **UI 골격** 을 신설합니다 (서명 캡처는 Slice C 후속).

### 0.1 신규 화면 (4개)

| # | 화면 | 디바이스 | 인증 |
| - | --- | --- | --- |
| 1 | LinkDispatchListPage (`/sales/link-dispatch`) | 데스크톱 (관리자) | MANAGER / MASTER |
| 2 | BatchDetailModal (행 클릭) | 데스크톱 모달 | MANAGER / MASTER |
| 3 | SlipFormPage / SlipDetailPage 헤더 driver 필드 추가 | 데스크톱 | 기존 동일 |
| 4 | 공개 모바일 페이지 (`sign.samhan-air.com/d/<token>`) | 모바일 (320~414px) | NO AUTH (토큰만) |

### 0.2 신규 디자인 시스템 컴포넌트 (3개)

| 이름 | 용도 |
| --- | --- |
| `<PhoneInput>` | 자동 하이픈 (010-XXXX-XXXX), 한국 휴대폰 패턴 검증 |
| `<CopyButton>` | clipboard.writeText + 토스트 "복사됨" |
| `<BatchStatusCell>` | LinkDispatchListPage 의 SMS 발송 상태 + 액션 셀 |

### 0.3 토큰 변경 요약 (신규 3 그룹)

| 그룹 | prefix | 용도 |
| --- | --- | --- |
| Phone Input | `--phone-input-*` | 입력 테두리 / 포커스 / 에러 색상 |
| Copy Button | `--copy-button-*` | 배경 / hover / icon 색상 |
| Batch List Row | `--batch-list-row-*` | 발송완료(sent) vs 미발송(unsent) 행 배경 색상 |

기존 1차 (`sales-form-polish-slice/`) + 2차 (`sales-polish-2-slice/`) 토큰은 **전체 그대로 재사용** + Slice B 한정 alias 만 추가합니다. 기존 화면 visual regression X.

---

## 1. 산출물 (본 디렉토리)

| 파일 | 내용 |
| --- | --- |
| `README.md` | 본 문서 — Slice B 디자인 요약 + 토큰 변경 요약 |
| `wireframes.md` | 4개 화면 ASCII / mermaid wireframe (LinkDispatchList / BatchDetailModal / SlipForm driver 필드 / 공개 모바일) |
| `tokens.md` | 신규 3 그룹 토큰 정의 (phone-input / copy-button / batch-list-row) |
| `components.md` | `<PhoneInput>` + `<CopyButton>` + `<BatchStatusCell>` 신규 spec |
| `ux-flow.md` | 사용자 흐름 4 시나리오 (관리자 SMS 발송 / 그룹 이동 / 기사 모바일 / 만료 토큰) |
| `mobile-spec.md` | 모바일 공개 페이지 UX (디바이스 / 카드 / Slice C 통합 지점) |
| `mocks/*.html` | 시연용 mock HTML 4종 (Edge headless 캡처 가능) |
| `screenshots/*.png` | mock 캡처 (PR QA 첨부용) |

---

## 2. 디자인 원칙

본 Slice B 는 1차/2차 슬라이스의 디자인 철학을 그대로 계승합니다.

- 모던 미니멀 + dense 정보 밀도 (Notion / Linear 영감)
- 4-base spacing (4 / 8 / 12 / 16 / 24 / 32 / 48)
- Pretendard 한국어 typography + tabular-nums 의무
- 기존 `@samhan/design-system` 17 컴포넌트 + 기존 토큰 충실 답습
- micro-interaction `120ms ease-out`
- 안티패턴: 둥근 12px 초과 / 진한 grey / 주황 primary / emoji / gradient

### 2.1 모바일 공개 페이지 — 자체 mini bundle

공개 모바일 페이지 (`/d/<token>`) 는 **인증 없음 + 토큰 검증만** 의 환경이라 다음 원칙을 적용합니다:

- `@samhan/design-system` 의존 **없음** (번들 사이즈 최소)
- 자체 CSS (single-file `mobile.css` ≤ 8KB)
- 1차 토큰 색상 팔레트는 **하드코딩 hex** 로 동기화 (`tokens.md` §4 참조)
- iOS Safari 14+ / Android Chrome 90+ 호환 (CSS Grid / flex / clamp)
- 별도 mini bundle 권장 — desktop app 과 분리 빌드

---

## 3. 회고 가드 적용

| 가드 | 적용 |
| --- | --- |
| `feedback_pr_qa_screenshots.md` | mock HTML 4종 + Edge headless 캡처 → `screenshots/` 저장 |
| `feedback_uuid_no_user_visibility.md` | UUID (slip id, partner id, batch id) 화면 노출 0건. 비즈니스 식별자 (슬립번호 / 거래처명 / 기사명 / batchToken base64url) 만 사용 |
| `feedback_korean_commits.md` | 모든 산출 한국어 (라벨 / wireframe / spec / 토큰 주석) |
| `feedback_print_design_iteration.md` | DispatchView 인쇄 영향: "용달기사" 결재란 셀에 driverName 자동 표시 — **기존 CSS 무변경** (셀 텍스트만 추가) |
| `feedback_role_naming_full.md` | 권한 표기는 풀네임 (MANAGER / MASTER) |

---

## 4. FE 가 인용해야 할 핵심 spec (top 5)

1. **`components.md` §1 — `<PhoneInput>` props/states + 자동 하이픈** — driver 필드 입력
2. **`components.md` §2 — `<CopyButton>` clipboard + 토스트** — 링크 복사 핵심
3. **`components.md` §3 — `<BatchStatusCell>` unsent/sent 분기** — LinkDispatchListPage 핵심 셀
4. **`wireframes.md` §1 — LinkDispatchListPage 6 컬럼 표 + 상단 [날짜 자동 그룹] 버튼**
5. **`mobile-spec.md` §2 — 공개 모바일 페이지 자체 CSS + Slice C 통합 지점 (서명 / Web Share API)**

---

## 5. 검증 항목 (QA 협조)

QA agent 가 본 슬라이스에서 검증해야 할 디자인 항목:

- [ ] LinkDispatchListPage 6 컬럼 (배송일 / 기사명 / 기사 연락처 / 슬립 수 / 링크 / SMS 발송완료)
- [ ] 발송완료 행 시각적 구분 (`--batch-list-row-sent-bg: #F0F9FF`)
- [ ] 상단 [날짜 자동 그룹] 버튼 → API 호출 후 표 자동 갱신
- [ ] BatchDetailModal 슬립 N건 리스트 + [슬립 추가] / [슬립 제거] 버튼
- [ ] PhoneInput 자동 하이픈 (010 → 010-1234-5678)
- [ ] PhoneInput 한국 휴대폰 패턴 검증 (010-XXXX-XXXX 외 에러 표시)
- [ ] CopyButton 클릭 시 클립보드 복사 + 토스트 "복사됨" 1.5초 표시
- [ ] BatchStatusCell unsent: [SMS 발송] 버튼 / sent: ☑ + HH:mm + [재발송] 링크
- [ ] [재발송] 클릭 시 confirm dialog (오작동 방지)
- [ ] SlipFormPage 헤더 driverName + driverPhone 2 필드 (DRAFT/SAVED 만 편집)
- [ ] 모바일 공개 페이지 — iOS Safari 14+ / Android Chrome 90+ 정상 렌더
- [ ] 모바일 공개 페이지 — viewport 320 / 375 / 414 모두 horizontal scroll 없음
- [ ] 만료 토큰 접근 시 410 GONE 페이지 (관리자 연락 안내 포함)
- [ ] UUID 노출 0건 (DOM inspector 로 data-* 까지 검증)
- [ ] DispatchView 인쇄 — "용달기사" 결재란에 driverName 자동 표시 (기존 mm 무변경)

---

## 6. 후속 슬라이스 (Slice C) 사전 디자인 노트

본 Slice B 의 4 화면 layout 은 Slice C (인수자 서명 캡처) 까지 그대로 유지합니다.

- Slice C: 모바일 공개 페이지 [상세보기] → **서명 캡처 페이지** 신규 + [인수자에게 공유] (Web Share API + clipboard 폴백) 활성화
- Slice C: BatchStatusCell sent 상태에 "서명완료 N/M" 추가 (예: ☑ 14:32 (서명 2/3))
- 본 Slice B 의 토큰 / 컴포넌트 / wireframe 은 Slice C 에서 재사용
