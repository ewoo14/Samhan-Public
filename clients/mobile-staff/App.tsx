/**
 * App.tsx — mobile-staff v2 entry.
 *
 * v1 (PR #63 close, commit `d69a7f7`) 의 RootNavigator + AuthStack + BottomTab 3-tab 전체 폐기.
 * v2 = SafeAreaProvider + StatusBar + 단일 EstimateWebViewScreen.
 *
 * 사용자 명시:
 *   "앱 버전에서도 현재 견적서의 모바일 뷰를 그대로 사용하는 방안으로 진행" — DECISIONS Phase 6 §.
 *
 * 인증 / RPC / mobile-mode 활성 / 뒤로가기 모두 EstimateWebViewScreen + WebView 안 legacy estimate
 * 가 처리. RN 측 코드는 wrapper 만.
 */

import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import EstimateWebViewScreen from './src/screens/EstimateWebViewScreen';
import { usePretendardFontGuarded } from './src/theme/usePretendardFontGuarded';

export default function App(): JSX.Element {
  // Phase 7 4차 잔여 — Pretendard 통일 폰트 (graceful guard).
  // expo-font 미설치 또는 asset 누락 시 no-op (즉시 ready=true), WebView 안 legacy
  // 가 자체 web font (Pretendard CDN) 로 렌더하므로 RN native UI 폰트 미적용해도 무영향.
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
      <EstimateWebViewScreen />
    </SafeAreaProvider>
  );
}
