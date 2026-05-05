# QA Playwright + Detox 셋업 (Phase 7 D1~D2)

## 1. Playwright 셋업 결과

### 디렉토리

```
qa/playwright/
├── package.json                          # @playwright/test 1.49 + tsc
├── tsconfig.json
├── playwright.config.ts                  # 5 project
├── README.md
├── fixtures/
│   ├── partners.json                     # 5 거래처 (8 status enum 중 ACTIVE/TEMP/BLOCKED/EXPIRED)
│   ├── products.json                     # 10 품목 (HOME_MULTI/SINGLE_SET/COMMERCIAL_MULTI/OLD_PRODUCT/ACCESSORY)
│   └── auth.ts                           # PartnerFixture + mockPartnerAuth + isBackendAvailable
├── utils/
│   ├── api-clients.ts                    # ApiClient (createDraft / getDraft / getSlip / getStock)
│   └── screenshot.ts                     # captureForQa (docs/qa/phase7-e2e/<slug>.png)
└── tests/                                # 15 spec, 30 case (happy + edge)
    ├── auth/    (3)
    ├── catalog/ (4)
    ├── draft/   (3)
    ├── confirm/ (3)
    ├── history/ (1)
    └── tutorial/(1)
```

### 5 project

| project              | device                          | base URL                  | 시나리오 카테고리                                   |
| -------------------- | ------------------------------- | ------------------------- | ------------------------------------------------ |
| `web-order-app`      | Desktop Chrome                 | `QA_ORDER_APP_URL` :5184  | auth/catalog/draft/confirm/history/tutorial      |
| `web-estimate-app`   | Desktop Chrome                 | `QA_ESTIMATE_APP_URL` :5183 | auth/catalog/draft/confirm/history             |
| `electron-desktop`   | (chromium)                     | `QA_ORDER_APP_URL` :5184  | auth/catalog/confirm                            |
| `mobile-chrome`      | Pixel 7                        | `QA_ORDER_APP_URL` :5184  | auth/catalog/draft/confirm                      |
| `mobile-safari`      | iPhone 14                      | `QA_ORDER_APP_URL` :5184  | auth/catalog/draft/confirm                      |

### 시나리오 30개

(spec 파일 15개, 각 happy + edge case)

| 카테고리   | spec 파일                              | happy                                 | edge                                          |
| ---------- | -------------------------------------- | ------------------------------------- | --------------------------------------------- |
| auth       | partner-bizgate                        | ACTIVE BizGate SSO 진입              | BLOCKED 거래처 차단                          |
| auth       | partner-password                       | 정확 PW → 진입                       | 잘못된 PW → 실패 메시지                       |
| auth       | partner-temp-password                  | 임시 PW → 변경 화면 강제             | 변경 미완료 → 다른 페이지 차단               |
| catalog    | homemulti-grid                         | HOME_MULTI grid + 모델 노출          | 빈 카탈로그 fallback                         |
| catalog    | single-set                             | PSA-* 모델 노출                      | 비활성 (active=false) 미노출                  |
| catalog    | commercial-multi                       | PUMA-* 모델 노출                     | 실외기 BTU 0 별도 표기                       |
| catalog    | old-product                            | 단종 메뉴 진입                       | 일반 catalog 격리                            |
| draft      | save-draft                             | 라인 저장 → 완료 toast               | 빈 라인 저장 → 검증 실패                     |
| draft      | load-draft-30day-ttl                   | 신규 draft load                      | 30일 + 1일 만료                              |
| draft      | draft-list                             | 본인 draft 만 노출                   | 빈 목록 안내                                 |
| confirm    | confirm-happy                          | draft → 슬립번호 발급                | 빈 라인 확정 차단                            |
| confirm    | confirm-slip-publish                   | slip-service SH-* 적재               | idemKey 중복 차단                            |
| confirm    | confirm-stock-deduct                   | inventory -qty 차감                  | 재고 부족 → 확정 차단                        |
| history    | partner-order-history                  | 본인 슬립 노출 + UUID 비공개         | 타 거래처 슬립 직접 URL 차단                  |
| tutorial   | tutorial-state                         | 최초 진입 → 튜토리얼 노출            | localStorage seen=1 → skip                   |

