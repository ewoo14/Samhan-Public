# Sales Form UX Polish — 2nd Round (Slice A) — 개발 리포트

본 리포트는 1차 슬라이스 (`sales-form-polish-slice/`) 결과물에 대한 사용자 12건 피드백 중 8건 (Slice A 범위) 처리 결과를 누적합니다.

> Designer 산출물: `docs/design/sales-polish-2-slice/` (README / wireframes / tokens / components / ux-flow / print-spec).

---

## 0. Slice A 범위 (피드백 8건)

| # | 피드백 | 처리 컴포넌트 |
| - | ------ | ------------- |
| 1 | "라이프사이클" 표현 모호 → "전표 진행 단계" | `<ProgressBar>` 신규 |
| 2 | 상단 "업무 화면" → 동적 화면명 | `usePageTitle()` 훅 + `<AppLayout>` 갱신 |
| 3 | 모델명/품목명 한 행 좌우 분리 (작업지시서) | `<DispatchView>` 라인 표 7-col grid |
| 4 | 상세에서 규격 입력 가능 | `<LineRow>` 10-col + `SlipLine.specification` |
| 5 | 수량 옆 빈 열 제거 (작업지시서) | `<DispatchView>` 라인 표 7-col |
| 6 | 배송지/연락처/특이사항 14pt 본문 | `--print-text-base: 14pt` 토큰 |
| 7 | 결재란 1×5 horizontal | `.dispatch-roles` 5-col grid |
| 8 | 용달기사/인수자 서명 잘리지 않도록 | A4 273mm 본문 영역 재배치 + 80×35mm 서명 박스 |
| 9 | 출고인/검수인 자동 (BE 자동 채움) | `Slip.dispatcher/inspector` 응답 + DispatchView 결재란 자동 채움 |

---

## 1. FE (Team-Sales-Polish-2 FE)

### 1.1 신규/변경 파일

| 파일 | 종류 | 설명 |
| ---- | ---- | ---- |
| `clients/web/design-system/src/tokens/tokens.css` | 갱신 | Slice A 신규 토큰 그룹 (progress / page-header / print) append |
| `clients/web/design-system/src/components/ProgressBar/ProgressBar.tsx` | **신규** | 10단계 + 분기 시각화 컴포넌트 |
| `clients/web/design-system/src/components/ProgressBar/ProgressBar.module.css` | **신규** | ProgressBar 스타일 (노드 32px / 연결선 / 라벨) |
| `clients/web/design-system/src/components/ProgressBar/ProgressBar.stories.tsx` | **신규** | 9 stories (각 단계 + REJECTED + CANCELED + Clickable + WithHistory) |
| `clients/web/design-system/src/components/ProgressBar/index.ts` | **신규** | export barrel |
| `clients/web/design-system/src/components/SlipStatusBadge/SlipStatusBadge.tsx` | 갱신 | INSPECTING enum 값 추가 (10단계로) |
| `clients/web/design-system/src/components/SlipStatusBadge/SlipStatusBadge.stories.tsx` | 갱신 | INSPECTING 포함 11종 stories |
| `clients/web/design-system/src/components/LineRow/LineRow.tsx` | 갱신 | 10-col grid (규격 input 추가) + `LineDraft.specification` |
| `clients/web/design-system/src/components/LineRow/LineRow.module.css` | 갱신 | grid-template 10 cols + `.specInput` + `.cellSpec` |
| `clients/web/design-system/src/components/LineRow/LineTableHeader.tsx` | 갱신 | 규격 컬럼 thead 추가 |
| `clients/web/design-system/src/components/LineRow/LineRow.stories.tsx` | 갱신 | `specification` 필드 + 신규 stories (WithSpecification / EmptySpecification) |
| `clients/web/design-system/src/index.ts` | 갱신 | ProgressBar export |
| `clients/desktop/src/renderer/stores/pageTitle.ts` | **신규** | zustand store (title + meta) |
| `clients/desktop/src/renderer/hooks/usePageTitle.ts` | **신규** | useEffect 기반 훅 (mount set / unmount cleanup) |
| `clients/desktop/src/renderer/components/AppLayout.tsx` | 갱신 | header `<h2>` 동적 화면명 + meta bracket |
| `clients/desktop/src/renderer/styles/global.css` | 갱신 | `.app-header` Slice A 토큰 적용 + DispatchView CSS 전체 재작성 |
| `clients/desktop/src/renderer/api/slip.ts` | 갱신 | `SlipLineDetail.specification` + `SlipDetail.{dispatcher,inspector,ownerDepartment,ownerFullName,shippingAddress,contactPhone}` + `SlipTransitionAction = ... \| 'inspect'` |
| `clients/desktop/src/renderer/api/mock.ts` | 갱신 | SAMPLE_LINES 에 `specification`, MOCK_SLIPS 7건 (INSPECTING 2건 신규) + dispatcher/inspector mock |
| `clients/desktop/src/renderer/stores/session.ts` | 갱신 | `canTransitionSlip` 에 `inspect` 추가 |
| `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` | 큰 갱신 | ProgressBar 카드 + 결재 정보 카드 (출고인/검수인) + INSPECTING transition + usePageTitle |
| `clients/desktop/src/renderer/routes/SlipFormPage.tsx` | 갱신 | LineDraft.specification 초기값 + onSpecificationChange + usePageTitle |
| `clients/desktop/src/renderer/routes/SlipListPage.tsx` | 갱신 | usePageTitle ('판매조회'/'구매조회') |
| `clients/desktop/src/renderer/routes/DashboardPage.tsx` | 갱신 | usePageTitle ('대시보드') |
| `clients/desktop/src/renderer/routes/WarehousesPage.tsx` | 갱신 | usePageTitle ('창고 관리') |
| `clients/desktop/src/renderer/routes/TransferListPage.tsx` | 갱신 | usePageTitle ('재고이동') |
| `clients/desktop/src/renderer/routes/TransferFormPage.tsx` | 갱신 | usePageTitle ('새 재고이동') |
| `clients/desktop/src/renderer/routes/TransferDetailPage.tsx` | 갱신 | usePageTitle ('재고이동 상세', transferNo) |
| `clients/desktop/src/renderer/print/InvoiceView.tsx` | 갱신 | usePageTitle ('거래명세서', slipNo) |
| `clients/desktop/src/renderer/print/DispatchView.tsx` | 큰 갱신 | RoleCell × 5 + 7-col 표 + 80×35mm 서명 + bottom-group page-break-avoid + usePageTitle |

