# phase7-completion-phase8-readiness — Phase 7 마무리 + Phase 8 진입 보고서

## 1. 개요

본 문서는 Phase 7 마무리 (DevOps 후속 3 + QA 후속 1 통합 산출) 와 Phase 8 진입 plan 을
1:1 정리한다. 본 PR 의 dev report.

## 2. 후속 작업 산출물 (DevOps 3 + QA 1)

### 2.1 DevOps #1 — self-host Pretendard font

| 항목 | 내용 |
|---|---|
| 트리거 | jsdelivr CDN SPOF 회피 + CSP `font-src 'self'` 만 허용 운영 환경 호환 |
| 신규 디렉토리 | `clients/web/order-app/public/fonts/`, `clients/web/estimate-app/public/fonts/` |
| 신규 디렉토리 | `clients/web/design-system/src/styles/`, `scripts/` |
| 신규 파일 | `scripts/download-pretendard-fonts.sh` (멱등 download script) |
| 신규 파일 | `clients/web/design-system/src/styles/fonts.css` (canonical @font-face) |
| 신규 파일 | 두 client `public/fonts/fonts.css` (mirror, static serve) |
| 신규 파일 | 두 client `public/fonts/.gitignore` (woff2 binary 제외 + .css/.md 보존) |
| 신규 파일 | 두 client `public/fonts/README.md` (사용법) |
| 변경 파일 | `clients/web/order-app/index.html` (head jsdelivr `<link>` → self-host preload + fonts.css) |
| 변경 파일 | `clients/web/estimate-app/views/index.ejs` (동일) |
| 변경 파일 | `clients/web/order-app/public/_headers` (font-src 'https:' 제거 → 'self' data: 만) |

### 2.2 DevOps #2 — Express helmet + CSP 정식 도입

| 항목 | 내용 |
|---|---|
| 트리거 | inline CSP middleware 보다 보강 (HSTS / X-DNS-Prefetch-Control / X-Download-Options 등 추가) |
| 변경 파일 | `clients/web/estimate-app/package.json` (helmet ^8.0.0 dependency 추가) |
| 변경 파일 | `clients/web/estimate-app/server.js` (inline CSP middleware → helmet middleware) |
| 정책 정합 | order-app 의 `_headers` (Cloudflare Pages) 와 1:1 정합 |
| 잔여 헤더 | Permissions-Policy (helmet 미지원) 만 별도 middleware |

### 2.3 DevOps #3 — Electron renderer CSP 갱신

| 항목 | 내용 |
|---|---|
| 트리거 | self-host font 적용 + production endpoint (samhan-air.com) 호출 호환 |
| 변경 파일 | `clients/desktop/src/renderer/index.html` (CSP meta) |
| 갱신 directive | font-src 'self' data:, img-src 추가 https:, connect-src 추가 https://*.samhan-air.com, script-src 'unsafe-inline' (electron-vite HMR) |

### 2.4 QA — visual baseline `document.fonts.ready` 가드

| 항목 | 내용 |
|---|---|
| 트리거 | self-host 적용 후에도 woff2 fetch + decode 비동기 → baseline race 발생 가능 |
| 변경 파일 | `qa/playwright/tests/visual/dark-mode-toggle.visual.spec.ts` |
| 변경 파일 | `qa/playwright/tests/visual/estimate-form.visual.spec.ts` |
| 변경 파일 | `qa/playwright/tests/visual/home-after-bizgate.visual.spec.ts` |
| 변경 파일 | `qa/playwright/tests/visual/mobile-gate.visual.spec.ts` |
| 변경 파일 | `qa/playwright/tests/visual/page-menu-drawer.visual.spec.ts` |
| 패턴 | `await page.evaluate(() => document.fonts.ready)` 직후 `page.goto('/')`, `page.waitForLoadState('networkidle')` 직전 |

## 3. Phase 7 회고 + ROADMAP + DECISIONS

### 3.1 회고 보고서 신규

`docs/dev-reports/phase7-retrospective.md` — Phase 7 1~6차 머지 PR 7건 + 학습 사항 매트릭스 + Phase 8 위임 사항.

### 3.2 ROADMAP 갱신

- Phase 7 상태 "진행 중" → "완료"
- Phase 8 상태 "대기" → "진입 준비 (D9 답변 대기)"
- Phase 7 4~6차 산출물 표기 + 머지 PR (#84 #85 #86 본 PR)
- 머지 PR ↔ Phase 매트릭스에 #84 ~ 본 PR 추가
- D9 row 갱신 (진행 중 → 답변 대기)

### 3.3 DECISIONS 갱신

`migration/decisions/DECISIONS.md` append:
- D-P7-06: Phase 7 6차 production cutover 보류 (D9 답변 의존)
- D-P7-07: 후속 PR 4건 본 PR 통합 발행
- D-P8-01: Phase 8 진입 조건
- D-P8-02: Phase 8 plan 위치

## 4. Phase 8 진입 plan 요약

`docs/migration/phase8/M-PHASE-8-readiness.md` — 5주 plan + 8 작업 분해.

| 주 | 작업 |
|---|---|
| W1 | 호스팅 결정 (X1~X4 중 1택) + DB 준비 (Flyway V1~V8 staging) |
| W2 | Eureka cluster (다중 노드 + AZ 분산) + Resilience4j prod 임계치 |
| W3 | API Gateway (rate limit + WAF) + 모니터링 alert (Slack/SMS) |
| W4 | DNS cutover (8 서브도메인) + production smoke (Playwright + Detox) |
| W5 | 운영 안정화 (24h 무장애) + Phase 8 회고 |

## 5. 후속 작업 (Phase 8)

| 항목 | 위임 |
|---|---|
| D9 답변 | 호스팅 결정 회의 |
| (X1 옵션 시) D6/D7/D8 답변 | 인프라 답변 |
| visual baseline 6 PNG (staging 생성) | QA 슬라이스 (Phase 8 W1) |
| 14 MSA staging endpoint 활성 | DEVOPS 슬라이스 (Phase 8 W1) |

## 6. 변경 파일 요약

| 카테고리 | 파일 수 |
|---|---|
| DevOps font self-host | 9 (script 1 + design-system css 1 + 두 client public/fonts × 4 = 8) |
| DevOps helmet+CSP | 2 (server.js + package.json) |
| DevOps order-app _headers | 1 |
| DevOps desktop CSP | 1 |
| QA visual fonts.ready | 5 |
| 두 client head 수정 | 2 (index.html + index.ejs) |
| ROADMAP / DECISIONS / 회고 / Phase 8 plan / dev-report | 5 |

총 ~25 파일 변경/신규.
