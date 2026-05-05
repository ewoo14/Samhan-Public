# Mobile v4 디자인 통합 fix — Scope 정의서

> 작성일: 2026-05-05
> 브랜치: `chore/mobile-v4-design-integration-legacy-match` (base = `origin/main` `ccb7f42`)
> 회고 PR: PR #66 (textColor 4행 hotfix → close 됨)
> 회고 사용자 피드백: "PR66의 경우 전체적으로 디자인이 모두 다름" / "PR은 한 번에 통합해서 QA 확인 후 TM 승인하게 업로드 요청"

---

## §1. 배경

PR #66 은 `HomeScreen.tsx` 의 4 카테고리 textColor 만 한 줄(line) 단위로 패치했으나,
사용자 (개발책임자) 가 "전체적으로 디자인이 모두 다름" 으로 reject. 단일 hotfix 패턴이
회고 패턴 위반 (`feedback_integrated_pr_pattern.md`).

본 통합 fix 는 **legacy `migration/source/scripts/partner-order/index.html` 의 모바일 viewport
(`@media (max-width: 1280px)` + `body.mobile-mode .top { display:none !important }`) 1:1 일치**
를 단일 PR 로 적용.

---

## §2. legacy 출처 (1:1 매핑 대상)

| legacy line | CSS / HTML | RN 적용 위치 |
|---|---|---|
| line 11 | `--c-strong:#111827` | `legacyVars.cStrong` — selectBigText.color |
| line 119 | `.mobile-gate { display:flex; flex-direction:column; gap:16px; margin:20px 0 12px }` | `legacyMobileGateStyles.mobileGate` |
| line 121 | `.select-big { width:100%; height:150px; border:1px solid #000; border-radius:18px; font-weight:800; font-size:36px }` | `legacyMobileGateStyles.selectBig` |
| line 122 | `.select-home {background:#eef2ff;border-color:#c7d2fe} ...` | `legacyMobileGateStyles.selectHome/Single/Comm/Old` |
| line 195 | `body.mobile-mode .top { display:none !important }` | titleBar 삭제 |
| line 685~689 | `<div class="mobile-gate"><button class="select-big select-home">홈멀티</button>...</div>` | HomeScreen.tsx CATEGORIES.map() |

---

## §3. P0 4건 (legacy 1:1 일치 의무)

### P0 #1 — 4 카테고리 textColor 통일 (#111827)

