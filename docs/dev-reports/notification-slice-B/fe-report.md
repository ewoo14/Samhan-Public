# notification-slice-B FE 산출 리포트

5-team 패턴: FE 팀.

## 1. 산출 요약

notification-slice-B 는 출고전표에 기사 정보를 부착하고, 같은 기사 + 같은 배송일자
의 슬립을 묶어 단일 e-sign URL 을 SMS 로 발송하는 슬라이스다. 본 FE 산출은
디자인 시스템 신규 컴포넌트 2종, LinkDispatchListPage 와 BatchDetailModal 신규
화면 2종, SlipFormPage / SlipDetailPage 의 driver 필드 추가, 그리고 SMS/토큰
관련 API 클라이언트 1종을 포함한다.

## 2. 변경 파일 목록

### 2.1 디자인 시스템 (`clients/web/design-system/src/`)

신규 18 컴포넌트 (16 → 18):

- `components/PhoneInput/PhoneInput.tsx` (+ `.module.css`, `.stories.tsx`, `index.ts`)
  - 자동 하이픈 010-XXXX-XXXX, maxLength 13
  - `KOREAN_MOBILE_PHONE_PATTERN` (`/^01[016789]-\d{3,4}-\d{4}$/`) export
  - `formatKoreanMobilePhone(raw)` 헬퍼 export
  - FormField 호환 (label/helperText/required)
