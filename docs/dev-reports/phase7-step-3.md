# Phase 7 3차 작업 — dev report

PR #82 (Phase 7 2차) 머지 후 5 후속 항목을 1 PR 통합 산출.

## 1. 작업 범위

| # | 영역 | 산출 | 분포 |
|---|---|---|---|
| 1 | BE — product-service by-code endpoint | 컨트롤러 1 + DTO 1 + IT 1 (3 case) | `web/ProductByCodeController.java` + `web/dto/ProductByCodeResponse.java` + `it/ProductByCodeControllerIT.java` |
| 2 | QA — tautology / race delta / immutable 정정 | 3 spec | `edge/api-5xx-fallback.spec.ts` (502 case), `edge/stock-reserve-deduct-race.spec.ts`, `edge/dc-snapshot-strict.spec.ts` |
| 3 | FE — selector 정밀 + testMatch 직교 | 2 항목 | `dc/dc-rule-priority.spec.ts` rate 텍스트 매칭 + `playwright.config.ts` desktop tutorial 분기 |
| 4 | DevOps — render.yaml mirror + vitest 도입 | 4 항목 | `render.yaml` 6 헤더 추가 + order-app `package.json` test script + `vitest.config.ts` + sanity 1 |
| 5 | Designer — dark-mode body[data-theme] assertion | 1 spec 보강 | `visual/dark-mode-toggle.visual.spec.ts` 신규 case |

총 산출: BE 3 file + QA 3 spec 정정 + FE 2 정정 + DevOps 4 file + Designer 1 spec 보강 = **12 file 변경/추가**.

## 2. BE — product-service by-code endpoint

### 2.1 신규 endpoint

`GET /api/products/by-code/{code}` — 사용자 노출 식별자 `modelCode` 로 productId (UUID) 조회.

- 컨트롤러: `services/product-service/src/main/java/com/samhanair/logis/product/web/ProductByCodeController.java`
- DTO: `web/dto/ProductByCodeResponse.java` (record — `id` + `modelCode` + `name`)
- repository 신규 메서드 X — 기존 `ProductRepository.findByModelCodeAndIsDeletedFalse(String)` 재사용 (V3 마이그에서 도입된 partial unique 컬럼).
- 권한: `MASTER/MANAGER/DEVELOPER/SALES/ACCOUNTANT/WAREHOUSE/INVENTORY` (전 인증 role 조회 가능, 기존 by-model 패턴 동일).

### 2.2 IT — 3 case

`ProductByCodeControllerIT`:
- happy → 200 + `$.data.id` (UUID) + `$.data.modelCode` + `$.data.name`
- not-found → 404 (BusinessException(NOT_FOUND))
- soft-deleted → 404 (`@SQLRestriction("is_deleted = false")` 자동 제외)

각 case 는 `AbstractPostgresIT` 의 싱글턴 PostgreSQL 컨테이너 공유. `Product.seedFromSheet` factory 로 modelCode 가 채워진 시드 생성.

### 2.3 QA 헬퍼 정합

`qa/playwright/utils/api-clients.ts` L121 의 `lookupProductIdByCode(code)` 가 본 endpoint 호출 (`/api/products/by-code/{code}` → `data.id`). 신규 추가로 stock 시나리오 spec 들의 `productId 매핑 미가용 — by-code lookup 미구현` skip 가드가 해소된다.

## 3. QA — tautology / race delta / immutable 정정

### 3.1 `edge/api-5xx-fallback.spec.ts` 502 case

이전: `expect(page.url()).toBeTruthy()` — page.url() 은 navigation 실패 시에도 about:blank 등으로 truthy 반환 → tautology.
정정: 503 case 와 동일 가드 (body innerText length + 스택트레이스/UUID 비노출 정규식).

### 3.2 `edge/stock-reserve-deduct-race.spec.ts`

이전: 단순 invariant (음수 X, availableQty = totalQty - reservedQty) — reserve 가 모두 실패해도 통과.
정정:
- before snapshot 캡처
- 동시 reserve 5회 (Promise.allSettled)
- afterReserve delta 검증 — `reservedQty 증가 = reserveOk 개수`, `availableQty 감소 = reserveOk`, `totalQty 불변`
- cleanup — release 5회 호출 + afterRelease 가 before 와 일치 (restore 검증, release endpoint 미가용 시 가드)

### 3.3 `edge/dc-snapshot-strict.spec.ts`

이전: 단발 조회 → snapshot 값이 0~1 numeric 인지만 확인. dc_rate config 변경 후 immutable 인지 검증 X.
정정:
- `QA_SAMPLE_SLIP_NO` 미설정 시 skip (이전 fallback `'SH-20260101-0001'` 은 신뢰 불가)
- snapshot1 캡처
- `POST /api/admin/dc-config/override` 로 dc_rate 변경 시뮬레이션 (admin endpoint 미가용 환경 가드)
- 동일 slipNo 재조회 → snapshot2 가 snapshot1 와 동일 (immutable)

## 4. FE — selector 정밀 + testMatch 직교

### 4.1 `dc/dc-rule-priority.spec.ts` happy case

이전: `await expect(rateBadge.first()).toBeAttached()` — testid 가 attached 인지만 확인 → count() > 0 분기 후 항상 통과 (tautology).
정정: `textContent()` 가져와 `/\d+\s*%/` 정규식 매칭 — 실 % 값 렌더 검증.

### 4.2 `playwright.config.ts` desktop tutorial 분기

