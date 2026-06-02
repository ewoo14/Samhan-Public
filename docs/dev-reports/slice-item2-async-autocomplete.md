# dev-report — item 2 공용 AsyncAutocomplete<T> 추출

- **일자**: 2026-06-02
- **PR**: #345 squash `4c07e580`
- **유형**: FE 내부 리팩터 (design-system, 공개 API 불변, BE/Flyway 무관)
- **spec/plan**: `docs/superpowers/specs/2026-06-02-async-autocomplete-extract-design.md` / `docs/superpowers/plans/2026-06-02-async-autocomplete-extract.md`

## 1. 배경
ProductAutocomplete(450)·PartnerAutocomplete(465)가 async 서버검색 typeahead로 95% 동일. 차이는 옵션 타입·식별 키·입력 표시값·옵션 행 렌더·기본 label/placeholder/검색 prop명뿐.

## 2. 변경 요약
- 신규 제네릭 `AsyncAutocomplete<T>`(430) — 전 로직(debounce·인스턴스 seq stale 무시·blur 게이트·키보드 내비·compact/FormField 분기·로딩/빈/에러·타이머 정리) + 어댑터 props `getKey`/`getInputLabel`/`renderOption`/`listboxLabel`/`matchExact`.
- `ProductAutocomplete` 450→87, `PartnerAutocomplete` 465→93 wrapper 축소. `ProductOption`/`PartnerOption` 타입·`searchProducts`/`searchPartners` prop명·기본 label/placeholder·forwardRef 보존 → **소비처(SlipFormPage·LineRow) 0 변경**.
- CSS 단일화 `AsyncAutocomplete.module.css`(구조 클래스 + optionPrimary/Secondary/Tertiary/Sep). 구 `ProductAutocomplete.module.css`·`PartnerAutocomplete.module.css` 삭제.
- **focus-ring 토큰 채택**(`--focus-ring-brand`/`--focus-ring-danger`) — Product 하드코딩 rgba 제거, AC-2 백포트 TODO 흡수.
- design-system barrel `AsyncAutocomplete`/`AsyncAutocompleteProps<T>` export 추가.

순감 900→610 + CSS 2개 삭제.

## 3. 파일별 변경
| 파일 | 변경 |
|---|---|
| `AsyncAutocomplete/{AsyncAutocomplete.tsx, .module.css, index.ts}` | 신규(제네릭+CSS+barrel) |
| `ProductAutocomplete/ProductAutocomplete.tsx` | wrapper 축소(ProductOption 유지) |
| `PartnerAutocomplete/PartnerAutocomplete.tsx` | wrapper 축소(PartnerOption 유지) |
| `ProductAutocomplete.module.css`, `PartnerAutocomplete.module.css` | 삭제 |
| `design-system/src/index.ts` | AsyncAutocomplete export |

## 4. 검증
- design-system `tsc --noEmit` + `vite build` + `build-storybook` exit 0.
- **회귀**: `ac-2-product-autocomplete`·`ac-3-partner-autocomplete` Playwright 스펙이 item 3-A2 CI 게이트(`Desktop Playwright`)에서 자동 실행·통과 → 동작 불변 실증. CI 29/29 green.
- 공개 API 불변(ProductOption/PartnerOption/컴포넌트/prop 시그니처) → 소비처 무변경.

## 5. dual 5/2-agent 리뷰 (수렴)
- Claude 2-agent(API보존/동작동등 + CSS/토큰/a11y): 코드 P0/P1 0. P2(모델명 tabular-nums primary→secondary 이동) fix(optionPrimary 복원).
- Codex cross-check: 추가 P0/P1 0 APPROVE. 비차단 관찰(minChars 축소 시 in-flight seq 무효화 약함 — 기존 동작, minChars 1에서 재현 약).

## 6. 후속(비차단)
- WarehouseAutocomplete/WarehouseSelector(sync 변형) 통합 — 별도 평가(범위 외, D-AAC-01).
- 단위 테스트(.test.tsx) 부재 — 회귀는 ac-2/ac-3 Playwright + storybook 의존.
