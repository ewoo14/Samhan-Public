/**
 * GpsBlockedScreen — Phase 10 W10-3 신규.
 *
 * 사용자 결정 4 GPS 하이브리드 (2026-05-07) — foreground 권한 거부 fallback = 어플 사용 불가.
 * 본 화면은 권한 거부 / 미가용 platform 시점에 표시되어 driver tab 진입 자체를 차단.
 *
 * 안내 내용:
 *   1. 권한 거부 사유 (foreground 의무, 거부 시 사용 불가).
 *   2. 설정 화면으로 이동 (또는 어플 재시작 안내).
 *   3. 권한 정책 (foreground = 의무, background = 선택, 사용자 결정 4 명시).
 */

import { Linking, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { badgeStyle, colors, radii, spacing, typography } from '../../theme/tokens';

export default function GpsBlockedScreen(): JSX.Element {
  const openSettings = async () => {
    try {
      await Linking.openSettings();
    } catch {
      // 미지원 platform — 무시.
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.iconWrap}>
          <Text style={badgeStyle('warn')}>GPS 권한 필요</Text>
        </View>
        <Text style={styles.h1}>어플 사용이 차단되었습니다</Text>
        <Text style={styles.subtitle}>
          driver tab 사용을 위해서는 GPS foreground 권한이 의무로 필요합니다.
        </Text>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>권한 정책 (사용자 결정 2026-05-07)</Text>
          <View style={styles.row}>
            <Text style={styles.label}>foreground</Text>
            <Text style={badgeStyle('warn')}>의무</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>background</Text>
            <Text style={badgeStyle('info')}>선택</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>거부 fallback</Text>
            <Text style={badgeStyle('sliceDeferred')}>어플 사용 불가</Text>
          </View>
        </View>

        <View style={styles.infoCard}>
          <Text style={styles.infoTitle}>해결 방법</Text>
          <Text style={styles.infoText}>1. 아래 "설정 열기" 버튼을 누릅니다.</Text>
          <Text style={styles.infoText}>2. 위치 권한을 "사용 중인 동안 허용" 이상으로 변경합니다.</Text>
          <Text style={styles.infoText}>3. 어플로 돌아와 재시작합니다.</Text>
        </View>

        <TouchableOpacity style={styles.btn} onPress={openSettings}>
          <Text style={styles.btnText}>설정 열기</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.surface.app },
  content: { padding: spacing[6], gap: spacing[3] },
  iconWrap: { alignItems: 'flex-start', marginBottom: spacing[3] },
  h1: {
    fontSize: typography.fontSize.h1,
    fontWeight: typography.fontWeight.bold,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
  },
  subtitle: {
    fontSize: typography.fontSize.base,
    color: colors.ink.secondary,
    marginTop: spacing[2],
    marginBottom: spacing[4],
    lineHeight: typography.fontSize.base * typography.lineHeight.base,
    fontFamily: typography.fontFamily.sans,
  },
  card: {
    backgroundColor: colors.surface.card,
    borderRadius: radii.card,
    padding: spacing[4],
    borderWidth: 1,
    borderColor: colors.line.default,
    marginBottom: spacing[3],
  },
  cardTitle: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.ink.primary,
    marginBottom: spacing[3],
    fontFamily: typography.fontFamily.sans,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing[2],
    borderBottomWidth: 0.5,
    borderBottomColor: colors.line.default,
  },
  label: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.secondary,
    fontFamily: typography.fontFamily.sans,
  },
  infoCard: {
    backgroundColor: colors.state.warningBg,
    borderRadius: radii.card,
    padding: spacing[4],
    borderLeftWidth: 4,
    borderLeftColor: colors.state.warning,
    marginBottom: spacing[3],
  },
  infoTitle: {
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.semibold,
    color: colors.ink.primary,
    marginBottom: spacing[2],
    fontFamily: typography.fontFamily.sans,
  },
  infoText: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.primary,
    paddingVertical: spacing[1],
    fontFamily: typography.fontFamily.sans,
  },
  btn: {
    backgroundColor: colors.action.brand,
    paddingVertical: spacing[3],
    paddingHorizontal: spacing[5],
    borderRadius: radii.button,
    alignItems: 'center',
    marginTop: spacing[3],
  },
  btnText: {
    color: colors.ink.onPrimary,
    fontWeight: typography.fontWeight.semibold,
    fontSize: typography.fontSize.base,
    fontFamily: typography.fontFamily.sans,
  },
});
