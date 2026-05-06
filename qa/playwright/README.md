# SamhanLogis Phase 7 QA — Playwright

웹 + Electron + Mobile emul e2e 시나리오 30개 (15 spec × happy/edge).

## 5 project

| project              | 대상                                                      | 시나리오 카테고리                                   |
| -------------------- | --------------------------------------------------------- | -------------------------------------------------- |
| `web-order-app`      | 거래처 주문서 v4 (Vite, port 5184)                       | auth / catalog / draft / confirm / history / tutorial |
| `web-estimate-app`   | 종합견적서 v2 (Express + EJS, port 5183)                  | auth / catalog / draft / confirm / history          |
| `electron-desktop`   | electron-vite packaged binary                             | auth / catalog / confirm                            |
| `mobile-chrome`      | Pixel 7 viewport (web order-app)                          | auth / catalog / draft / confirm                    |
| `mobile-safari`      | iPhone 14 viewport (web order-app)                        | auth / catalog / draft / confirm                    |

## 시나리오 (15 spec, 30 case)

### auth/ (3 spec, 6 case)

- `partner-bizgate.spec.ts` — BizGate SSO (happy ACTIVE / edge BLOCKED)
- `partner-password.spec.ts` — 비밀번호 (happy 정확 / edge 실패)
- `partner-temp-password.spec.ts` — 임시 PW (happy 변경 화면 / edge 미변경 차단)

### catalog/ (4 spec, 8 case)

- `homemulti-grid.spec.ts` — 홈멀티 (happy grid / edge 빈 카탈로그)
- `single-set.spec.ts` — 싱글셋 (happy 노출 / edge 비활성 미노출)
- `commercial-multi.spec.ts` — 상업멀티 (happy PUMA / edge 실외기 BTU 0)
- `old-product.spec.ts` — 단종 (happy 메뉴 / edge 일반 격리)

### draft/ (3 spec, 6 case)

- `save-draft.spec.ts` — 임시저장 (happy 완료 / edge 빈 라인 차단)
- `load-draft-30day-ttl.spec.ts` — 30일 TTL (happy load / edge 만료)
- `draft-list.spec.ts` — 목록 (happy 본인만 / edge 빈 목록)

### confirm/ (3 spec, 6 case)

- `confirm-happy.spec.ts` — 확정 (happy 슬립번호 / edge 빈 라인 차단)
- `confirm-slip-publish.spec.ts` — slip-service 적재 (happy 발행 / edge idemKey)
- `confirm-stock-deduct.spec.ts` — inventory 차감 (happy 차감 / edge 부족 차단)

### history/ (1 spec, 2 case)

- `partner-order-history.spec.ts` — 이력 (happy 본인 / edge 타거래처 차단)

### tutorial/ (1 spec, 2 case)

- `tutorial-state.spec.ts` — 튜토리얼 상태 (happy 최초 / edge 재진입 skip)

## 사용법

```sh
cd qa/playwright
npm install
npx playwright install --with-deps

# 전체 (backend 미가동 시 happy 는 it.skip)
npm test

# project 별
npm run test:web-order
npm run test:web-estimate
npm run test:electron
npm run test:mobile-chrome
npm run test:mobile-safari

# HTML 리포트
npm run test:html && npm run report
```

## 환경 변수

| 이름                    | 기본값                    | 설명                                |
| ----------------------- | ------------------------- | ----------------------------------- |
| `QA_ORDER_APP_URL`      | `http://localhost:5184`  | order-app v4 dev server            |
| `QA_ESTIMATE_APP_URL`   | `http://localhost:5183`  | estimate-app v2 server             |
| `QA_API_BASE_URL`       | `http://localhost:8080`  | Spring Boot gateway / 서비스        |
| `QA_REPO_ROOT`          | (자동 추정)              | 스크린샷 저장 경로 base             |
| `CI`                    | (CI 자동 set)             | retries=2 + worker=2                |

## CI

`.github/workflows/qa-e2e.yml` 의 `playwright` job 이 PR 에서 자동 실행. 본 셋업 PR 은 typecheck + dry-run (it.skip) 까지 검증, 실 e2e 는 backend stack 가동 후속 PR.

## 가드 (Phase 7 정착 + Phase 8 / 9 일관 적용)

- **skip 가드** — backend up 실패 시 모든 happy 시나리오는 `it.skip` 으로 자동 스킵 (CI 레드 회피). 각 spec 의 setup 단계에서 `gateway/health` 200 응답 + 의존 service health 검증 후 본 시나리오 진입.
- **`document.fonts.ready`** — visual regression spec 은 모든 캡처 직전 `await page.evaluate(() => document.fonts.ready)` 호출 의무 (Phase 7 4차 PR #84 학습 — 폰트 로딩 race 회피). `qa/playwright/utils/visual.ts` 의 `waitForFontsReady(page)` helper 사용.
- **`data-testid`** — selector 는 `data-testid` 우선, CSS selector / XPath 는 비상용. 한국어 텍스트 기반 selector 는 i18n 변경 시 race 발생 → 회피.
- **UUID 미노출 검증** — Phase 6 학습 가드 (UUID 사용자 비공개 원칙) 일관 적용. 모든 화면 캡처 후 `await expect(page.locator('text=/[0-9a-f]{8}-[0-9a-f]{4}-/')).toHaveCount(0)` 의무 (PR #18 회고).
- **Internal-Token 헤더 격리** — `utils/api-clients.ts` 의 helper 가 `X-Internal-Token` 헤더 자동 주입. spec 측에서는 직접 호출 금지.

## Phase 9 신규 e2e 위치 (예정)

Phase 9 4 신규 service 가 시작되면 본 디렉토리에 다음 시나리오가 추가된다:

| Service              | 위치                                | 시나리오 카테고리                                |
| -------------------- | ----------------------------------- | ------------------------------------------------ |
| partner-service      | `qa/playwright/tests/partner/`      | master CRUD / lookup-by-code / credit-limit     |
| groupware-service    | `qa/playwright/tests/groupware/`    | 결재선 / 메신저 / 일정                           |
| notification-service | `qa/playwright/tests/notification/` | push 발송 / SMS 발송 / 이메일 발송 / 재시도      |
| dashboard-service    | `qa/playwright/tests/dashboard/`    | KPI 조회 / 실시간 재고 / 매출 집계 / **visual baseline 신규** |

dashboard-service 는 시각화 컴포넌트 다수 → Designer 와 협업으로 visual regression baseline 신규 작성 의무 (Phase 7 4차 학습 — `dark-mode-toggle.visual.spec.ts` 패턴 1:1 적용).

상세는 `docs/migration/phase9/M-PHASE-9-readiness.md` 참조.
