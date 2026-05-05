/**
 * navigation 타입 정의 — react-navigation v7 표준 declaration merging.
 *
 * v4 (legacy 임베드):
 *   - Order Stack 의 6 React 화면 (OrderList/OrderForm/OrderDetail/ProductPicker/
 *     BranchCalc/DraftList) 폐기 → 단일 `LegacyOrder` (react-native-webview).
 *   - 모든 견적/주문/조회/저장 동작 = WebView 안 legacy index.html 이 처리.
 *
 * Stack:
 *   - Auth: BizGate / TempPassword / Register (인증 통과 전 — RN native 보존)
 *   - Main: BottomTab (인증 후 진입 — RN native 보존)
 *
 * BottomTab:
 *   - Home: HomeScreen (legacy 4 카테고리 진입 버튼 + 추가 메뉴)
 *   - Order: LegacyOrder (WebView)
 *   - Notifications: NotificationListScreen (RN native)
 *   - Profile: ProfileScreen → SettingsScreen (RN native)
 */

import type { NavigatorScreenParams } from '@react-navigation/native';

export type AuthStackParamList = {
  BizGate: undefined;
  TempPassword: { partnerCode: string; partnerName: string };
  Register: { partnerCode?: string };
};

/**
 * legacy `enterMobile(which)` 카테고리 키 (line 4467~4469).
 * - home : 홈멀티
 * - single : 싱글 세트
 * - comm : 상업멀티
 * - old : 구형
 */
export type LegacyCategory = 'home' | 'single' | 'comm' | 'old';

/**
 * v4: 단일 화면 (LegacyOrder) — react-native-webview 로 legacy index.html 임베드.
 *
 * 이전 v3 의 6 화면 (OrderList/OrderForm/OrderDetail/ProductPicker/BranchCalc/DraftList) 모두
 * WebView 가 처리 — RN 측은 navigation routing 만 담당.
 *
 * `initialCategory`: HomeScreen 의 4 카테고리 버튼 클릭 시 legacy `enterMobile(which)` 사전 트리거.
 */
export type OrderStackParamList = {
  LegacyOrder: { initialCategory?: LegacyCategory } | undefined;
};

export type ProfileStackParamList = {
  Profile: undefined;
  Settings: undefined;
};

export type RootTabParamList = {
  HomeTab: undefined;
  OrderTab: NavigatorScreenParams<OrderStackParamList>;
  NotificationsTab: undefined;
  ProfileTab: NavigatorScreenParams<ProfileStackParamList>;
};

export type RootStackParamList = {
  Auth: NavigatorScreenParams<AuthStackParamList>;
  Main: NavigatorScreenParams<RootTabParamList>;
};

declare global {
  namespace ReactNavigation {
    interface RootParamList extends RootStackParamList {}
  }
}