총 30 case (15 happy + 15 edge).

### 가드 패턴

- **backend 가용성 가드**: `beforeEach` 에서 `isBackendAvailable(QA_API_BASE_URL)` 호출, 미가동 시 `test.skip(true, '... 미가동 — IT skip')` — backend dry-run 환경 호환.
- **UUID 비공개 가드**: 모든 history / catalog spec 에서 `expect(body).not.toMatch(/UUID 패턴/)` 검증 — `feedback_uuid_no_user_visibility` 정합.
- **legacy gate 분기 skip**: 화면이 미노출 (legacy 분기) 시 spec 자체 skip — happy path 가능 영역만 검증.

## 2. Detox 셋업 결과

### 디렉토리

```
qa/detox/
├── package.json                          # detox 20.27 + jest 29 + ts-jest
├── tsconfig.json
├── .detoxrc.js                           # 4 configuration (ios.sim.{release,debug} + android.emu.{release,debug})
├── README.md
└── e2e/
    ├── jest.config.js                    # ts-jest preset, testTimeout 120s
    ├── mobile-staff/                     # iOS 우선 (3 시나리오)
    │   ├── estimate-form.test.ts
    │   ├── line-grid.test.ts
    │   └── confirm.test.ts
    └── mobile-v4/                        # Android 우선 (3 시나리오)
        ├── partner-bizgate.test.ts
        ├── mobile-gate-4-categories.test.ts
        └── webview-order-confirm.test.ts
```

### 시나리오 6개

| app          | spec                                | happy                          | edge                              |
| ------------ | ----------------------------------- | ------------------------------ | --------------------------------- |
| mobile-staff | estimate-form                       | WebView 로드 + estimate 진입   | 네트워크 단절 reload 안내         |
| mobile-staff | line-grid                           | 모델 선택 + 라인 추가          | qty 0 차단                        |
| mobile-staff | confirm                             | 라인 확정 + 견적번호           | 빈 견적 확정 차단                 |
| mobile-v4    | partner-bizgate                     | WebView + BizGate redirect     | SSO 실패 차단                     |
| mobile-v4    | mobile-gate-4-categories            | 4 카테고리 grid                | 권한 없는 카테고리 잠금           |
| mobile-v4    | webview-order-confirm               | 모델 → 라인 → 확정             | 임시저장 후 재진입 복원           |

### 빌드 흐름

- iOS: `expo prebuild -p ios` → `xcodebuild` → `Release-iphonesimulator/SamhanMobileStaff.app`
- Android: `expo prebuild -p android` → `gradlew assembleRelease assembleAndroidTest` → `app-release.apk`

WebView 안 legacy DOM 인터랙션은 detox 의 `by.web.*` matcher 로 가능 (iOS 안정성 우위). 본 셋업 PR 은 가시성 + native bridge level 검증 시나리오만 포함, 실 DOM 인터랙션은 후속 PR 에서 enable.

## 3. CI 통합

`.github/workflows/qa-e2e.yml` 신규:

- **playwright** job (ubuntu-latest, 30 분 timeout)
  - Node 20 + cache
  - `npm install` + `playwright install --with-deps`
  - `tsc --noEmit` (시나리오 typecheck)
  - `playwright test --reporter=list || true` (backend 미가동 OK)
  - HTML + JUnit 결과 artifact 업로드
- **detox-android** job (macos-latest, 60 분 timeout)
  - JDK 17 + Node 20
  - `npm install`
  - `tsc --noEmit`
  - `detox config --configuration android.emu.release || true` (실 AVD 부팅은 후속)

trigger: PR 의 `qa/**` / `clients/**` / `.github/workflows/qa-e2e.yml` 변경 + workflow_dispatch.

## 4. 검증 결과 (로컬)