이전: 4 entry 가 각각 `#3730A3 / #0E7490 / #9A3412 / #6B21A8` (legacy 미존재)
변경: `legacyMobileGateStyles.selectBigText.color = legacyVars.cStrong` (#111827) 1곳에서만 정의.
`CATEGORIES` array entry 의 `textColor` 필드 폐기.

### P0 #2 — DC notice 박스 완전 삭제

이전: `dcError` 가 있을 때 `View styles.dcErrorBox` 노출 (PR #61 에서 정상 안내는 삭제됨)
변경: View 자체 폐기. `dcError` 는 `useEffect` 안 `console.warn` 만.
`styles.dcNotice / dcNoticeText / dcErrorBox / dcErrorText` stylesheet 객체 제거.

근거: PM 결정 U3 — dcConfigStore 의 backend 적용 (calcDcPrice) 은 그대로 유지, **RN 시각 노출만 제거**.

### P0 #3 — 상단 titleBar 삭제

이전: HomeScreen.tsx 에 자체 `titleBar` View (주문서 / 거래처명+코드) 표시
변경: View + styles 통째 제거.

근거:
- legacy line 195 `body.mobile-mode .top { display:none !important }` 모바일에서 .top 숨김
- 거래처명/사업자번호는 WebView 안 legacy 가 표시 (이중 표시 방지 + UUID 비공개 원칙)

### P0 #4 — mobile-gate paddingBottom 제거

이전: `legacyMobileGateStyles.mobileGate.paddingBottom: 30` (legacy 미존재)
변경: `paddingBottom` 제거 → `marginTop: 20, marginBottom: 12, paddingHorizontal: 16` 만 유지.

근거: legacy line 119 `margin: 20px 0 12px` 일관 — 본 styled 패딩 30 은 추가 메뉴 영역 위 과다 여백 원인.

---

## §4. P1 보존 (정정 #17 의도 그대로)

`extraMenuSection` View + 5 Pressable + style 모두 보존:
- 임의 분기계산 (`#F5F3FF / #C4B5FD`)
- 견적·주문하기 (`#ECFEFF / #67E8F9`)
- 과거 발송내역 확인 (`#F0FDF4 / #86EFAC`)
- 주문저장 (`#FFFBEB / #FCD34D`)
- 저장내역 (`#FFF7ED / #FDBA74`)

근거: 거래처 사용성 (legacy `#btnOpenBranch / #btnSendOrder / #btnHistory / #btnSaveDraft / #btnDraftList`
의 모바일 진입 우회) — PM 결정 U1 보존.

---

## §5. capture script 신규 (PM 결정 U5)

### §5.1 폐기

`clients/mobile/scripts/capture-v4.cjs` 삭제 (mock overlay 6장 — 사용자 reject 패턴).

### §5.2 신규

`clients/mobile/scripts/capture-home.cjs` — mobile-staff v3 의 `capture-v3.cjs` 패턴 1:1 참조:

- iOS viewport 390x844 + Android viewport 412x915 (총 2 viewport)
- 각 viewport 5장 = **총 10장**:
  1. `01-bizgate` — 실 expo export bundle 진입 (RN web 자체 렌더 — 어두운 .biz-box layout)
  2. `02-home-after-fix` — HomeScreen mock (P0 fix 결과 — 4 카테고리 검정 + paddingBottom 0)
  3. `03-home-extra-menu` — HomeScreen mock 스크롤 (extraMenuSection 5 메뉴)
  4. `04-webview-order` — LegacyOrder WebView placeholder (legacy 임베드 영역)
  5. `05-bottom-tab` — Home + Bottom Tab (홈/주문/알림/프로필)
- expo dev server 미가동 시 abort + 사용자 안내
- 출력: `docs/qa/migration-fe-mobile-v4-design-audit/{iOS,Android}-{01..05}-*.png`

본 환경 (Windows / external backend 미가동) 에서 BizGate axios POST 가 cross-origin + backend 미가동
으로 실패 → BottomTab 진입 불가. 따라서 mock HTML 로 P0 fix 의 시각 결과를 직접 검증.

---

## §6. mobile-staff v3 reference (비교용)

`docs/qa/migration-fe-mobile-staff-v3/{01,02,03}-*.png` 3장 — `origin/feature/migration-fe-mobile-staff-v3-rewrite`
브랜치에서 cherry-pick. 본 통합 PR 에는 비교 reference 로만 첨부 (mobile-staff v3 PR 은 별도 발행).

---

## §7. 변경 매트릭스 요약

| File | 변경 | 라인 | P# |
|---|---|---|---|
| `clients/mobile/src/screens/home/HomeScreen.tsx` | 전체 rewrite (titleBar/DC notice 삭제, textColor 단순화) | -50 +30 | P0 #1 #2 #3 |
| `clients/mobile/src/styles/legacyMobile.ts` | mobileGate.paddingBottom 제거 + selectBigText.color 추가 | -1 +12 | P0 #1 #4 |
| `clients/mobile/scripts/capture-home.cjs` | 신규 (mobile-staff v3 패턴) | +280 | U5 |
| `clients/mobile/scripts/capture-v4.cjs` | 삭제 | -417 | U5 |
| `clients/mobile/package.json` | `capture:home` script 추가 | +1 | U5 |
| `docs/dev-reports/mobile-design-integration-scope.md` | 본 정의서 신규 | +200 | docs |
| `docs/qa/migration-fe-mobile-v4-design-audit/*.png` | QA 캡처 10장 신규 | +10 files | QA |
| `docs/qa/migration-fe-mobile-staff-v3/*.png` | reference 3장 cherry-pick | +3 files | ref |

---

## §8. 미결 항목 — 통합 PR 적용 결과 (2026-05-05)

| 미결 | 결정 | 적용 결과 |
|---|---|---|
| U1 추가 메뉴 5개 | 보존 (정정 #17 의도, 거래처 사용성) | extraMenuSection 그대로 보존 |
| U2 titleBar 후 거래처명 | legacy 만 (WebView 안 표시) | titleBar View 삭제, partnerCode/Name 노출 X |
| U3 dcConfigStore | 유지 (backend 적용 그대로 + RN 시각만 제거) | dcConfigStore import 유지, dcNotice/dcError View 제거, error 는 console.warn 만 |
| U5 capture-v4.cjs | 폐기 + capture-home.cjs 신규 대체 | capture-v4.cjs git rm, capture-home.cjs 신규 (mobile-staff v3 패턴 1:1) |
| U6 origin/main 통합 검증 | 완료 — PR #50/#52/#53/#54/#58/#61 모두 머지됨 | base = `ccb7f42` 확인 |

---

## §9. 검증

- `npm run typecheck` (Mobile v4) — PASS
- `npx expo export --platform web` — PASS (dist 생성)
- `node scripts/capture-home.cjs` — 10장 모두 정상 생성
- 라인 수: HomeScreen.tsx 265 → 220 (-45), legacyMobile.ts 변경 +11/-2

---

## §10. 회고 가드

본 PR 발행 시 다음 memory 가드 준수:
- `feedback_integrated_pr_pattern.md` — 통합 PR 1개 + QA 캡처 + TM 승인
- `feedback_pr_qa_screenshots.md` — QA 캡처 인라인 첨부 13장 (Mobile v4 10 + mobile-staff v3 reference 3)
- `feedback_pr_ci_monitoring.md` — PR 발행 후 즉시 `gh pr checks --watch`
- `feedback_korean_commits.md` — 한국어 commit/PR 본문
- `feedback_role_naming_full.md` — role 풀네임
- `feedback_powershell_utf8_writes.md` — PR body Write tool 만
