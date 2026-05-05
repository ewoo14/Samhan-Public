/**
 * App.tsx — Mobile v4 entry (회고 #2 정정).
 *
 * 회고 #2 (2026-05-05) — 사용자 명시:
 *   "주문서는 ... 처음 모바일 게이트를 제외한 나머지는 모두 다름을 확인."
 *
 * 정정 결정 (mobile-staff v3 의 `App.tsx` 패턴 1:1 적용):
 *   - 이전 v4 = QueryClientProvider + NavigationContainer + RootNavigator (AuthStack + BottomTab + 7+ screen).
 *   - 신규 v4 = SafeAreaProvider + StatusBar + 단일 MobileOrderWebViewScreen.
 *
 * 인증 / RPC / mobile-mode 활성 / 뒤로가기 모두 MobileOrderWebViewScreen + WebView 안 order-legacy v4
 * 가 처리. RN 측 코드는 wrapper 만.
 */

import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import MobileOrderWebViewScreen from './src/screens/MobileOrderWebViewScreen';

export default function App(): JSX.Element {
  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <MobileOrderWebViewScreen />
    </SafeAreaProvider>
  );
}
