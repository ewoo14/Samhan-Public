# Audit Slice C — QA Cycle 1 리뷰 (claude-qa-cycle1)

작성자: QA Agent
날짜: 2026-05-19

---

## 1. 검토 범위

- `qa/detox/e2e/arologis-mobile/smoke.test.ts` (3 시나리오)
- `qa/detox/.detoxrc.js` (apps / devices / configurations 3건 추가)
- `qa/detox/package.json` (scripts 4건 추가)
- `.github/workflows/qa-e2e.yml` (detox-android-arologis job 신규)
- `qa/playwright/tests/signature-c/signature-c-smoke.spec.ts` (12 case: 8 active + 4 fixme)
- `qa/playwright/playwright.config.ts` (signature-c-smoke project 신규)

---

## 2. arologis-mobile Detox 패턴 일관성 검증

### 2.1 .detoxrc.js — apps / devices / configurations

기존 mobile-staff (iOS) / mobile-v4 (Android) 패턴과 비교했을 때 arologis-mobile 항목이 동일 구조를 따른다.

- `apps.arologis-mobile.android.release` / `arologis-mobile.android.debug` 양쪽 모두 `npx expo prebuild -p android --clean` + `./gradlew assembleRelease assembleAndroidTest -DtestBuildType=release` 패턴으로 mobile-v4 와 동일하다. 합격.
- `devices.arologisEmulator` 는 `type: 'android.emulator'`에 `avdName: 'Pixel_6_API_34'` 를 지정한다. 기존 emulator (Pixel_API_33) 와 분리된 전용 디바이스 항목이라 기존 mobile-v4 테스트 실행에 충돌이 없다. 합격.
- `configurations.arologis.android.release` / `arologis.android.debug` 양쪽 모두 `device: 'arologisEmulator'`를 참조하고 `testRunner.args.roots: ['e2e/arologis-mobile']` 로 격리한다. mobile-staff / mobile-v4 시나리오와 교차 실행되지 않는다. 합격.

### 2.2 package.json scripts

`detox-build:arologis` / `detox-test:arologis` / `detox-build:arologis-debug` / `detox-test:arologis-debug` 4건이 추가되었다. 기존 `test:android` / `build:android` 스크립트(mobile-v4 전용)와 이름이 겹치지 않는다. 합격.

### 2.3 smoke.test.ts 시나리오 3건 검증

**시나리오 1 (앱 부팅 후 로그인 화면 표시):** `waitFor(element(by.text('아로로지스 기사'))).toBeVisible().withTimeout(15000)` — `PhoneLoginScreen.tsx` 의 실제 `<Text style={styles.heading}>아로로지스 기사</Text>` 와 정합한다. `loading` 상태(ActivityIndicator) 와 auto-detect 완료 상태 양쪽 모두 동일 heading 텍스트를 렌더링하므로 어느 경로에서도 검증된다. 합격.

**시나리오 2 (수동 입력 카드 표시):** `by.id('phone-input')` / `by.id('phone-submit')` 로 검증한다. `PhoneLoginScreen.tsx` 라인 166, 173에서 `testID="phone-input"` / `testID="phone-submit"` 가 정의된다. `permissions: { notifications: 'YES' }` 만 부여하고 `READ_PHONE_NUMBERS` 는 미부여하므로 수동 입력 카드 경로로 fallback 되는 것이 spec 주석으로 설명된다. 합격.

**시나리오 3 (빈 번호 제출 Alert):** `phone-submit` 탭 후 `by.text('휴대번호를 입력해 주세요.')` 대기. `PhoneLoginScreen.tsx` 라인 89에 `Alert.alert('휴대번호를 입력해 주세요.')` 가 정의된다. Alert 닫기는 `.tap().catch(async () => element(by.text('OK')).tap())` 패턴으로 Android 에뮬레이터 변형(한국어 "확인" vs 영어 "OK")을 방어한다. 합격.

### 2.4 qa-e2e.yml detox-android-arologis job

`runs-on: macos-latest` (hardware accel AVD 가능), `working-directory: qa/detox`, `npx tsc --noEmit` + `npx detox config --configuration arologis.android.release || true` 패턴이다. 실 에뮬레이터 구동은 후속 PR 에서 enable 예정임이 명시되어 있다. `|| true` 는 config 검증 단계에 한정되며 실제 test run step 에는 없다. 합격.

---

## 3. signature-c Playwright spec false green 검증