### 1.2 Designer spec 충실도 매트릭스

| Designer spec 항목 | 구현 결과 | 위치 |
| ------------------ | ---------- | ---- |
| `<ProgressBar>` 10단계 정의 | `PROGRESS_STEPS` 상수 (DRAFT~CONFIRMED) | `ProgressBar.tsx` § PROGRESS_STEPS |
| ProgressBar visual states (done/current/todo) | `state-done` / `state-current` / `state-todo` 클래스 + `--progress-step-bg-*` 토큰 | `ProgressBar.module.css` |
| ProgressBar REJECTED ⊗ 빨간 채움 | `branch-rejected` 클래스 + `--progress-fill-rejected` | 동상 |
| ProgressBar CANCELED ⊗ 회색 채움 | `branch-canceled` 클래스 + `--progress-fill-canceled` | 동상 |
| ProgressBar 분기 사유 표시 | `<p class="branchReason">` (rejected/canceled variant) | `ProgressBar.tsx` |
| ProgressBar 노드 hover tooltip | `title` attr (history actorFullName) | 동상 |
| ProgressBar 접근성 role/aria | `role="progressbar"` + `aria-valuemin/max/now` + 노드 `aria-label` | 동상 |
| ProgressBar pulse 애니메이션 | `@keyframes progressCurrentPulse` 2s ease-in-out infinite | `ProgressBar.module.css` |
| AppHeader 56px 높이 + 1px 하단 선 | `.app-header { height: 56px; border-bottom: 1px solid }` | `global.css` |
| AppHeader 화면명 20px 600 + meta bracket 14px secondary | `--page-title-size/weight` + `--page-title-meta-*` | 동상 |
| `usePageTitle()` 훅 | `useEffect` mount set / unmount cleanup | `hooks/usePageTitle.ts` |
| 라우트 13개 매핑 (Designer wireframes.md § 1.3) | 13 라우트 모두 적용 (Login 제외) | `routes/*.tsx` |
| LineRow 10-col grid | `grid-template-columns: ... 100px ...` (규격 컬럼) | `LineRow.module.css` |
| LineRow 규격 input placeholder "예: 220V" | `<input ... placeholder="예: 220V" maxLength={50} />` | `LineRow.tsx` |
| LineRow 규격 빈 값 허용 | `LineDraft.specification: ''` 초기값, `'-'` 표시 | 동상 |
| DispatchView 결재란 1×5 grid 38mm × 22mm | `grid-template-columns: repeat(5, 1fr)` + `--print-approval-h: 22mm` | `global.css` |
| DispatchView 출고인 셀 자동 채움 (이름 12pt + 시각 9pt) | `<RoleCell label="출고인" value={dispatcher.fullName} time={dispatcher.signedAt} />` | `DispatchView.tsx` |
| DispatchView 검수인 셀 자동 채움 | 동상 | 동상 |
| DispatchView 라인 표 7-col 빈 열 제거 | thead 6 컬럼 (월/일/모델/품/규격/수량) + tfoot 합계 | 동상 |
| DispatchView 모델명/품목명 한 행 좌우 | `.col-model` + `.col-product` 별도 td | 동상 |
| DispatchView 본문 14pt | `.dispatch-section .content { font-size: var(--print-text-base) }` | `global.css` |
| DispatchView 서명 박스 80×35mm 가로 | `grid-template-columns: repeat(2, 80mm)` + gap 6mm | 동상 |
| DispatchView 서명 박스 page-break-inside: avoid | `.dispatch-signatures` + `.dispatch-bottom-group` | 동상 |
| DispatchView A4 273mm budget | `--print-content-h: 273mm` + 섹션별 budget 토큰 | `tokens.css` |
| `Slip.dispatcher / inspector` 응답 필드 | `SlipApprovalActor` 인터페이스 + `SlipDetail.dispatcher/inspector` | `api/slip.ts` |
| `SlipLine.specification` 응답 필드 | `SlipLineDetail.specification: string \| null` | 동상 |
| INSPECTING 신규 enum + transition | `SlipStatus = ... \| 'INSPECTING'`, `SlipTransitionAction = ... \| 'inspect'` | `SlipStatusBadge.tsx` + `api/slip.ts` |

