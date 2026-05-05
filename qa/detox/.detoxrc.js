/**
 * SamhanLogis Phase 7 QA — Detox config
 *
 * 2 app:
 *  - mobile-staff (영업직원 견적 RN, Expo SDK 53, iOS 우선)
 *  - mobile (거래처 주문서 v4 RN, Expo SDK 53, Android 우선)
 *
 * 두 앱 모두 WebView 안 legacy (estimate-app v2 / order-app v4) 흐름 검증.
 *
 * NOTE: Expo prebuild 산출물 (ios/, android/) 이 실제 빌드 시 필요.
 * CI 환경에서는 npx expo prebuild 후 detox build 호출.
 */

/** @type {Detox.DetoxConfig} */
module.exports = {
  testRunner: {
    args: {
      $0: 'jest',
      config: 'e2e/jest.config.js',
    },
    jest: {
      setupTimeout: 120000,
    },
  },
  apps: {
    'mobile-staff.ios.release': {
      type: 'ios.app',
      binaryPath: '../../clients/mobile-staff/ios/build/Build/Products/Release-iphonesimulator/SamhanMobileStaff.app',
      build: 'cd ../../clients/mobile-staff && npx expo prebuild -p ios --clean && xcodebuild -workspace ios/SamhanMobileStaff.xcworkspace -scheme SamhanMobileStaff -configuration Release -sdk iphonesimulator -derivedDataPath ios/build',
    },
    'mobile-staff.ios.debug': {
      type: 'ios.app',
      binaryPath: '../../clients/mobile-staff/ios/build/Build/Products/Debug-iphonesimulator/SamhanMobileStaff.app',
      build: 'cd ../../clients/mobile-staff && npx expo prebuild -p ios --clean && xcodebuild -workspace ios/SamhanMobileStaff.xcworkspace -scheme SamhanMobileStaff -configuration Debug -sdk iphonesimulator -derivedDataPath ios/build',
    },
    'mobile-v4.android.release': {
      type: 'android.apk',
      binaryPath: '../../clients/mobile/android/app/build/outputs/apk/release/app-release.apk',
      build: 'cd ../../clients/mobile && npx expo prebuild -p android --clean && cd android && ./gradlew assembleRelease assembleAndroidTest -DtestBuildType=release',
    },
    'mobile-v4.android.debug': {
      type: 'android.apk',
      binaryPath: '../../clients/mobile/android/app/build/outputs/apk/debug/app-debug.apk',
      build: 'cd ../../clients/mobile && npx expo prebuild -p android --clean && cd android && ./gradlew assembleDebug assembleAndroidTest -DtestBuildType=debug',
    },
  },
  devices: {
    simulator: {
      type: 'ios.simulator',
      device: { type: 'iPhone 14' },
    },
    emulator: {
      type: 'android.emulator',
      device: { avdName: 'Pixel_API_33' },
    },
  },
  configurations: {
    'ios.sim.release': {
      device: 'simulator',
      app: 'mobile-staff.ios.release',
      testRunner: { args: { roots: ['e2e/mobile-staff'] } },
    },
    'ios.sim.debug': {
      device: 'simulator',
      app: 'mobile-staff.ios.debug',
      testRunner: { args: { roots: ['e2e/mobile-staff'] } },
    },
    'android.emu.release': {
      device: 'emulator',
      app: 'mobile-v4.android.release',
      testRunner: { args: { roots: ['e2e/mobile-v4'] } },
    },
    'android.emu.debug': {
      device: 'emulator',
      app: 'mobile-v4.android.debug',
      testRunner: { args: { roots: ['e2e/mobile-v4'] } },
    },
  },
};