이전: web-order-app, web-estimate-app 의 testMatch 가 `tutorial/**` 광역 매칭 → 모바일 전용 `tutorial-mobile.spec.ts` 까지 desktop project 에서 실행.
정정: 두 desktop project 의 testMatch 를 `tutorial/tutorial-(pc|staff|state).spec.ts` 만으로 narrow → mobile-chrome / mobile-safari 의 tutorial-mobile/staff/state 매칭과 직교성 확보.

## 5. DevOps — render.yaml mirror 동기 + order-app vitest 도입

### 5.1 render.yaml order-app 헤더 6종 추가

`infrastructure/render/render.yaml` 의 `samhan-order-app` (static) 의 `headers` 섹션에 Cloudflare Pages `_headers` (`clients/web/order-app/public/_headers`) 와 1:1 정합되는 6 종 추가:
- `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`
- `Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://t1.kakaocdn.net https://cdnjs.cloudflare.com; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data: https:; connect-src 'self' https://*.samhan-air.com; frame-ancestors 'self'; base-uri 'self'; form-action 'self'`
- `X-Frame-Options: SAMEORIGIN` (이전 `DENY` → `_headers` 와 일치)
- `Referrer-Policy: strict-origin-when-cross-origin` (기존 유지)
- `X-Content-Type-Options: nosniff`
- `Permissions-Policy: camera=(), microphone=(), geolocation=()`

CSP 의 `script-src 'unsafe-eval'` 는 Cloudflare 정책과 일관 (Vite 빌드 시 evaluator 일부 의존).

### 5.2 order-app vitest 도입

- `clients/web/order-app/package.json` — `test`/`test:watch` script 추가, devDependency 에 `vitest@^2.1.4`
- `clients/web/order-app/vitest.config.ts` 신규 — `src/**/*.test.ts` include + node 환경
- `clients/web/order-app/src/__tests__/sanity.test.ts` 신규 — sanity 2 case (vitest 실행 + globalThis 접근)
- `clients/web/order-app/package-lock.json` 갱신 — `npm ci` 호환

`.github/workflows/deploy-order-app.yml` 의 `npm test --if-present` gate 가 silent skip 대신 실 PASS 기록.

검증:
- `npm run typecheck` PASS
- `npm test` 2/2 PASS
- `npm run lint` PASS

## 6. Designer — dark-mode body[data-theme] assertion

`visual/dark-mode-toggle.visual.spec.ts` 에 신규 test case 1 추가:
- emulateMedia({ colorScheme: 'dark' }) 적용 후 `body[data-theme]` 속성 존재 확인
- 미구현 환경 (현재 order-app/estimate-app) → skip
- 구현 환경 → `getAttribute('data-theme') === 'dark'` + computed background 가 흰색/투명 fallback 이 아님

design-system `tokens.css` 의 `[data-theme="dark"]` 셀렉터가 실제로 DOM 에 적용되는지 검증. snapshot 만으로는 토큰 전환을 분리 검증할 수 없는 한계 보완.

## 7. 사전 검증 (PM 통합 풀빌드 가드)

| 영역 | 명령 | 결과 |
|---|---|---|
| BE — product-service compile | `./gradlew :services:product-service:compileJava :services:product-service:compileTestJava` | BUILD SUCCESSFUL (10s) |
| FE — order-app typecheck | `npm run typecheck` | PASS |
| FE — order-app vitest | `npm test` | 2/2 PASS |
| FE — order-app lint | `npm run lint` | PASS |

IT (`./gradlew test`) 는 한국 path JDK 트랩 회피로 본 환경에서 실행 보류 — CI 의 Linux runner 에서 정식 검증.

## 8. 후속 (Phase 7 4차 또는 Phase 8 후보)

| 후보 | 비고 |
|---|---|
| 14 backend MSA 별도 호스팅 결정 | Render multi-service vs cafe24 sub 등 비교 |
| dev / staging 환경 분리 | sync:false placeholder 의 실 secret 등록 흐름 |
| k6 부하 테스트 | inventory race / dc rule cascade 등 |
| OWASP ZAP 정기 스캔 | CSP / HSTS 적용 후 baseline |
| order-app/estimate-app dark-mode 정식 도입 | body[data-theme] 토글 + DS dark 토큰 적용 |

## 9. 변경 파일 목록

```
services/product-service/src/main/java/com/samhanair/logis/product/web/ProductByCodeController.java       (신규)
services/product-service/src/main/java/com/samhanair/logis/product/web/dto/ProductByCodeResponse.java     (신규)
services/product-service/src/test/java/com/samhanair/logis/product/it/ProductByCodeControllerIT.java      (신규)
qa/playwright/tests/edge/api-5xx-fallback.spec.ts                                                          (정정)
qa/playwright/tests/edge/stock-reserve-deduct-race.spec.ts                                                 (정정)
qa/playwright/tests/edge/dc-snapshot-strict.spec.ts                                                        (정정)
qa/playwright/tests/dc/dc-rule-priority.spec.ts                                                            (정정)
qa/playwright/playwright.config.ts                                                                          (정정)
qa/playwright/tests/visual/dark-mode-toggle.visual.spec.ts                                                 (보강)
infrastructure/render/render.yaml                                                                           (정정 — 헤더 6종)
clients/web/order-app/package.json                                                                          (정정)
clients/web/order-app/package-lock.json                                                                     (자동 갱신)
clients/web/order-app/vitest.config.ts                                                                      (신규)
clients/web/order-app/src/__tests__/sanity.test.ts                                                          (신규)
docs/dev-reports/phase7-step-3.md                                                                           (신규)
```
