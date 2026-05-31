## designer 사이클 2 리뷰 (head `d6364d4b`)

### 사이클 1 Designer 잔존 해소 표

| 항목 | 사이클 1 지적 | 사이클 2 상태 |
|---|---|---|
| PNG 한글 깨짐 01~04 | P0 | **해소** (5장 한글 정상 — 사용자 확인) |
| PNG 05 신규 PARTNER role-guard | P0 신규 | **해소** |
| Modal 본문 <strong> + 조사 | P1 | **해소** (L542-543) |
| variant="danger" 2건 | P1 | **해소** (L211, L526) |
| successBanner 토큰 통일 | Nit | **해소** |
| `.listBackLink` margin-left auto | Nit | **해소** (L118-120) |

사이클 1 Designer 6건 전원 해소.

### 사이클 2 신규 발견

**D1 (P1) — `.successBanner` color 토큰 불일치**

`.errorBanner` 는 fallback 없이 `var(--state-danger)` 순수 토큰 사용, `.successBanner` 는 `var(--state-success, #10b981)` fallback hex 병기. `tokens.css` 기준 `--state-success: #10B981` 정의되어 있음. fallback 불일치 위험. 또한 `#10b981` 은 statusSent text color `#065f46` 기준 대비 접근성 4.5:1 미달 가능성. `errorBanner` 와 맞추어 fallback 제거 또는 `#059669` (AA 통과 green) 통일.

**D2 (P1) — PNG 04 에러 코드 노출**

`04-from-estimate-already-converted.png` 에 `PARTNER_ORDER_FROM_ESTIMATE_ALREADY_CONVERTED` 에러 코드 카드 본문 직접 노출. 이카운트 reference 기준 에러 상태 화면은 한국어 사용자 메시지만 노출, 코드는 숨김/collapsible. UUID 사용자 비공개 원칙과 같은 맥락. FE 에 `errorCode` `<details>` 또는 콘솔 only 처리 spec 전달.

**D3 (Nit) — historyRow border 토큰 `--line-default` 출처**

`.historyRow` `border-bottom: 1px solid var(--line-default)` — `sales.module.css` 내 `:root` 블록에 `--line-default` 미선언. `tokens.css` `#E1E5EA` 에서 공급. 런타임 정상이나 `.salesScope` 스코프 `--c-line: #000` 와 의도 불명확. `.estTable` `var(--c-line)` (black) vs `.historyRow` `var(--line-default)` (gray) 시각 무게 차이 — `--c-line` 또는 중간값 통일 검토.

### 종합

사이클 1 6건 완전 해소, PNG 5장 한글 정상. 사이클 2 D1~D3 신규. D2 는 FE spec 레벨 (errorCode 노출 정책), D1·D3 는 CSS Nit. 핵심 삭제 Modal + PARTNER role-guard UI 디자인 충족.

**APPROVE** — D2 FE spec 전달 후 사이클 3 불필요. D1·D3 후속 슬라이스.

**designer agent — 2026-05-17**
