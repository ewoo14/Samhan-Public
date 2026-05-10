/**
 * SlipEditRequestsScreen — Phase 12 PR-H3 신규 (mobile-staff FE-2).
 *
 * 창고 직원 (WAREHOUSE) 모바일 화면 — 모든 slip 의 PENDING 수정 요청을 단일 list 로 노출하고
 * 즉시 수락 / 거절을 처리할 수 있다.
 *
 * 시나리오:
 *   - 창고 직원이 모바일로 입고 / 검수 작업 중에 영업직원의 수정 요청이 도착하면 본 화면에서
 *     PENDING list 를 확인하고 수락 / 거절 (사유 동반) 을 처리한다.
 *   - 단일 slip 의 상세는 SlipDetailScreen 에서도 동일 처리 가능 — 본 화면은 multiple slip 의
 *     PENDING 을 한눈에 보는 inbox 역할.
 *
 * 데이터 소스:
 *   - GET `/slips/edit-requests?status=PENDING` (api/slipEditRequest.ts listPendingSlipEditRequests).
 *   - 진입 시점 1회 + pull-to-refresh + 30초 polling fallback (SSE 가 영구 연결 실패 시).
 *   - 추가 SSE 구독: gateway 가 user 단위 broadcast 채널을 발행하면 향후 wire-up 가능 — 본 PR
 *     은 polling + Detail 화면 내부 SSE 로 충분.
 *
 * 한국어 UI / ROLE 풀네임 / UUID 비공개 가드 일관.
 *
 * data-testid:
 *   - `slip-edit-requests-list-mobile`
 *   - `slip-edit-requests-empty-mobile`
 *   - `slip-edit-requests-item-mobile-${id}`
 *   - `slip-edit-requests-approve-mobile-${id}`
 *   - `slip-edit-requests-reject-mobile-${id}`
 *   - `slip-edit-requests-reject-reason-input-mobile`
 *   - `slip-edit-requests-reject-submit-mobile`
 *   - `slip-edit-requests-reject-cancel-mobile`
 */

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  FlatList,
  Modal,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import {
  approveSlipEdit,
  listPendingSlipEditRequests,
  rejectSlipEdit,
  type SlipEditRequestActorRole,
  type SlipEditRequestResponse,
} from '../api/slipEditRequest';
import { badgeStyle, colors, radii, spacing, typography } from '../theme/tokens';

interface Props {
  /** JWT access token — 진입 직전 user-service `/auth/me` 로 ROLE 확인 후 보관. */
  token: string | null;
  /**
   * 현재 로그인 사용자 ROLE — WAREHOUSE / MASTER / MANAGER 만 진입 의도.
   * 그 외 ROLE 진입 시 안내 화면 노출 (BE 가 403 을 반환하지만 client 가드 1차).
   */
  currentUserRole: SlipEditRequestActorRole | null;
  /** slip 상세로 이동하는 콜백 — 미전달 시 카드 클릭 무시 (read-only inbox 모드). */
  onOpenSlip?: (slipId: string, slipNo: string) => void;
  /** 뒤로가기 콜백 — 미전달 시 버튼 미표시. */
  onBack?: () => void;
}

/** polling 주기 (ms) — SSE 미가용 시 fallback. */
const POLL_INTERVAL_MS = 30_000;

