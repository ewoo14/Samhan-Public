# SamhanLogis Phase 7 QA — Detox

React Native (Expo SDK 53) e2e 시나리오. 2 config:

- `ios.sim.release` / `ios.sim.debug` — `mobile-staff` (영업직원 견적, iPhone 14 simulator)
- `android.emu.release` / `android.emu.debug` — `mobile` v4 (거래처 주문서, Pixel API 33 emulator)

## 사전 조건

- Node 20+, Java 17 (Android), Xcode 15+ (iOS)
- iOS: macOS + Xcode + iPhone 14 simulator
- Android: Android Studio + Pixel_API_33 AVD (또는 다른 AVD 이름으로 `.detoxrc.js` 수정)

## 빌드

```sh
cd qa/detox
npm install

# iOS
npm run build:ios
npm run test:ios

# Android
npm run build:android
npm run test:android
```

빌드는 내부적으로 `clients/mobile-staff/` 또는 `clients/mobile/` 에서 `expo prebuild` 후 native 빌드를 실행한다.

## 시나리오

### `e2e/mobile-staff/` (3 시나리오, iOS)

- `estimate-form.test.ts` — WebView 진입 + 네트워크 단절 fallback
- `line-grid.test.ts` — 라인 grid + sidebar
- `confirm.test.ts` — 확정 흐름

### `e2e/mobile-v4/` (3 시나리오, Android)

- `partner-bizgate.test.ts` — BizGate SSO
- `mobile-gate-4-categories.test.ts` — 4 카테고리 grid
- `webview-order-confirm.test.ts` — 주문 확정 + 임시저장 복원

## CI

`.github/workflows/qa-e2e.yml` 의 `detox-android` job 이 macOS runner 에서 자동 실행. iOS 는 self-hosted macOS runner 또는 EAS Build 후속 통합 예정.

## 주의

- WebView 안 legacy (estimate-app v2 / order-app v4) 의 DOM 인터랙션은 detox 의 `by.web.*` matcher 로 가능하나 iOS 안정성이 더 높음. Android 는 가시성 + native bridge 레벨 검증을 우선.
- backend 미가동 시 happy path 시나리오는 timeout 발생 — CI 에서는 backend up 후 실행.
