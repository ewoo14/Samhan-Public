/**
 * DriverDashboardScreen — Phase 10 W10-3 신규.
 *
 * 본인 사용자 (ROLE_DRIVER) 의 오늘 배정 vehicle 목록 + 정차 상태 표시.
 *
 * 동작:
 *   1. mount 직후 GET `/driver-app/arologis/dispatches/today` 호출 (token = JWT).
 *   2. 응답 = `[{vehicleSequence, tonnage, status}]` (W10-1 backend 단순화).
 *   3. 각 vehicle 카드 = sequence + tonnage + status badge 표시.
 *   4. 각 stop 상태 (PENDING / ARRIVED / DELIVERED / FAILED / UNPARSED) = STOP_STATUS_BADGE 매핑.
 *
 * 토큰 사용:
 *   - `theme/tokens.ts` 의 surface / ink / line / sliceAccent / b-channel-* / b-unparsed.
 *   - W3+W4+W5+post-W5+W10-1 토큰 1:1 복제 일관 (Designer-2 채택).
 *
 * UUID 비공개:
 *   - 응답에 driverCode + vehicleSequence + tonnage + status 만. dispatch UUID 는 path 만.
 */

import { useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { fetchTodayDispatches, type DispatchVehicleSummary } from '../../api/arologis';
import { badgeStyle, colors, radii, spacing, typography } from '../../theme/tokens';

interface Props {
  /** JWT access token — driver tab 진입 시점에 user-service `/auth/me` 로 확인 후 보관. */
  token: string | null;
}

const TONNAGE_LABEL: Record<DispatchVehicleSummary['tonnage'], string> = {
  TONNAGE_1:    '1톤',
  TONNAGE_1_4:  '1.4톤',
  TONNAGE_2_5:  '2.5톤',
  TONNAGE_5:    '5톤',
  TONNAGE_BIG:  '대형',
};

const STATUS_LABEL: Record<DispatchVehicleSummary['status'], string> = {
  PENDING:   '대기',
  MATCHING:  '매칭중',
  ASSIGNED:  '배정완료',
  DEPARTED:  '출발',
  DELIVERED: '배송완료',
  CANCELLED: '취소',
};

const STATUS_BADGE_KIND: Record<DispatchVehicleSummary['status'], Parameters<typeof badgeStyle>[0]> = {
  PENDING:   'slicePending',
  MATCHING:  'info',
  ASSIGNED:  'channelPush',
  DEPARTED:  'channelEmail',
  DELIVERED: 'sliceSuccess',
  CANCELLED: 'sliceDeferred',
};

export default function DriverDashboardScreen({ token }: Props): JSX.Element {
  const [vehicles, setVehicles] = useState<DispatchVehicleSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setError(null);
    try {
      const data = await fetchTodayDispatches(token);
      setVehicles(data);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const onRefresh = () => {
    setRefreshing(true);
    load();
  };

  if (loading) {
    return (
      <SafeAreaView style={styles.center}>
        <ActivityIndicator size="large" color={colors.action.brand} />
        <Text style={styles.muted}>오늘의 배차 불러오는 중…</Text>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <Text style={styles.h1}>오늘의 배차</Text>
        <Text style={styles.subtitle}>본인 배정 vehicle {vehicles.length}대</Text>
      </View>
      {error && (
        <View style={styles.errorCard}>
          <Text style={[styles.errorText, badgeStyle('warn')]}>오류</Text>
          <Text style={styles.errorMessage}>{error}</Text>
        </View>
      )}
      <FlatList
        data={vehicles}
        keyExtractor={(item) => `vehicle-${item.vehicleSequence}`}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        ListEmptyComponent={
          !error ? (
            <View style={styles.empty}>
              <Text style={styles.muted}>배정된 vehicle 이 없습니다</Text>
            </View>
          ) : null
        }
        renderItem={({ item }) => (
          <View style={styles.card}>
            <View style={styles.cardHead}>
              <Text style={styles.cardTitle}>차량 #{item.vehicleSequence}</Text>
              <Text style={badgeStyle(STATUS_BADGE_KIND[item.status])}>
                {STATUS_LABEL[item.status]}
              </Text>
            </View>
            <View style={styles.cardRow}>
              <Text style={styles.label}>톤수</Text>
              <Text style={styles.value}>{TONNAGE_LABEL[item.tonnage]}</Text>
            </View>
            <View style={styles.cardRow}>
              <Text style={styles.label}>상태</Text>
              <Text style={styles.value}>{STATUS_LABEL[item.status]}</Text>
            </View>
          </View>
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.surface.app },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: colors.surface.app },
  header: {
    paddingHorizontal: spacing[6],
    paddingTop: spacing[4],
    paddingBottom: spacing[2],
    backgroundColor: colors.surface.card,
    borderBottomWidth: 1,
    borderBottomColor: colors.line.default,
  },
  h1: {
    fontSize: typography.fontSize.h1,
    fontWeight: typography.fontWeight.bold,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
  },
  subtitle: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.secondary,
    marginTop: spacing[1],
    fontFamily: typography.fontFamily.sans,
  },
  list: { padding: spacing[4], gap: spacing[3] },
  empty: { alignItems: 'center', paddingTop: spacing[10] },
  muted: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.tertiary,
    marginTop: spacing[2],
    fontFamily: typography.fontFamily.sans,
  },
  card: {
    backgroundColor: colors.surface.card,
    borderRadius: radii.card,
    padding: spacing[4],
    marginBottom: spacing[3],
    borderWidth: 1,
    borderColor: colors.line.default,
    // soft elevation alias (web tokens.css `--elev-card` 1:1)
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 3,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
  cardHead: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing[3],
  },
  cardTitle: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
  },
  cardRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: spacing[1],
  },
  label: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.secondary,
    fontFamily: typography.fontFamily.sans,
  },
  value: {
    fontSize: typography.fontSize.base,
    color: colors.ink.primary,
    fontWeight: typography.fontWeight.medium,
    fontFamily: typography.fontFamily.sans,
  },
  errorCard: {
    margin: spacing[4],
    padding: spacing[3],
    backgroundColor: colors.state.warningBg,
    borderRadius: radii.card,
    borderLeftWidth: 4,
    borderLeftColor: colors.state.warning,
  },
  errorText: { alignSelf: 'flex-start' },
  errorMessage: {
    marginTop: spacing[2],
    color: colors.ink.primary,
    fontSize: typography.fontSize.sm,
    fontFamily: typography.fontFamily.sans,
  },
});
