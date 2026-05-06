# Phase 7 2차 작업 — dev report

PR #81 (Phase 7 1차) 머지 후 5 후속 항목을 1 PR 통합 산출.

## 1. 작업 범위

| # | 영역 | 산출 | 분포 |
|---|---|---|---|
| 1 | QA edge | 5 신규 spec | `tests/edge/` 5 |
| 2 | Designer 시각적 회귀 | 5 visual baseline spec | `tests/visual/` 5 |
| 3 | FE schema/selector/가드/boundary | 4 항목 | api-clients schema + dc testid + tutorial testMatch + partner status spec |
| 4 | DevOps 보안+alert+rotation+test gate | 4 항목 | HSTS+CSP / Slack webhook / SA rotation cron / npm test gate |
| 5 | Detox e2e | 6 시나리오 (기 존재 검증) | mobile-staff 3 + mobile-v4 3 |

신규 spec 합계: edge 5 + visual 5 + auth boundary 1 = **11 Playwright spec**, Detox 6 (기 존재 — 본 PR 에서 검증).

## 2. QA edge 시나리오 (5)

| spec | 검증 |
|---|---|
| `dc-cascade-fallback.spec.ts` | 모델/카테고리/거래처 default 모두 404 → standard_price fallback |
| `dc-snapshot-strict.spec.ts` | dc_rate_snapshot strict 비교 (이전 tautology 정정) |
| `stock-reserve-deduct-race.spec.ts` | 동시 reserve/deduct race → invariant 유지, 음수 X |
| `draft-ttl-boundary.spec.ts` | 30일 TTL 경계 (29.9d / 30.1d) |
| `api-5xx-fallback.spec.ts` | backend 5xx 시 white-screen 방지 + 안내 노출 |

각 spec = `isBackendAvailable()` skip 가드 + 한국어 testID + UUID 비공개 가드.

## 3. Designer 시각적 회귀 baseline (5)

| spec | snapshot |
|---|---|
| `mobile-gate.visual.spec.ts` | `mobile-gate.png` |
| `page-menu-drawer.visual.spec.ts` | `page-menu-drawer-open.png` |
| `estimate-form.visual.spec.ts` | `estimate-form.png` |
| `home-after-bizgate.visual.spec.ts` | `home-after-bizgate.png` |
| `dark-mode-toggle.visual.spec.ts` | `home-light.png` + `home-dark.png` |

`playwright.config.ts` 에 `expect.toHaveScreenshot.maxDiffPixelRatio: 0.02` 글로벌 설정.
3 project (mobile-chrome / mobile-safari / electron-desktop) 별 baseline 별도 보존.

baseline 갱신: `npx playwright test --update-snapshots`

## 4. FE — schema + selector + 가드 + boundary

### 4.1 stock ApiClient schema 분리

`utils/api-clients.ts`:
- 이전: `getStock() → { qty: number }` (단일 필드 — reserve/deduct 의미 모호)
- 정정: `getStock() → StockSnapshot { on_hand, reserved, available }` (Phase 6 backend 실 schema)

영향 spec 정정:
- `stock-reserve-on-confirm.spec.ts` — reserve = `reserved` ↑ + `available` ↓ + `on_hand` 불변
- `stock-deduct-on-slip-publish.spec.ts` — deduct = `on_hand` ↓ + `reserved` ↓
- `confirm/confirm-stock-deduct.spec.ts` — `available` 감소 검증 (typecheck 보존)

### 4.2 dc spec data-testid

`clients/web/estimate-app/views/index.ejs`:
- rate badge → `data-testid="dc-applied-rate"`
- homeTotal sum → `data-testid="dc-final-price"`

DC spec selector 정정:
- `dc-config-apply.spec.ts` + `dc-rule-priority.spec.ts` 의 `body` 광범위 매칭 → `[data-testid="..."]` narrow 매칭

### 4.3 tutorial-pc / mobile testMatch 분기

`playwright.config.ts`:
- mobile-chrome / mobile-safari testMatch — `tutorial-pc.spec.ts` 제외 (이전: `tutorial/**` 전체 매칭)
- electron-desktop testMatch — `tutorial-pc.spec.ts` 만 포함

### 4.4 Partners status boundary spec

