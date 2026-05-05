/**
 * LegacyOrderWebViewScreen v4 — react-native-webview 로 legacy partner-order/index.html 임베드.
 *
 * DECISIONS Phase 6 v4 (commit b15fa12):
 *   - v3 (React 변환 6 screen) 폐기 → v4 (legacy 코드 임베드) 채택.
 *   - 메인 견적/주문 화면 = `<WebView>` 로드 (legacy 9427 라인 모바일 viewport 그대로).
 *   - RN 프레임워크 (BizGate native + Bottom Tab + safe area) 보존.
 *
 * legacy 출처: `migration/source/scripts/partner-order/index.html`
 *   - line 119      : `.mobile-gate` (모바일 진입 4 카테고리)
 *   - line 4452     : `function isMobileNow() { return matchMedia('(max-width: 1280px)').matches; }`
 *   - line 4467~4469: `el('#btnEnterHome').addEventListener('click', ()=>enterMobile('home'))`
 *
 * Bridge 설계:
 *   - shimScript (legacyShim.ts) → `injectedJavaScriptBeforeContentLoaded` 로 사전 주입.
 *   - shim 이 `window.google.script.run.<fn>(...)` 체인을 SamhanLogis MS axios fetch 로 변환.
 *   - BizGate native 인증 token → `setAuthScript({...})` injectJavaScript 로 WebView 에 전달.
 *   - WebView → RN 메시지 (`postMessage`) — log / rpc-error / shim-installed / legacy-loaded.
 *
 * v3 → v4 폐기 화면 (대체):
 *   - OrderListScreen / OrderFormScreen / OrderDetailScreen / ProductPickerScreen /
 *     BranchCalcScreen / DraftListScreen — 모두 본 단일 WebView 로 대체.
 *
 * UUID 미노출: legacy index.html 자체가 사업자번호/거래처코드/모델명 만 노출 (UUID X).
 */

import { useFocusEffect, useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WebView, type WebViewMessageEvent, type WebViewNavigation } from 'react-native-webview';
import { useAuthStore } from '@/stores/authStore';
import { colors, fontSize, fontWeight } from '@/tokens/tokens';
import { getInjectedShim, setAuthScript } from '@/webview/legacyShim';
import { getLegacyUri } from '@/webview/legacySource';
import type { OrderStackParamList } from '@/navigation/types';

type LegacyRoute = RouteProp<OrderStackParamList, 'LegacyOrder'>;

const API_BASE_URL =
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  typeof (globalThis as any).__DEV__ !== 'undefined' && (globalThis as any).__DEV__
    ? 'http://localhost:8080'
    : 'https://api.samhan-air.com';

/**
 * legacy isMobileNow() trigger — userAgent 에 'samhan-mobile' 마커 + width hint.
 * 단 결정적 분기는 `(max-width: 1280px)` matchMedia 이므로 WebView 의 device width 가 자동 처리.
 */
const MOBILE_USER_AGENT_SUFFIX = ' SamhanMobileApp/0.4.0 (samhan-mobile)';