### 3.1 page.setContent 0건 확인

spec 본문에서 `page.setContent` 는 주석(라인 27, 36)에만 등장하고 실제 호출 코드 0건이다. 모든 BE 응답은 `page.route().fulfill()` 로 intercept 하고 `page.evaluate` 내에서 fetch 를 호출하는 구조다. false green 방지 원칙(audit-slice-a 패턴) 준수. 합격.

### 3.2 || true / test.skip(!ok) 0건 확인

spec 코드에서 `|| true` 패턴은 package-lock.json 에서만 나타나며 spec 파일 내 test 코드에서는 0건이다. `test.skip(!ok)` 형식도 없다. 모든 active case 는 `expect(result.status).toBe(N)` 등 명시적 assertion 으로 실패를 전파한다. 합격.

### 3.3 test.fixme 사용 정합 (4건 SC-6/7/9/10)

FE 미구현 4건(SC-6 bundle 위치, SC-7 UUID DOM 가드, SC-9 passive:false touch, SC-10 canvas 사이즈)은 모두 `test.fixme('제목', async () => { /* TODO */ })` 로 표기한다. fixme 내부는 TODO 주석만 있고 실행 코드가 없어 실 실행 시 "fixme" 상태로 표시될 뿐 PASS 되지 않는다. 미구현 상태에서 false green 차단이 구조적으로 보장된다. 합격.

### 3.4 playwright.config.ts signature-c-smoke project

`signature-c-smoke` project 가 `testMatch: [/.*\/signature-c\/signature-c-smoke\.spec\.ts/]` 로 격리된다. 기존 5개 project(web-order-app / web-estimate-app / electron-desktop / mobile-chrome / mobile-safari) 의 testMatch regex 에는 `signature-c` 경로가 포함되지 않아 기존 project 와 교차 실행 없다. `baseURL` 은 `QA_SIGNATURE_URL ?? QA_API_BASE_URL ?? http://localhost:8080` 으로 점진적 실 서버 연동이 가능하다. 합격.

---

## 4. DEFECT-C1 식별 정합

spec 파일 라인 18~24 에 FE 미구현 목록이 명시된다: signature.js (≤6KB gzip 없음), mobile.css canvas 클래스 없음, /d/{token}/s/{slipNo} HTML 서빙 없음, /share/{shareToken} HTML 서빙 없음, slip-service static resource 서빙 설정 없음, vite/esbuild build target 없음. 이를 근거로 SC-6/7/9/10 이 fixme 로 처리된 것이 정합하다.

SC-1~SC-5 및 SC-8 (8건 active)은 page.route() mock 으로 BE 계약을 검증하며 FE 번들 없이 실행 가능하다. Web Crypto SHA-256 검증 2건(결정성 + data URI split)도 브라우저 내장 API 만 사용하므로 FE 번들 무관하게 active 상태다.

---

## 5. 기존 spec/IT 회귀 영향

- 기존 Detox configurations (mobile-staff ios.sim / mobile-v4 android.emu) 은 `arologisEmulator` 디바이스를 참조하지 않으므로 기존 AVD 실행에 영향 없다.
- 기존 Playwright projects (web-order-app 등 5건 + nine-slice-smoke + arologis-sp-10-2)의 testMatch 에 `signature-c` 경로가 없어 회귀 없다.
- qa-e2e.yml 의 `playwright` job 은 `npx playwright test --reporter=list || true` dry-run 이므로 새 project 추가로 인한 CI FAIL 없다.

회귀 영향 0건. 합격.

---

## 6. 결함 요약

| 번호 | 심각도 | 내용 | 판정 |
|---|---|---|---|
| 없음 | — | — | 전 항목 합격 |

---

## 7. 종합 판정

arologis-mobile Detox 3 시나리오는 PhoneLoginScreen testID / Alert 텍스트가 실제 구현과 정합하고 기존 mobile-staff / mobile-v4 패턴을 100% 일관하게 따른다.

signature-c spec 은 false green 0건 구조(page.setContent 없음, || true 없음, test.skip(!ok) 없음)를 충족하며 FE 미구현 4건을 test.fixme 로 명확히 표기한다. BE API 계약 8건(mock 기반) + Web Crypto 2건이 active 상태로 즉시 실행 가능하다.

기존 spec/IT 회귀 영향 없음 확인.

**QA Cycle 1 판정: 합격 — 코드 수정 불필요.**