- `components/CopyButton/CopyButton.tsx` (+ `.module.css`, `.stories.tsx`, `index.ts`)
  - clipboard.writeText 우선 + execCommand 폴백 (file:// 환경 호환)
  - "복사됨" 토스트 (기본 3초, `toastDurationMs` props 로 조정 가능)
  - `onCopy` 콜백 (analytics)
- `index.ts` — 두 컴포넌트 re-export
- `components/DataTable/DataTable.tsx` — `rowClassName?: (row: T) => string | undefined`
  prop 추가 (sent 행 옅은 파랑 시각화 용)

### 2.2 desktop renderer (`clients/desktop/src/renderer/`)

신규/변경:

- `routes/LinkDispatchListPage.tsx` (신규)
  - `useQuery(['delivery-batches', date])` + DataTable 6 컬럼
  - 상단 [날짜 자동 그룹] primary 버튼 → `POST /delivery-batches/auto-group`
  - 행 클릭 → `BatchDetailModal`
  - sent 행 `batch-row-sent` 클래스 자동 부여
  - `usePageTitle('링크발송')`
- `routes/components/BatchDetailModal.tsx` (신규)
  - 배치 메타 + e-sign URL + CopyButton + [토큰 재발행] + 슬립 표 + 슬립 추가 폼
- `routes/components/BatchStatusCell.tsx` (신규)
  - sent: ☑ + HH:mm + [재발송] ghost 링크
  - unsent: [SMS 발송] primary 버튼
  - 행 클릭 propagation 차단 (모달 열림 방지)
- `routes/SlipFormPage.tsx` — 헤더 카드 신규 행 (OUTBOUND 만):
  - 기사명 input + PhoneInput
  - createSlip payload 에 `driverName` / `driverPhone` 포함
- `routes/SlipDetailPage.tsx` — 헤더 카드 직후 신규 "기사 정보" 카드:
  - DRAFT/SAVED 단계만 [편집] 버튼 활성
  - 인라인 편집 → `PATCH /slips/{id}/driver`
  - PhoneInput 로 검증된 값만 저장 가능
- `routes/index.tsx` — 신규 라우트 `/sales/link-dispatch` 등록 (`/sales/:id` 보다 먼저)
- `components/AppLayout.tsx` — 사이드바 "링크발송" 메뉴 추가
- `api/delivery.ts` (신규) — listBatches / getBatch / autoGroup / addSlipToBatch /
  removeSlipFromBatch / sendBatchSms / regenerateBatchToken
- `api/slip.ts` — `SlipDetail.driverName` / `driverPhone` 추가, `CreateSlipRequest`
  에도 동일 필드, `updateSlipDriver()` (PATCH /slips/{id}/driver) 함수 추가
- `api/mock.ts` — 4 배치 mock + 모든 새 endpoint 핸들러 + slip-001 driver 필드
- `print/DispatchView.tsx` — 용달기사 서명 라벨에 `driverName` 자동 노출 (괄호 안)
  - 인쇄 본문 디자인 자체는 변경 X (`feedback_print_design_iteration.md` 가드)

### 2.3 CSS (`clients/desktop/src/renderer/styles/global.css`)

신규 섹션 "notification-slice-B" 추가:

- `:root { --batch-list-row-sent-bg: #F0F9FF }` (Designer tokens.md 인용)
- `.batch-row-sent` — 옅은 파랑 배경
- `.batch-date-input` — 날짜 input 스타일
- `.link-cell` / `.link-cell-url` — DataTable 링크 셀 (URL truncate + 복사 버튼)
- `.batch-status-sent` / `.batch-status-unsent` / `.batch-status-icon` /
  `.batch-status-time` — BatchStatusCell variants
- `.batch-detail-meta` / `.batch-detail-url-row` / `.batch-detail-url` /
  `.batch-slip-table` / `.batch-add-slip-row` / `.batch-add-slip-input` —
  BatchDetailModal 내부
- `.sfp-form-grid--driver` — SlipFormPage driver 2-col 그리드
- `.driver-edit-grid` / `.driver-edit-field` / `.driver-edit-actions` —
  SlipDetailPage 인라인 편집 그리드

## 3. 검증 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| 디자인 시스템 빌드 | `cd clients/web/design-system && npm run build` | PASS (style.css 35.75kB / index.js 57.67kB) |
| 디자인 시스템 lint | `cd clients/web/design-system && npm run lint` | PASS (0 error / 0 warning) |
| 데스크톱 typecheck | `cd clients/desktop && npm run typecheck` | PASS |
| 데스크톱 lint | `cd clients/desktop && npm run lint` | PASS (0 error, 1 warning — 본 슬라이스 무관 pre-existing `err` unused) |

## 4. 회귀 가드 준수

- `feedback_uuid_no_user_visibility.md` — LinkDispatchListPage 표 컬럼에서 `batch.id`
  미노출. DataTable 의 React `rowKey` 에서만 사용. BatchDetailModal 내부 슬립 표도
  `slipNo` 만 노출 (slipId 는 액션 path 에서만).
- `feedback_print_design_iteration.md` — DispatchView 본문 디자인 변경 0. 용달기사
  서명 라벨에 driverName 자동 노출만 (JSX 한 줄 수정).
- `feedback_function_documentation.md` — 모든 신규 컴포넌트/함수에 한국어 Javadoc
  (PhoneInput / CopyButton / BatchStatusCell / BatchDetailModal /
  LinkDispatchListPage / api/delivery.ts / api/slip.ts updateSlipDriver).
- `feedback_korean_commits.md` — commit 메시지는 PM 통합 단계에서 한국어로 작성.
- `feedback_pr_qa_screenshots.md` — Designer mock 캡처 4종 + FE 가 capture 모드
  (`VITE_MOCK_MODE=1`) 로 추가 캡처 가능 (mock.ts 에 4 배치 mock 추가됨).

## 5. 회귀 위험 평가

| 영역 | 위험 | 완화 |
|---|---|---|
| `/sales/:id` 라우트 매칭 | LinkDispatchListPage 가 `/sales/link-dispatch` 라 `:id="link-dispatch"` 로 매칭될 수 있음 | `routes/index.tsx` 에서 `/sales/link-dispatch` 를 `/sales/:id` 보다 먼저 등록함 (React Router v6 정확 매칭 우선) |
| DataTable rowClassName | 기존 사용처 (SlipListPage / TransferListPage) 영향 0 | optional prop, 미지정 시 기존 동작 유지 |
| SlipDetail driverName 미존재 응답 | BE 가 신규 필드 미응답 시 undefined | `slip.driverName ?? '-'` fallback, 인쇄 라벨도 조건부 |
| PhoneInput 자동 하이픈 | 기존 입력 (예: 사용자 paste "01012345678") 직후 정규화 | `formatKoreanMobilePhone()` 가 동일 입력에 idempotent |
| SMS / 토큰 재발행 confirm | 사용자 실수 발송 위험 | 모든 mutation 에 `window.confirm` 가드 (BatchStatusCell 클릭 시 driverName + driverPhone 표시) |

## 6. 캡처 (옵션)

본 worktree 산출은 컴파일/lint/typecheck PASS 까지만 검증했고, dev-mock 환경
(`VITE_MOCK_MODE=1`) 에서 실제 화면 캡처는 PM 통합 단계에서 4-team 합본 후 진행
권장. mock 데이터는 다음을 포함:

- 배치 4건 (sent 2 / unsent 2, 모두 2026-05-04)
- 자동 그룹 응답 1건 (신규 batch)
- SMS 발송 후 `smsSentAt` 갱신
- 토큰 재발행 후 `signUrl` 갱신
- slip-001 의 driverName='홍지수', driverPhone='010-1234-5678' (DispatchView 인쇄 시 라벨 자동 표시)

## 7. 다음 단계

1. PM 통합 단계 — BE / Designer / QA / DevOps 산출 합본
2. 통합 풀빌드 가드 (`feedback_pm_integration_build_check.md`):
   - BE PATCH /slips/{id}/driver 컴파일 확인
   - BE GET/POST /delivery-batches 라우팅 확인
   - 도메인 메서드 의미 정렬 (driverName / driverPhone 검증 패턴 동일)
3. QA — `KOREAN_MOBILE_PHONE_PATTERN` 단위 테스트, BatchStatusCell 분기 스토리,
   행 클릭 vs SMS 발송 버튼 propagation 격리
4. DevOps — sign.samhan-air.com 서브도메인 인증서 + nginx 라우팅
5. 캡처 — Vite dev-mock 모드 + Edge headless 로 LinkDispatchListPage / BatchDetailModal
   2 컷 추가 (PR 본문 인라인)