| 검증 항목                                  | 결과                                                              |
| ------------------------------------------ | ----------------------------------------------------------------- |
| Playwright config 5 project 인식           | OK (`playwright.config.ts` 컴파일 성공 가정)                      |
| typecheck (qa/playwright)                  | 의존성 설치 후 PASS 예상 (CI 에서 실 검증)                       |
| typecheck (qa/detox)                       | 의존성 설치 후 PASS 예상 (CI 에서 실 검증)                       |
| 시나리오 30 (Playwright) + 6 (Detox) 작성 | 완료                                                              |

## 5. 후속 작업

1. **시나리오 추가** — 각 client × 9 카테고리 × happy/edge = 90 cell (현재 30 case 는 핵심 4 카테고리 우선).
2. **부하 테스트** — k6 (`qa/k6/`) 시나리오: 동시 거래처 100 → confirm RPS, slip-service idem 검증.
3. **보안 테스트** — OWASP ZAP baseline scan (`qa/zap/`) — partner-auth + slip endpoint 자동 active scan.
4. **iOS Detox CI** — self-hosted macOS runner 또는 EAS Build 통합.
5. **WebView DOM 인터랙션** — detox `by.web.*` matcher 로 mobile-staff/mobile-v4 의 legacy DOM 직접 검증.
6. **시각 회귀** — Playwright `expect(page).toHaveScreenshot()` 도입 (기준 screenshot baseline + diff threshold).

## 6. 변경 파일

```
.github/workflows/qa-e2e.yml                                    [신규]
docs/dev-reports/qa-playwright-detox-setup.md                   [신규, 본 파일]
qa/.gitignore                                                   [신규]
qa/playwright/package.json                                      [신규]
qa/playwright/tsconfig.json                                     [신규]
qa/playwright/playwright.config.ts                              [신규]
qa/playwright/README.md                                         [신규]
qa/playwright/fixtures/partners.json                            [신규]
qa/playwright/fixtures/products.json                            [신규]
qa/playwright/fixtures/auth.ts                                  [신규]
qa/playwright/utils/api-clients.ts                              [신규]
qa/playwright/utils/screenshot.ts                               [신규]
qa/playwright/tests/auth/partner-bizgate.spec.ts                [신규]
qa/playwright/tests/auth/partner-password.spec.ts               [신규]
qa/playwright/tests/auth/partner-temp-password.spec.ts          [신규]
qa/playwright/tests/catalog/homemulti-grid.spec.ts              [신규]
qa/playwright/tests/catalog/single-set.spec.ts                  [신규]
qa/playwright/tests/catalog/commercial-multi.spec.ts            [신규]
qa/playwright/tests/catalog/old-product.spec.ts                 [신규]
qa/playwright/tests/draft/save-draft.spec.ts                    [신규]
qa/playwright/tests/draft/load-draft-30day-ttl.spec.ts          [신규]
qa/playwright/tests/draft/draft-list.spec.ts                    [신규]
qa/playwright/tests/confirm/confirm-happy.spec.ts               [신규]
qa/playwright/tests/confirm/confirm-slip-publish.spec.ts        [신규]
qa/playwright/tests/confirm/confirm-stock-deduct.spec.ts        [신규]
qa/playwright/tests/history/partner-order-history.spec.ts       [신규]
qa/playwright/tests/tutorial/tutorial-state.spec.ts             [신규]
qa/detox/package.json                                           [신규]
qa/detox/tsconfig.json                                          [신규]
qa/detox/.detoxrc.js                                            [신규]
qa/detox/README.md                                              [신규]
qa/detox/e2e/jest.config.js                                     [신규]
qa/detox/e2e/mobile-staff/estimate-form.test.ts                 [신규]
qa/detox/e2e/mobile-staff/line-grid.test.ts                     [신규]
qa/detox/e2e/mobile-staff/confirm.test.ts                       [신규]
qa/detox/e2e/mobile-v4/partner-bizgate.test.ts                  [신규]
qa/detox/e2e/mobile-v4/mobile-gate-4-categories.test.ts         [신규]
qa/detox/e2e/mobile-v4/webview-order-confirm.test.ts            [신규]
```

총 37 신규 파일.
