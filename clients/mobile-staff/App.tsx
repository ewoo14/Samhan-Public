/**
 * App.tsx — mobile-staff v3 entry (W10-3 driver tab 통합).
 *
 * v1 (PR #63 close, commit `d69a7f7`) 의 RootNavigator + AuthStack + BottomTab 3-tab 전체 폐기.
 * v2 (PR #80, ad313ed) = SafeAreaProvider + StatusBar + 단일 EstimateWebViewScreen.
 * v3 (Phase 10 W10-3, 본 PR) = AppRootNavigator (estimate / driver mode 분기).
 *
 * 사용자 결정 (2026-05-07) — `clients/mobile-staff` 내부 driver tab 채택 (별도 mobile-driver 신규 X).
 *   - 기존 v2 영업직원 견적 WebView 100% 보존.
 *   - 신규 driver tab = AppRootNavigator 안 'driver' mode 로 분기.
 *
 * 사용자 명시 (Phase 6 DECISIONS, 2026-04-30):
 *   "앱 버전에서도 현재 견적서의 모바일 뷰를 그대로 사용하는 방안으로 진행".
 *
 * 인증 / RPC / mobile-mode 활성 / 뒤로가기:
 *   - estimate mode = EstimateWebViewScreen + WebView 안 legacy estimate 가 처리 (RN 미관여).
 *   - driver mode = JWT bearer (user-service 발급) + arologis-service driver-app 3 endpoint.
 *
 * Pretendard self-host (Designer-2 채택 2026-05-07):
 *   - jsdelivr CDN 회피 + `assets/fonts/Pretendard-*.otf` 9 weight 정식 도입.
 *   - usePretendardFontGuarded() 가 expo-font 가용성 + asset 등록 graceful guard.
 */

import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import AppRootNavigator from './src/screens/AppRootNavigator';
import { usePretendardFontGuarded } from './src/theme/usePretendardFontGuarded';

export default function App(): JSX.Element {
  // Phase 10 W10-3 — Pretendard self-host 정식 (graceful guard 보존).
  // expo-font 미설치 또는 asset 누락 시 ready=true (RN UI 미차단), WebView 안 legacy 는 자체 web
  // font (Pretendard self-host or CDN fallback) 로 렌더. driver tab 의 RN native UI 는 등록된
  // 'Pretendard' family 적용 — `theme/tokens.ts` 의 `typography.fontFamily.sans = 'Pretendard'`.
  const fontsReady = usePretendardFontGuarded();
  if (!fontsReady) {
    return (
      <SafeAreaProvider>
        <StatusBar style="dark" />
      </SafeAreaProvider>
    );
  }
  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <AppRootNavigator />
    </SafeAreaProvider>
  );
}