### 1.3 검증 결과

| 검증 | 결과 |
| ---- | ---- |
| `npm run typecheck` (desktop) | PASS (0 에러) |
| `npm run lint` (desktop) | PASS (0 에러) |
| `npm run lint` (design-system) | PASS (0 에러 / 0 경고) |
| `npm run build` (design-system) | PASS — `dist/style.css 33.20 kB` / `dist/index.js 53.84 kB` (gzip 14.48 kB) |
| `npm run build` (desktop) | PASS — `out/renderer/index.js 834.40 kB` / `assets/index-*.css 63.98 kB` |
| `npm run build-storybook` | PASS — `storybook-static/` 생성 |

### 1.4 잠재 이슈

- **번들 크기 증가**: 1차 슬라이스 (PR #20) 의 838KB 와 거의 동일 (834.4KB). ProgressBar 추가가 미미한 영향.
- **CSS 크기 증가**: 약 +5KB (DispatchView 재작성 + ProgressBar). 인쇄용 토큰 그룹 모두 :root 에 정의 — 미사용 페이지에서도 메모리 보유 (무시 가능).
- **BE 응답 의존성**: `SlipDetail.dispatcher / inspector / ownerDepartment / ownerFullName / shippingAddress / contactPhone` 필드는 BE 가 응답해야 동작. BE 미구현 시 모두 `undefined` 로 동작 (빈 셀 표시). Mock 모드는 채워서 응답.
- **분기 노드 위치 휴리스틱**: history 가 없을 때 REJECTED 는 SENT (idx 2), CANCELED 는 DRAFT (idx 0) 를 마지막 done 으로 가정. BE 가 history 응답 시 정확한 인덱스 사용.
- **LineRow 호환성 깨짐**: `LineDraft.specification` + `onSpecificationChange` 신규 필수 필드 추가 — 기존 호출자는 모두 갱신 필요 (SlipFormPage 만 사용 중이므로 한 곳만 갱신).
- **다크 모드**: Slice A 토큰은 light theme 만. dark mode 는 Slice C 이후 별도 검토 (Designer tokens.md § 4).
- **Storybook chunk size warning**: storybook-static 에 884KB chunk (Storybook 자체 issue). 운영 빌드 영향 없음.

---

## 2. (PM 통합 시 채움)

### 2.1 BE / QA / DevOps

(다른 팀 산출물은 PM 통합 단계에서 본 문서에 추가)
