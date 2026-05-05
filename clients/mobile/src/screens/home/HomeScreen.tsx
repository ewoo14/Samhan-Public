/**
 * HomeScreen v4 — legacy 4 카테고리 진입 + 추가 메뉴 (모두 LegacyOrder WebView 로 진입).
 *
 * DECISIONS Phase 6 v4 — legacy index.html 임베드.
 *
 * v3 → v4 변경:
 *   - 4 카테고리 버튼 클릭 → `OrderTab/LegacyOrder` 진입 + `initialCategory` 전달.
 *   - 추가 메뉴 (분기계산 / 견적·주문 / 과거 발송내역 / 주문저장 / 저장내역) — 모두 LegacyOrder 진입 통일.
 *     (legacy index.html 의 `enterMobile`/`btnSendOrder`/`btnHistory`/`btnSaveDraft`/`btnDraftList` 가
 *      WebView 안에서 처리)
 *   - draftStore 직접 호출 폐기 — legacy 가 localStorage 또는 partner-order-service API 로 임시저장 처리.
 *
 * legacy 출처 (`migration/source/scripts/partner-order/index.html`):
 *   - line 119  : `.mobile-gate { display:flex; flex-direction:column; gap:16px; margin:20px 0 12px }`
 *   - line 121  : `.select-big { width:100%; height:150px; ... }`
 *   - line 195  : `body.mobile-mode .top { display:none !important }` → titleBar 미표시 (WebView 안 표시)
 *   - line 685~689 : `<div class="mobile-gate"><button class="select-big select-home">홈멀티</button>...</div>`
 *   - JS line 4467~4469 : `el('#btnEnterHome').addEventListener('click', ()=>enterMobile('home'))`
 *
 * [PR #66 회고 — 통합 fix 2026-05-05]
 *   - P0 #1 : 4 카테고리 textColor 통일 (#111827, legacy `--c-strong` 일관) — array entry 제거,
 *             selectBigText stylesheet 1곳에서만 정의.
 *   - P0 #2 : DC notice/error View 완전 제거 (dcConfigStore 의 error 는 console.warn 만).
 *   - P0 #3 : 상단 titleBar 삭제 (legacy `body.mobile-mode .top { display:none !important }` 일관).
 *             거래처명/사업자번호는 WebView 안 legacy 가 표시.
 *   - P0 #4 : mobile-gate paddingBottom 제거 (legacy `margin: 20px 0 12px` 일관) — legacyMobile.ts.
 *   - P1 : 추가 메뉴 5개 (extraMenuSection) 보존 (정정 #17 의도).
 *
 * UUID 미노출 — partnerName/partnerCode 는 본 화면에서 표시하지 않음 (WebView 안 legacy 표시).
 */

import { useEffect } from 'react';
import { useNavigation } from '@react-navigation/native';
import type { CompositeNavigationProp } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useDcConfigStore } from '@/stores/dcConfigStore';
import { legacyMobileGateStyles, legacyVars } from '@/styles/legacyMobile';
import type { LegacyCategory, OrderStackParamList, RootTabParamList } from '@/navigation/types';

type Nav = CompositeNavigationProp<
  BottomTabNavigationProp<RootTabParamList, 'HomeTab'>,
  NativeStackNavigationProp<OrderStackParamList>
>;

/** legacy 4 카테고리 button label (index.html line 686~689) — textColor 는 styles.selectBigText 에서 일괄. */
const CATEGORIES: Array<{
  key: 'home' | 'single' | 'comm' | 'old';
  label: string;
  styleKey: 'selectHome' | 'selectSingle' | 'selectComm' | 'selectOld';
}> = [
  { key: 'home', label: '홈멀티', styleKey: 'selectHome' },
  { key: 'single', label: '싱글 세트', styleKey: 'selectSingle' },
  { key: 'comm', label: '상업멀티', styleKey: 'selectComm' },
  { key: 'old', label: '구형', styleKey: 'selectOld' },
];