export function LegacyOrderWebViewScreen(): JSX.Element {
  const route = useRoute<LegacyRoute>();
  const nav = useNavigation();
  const token = useAuthStore((s) => s.token);
  const partnerCode = useAuthStore((s) => s.partnerCode);

  const webViewRef = useRef<WebView>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [shimReady, setShimReady] = useState(false);

  const initialUri = getLegacyUri({ category: route.params?.initialCategory });
  const shimScript = getInjectedShim({
    apiBaseUrl: API_BASE_URL,
    token,
    partnerCode,
  });

  // RN → WebView token 갱신 — auth 변경 시 동기화 (logout / 재로그인).
  useEffect(() => {
    if (!shimReady || !webViewRef.current) return;
    webViewRef.current.injectJavaScript(
      setAuthScript({ apiBaseUrl: API_BASE_URL, token, partnerCode }),
    );
  }, [shimReady, token, partnerCode]);

  // OrderTab 재진입 시 카테고리 hash 변경 — `enterMobile` 자동 호출 유도.
  useFocusEffect(
    useCallback(() => {
      const cat = route.params?.initialCategory;
      if (!cat || !shimReady || !webViewRef.current) return;
      const js = `
        (function() {
          try {
            if (typeof window.enterMobile === 'function') {
              window.enterMobile(${JSON.stringify(cat)});
            } else {
              location.hash = 'category=${cat}';
            }
          } catch (e) { /* swallow */ }
        })();
        true;
      `;
      webViewRef.current.injectJavaScript(js);
    }, [route.params?.initialCategory, shimReady]),
  );

  const handleMessage = useCallback((evt: WebViewMessageEvent) => {
    try {
      const raw = evt.nativeEvent.data;
      const msg = JSON.parse(raw) as { type: string; payload: unknown };
      if (msg.type === 'shim-installed') {
        setShimReady(true);
      } else if (msg.type === 'legacy-loaded') {
        setLoading(false);
      } else if (msg.type === 'rpc-error') {
        // dev 가시성 — production 은 silent (log-service 로 별도 push 가능).
        // eslint-disable-next-line no-console
        console.warn('[Legacy WebView] RPC error', msg.payload);
      } else if (msg.type === 'rpc-missing') {
        // eslint-disable-next-line no-console
        console.warn('[Legacy WebView] RPC missing — check legacyShim.ts mapping table', msg.payload);
      } else if (msg.type === 'host-close') {
        // legacy 가 google.script.host.close() 호출 — 모바일에서는 BottomTab 으로 복귀.
        nav.goBack();
      }
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('[Legacy WebView] message parse failed', e);
    }
  }, [nav]);

  const handleNavStateChange = useCallback((state: WebViewNavigation) => {
    if (!state.loading && state.url) setLoading(false);
  }, []);

  const handleError = useCallback((evt: { nativeEvent: { description?: string } }) => {
    const desc = evt?.nativeEvent?.description ?? '알 수 없는 오류';
    setLoadError(desc);
    setLoading(false);
  }, []);

  const handleReload = useCallback(() => {
    setLoadError(null);
    setLoading(true);
    setShimReady(false);
    webViewRef.current?.reload();
  }, []);

  const handleClearAuth = useCallback(() => {
    Alert.alert('주의', '본 화면은 legacy WebView 입니다. 로그아웃은 프로필 탭에서 진행해 주세요.');
  }, []);

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <View style={styles.headerBar} testID="legacy-webview-header">
        <Text style={styles.headerTitle}>주문서 (legacy)</Text>
        <View style={styles.headerActions}>
          <Pressable style={styles.headerBtn} onPress={handleReload} testID="legacy-reload">
            <Text style={styles.headerBtnLabel}>새로고침</Text>
          </Pressable>
          <Pressable style={styles.headerBtn} onPress={handleClearAuth}>
            <Text style={styles.headerBtnLabel}>?</Text>
          </Pressable>
        </View>
      </View>

      {loadError ? (
        <View style={styles.errorBox} testID="legacy-error">
          <Text style={styles.errorTitle}>legacy 화면 로드 실패</Text>
          <Text style={styles.errorDesc}>{loadError}</Text>
          <Text style={styles.errorHint}>
            URL: {initialUri}
            {'\n'}
            order.samhan-air.com 호스팅 또는 dev (localhost:5180) 가 켜져 있는지 확인하세요.
          </Text>
          <Pressable style={styles.retryBtn} onPress={handleReload}>
            <Text style={styles.retryBtnLabel}>재시도</Text>
          </Pressable>
        </View>
      ) : (
        <View style={styles.webviewWrap}>
          <WebView
            ref={webViewRef}
            source={{ uri: initialUri }}
            originWhitelist={['*']}
            javaScriptEnabled
            domStorageEnabled
            sharedCookiesEnabled
            thirdPartyCookiesEnabled
            allowsInlineMediaPlayback
            mediaPlaybackRequiresUserAction={false}
            setSupportMultipleWindows={false}
            applicationNameForUserAgent={MOBILE_USER_AGENT_SUFFIX}
            // shim 은 contentLoaded 이전 주입 — google.script.run 첫 호출 보호.
            injectedJavaScriptBeforeContentLoaded={shimScript}
            onMessage={handleMessage}
            onNavigationStateChange={handleNavStateChange}
            onError={handleError}
            onHttpError={handleError}
            renderLoading={() => (
              <View style={styles.loadingOverlay} pointerEvents="none">
                <ActivityIndicator size="large" color={colors.brand500} />
              </View>
            )}
            startInLoadingState
            style={styles.webview}
            testID="legacy-webview"
          />
          {loading && !loadError ? (
            <View style={styles.loadingOverlay} pointerEvents="none">
              <ActivityIndicator size="large" color={colors.brand500} />
              <Text style={styles.loadingText}>legacy 주문서 로드 중…</Text>
            </View>
          ) : null}
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.neutral0 },
  headerBar: {
    height: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    backgroundColor: colors.neutral0,
  },
  headerTitle: { fontSize: fontSize.lg, fontWeight: fontWeight.semibold, color: colors.text },
  headerActions: { flexDirection: 'row', gap: 8 },
  headerBtn: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 6,
    backgroundColor: colors.neutral100,
  },
  headerBtnLabel: { fontSize: fontSize.sm, color: colors.text, fontWeight: fontWeight.semibold },

  webviewWrap: { flex: 1, position: 'relative' },
  webview: { flex: 1, backgroundColor: '#ffffff' },

  loadingOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.6)',
    gap: 12,
  },
  loadingText: { color: colors.textMuted, fontSize: fontSize.sm },

  errorBox: {
    flex: 1,
    padding: 24,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 12,
    backgroundColor: '#FEF2F2',
  },
  errorTitle: { fontSize: 18, fontWeight: '800', color: '#991B1B' },
  errorDesc: { fontSize: fontSize.sm, color: '#991B1B', textAlign: 'center' },
  errorHint: {
    fontSize: 12,
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: 12,
    fontFamily: Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' }),
  },
  retryBtn: {
    marginTop: 16,
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: colors.brand500,
  },
  retryBtnLabel: { color: colors.neutral0, fontWeight: fontWeight.semibold, fontSize: fontSize.md },
});
