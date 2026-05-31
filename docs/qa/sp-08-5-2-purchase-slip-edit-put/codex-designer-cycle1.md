### Codex Designer 사이클 1 2a 리뷰 (head `a29bc83e`)

#### Claude 발견 평가

| 항목 | Codex 평가 | 사유 |
|---|---|---|
| D-C1-1 | **부분 해결** | `tokens.css:42-57` warning/danger scale + alias 호환 OK. 그러나 `clients/web/design-system/src/tokens/index.ts:37-40` TS 토큰은 기존 semantic 단일값만 — CSS/TS mirror 불일치 |
| D-C1-2 | **해결** | `global.css:686` `.purchase-edit-field` 추가, modal label 분리 |
| D-C1-3 | **해결** | 합계 셀 inline → `SlipDetailPage.tsx:1874` `.td-right` + `global.css:700` |
| D-C1-4 | **미해결** | 코드 메시지는 L394 정합. 그러나 재생성된 `02-purchase-edit-conflict-banner.png` 한글 mojibake — QA 증적 부적합 |
| D-C1-5 | **해결** | `.loading-fallback` + `<Spinner size="md" label="불러오는 중">`. SpinnerSize md 존재, design-system export 정합 |

#### Codex 자체 신규 발견 (Designer 영역)

- **Major**: `clients/web/design-system/src/tokens/index.ts:37-40` — tokens.css 추가 warning/danger scale 이 TS 토큰에 미반영. design-system 소비자가 `tokens.colors` 사용 시 scale 토큰 미노출 → CSS/TS source 갈라짐. **수정 권고**: TS 토큰 mirror 추가 (`warningScale`/`dangerScale` 또는 동등 구조).

- **Major**: `docs/qa/sp-08-5-2-purchase-slip-edit-put/screenshots/02-purchase-edit-conflict-banner.png` — 한글 폰트/인코딩 깨짐 (mojibake). UUID 노출 없음. 그러나 비즈니스 식별자 + 409 문구 판독 불가 → PR QA 스크린샷 부적합. **수정 권고**: PowerShell unicode escape 정합 재확인 후 재생성. 또는 PNG 04 가드 mock 처럼 한글 fallback 적용.

- **Minor**: `SlipDetailPage.tsx:1804,1814,1892` — 매입 수정 modal 내부 spacing/overflow inline style 잔존. D-C1-3 합계 셀 inline 제거 됐지만 같은 modal 반복 spacing 도 `.purchase-edit-*` 계열 클래스 정리 권고.

- **Nit**: `global.css:700` `.td-right` 전역 generic utility — 다른 테이블 의도치 않은 재사용 가능성. `.slip-line-table .td-right` 등 scope 좁힘 권고.

#### 대비 검토

warning-800 #8C5C13 on warning-50 #FEF6E7, danger-800 #7F1D1D on danger-50 #FFF1F1 — WCAG AA 4.5:1 만족 추정. Storybook 색상 페이지 별도 노출 누락 — 본 PR 필수 결함 X.

#### 종합

CHANGES REQUESTED. D-C1-1 CSS 만 해결 (TS mirror 남음) + D-C1-4 PNG 한글 깨짐 재생성 필요.