export function HomeScreen(): JSX.Element {
  const nav = useNavigation<Nav>();
  // PM 결정 U3 — dcConfigStore 는 backend 적용 그대로 유지, RN 시각만 제거.
  // error 는 사용자 노출 X, console.warn 으로만 통지.
  const dcError = useDcConfigStore((s) => s.error);

  useEffect(() => {
    if (dcError) {
      // eslint-disable-next-line no-console
      console.warn('[HomeScreen] dcConfig load error (silent):', dcError);
    }
  }, [dcError]);

  /** legacy `enterMobile(which)` → WebView 안에서 카테고리 사전 진입. */
  const handleEnter = (key: LegacyCategory): void => {
    nav.navigate('OrderTab', { screen: 'LegacyOrder', params: { initialCategory: key } });
  };

  /** v4: 추가 메뉴 5개 모두 단일 LegacyOrder 진입 — WebView 안 legacy 가 분기 처리. */
  const handleOpenLegacy = (): void => {
    nav.navigate('OrderTab', { screen: 'LegacyOrder', params: undefined });
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        {/* legacy `.mobile-gate` 4 카테고리 큰 진입 버튼 (line 685~689) */}
        <View style={legacyMobileGateStyles.mobileGate} testID="mobile-gate">
          {CATEGORIES.map((cat) => (
            <Pressable
              key={cat.key}
              style={({ pressed }) => [
                legacyMobileGateStyles.selectBig,
                legacyMobileGateStyles[cat.styleKey],
                pressed && styles.pressed,
              ]}
              onPress={() => handleEnter(cat.key)}
              testID={`enter-${cat.key}`}
            >
              <Text style={legacyMobileGateStyles.selectBigText}>{cat.label}</Text>
            </Pressable>
          ))}
        </View>

        {/* 정정 #17 — legacy partner-order 모바일 분기 추가 5 메뉴 (PM 결정 U1: 보존) */}
        <View style={styles.extraMenuSection}>
          <Text style={styles.extraMenuHeader}>추가 메뉴</Text>

          <Pressable
            style={({ pressed }) => [styles.menuButton, styles.menuBranch, pressed && styles.pressed]}
            onPress={handleOpenLegacy}
            testID="menu-branch-calc"
          >
            <Text style={styles.menuButtonLabel}>임의 분기계산</Text>
            <Text style={styles.menuButtonHint}>WebView 진입 후 legacy `pageBranch` 자동</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [styles.menuButton, styles.menuSendOrder, pressed && styles.pressed]}
            onPress={handleOpenLegacy}
            testID="menu-send-order"
          >
            <Text style={styles.menuButtonLabel}>견적·주문하기</Text>
            <Text style={styles.menuButtonHint}>WebView 안 legacy 견적/주문 모달</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [styles.menuButton, styles.menuHistory, pressed && styles.pressed]}
            onPress={handleOpenLegacy}
            testID="menu-history"
          >
            <Text style={styles.menuButtonLabel}>과거 발송내역 확인</Text>
            <Text style={styles.menuButtonHint}>WebView 안 legacy `pageHistory`</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [styles.menuButton, styles.menuSaveDraft, pressed && styles.pressed]}
            onPress={handleOpenLegacy}
            testID="menu-save-draft"
          >
            <Text style={styles.menuButtonLabel}>주문저장</Text>
            <Text style={styles.menuButtonHint}>WebView 안 legacy `btnSaveDraft`</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [styles.menuButton, styles.menuDraftList, pressed && styles.pressed]}
            onPress={handleOpenLegacy}
            testID="menu-draft-list"
          >
            <Text style={styles.menuButtonLabel}>저장내역</Text>
            <Text style={styles.menuButtonHint}>WebView 안 legacy `btnDraftList`</Text>
          </Pressable>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: legacyVars.cBg },
  scroll: { paddingBottom: 30 },
  pressed: { opacity: 0.85 },

  extraMenuSection: {
    paddingHorizontal: 16,
    paddingTop: 4,
    gap: 10,
  },
  extraMenuHeader: {
    fontSize: 14,
    fontWeight: '700',
    color: legacyVars.cMuted,
    paddingTop: 8,
    paddingBottom: 4,
  },
  menuButton: {
    minHeight: 64,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    flexDirection: 'column',
    justifyContent: 'center',
    gap: 2,
  },
  menuButtonLabel: {
    fontSize: 18,
    fontWeight: '800',
    color: legacyVars.cStrong,
  },
  menuButtonHint: {
    fontSize: 12,
    color: legacyVars.cMuted,
  },
  // legacy `#btnOpenBranch` (line 668) — 분기계산 (보라 배경)
  menuBranch: {
    backgroundColor: '#F5F3FF',
    borderColor: '#C4B5FD',
  },
  // legacy `#btnSendOrder` (line 1086) — 견적/주문 (강조 brand)
  menuSendOrder: {
    backgroundColor: '#ECFEFF',
    borderColor: '#67E8F9',
  },
  // legacy `#btnHistory` (line 671) — 과거 발송내역
  menuHistory: {
    backgroundColor: '#F0FDF4',
    borderColor: '#86EFAC',
  },
  // legacy `#btnSaveDraft` (mobile) — 주문저장
  menuSaveDraft: {
    backgroundColor: '#FFFBEB',
    borderColor: '#FCD34D',
  },
  // legacy `#btnDraftList` (mobile) — 저장내역
  menuDraftList: {
    backgroundColor: '#FFF7ED',
    borderColor: '#FDBA74',
  },
});
