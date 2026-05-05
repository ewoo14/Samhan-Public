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
