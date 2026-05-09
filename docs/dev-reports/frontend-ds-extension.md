# Frontend DS Extension (legacy migration 사전) — 개발 리포트

본 리포트는 legacy (Apps Script estimate / partner-order) 마이그레이션의 Frontend Phase 6 사전 작업으로 `@samhan/design-system` 에 6 신규 컴포넌트를 추가한 결과를 정리합니다.

> 입력 출처: `migration/analysis/06-frontend-design.md` §3.2 / `migration/decisions/DOMAIN-EXTENSIONS.md` §3 §4 / `migration/decisions/DECISIONS.md` (F1 / F3 / F7).
>
> 브랜치: `feature/migration-ds-extension` (F7 결정에 따라 4 sub-team 진입 전에 별도 PR 우선 merge).

---

## 0. 5-team 가드 예외 사유 (의무 명기)

본 작업은 `feedback_multi_agent_team_pattern.md` 의 **5-team (BE/FE/Designer/QA/DevOps) parallel** 패턴에서 의도적으로 이탈했습니다.

| 가드 | 위반 여부 | 사유 |
| --- | --- | --- |
| 5-team 패턴 | **이탈** | DS 패키지 단독 변경 — BACKEND/DEVOPS 무관 (서버 코드 0, infra 변경 0). monorepo `file:` workspace dependency 로 4 sub-team (A/B/C/D) 가 그대로 import. |
| Designer 추가 | **이탈** | DS 6 컴포넌트는 §3.2 명세가 이미 props/사용처 확정 — 별도 디자인 mock 불필요. 단, **PrintPreview** 는 `feedback_print_design_iteration.md` 가드에 따라 Sub-team A 의 EstimatePrintRenderer 합류 시 3~5회 iteration 필수. |
| QA 분리 | **단일 통합** | 단위 테스트는 본 agent (FE 책임) 가 작성. SpringBootTest IT 무관. |

본 예외는 PM (Claude) 디스패치 단계에서 사전 승인된 "DS extension 별도 PR" (DECISIONS.md F7) 정책에 따른 것이며, 통상 슬라이스에는 적용되지 않습니다.

---

## 1. 신규 6 컴포넌트 표

| # | 컴포넌트 | 사용처 | props 요약 | Storybook | 파일 |
| --- | --- | --- | --- | --- | --- |
| 1 | `<EstimateLineRow>` | desktop EstimateFormPage / web/order-app OrderFormPage 라인 grid | `lineNumber, model, productName?, spec?, qty, releasePrice, deliveryPrice, discountRate?, lineAmount, onQtyChange?, onDelete?, onSpecClick?, readOnly?` | 3 stories (기본/읽기전용/할인없음) | `EstimateLineRow/{tsx,module.css,stories.tsx,test.tsx,index.ts}` |
| 2 | `<BundleExpandToggle>` | desktop / web/order-app 라인의 BUNDLE 행 옆 inline | `mode: 'EXPAND' \| 'KEEP', onChange, disabled?, ariaLabel?` | 4 stories (EXPAND/KEEP/Disabled/Inline) | `BundleExpandToggle/...` |
| 3 | `<ProductSpecList>` | EstimateLineRow `spec` slot / SpecModal / PrintPreview | `specs: ProductSpec[], mode: 'screen'\|'print', templateOrder?, layout: 'inline'\|'card'\|'table', emptyMessage?` | 5 stories (Table×Screen/Print, Card, Inline, Empty) | `ProductSpecList/...` |
| 4 | `<SpecAddModal>` | product-service admin ProductSpecEditor `[+ 스펙 추가]` | `open, onClose, category, recommended: SpecKeyTemplate[], existingKeys, onAdd(key, val, unit?)` | 3 stories (HOME_MULTI×등록기존, 빈, OTHER) | `SpecAddModal/...` |
| 5 | `<CategoryTabs>` | desktop / web/order-app 카테고리 선택, ProductPickerModal 필터 | `value, onChange, categories?, disabled?, counts?, ariaLabel?` | 4 stories (기본/counts/disabled/4탭) | `CategoryTabs/...` |
| 6 | `<PrintPreview>` | estimate/slip/partner-order print 페이지 wrapper | `mode: 'pdf'\|'browser', children, paperSize, orientation, pdfRenderer?, showPrintButton?, onPrint?` | 5 stories (A4세로/A4가로/A5/PDF fallback/PDF renderer) | `PrintPreview/...` |