`fixtures/auth.ts` 의 `Partners.blocked() / expired() / tempCredential()` 헬퍼는 기 존재.
신규 spec: `auth/partner-status-boundary.spec.ts`
- BLOCKED → 차단 안내
- EXPIRED → 만료 + 갱신 유도
- TEMP_CREDENTIAL → 임시 인증 + 정식 전환 유도
- 각 case = UUID 비공개 가드

## 5. DevOps — 보안 + alert + rotation + test gate

### 5.1 HSTS / CSP

| 대상 | 위치 |
|---|---|
| order-app v4 (Cloudflare Pages) | `clients/web/order-app/public/_headers` |
| estimate-app v2 (Express) | `clients/web/estimate-app/server.js` middleware |

헤더:
- `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload` (2년)
- `Content-Security-Policy: default-src 'self'; ...; connect-src 'self' https://*.samhan-air.com`
- `X-Frame-Options: SAMEORIGIN`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `X-Content-Type-Options: nosniff`
- `Permissions-Policy: camera=(), microphone=(), geolocation=()`

CSP `script-src` 는 카카오 우편번호 (`t1.kakaocdn.net`) + html2canvas (`cdnjs.cloudflare.com`) 명시.

### 5.2 Slack alert webhook

`render.yaml` envVars: `SLACK_WEBHOOK_URL: sync: false` 추가.

`lib/slip-bridge.js`:
- `postSlackAlert()` helper 추가 — webhook URL 미설정 시 silent no-op
- slip-service 5xx / 네트워크 오류 시 alert POST (4xx 는 사용자 입력 오류 — alert 제외)

### 5.3 Service Account 90일 rotation

| 산출 | 역할 |
|---|---|
| `infrastructure/security/sa-rotation-cron.md` | 절차 문서 (5 step + 비상 절차) |
| `.github/workflows/sa-rotation-reminder.yml` | 분기 첫날 09:00 UTC Issue 자동 생성 (cron `0 9 1 */3 *`) |

Issue 본문 = checklist 5개 (신규 key 발급 / Render 등록 / healthz 검증 / 기존 key Disable / 24h 후 Delete).

### 5.4 npm test gate

| workflow | 변경 |
|---|---|
| `deploy-estimate-app.yml` | 기 존재 (`npm test`) — 변경 없음 |
| `deploy-order-app.yml` | typecheck 와 vite build 사이 `npm test --if-present` 추가 |

## 6. Detox 6 시나리오 검증

기 존재 파일 (PR #78 이후 commit) 의 spec 본문 확인:

| 디렉토리 | 파일 | 시나리오 |
|---|---|---|
| `mobile-staff/` | `estimate-form.test.ts` | WebView 로드 + estimate-app 진입 + 네트워크 단절 reload |
| `mobile-staff/` | `line-grid.test.ts` | 모델 선택 + 라인 grid + qty 0 차단 |
| `mobile-staff/` | `confirm.test.ts` | 라인 확정 + 빈 견적 차단 alert |
| `mobile-v4/` | `partner-bizgate.test.ts` | WebView 로드 + BizGate SSO + 실패 차단 |
| `mobile-v4/` | `mobile-gate-4-categories.test.ts` | 게이트 4 카테고리 grid + 권한 잠금 |
| `mobile-v4/` | `webview-order-confirm.test.ts` | 라인 확정 + 임시저장 재진입 복원 |

`tsc --noEmit` clean (qa/detox/tsconfig.json `types: ['node', 'jest', 'detox']`).

## 7. 검증 결과

| 항목 | 결과 |
|---|---|
| Playwright `tsc --noEmit` | clean |
| Detox `tsc --noEmit` | clean |
| estimate-app `npm test` | 17/17 PASS |
| `node --check server.js` | OK |
| `node --check lib/slip-bridge.js` | OK |

## 8. 후속 (Phase 7 3차 후보)

- 14 backend MSA Render 호스팅 결정 (estimate-app 외 13 service)
- dev / staging 환경 endpoint 분리 + envVars staging value 등록
- Slack webhook 실 URL 등록 + alert 검증
- visual baseline 1회 `--update-snapshots` 실행 (PR #82 머지 후, 실 production CDN 기반)
- Detox iOS 빌드 단계 enable (현재 `--configuration` typecheck only)
- SA key 1회차 회전 실행 (분기 시점)
