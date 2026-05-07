/**
 * AppRootNavigator — Phase 10 W10-3 신규.
 *
 * mobile-staff 의 최상위 navigation. 사용자 권한에 따라 다음 분기:
 *   - ROLE_DRIVER (driver tab) — 본 PR (W10-3) 신규.
 *   - 그 외 (영업직원 / 관리자) — 기존 v2 EstimateWebViewScreen (변경 X).
 *
 * 사용자 결정 (2026-05-07) — `clients/mobile-staff` 내부 driver tab 채택 (별도 mobile-driver 신규 X).
 *
 * 본 PR 진입 시점:
 *   - 기존 mobile-staff 의 v2 (단일 EstimateWebViewScreen) 동작 100% 보존.
 *   - 우상단 "역할 전환" 버튼으로 driver tab 활성 (개발/QA 가시성용).
 *   - production = user-service `/api/v1/auth/me` 응답 의 `roles[]` 에 ROLE_DRIVER 포함 시 자동 활성.
 *
 * 후속 (W10-4 slip 통합 시점):
 *   - JWT token 의 ROLE_DRIVER claim 자동 감지 → role state 결정.
 *   - dashboard → signature deeplink 활성 (vehicleSeq + stopSeq 전달).
 */

import { useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import EstimateWebViewScreen from './EstimateWebViewScreen';
import DriverTabNavigator from './driver/DriverTabNavigator';
import { colors, radii, spacing, typography } from '../theme/tokens';

type AppMode = 'estimate' | 'driver';

interface Props {
  /**
   * 진입 시점 default mode.
   *   - 'estimate' (default) — 기존 v2 동작 (영업직원 견적 WebView).
   *   - 'driver' — driver tab 직진입 (production user-service `/auth/me` 결과 ROLE_DRIVER 시 자동).
   */
  initialMode?: AppMode;
  /** JWT access token — driver tab 호출 시 사용. estimate WebView 은 자체 cookie 인증 (RN 미관여). */
  token?: string | null;
}

export default function AppRootNavigator({ initialMode = 'estimate', token = null }: Props): JSX.Element {
  const [mode, setMode] = useState<AppMode>(initialMode);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.modeBar}>
        <ModeButton
          label="영업견적"
          active={mode === 'estimate'}
          onPress={() => setMode('estimate')}
          testID="mode-estimate"
        />
        <ModeButton
          label="배송기사"
          active={mode === 'driver'}
          onPress={() => setMode('driver')}
          testID="mode-driver"
        />
      </View>
      <View style={styles.body}>
        {mode === 'estimate' ? (
          <EstimateWebViewScreen />
        ) : (
          <DriverTabNavigator token={token} />
        )}
      </View>
    </SafeAreaView>
  );
}

interface ModeButtonProps {
  label: string;
  active: boolean;
  onPress: () => void;
  testID?: string;
}

function ModeButton({ label, active, onPress, testID }: ModeButtonProps): JSX.Element {
  return (
    <TouchableOpacity
      onPress={onPress}
      style={[styles.modeBtn, active && styles.modeBtnActive]}
      testID={testID}
    >
      <Text style={[styles.modeLabel, active && styles.modeLabelActive]}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.surface.card },
  modeBar: {
    flexDirection: 'row',
    backgroundColor: colors.surface.card,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
    borderBottomWidth: 1,
    borderBottomColor: colors.line.default,
    gap: spacing[2],
  },
  modeBtn: {
    paddingVertical: spacing[2],
    paddingHorizontal: spacing[4],
    borderRadius: radii.button,
    borderWidth: 1,
    borderColor: colors.line.default,
  },
  modeBtnActive: {
    backgroundColor: colors.action.brand,
    borderColor: colors.action.brandActive,
  },
  modeLabel: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.secondary,
    fontWeight: typography.fontWeight.medium,
    fontFamily: typography.fontFamily.sans,
  },
  modeLabelActive: {
    color: colors.ink.onPrimary,
    fontWeight: typography.fontWeight.semibold,
  },
  body: { flex: 1 },
});