각 컴포넌트는 5 파일 (`<name>.tsx` / `<name>.module.css` / `<name>.stories.tsx` / `<name>.test.tsx` / `index.ts`) 로 구성하며, `clients/web/design-system/src/index.ts` 에 6 export 추가됩니다.

---

## 2. 회고 가드 적용 검증

| 가드 | 적용 여부 | 비고 |
| --- | --- | --- |
| `feedback_function_documentation.md` (3-layer) | **(1) 한국어 JSDoc**: 6/6 컴포넌트 모두 적용 — 출처 (`§3.2`) 명시 + `@example` + 사용처 다중 · **(2) Storybook 자동 등재**: stories 파일 6/6 + 각 2~5 story · **(3) dev-reports**: 본 문서 | 누락 0 |
| `feedback_uuid_no_user_visibility.md` | **EstimateLineRow** 의 prop 에 internal id 없음 — 사용자 노출 식별자는 `model` (모델명) 만. `lineNumber` 는 1-base 표시 번호. | 가드 통과 |
| `feedback_print_design_iteration.md` | **PrintPreview** 만 해당 — JSDoc 에 "단번 완성 가정 금지" 가드 명기 + Sub-team A QA iteration 의무 명시 (`feedback_print_design_iteration.md` 인용). 본 wrapper 는 외곽 (toolbar/stage/paper border) 만 책임, 본문 인쇄 양식 디자인은 sub-team A 가 3~5회 정정. | 가드 통과 |
| `feedback_korean_commits.md` | 본 commit / PR / 본 dev-reports 모두 한국어. JSDoc 한국어. CSS 주석 한국어. | 가드 통과 |
| `feedback_role_naming_full.md` | 본 문서 내 권한/역할 표기 풀네임 (BACKEND/DEVOPS/FRONTEND/DESIGN/QA/MASTER/MANAGER) 사용. | 가드 통과 |
| `feedback_multi_agent_team_pattern.md` | **이탈** — 사유는 본 문서 §0 명기. | 예외 처리 |
| `feedback_pm_integration_build_check.md` | 본 단일 agent 작업이라 4-team merge 통합 빌드 가드 무관. typecheck + lint + build + storybook build 4 단계 자체 검증 완료 (§4). | 가드 통과 |
| `feedback_powershell_utf8_writes.md` | dev-reports / commit message 모두 Write 도구 또는 git heredoc 사용. PowerShell `Set-Content` 미사용. | 가드 통과 |

---

## 3. 핵심 결정 인용 (DECISIONS / DOMAIN-EXTENSIONS)

### F1 하이브리드 (DECISIONS.md)
- **PrintPreview**: 외곽 wrapper (toolbar / paper / shadow) 는 Samhan Public DS 토큰 기반. 본문 (`children`) 의 인쇄 양식 디자인은 Sub-team A (estimate-service Frontend) 가 legacy CSS 보존 + iteration. F1 하이브리드를 컴포넌트 경계에서 구현.

### F3 react-pdf (DECISIONS.md)
- **PrintPreview** 의 `pdfRenderer` prop pattern: design-system 패키지는 `react-pdf` 직접 import 안함 (peerDep 회피 + 번들 크기 가드). 호출자 (Sub-team A) 가 `pdfRenderer={(node) => <PDFViewer>{node}</PDFViewer>}` 로 주입. 미주입 시 자동 브라우저 print fallback (toolbar 메타에 "fallback" 표시).

### F7 별도 PR 우선 (DECISIONS.md)
- 본 작업은 `feature/migration-ds-extension` 브랜치 단독 PR. 4 sub-team (A/B/C/D) 는 본 PR merge 후 진입.

### D15 — SpecKeyTemplate 추천 vs 자유 (DOMAIN-EXTENSIONS §4)
- **SpecAddModal**: 추천 chip 중 `existingKeys` 와 중복 키는 `disabled` + `text-decoration: line-through`. 자유 입력으로 중복 키 입력 시 inline 에러 + 추가 버튼 disabled. Backend 의 409 strict (unique constraint `(productMasterId, specKey)`) 와 1:1 가드.