export default function SlipEditRequestsScreen({
  token,
  currentUserRole,
  onOpenSlip,
  onBack,
}: Props): JSX.Element {
  const [items, setItems] = useState<SlipEditRequestResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [approvingId, setApprovingId] = useState<string | null>(null);
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectTargetId, setRejectTargetId] = useState<string | null>(null);
  const [rejectTargetSlipNo, setRejectTargetSlipNo] = useState<string>('');
  const [rejectReasonDraft, setRejectReasonDraft] = useState('');
  const [rejectSubmitting, setRejectSubmitting] = useState(false);

  const canResolve =
    currentUserRole === 'WAREHOUSE' ||
    currentUserRole === 'MASTER' ||
    currentUserRole === 'MANAGER';

  const load = useCallback(async () => {
    if (!canResolve) {
      setLoading(false);
      return;
    }
    setError(null);
    try {
      const data = await listPendingSlipEditRequests(token);
      setItems(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [token, canResolve]);

  useEffect(() => {
    load();
  }, [load]);

  // polling fallback — SSE 미가용 환경 보호.
  useEffect(() => {
    if (!canResolve) return;
    const id = setInterval(() => {
      load();
    }, POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [load, canResolve]);

  const onRefresh = () => {
    setRefreshing(true);
    load();
  };

  const onApprove = async (req: SlipEditRequestResponse) => {
    if (!canResolve || approvingId !== null) return;
    setApprovingId(req.id);
    setError(null);
    try {
      await approveSlipEdit(token, req.slipId, req.id);
      // 낙관적 제거 (PENDING list 에서 빠짐).
      setItems((prev) => prev.filter((r) => r.id !== req.id));
      Alert.alert('수락 완료', `전표 ${req.slipNo} 수정 요청을 수락했습니다.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setApprovingId(null);
    }
  };

  const onOpenReject = (req: SlipEditRequestResponse) => {
    if (!canResolve) return;
    setRejectTargetId(req.id);
    setRejectTargetSlipNo(req.slipNo);
    setRejectReasonDraft('');
    setRejectModalOpen(true);
  };

  const onSubmitReject = async () => {
    const trimmed = rejectReasonDraft.trim();
    if (
      trimmed.length === 0 ||
      rejectTargetId == null ||
      rejectSubmitting
    ) {
      return;
    }
    const target = items.find((r) => r.id === rejectTargetId);
    if (!target) {
      setRejectModalOpen(false);
      setRejectTargetId(null);
      return;
    }
    setRejectSubmitting(true);
    setError(null);
    try {
      await rejectSlipEdit(token, target.slipId, target.id, {
        rejectionReason: trimmed,
      });
      setItems((prev) => prev.filter((r) => r.id !== target.id));
      setRejectModalOpen(false);
      setRejectTargetId(null);
      setRejectReasonDraft('');
      Alert.alert('거절 완료', `전표 ${target.slipNo} 수정 요청을 거절했습니다.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setRejectSubmitting(false);
    }
  };

  // ---- ROLE 가드 — WAREHOUSE / 관리자 외 진입 시 안내. ----
  if (!canResolve) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.center}>
          <Text style={styles.muted}>창고 직원 전용 화면입니다.</Text>
          <Text style={styles.muted}>현재 ROLE: {currentUserRole ?? '미인증'}</Text>
          {onBack ? (
            <TouchableOpacity onPress={onBack} style={styles.backBtnCenter}>
              <Text style={styles.backLabel}>뒤로</Text>
            </TouchableOpacity>
          ) : null}
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        {onBack ? (
          <TouchableOpacity
            onPress={onBack}
            style={styles.backBtn}
            testID="slip-edit-requests-back-mobile"
          >
            <Text style={styles.backLabel}>{'< 뒤로'}</Text>
          </TouchableOpacity>
        ) : null}
        <View style={styles.headerInfo}>
          <Text style={styles.h1}>수정 요청 처리</Text>
          <Text style={styles.subtitle}>
            대기 중 {items.length}건 · 30초마다 자동 새로고침
          </Text>
        </View>
      </View>

      {error ? (
        <View style={styles.errorCard} testID="slip-edit-requests-error-mobile">
          <Text style={[styles.errorText, badgeStyle('warn')]}>오류</Text>
          <Text style={styles.errorMessage}>{error}</Text>
        </View>
      ) : null}

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.action.brand} />
          <Text style={styles.muted}>대기 중 요청 불러오는 중…</Text>
        </View>
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
          }
          testID="slip-edit-requests-list-mobile"
          ListEmptyComponent={
            <View style={styles.empty} testID="slip-edit-requests-empty-mobile">
              <Text style={styles.muted}>대기 중인 수정 요청이 없습니다</Text>
            </View>
          }
          renderItem={({ item }) => (
            <PendingCard
              item={item}
              approving={approvingId === item.id}
              busy={approvingId !== null}
              onApprove={() => onApprove(item)}
              onReject={() => onOpenReject(item)}
              onOpenSlip={
                onOpenSlip
                  ? () => onOpenSlip(item.slipId, item.slipNo)
                  : undefined
              }
            />
          )}
        />
      )}

      {/* 거절 사유 모달. */}
      <Modal
        visible={rejectModalOpen}
        animationType="slide"
        transparent
        onRequestClose={() => setRejectModalOpen(false)}
      >
        <View style={styles.modalBackdrop}>
          <ScrollView
            style={styles.modalCard}
            contentContainerStyle={styles.modalContent}
            keyboardShouldPersistTaps="handled"
          >
            <Text style={styles.modalTitle}>전표 {rejectTargetSlipNo} 거절</Text>
            <Text style={styles.modalDescription}>
              영업직원에게 거절 사유가 전달됩니다. (최대 500자)
            </Text>
            <TextInput
              style={styles.modalInput}
              value={rejectReasonDraft}
              onChangeText={setRejectReasonDraft}
              placeholder="예: 이미 출고 완료된 전표로 수정 불가"
              placeholderTextColor={colors.ink.tertiary}
              multiline
              maxLength={500}
              editable={!rejectSubmitting}
              testID="slip-edit-requests-reject-reason-input-mobile"
            />
            <View style={styles.modalActions}>
              <TouchableOpacity
                onPress={() => {
                  setRejectModalOpen(false);
                  setRejectTargetId(null);
                }}
                disabled={rejectSubmitting}
                style={styles.modalCancelBtn}
                testID="slip-edit-requests-reject-cancel-mobile"
              >
                <Text style={styles.modalCancelLabel}>취소</Text>
              </TouchableOpacity>
              <TouchableOpacity
                onPress={onSubmitReject}
                disabled={
                  rejectSubmitting || rejectReasonDraft.trim().length === 0
                }
                style={[
                  styles.modalSubmitBtn,
                  styles.modalRejectBtn,
                  (rejectSubmitting || rejectReasonDraft.trim().length === 0) &&
                    styles.btnDisabled,
                ]}
                testID="slip-edit-requests-reject-submit-mobile"
              >
                <Text style={styles.modalSubmitLabel}>
                  {rejectSubmitting ? '전송 중…' : '거절'}
                </Text>
              </TouchableOpacity>
            </View>
          </ScrollView>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

interface PendingCardProps {
  item: SlipEditRequestResponse;
  approving: boolean;
  busy: boolean;
  onApprove: () => void;
  onReject: () => void;
  onOpenSlip?: () => void;
}

function PendingCard({
  item,
  approving,
  busy,
  onApprove,
  onReject,
  onOpenSlip,
}: PendingCardProps): JSX.Element {
  const time = formatTime(item.createdAt);
  return (
    <View
      style={styles.card}
      testID={`slip-edit-requests-item-mobile-${item.id}`}
    >
      <View style={styles.cardHead}>
        <Text style={styles.cardSlipNo}>전표 {item.slipNo}</Text>
        <Text style={styles.cardTime}>{time}</Text>
      </View>
      <View style={styles.cardAuthor}>
        <Text style={styles.cardAuthorName}>{item.requesterFullName}</Text>
        <Text style={badgeStyle('info')}>{item.requesterRole}</Text>
      </View>
      <Text style={styles.cardReason}>{item.reason}</Text>
      <View style={styles.cardActions}>
        {onOpenSlip ? (
          <TouchableOpacity
            onPress={onOpenSlip}
            style={styles.openSlipBtn}
            testID={`slip-edit-requests-open-slip-mobile-${item.id}`}
          >
            <Text style={styles.openSlipLabel}>전표 보기</Text>
          </TouchableOpacity>
        ) : null}
        <View style={styles.actionGroup}>
          <TouchableOpacity
            onPress={onApprove}
            disabled={busy}
            style={[styles.approveBtn, busy && styles.btnDisabled]}
            testID={`slip-edit-requests-approve-mobile-${item.id}`}
          >
            <Text style={styles.approveBtnLabel}>
              {approving ? '수락 중…' : '수락'}
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={onReject}
            disabled={busy}
            style={[styles.rejectBtn, busy && styles.btnDisabled]}
            testID={`slip-edit-requests-reject-mobile-${item.id}`}
          >
            <Text style={styles.rejectBtnLabel}>거절</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

function formatTime(iso: string): string {
  try {
    const d = new Date(iso);
    const mo = String(d.getMonth() + 1).padStart(2, '0');
    const da = String(d.getDate()).padStart(2, '0');
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    return `${mo}/${da} ${hh}:${mm}`;
  } catch {
    return iso;
  }
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.surface.app },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: spacing[4], gap: spacing[2] },
  header: {
    paddingHorizontal: spacing[4],
    paddingTop: spacing[3],
    paddingBottom: spacing[3],
    backgroundColor: colors.surface.card,
    borderBottomWidth: 1,
    borderBottomColor: colors.line.default,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
  },
  headerInfo: { flex: 1, gap: spacing[1] },
  backBtn: {
    paddingVertical: spacing[1],
    paddingHorizontal: spacing[2],
    borderRadius: radii.button,
    borderWidth: 1,
    borderColor: colors.line.default,
  },
  backBtnCenter: {
    paddingVertical: spacing[2],
    paddingHorizontal: spacing[4],
    borderRadius: radii.button,
    borderWidth: 1,
    borderColor: colors.line.default,
    marginTop: spacing[3],
  },
  backLabel: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.secondary,
    fontFamily: typography.fontFamily.sans,
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
    padding: spacing[3],
    marginBottom: spacing[2],
    borderWidth: 1,
    borderColor: colors.line.default,
    gap: spacing[2],
  },
  cardHead: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  cardSlipNo: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
  },
  cardTime: {
    fontSize: typography.fontSize.xs,
    color: colors.ink.tertiary,
    fontFamily: typography.fontFamily.sans,
  },
  cardAuthor: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
    flexWrap: 'wrap',
  },
  cardAuthorName: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.secondary,
    fontWeight: typography.fontWeight.medium,
    fontFamily: typography.fontFamily.sans,
  },
  cardReason: {
    fontSize: typography.fontSize.base,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
    lineHeight: typography.fontSize.base * typography.lineHeight.base,
  },
  cardActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: spacing[2],
  },
  actionGroup: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  openSlipBtn: {
    paddingVertical: spacing[2],
    paddingHorizontal: spacing[3],
    borderWidth: 1,
    borderColor: colors.line.default,
    borderRadius: radii.button,
  },
  openSlipLabel: {
    fontSize: typography.fontSize.xs,
    color: colors.ink.secondary,
    fontFamily: typography.fontFamily.sans,
    fontWeight: typography.fontWeight.medium,
  },
  approveBtn: {
    paddingVertical: spacing[2],
    paddingHorizontal: spacing[4],
    backgroundColor: colors.state.success,
    borderRadius: radii.button,
  },
  approveBtnLabel: {
    color: colors.ink.onPrimary,
    fontWeight: typography.fontWeight.semibold,
    fontFamily: typography.fontFamily.sans,
    fontSize: typography.fontSize.sm,
  },
  rejectBtn: {
    paddingVertical: spacing[2],
    paddingHorizontal: spacing[4],
    backgroundColor: colors.state.danger,
    borderRadius: radii.button,
  },
  rejectBtnLabel: {
    color: colors.ink.onPrimary,
    fontWeight: typography.fontWeight.semibold,
    fontFamily: typography.fontFamily.sans,
    fontSize: typography.fontSize.sm,
  },
  btnDisabled: { opacity: 0.5 },
  errorCard: {
    margin: spacing[3],
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
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.45)',
    justifyContent: 'flex-end',
  },
  modalCard: {
    backgroundColor: colors.surface.card,
    borderTopLeftRadius: radii.card,
    borderTopRightRadius: radii.card,
    maxHeight: '80%',
  },
  modalContent: {
    padding: spacing[4],
    gap: spacing[2],
  },
  modalTitle: {
    fontSize: typography.fontSize.xl,
    fontWeight: typography.fontWeight.bold,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
  },
  modalDescription: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.secondary,
    fontFamily: typography.fontFamily.sans,
  },
  modalInput: {
    minHeight: 100,
    maxHeight: 200,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
    borderWidth: 1,
    borderColor: colors.line.default,
    borderRadius: radii.button,
    fontSize: typography.fontSize.base,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
    backgroundColor: colors.surface.app,
    textAlignVertical: 'top',
  },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: spacing[2],
    marginTop: spacing[2],
  },
  modalCancelBtn: {
    paddingVertical: spacing[2],
    paddingHorizontal: spacing[4],
    borderWidth: 1,
    borderColor: colors.line.default,
    borderRadius: radii.button,
  },
  modalCancelLabel: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.secondary,
    fontFamily: typography.fontFamily.sans,
    fontWeight: typography.fontWeight.medium,
  },
  modalSubmitBtn: {
    paddingVertical: spacing[2],
    paddingHorizontal: spacing[4],
    backgroundColor: colors.action.brand,
    borderRadius: radii.button,
  },
  modalRejectBtn: {
    backgroundColor: colors.state.danger,
  },
  modalSubmitLabel: {
    color: colors.ink.onPrimary,
    fontWeight: typography.fontWeight.semibold,
    fontFamily: typography.fontFamily.sans,
    fontSize: typography.fontSize.sm,
  },
});