### D18 — 인쇄 ProductSpec 출력 순서 (DOMAIN-EXTENSIONS §4 / Round 3 §5)
- **ProductSpecList** 의 `mode` prop:
  - `'screen'` (기본) → `ProductSpec.displayOrder` 기준 (사용자가 drag&drop 으로 정의한 순서)
  - `'print'` → `templateOrder: Record<specKey, number>` (= `SpecKeyTemplate.displayOrder`) 기준 (카테고리 표준 순서로 인쇄 일관성 보장). 없는 키는 후순위로 `ProductSpec.displayOrder` 적용.

### D17 — usageScope=BOTH 카테고리 중복 UX (DOMAIN-EXTENSIONS §3)
- **CategoryTabs** 는 `disabled?: readonly EstimateCategory[]` prop 으로 데이터 없는 카테고리를 시각적으로 비활성. `counts` prop 으로 라인 수 badge 표시 → 사용자에게 BOTH 품목의 양쪽 노출 위치 명확.

### estimateCategory enum 일관성
- **CategoryTabs** 와 **SpecAddModal** 모두 같은 `EstimateCategory` 사용 → canonical 정의는 `CategoryTabs/CategoryTabs.tsx`. `SpecAddModal` 은 `import type { EstimateCategory } from '../CategoryTabs/CategoryTabs'` 로 재사용. `src/index.ts` 의 wildcard re-export 충돌 방지.

---

## 4. 검증 결과 (typecheck / lint / build / storybook)

본 worktree 안에서 실행:

```
cd clients/web/design-system

# 1) build typecheck (stories/tests 제외)
npx tsc -p tsconfig.build.json --noEmit
# → exit 0 (오류 0)

# 2) full typecheck (stories/tests 포함)
npx tsc -p tsconfig.json --noEmit
# → exit 0 (오류 0)

# 3) lint — 6 신규 컴포넌트 + index.ts
npx eslint src/components/EstimateLineRow src/components/BundleExpandToggle src/components/ProductSpecList src/components/SpecAddModal src/components/CategoryTabs src/components/PrintPreview src/index.ts
# → exit 0 (오류 0)

# 4) 전체 lint — 기존 코드 (signature-slice-C SignaturePad) 의 사전 존재 오류 2건 + warning 1건 발견. 본 PR 범위 외이므로 수정하지 않음.
npm run lint
# → 2 errors + 1 warning (모두 SignaturePad 기존 코드)

# 5) build (vite + rollup-types)
npm run build
# → ✓ built in 2.40s · dist/index.js 92.66 kB · dist/style.css 59.07 kB

# 6) Storybook build
npm run build-storybook
# → ✓ built in 5.95s · 6 신규 stories chunk 생성 확인 (storybook-static/assets/{EstimateLineRow,BundleExpandToggle,ProductSpecList,SpecAddModal,CategoryTabs,PrintPreview}.stories-*.js)
```

**결과**: typecheck pass / lint pass (신규 코드) / build pass / storybook build pass. 기존 SignaturePad 의 lint 오류 2건은 본 PR 범위 외 (signature-slice-C 합본 PR `639a675`).

---

## 5. 단위 테스트 (`*.test.tsx`)

본 design-system 패키지에 vitest 가 아직 설치되지 않아, 6 신규 `.test.tsx` 파일은 **주석 블럭 안에 실행 가능 spec 보존** + `export {}` 빈 ES module 형태로 작성. typecheck / lint 영향 없음.

각 파일에 활성화 절차 (`npm i -D vitest @testing-library/react @testing-library/jest-dom jsdom` + vitest.config.ts 추가) 명기. 이는 후속 슬라이스 (Sub-team A 진입 시) 또는 Phase 6 QA 합본에서 한 번에 활성화 권장.

총 spec 건수 (활성화 시 재생):

| 컴포넌트 | spec 건수 | 주요 검증 |
| --- | --- | --- |
| EstimateLineRow | 4 | 렌더 / 수량 음수 차단 / readOnly 비활성 / 할인 0 시 "-" |
| BundleExpandToggle | 4 | aria-pressed / 변경 호출 / 활성 재클릭 무시 / disabled 무시 |
| ProductSpecList | 4 | screen 정렬 / print 정렬 / unit 자동 합성 / empty |
| SpecAddModal | 4 | 중복 chip disabled / chip 선택 input 채움 / 자유 입력 중복 가드 / 정상 추가 |
| CategoryTabs | 5 | 5 탭 렌더 / aria-selected / onChange / disabled 무시 / counts badge |
| PrintPreview | 5 | pdf fallback / pdfRenderer 호출 / A4 portrait 크기 / A4 landscape swap / onPrint 우선 |
| **합계** | **26** | |

---

## 6. 향후 Sub-team A/B/C/D import 패턴

각 sub-team 은 본 PR merge 후 다음과 같이 import:

```tsx
// clients/desktop/src/renderer/routes/EstimateFormPage.tsx (Sub-team A)
import {
  EstimateLineRow,
  BundleExpandToggle,
  CategoryTabs,
  ProductSpecList,
  SpecAddModal,
  PrintPreview,
  type EstimateCategory,
  type ProductSpec,
  type SpecKeyTemplate,
  type BundleExpandMode,
} from '@samhan/design-system'

// clients/web/order-app/src/pages/OrderFormPage.tsx (Sub-team C)
import {
  EstimateLineRow,
  BundleExpandToggle,
  CategoryTabs,
  type EstimateCategory,
} from '@samhan/design-system'
```

monorepo `file:` workspace dependency 는 기존 `clients/desktop/package.json` / `clients/web/order-app/package.json` 의 `"@samhan/design-system": "file:../web/design-system"` (또는 동등) 설정으로 자동 인식.

### Sub-team 별 신규 컴포넌트 사용 매트릭스

| Sub-team | 범위 | EstimateLineRow | BundleExpandToggle | ProductSpecList | SpecAddModal | CategoryTabs | PrintPreview |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **A** | desktop 견적서 (estimate-service FE) | ✓ | ✓ | ✓ | ✓ (admin 일부) | ✓ | ✓ |
| **B** | desktop 주문조회 + 장기미발주 (read-only) | (read mode) | (read) | (read) | — | — | (PartnerOrderPrintPage) |
| **C** | web/order-app 신규 (partner-order FE Web) | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| **D** | mobile 신규 (RN, Expo) | (별도 RN 구현) | (별도 RN 구현) | (별도 RN 구현) | — | (별도 RN 구현) | (별도 RN 구현) |

Sub-team D 의 mobile (React Native) 는 본 컴포넌트를 import 하지 않고 디자인 spec 만 공유 (06-frontend-design.md §2.3.3). DS tokens 는 RN-호환 token 변환이 필요 (별도 작업).

---

## 7. 후속 작업 의무 (PR body 에 인용)

1. **Sub-team A** 진입 시: PrintPreview 안에 EstimatePrintRenderer skeleton 작성 + Edge 캡처 → CSS-only 미세 조정 3~5회 iteration (`feedback_print_design_iteration.md`).
2. **Phase 6 QA 합본 시**: vitest devDep 설치 + 6 `.test.tsx` 파일의 주석 블럭 활성화 + jsdom 환경에서 26 spec 실행.
3. **Mobile (Sub-team D)**: DS tokens 의 RN 호환 변환 (별도 슬라이스).
4. **EstimateCategory enum BE 동기화**: `service.product` 의 Java enum (`HOME_MULTI / SINGLE_SET / COMMERCIAL_MULTI / LEGACY / OTHER`) 와 일치 — Sub-team A 진입 시 Layer 4 (도메인 메서드 의미 정렬) 검증 의무.

---

## 8. PR 발행 안내

본 agent 는 commit + push 만 수행. PR 발행은 PM (Claude) 이 수동 검증 후 한국어 PR (TL → PM → MASTER 승인 체인 / `feedback_github_pr_workflow.md`) 발행.

**PR body 의무**:
- QA 결과 스크린샷 1장 이상 (`docs/qa/migration-ds-extension/*.png`) — Storybook 6 신규 컴포넌트 캡처 (`feedback_pr_qa_screenshots.md`).
- 본 dev-reports 링크.
- F7 별도 PR 우선 merge 명기 + 후속 4 sub-team 진입 안내.
