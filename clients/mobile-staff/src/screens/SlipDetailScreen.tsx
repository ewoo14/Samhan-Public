/**
 * SlipDetailScreen — Phase 12 PR-H1 신규 (mobile-staff FE-2).
 * Phase 12 PR-H2 보강 — slip 헤더 필드에 AuditOverlay (변경 이력 취소선 + 수정자 색상) 적용.
 *
 * mobile-staff v3 (Phase 10 W10-3) 시점 = driver/estimate tab 만 보유 → slip 상세 화면 부재.
 * 본 화면은 PR-H1 의 SSE 실시간 + 코멘트 의 사용처로서 신규되었고, PR-H2 에서 audit overlay
 * 컴포넌트가 추가되어 partnerName / statusLabel 등 헤더 필드의 변경 이력을 시각적으로 노출한다.
 *
 * 범위:
 *   - slip 정보 영역 (slipNo / 상태 / 거래처명 — UUID 미노출)
 *   - **AuditOverlay (PR-H2)** — partnerName / statusLabel 변경 이력 취소선 + 수정자 hash 색상
 *   - **수정 횟수 헤더 (PR-H2)** — "수정 N회" 라벨 (DRIVER / SALES 도 모두 노출, read-only)
 *   - **복원 버튼 (PR-H2)** — MASTER / MANAGER ROLE 만 노출 (DRIVER 대상에서는 비표시)
 *   - 코멘트 영역 (목록 + 입력창 + 전송 버튼)
 *   - SSE 구독 (`subscribeToSlip`) 으로 코멘트/`slip.edit` 변경 실시간 반영
 *     (slip.edit 수신 시 audit log + 헤더 invalidate — onAuditEdit 콜백으로 부모에 통지)
 *
 * 한국어 UI / ROLE 풀네임 / UUID 비공개 가드 일관.
 *
 * data-testid (PR-H1 + PR-H2 추가):
 *   - `slip-detail-comment-list-mobile`
 *   - `slip-detail-comment-input-mobile`
 *   - `slip-detail-comment-submit-mobile`
 *   - `slip-detail-comment-item-mobile-${id}` (id = 코멘트 식별자, UI 미노출 — testID only)
 *   - `slip-detail-edit-count-mobile` (PR-H2)
 *   - `slip-detail-audit-revert-mobile-${auditLogId}` (PR-H2, MASTER/MANAGER 만)
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  RefreshControl,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import AuditOverlay from '../components/AuditOverlay';
import {
  listSlipAuditLogs,
  revertSlipAuditLog,
  type SlipAuditActorRole,
  type SlipAuditLogResponse,
} from '../api/slipAudit';
import {
  createSlipComment,
  deleteSlipComment,
  listSlipComments,
  type SlipCommentResponse,
} from '../api/slipComment';
import { subscribeToSlip, type SlipRealtimeEvent } from '../realtime/SlipRealtimeClient';
import { badgeStyle, colors, radii, spacing, typography } from '../theme/tokens';

interface Props {
  /** JWT access token — driver tab 진입 시점에 user-service `/auth/me` 로 확인 후 보관. */
  token: string | null;
  /** slip 식별자 — path 만, UI 미노출. */
  slipId: string;
  /** 헤더 표시용 slip 번호 (사용자 노출 식별자). 미전달 시 placeholder. */
  slipNo?: string;
  /** 헤더 표시용 거래처명. */
  partnerName?: string | null;
  /** 헤더 표시용 상태 라벨 (예: "출고", "검수 중"). */
  statusLabel?: string;
  /** 뒤로가기 콜백 — 미전달 시 버튼 미표시. */
  onBack?: () => void;
  /**
   * 현재 로그인 사용자 ROLE — PR-H2 복원 버튼 표시 가드.
   * MASTER / MANAGER 만 복원 버튼 노출. DRIVER / SALES / 그 외 = 비표시.
   * 미전달 시 비표시 (안전 default).
   */
  currentUserRole?: SlipAuditActorRole | null;
}

export default function SlipDetailScreen({
  token,
  slipId,
  slipNo,
  partnerName,
  statusLabel,
  onBack,
  currentUserRole,
}: Props): JSX.Element {
  const [comments, setComments] = useState<SlipCommentResponse[]>([]);
  const [auditLogs, setAuditLogs] = useState<SlipAuditLogResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [reverting, setReverting] = useState(false);
  const [realtimeStatus, setRealtimeStatus] = useState<'connecting' | 'live' | 'offline'>(
    'connecting',
  );
  const listRef = useRef<FlatList<SlipCommentResponse>>(null);

  // ----------------------------------------------------------------------
  // load — 코멘트 + audit logs 병렬 조회 (PR-H2: audit logs 추가).
  // 한쪽 실패 시 다른 한쪽은 표시 (실패한 쪽만 error 노출).
  // ----------------------------------------------------------------------
  const load = useCallback(async () => {
    setError(null);
    try {
      const [commentsData, auditData] = await Promise.allSettled([
        listSlipComments(token, slipId),
        listSlipAuditLogs(token, slipId),
      ]);
      if (commentsData.status === 'fulfilled') {
        setComments(commentsData.value);
      } else {
        setError(
          commentsData.reason instanceof Error
            ? commentsData.reason.message
            : String(commentsData.reason),
        );
      }
      if (auditData.status === 'fulfilled') {
        setAuditLogs(auditData.value);
      } else if (commentsData.status === 'fulfilled') {
        // 코멘트는 성공한 경우에만 audit 실패 메시지 노출 (코멘트 에러 우선).
        setError(
          auditData.reason instanceof Error
            ? auditData.reason.message
            : String(auditData.reason),
        );
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [token, slipId]);

  // ----------------------------------------------------------------------
  // PR-H2: field 별 audit log group + 수정 횟수 + 복원 권한.
  // ----------------------------------------------------------------------
  const auditByField = useMemo<Record<string, SlipAuditLogResponse[]>>(() => {
    const grouped: Record<string, SlipAuditLogResponse[]> = {};
    for (const log of auditLogs) {
      if (!grouped[log.field]) grouped[log.field] = [];
      grouped[log.field].push(log);
    }
    return grouped;
  }, [auditLogs]);

  const editCount = auditLogs.length;
  const canRevert = currentUserRole === 'MASTER' || currentUserRole === 'MANAGER';

  useEffect(() => {
    load();
  }, [load]);

  // SSE 구독 — comment.* + slip.edit (PR-H2) 이벤트 도착 시 목록 재조회 (간단 invalidate 전략).
  // React Query 미사용 환경 (mobile-staff) 이므로 cache invalidate = load() 재호출 방식.
  useEffect(() => {
    const sub = subscribeToSlip(slipId, token, (evt: SlipRealtimeEvent) => {
      if (evt.type === 'heartbeat') {
        setRealtimeStatus('live');
        return;
      }
      if (
        evt.type === 'comment.created' ||
        evt.type === 'comment.updated' ||
        evt.type === 'comment.deleted' ||
        evt.type === 'slip.edit'
      ) {
        setRealtimeStatus('live');
        load();
      }
    });
    return () => sub.close();
  }, [slipId, token, load]);

  const onRefresh = () => {
    setRefreshing(true);
    load();
  };

  const onSubmit = async () => {
    const trimmed = draft.trim();
    if (trimmed.length === 0 || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const created = await createSlipComment(token, slipId, { body: trimmed });
      setDraft('');
      // 낙관적 append — SSE 가 곧 도착하지만 즉시 반영.
      setComments((prev) => {
        if (prev.some((c) => c.id === created.id)) return prev;
        return [...prev, created];
      });
      setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 50);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSubmitting(false);
    }
  };

  const onDelete = async (commentId: string) => {
    try {
      await deleteSlipComment(token, slipId, commentId);
      setComments((prev) => prev.filter((c) => c.id !== commentId));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  // ----------------------------------------------------------------------
  // PR-H2 복원 — MASTER / MANAGER 만 호출 가능 (canRevert 가드).
  // BE 가 audit log 1건을 추가 기록하므로 success 시 load() 로 재조회.
  // ----------------------------------------------------------------------
  const onRevert = async (auditLogId: string) => {
    if (!canRevert || reverting) return;
    setReverting(true);
    setError(null);
    try {
      await revertSlipAuditLog(token, slipId, auditLogId);
      await load();
      Alert.alert('복원 완료', '선택한 시점의 값으로 복원되었습니다.');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setReverting(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        {onBack ? (
          <TouchableOpacity onPress={onBack} style={styles.backBtn} testID="slip-detail-back-mobile">
            <Text style={styles.backLabel}>{'< 뒤로'}</Text>
          </TouchableOpacity>
        ) : null}
        <View style={styles.headerInfo}>
          <View style={styles.titleRow}>
            <Text style={styles.h1}>전표 {slipNo ?? '상세'}</Text>
            {/* PR-H2: 수정 횟수 헤더 — DRIVER / SALES 도 모두 노출 (read-only). */}
            <Text style={styles.editCount} testID="slip-detail-edit-count-mobile">
              수정 {editCount}회
            </Text>
          </View>

          {/* PR-H2: AuditOverlay — partnerName / statusLabel 변경 이력 표시. */}
          <View style={styles.auditFieldRow}>
            <Text style={styles.auditFieldLabel}>거래처</Text>
            <View style={styles.auditFieldValue}>
              <AuditOverlay
                field="partnerName"
                currentValue={partnerName ?? null}
                history={auditByField['partnerName'] ?? []}
              />
              {canRevert && (auditByField['partnerName']?.length ?? 0) > 0 ? (
                <TouchableOpacity
                  onPress={() => onRevert(auditByField['partnerName']![auditByField['partnerName']!.length - 1].id)}
                  disabled={reverting}
                  style={styles.revertBtn}
                  testID={`slip-detail-audit-revert-mobile-${auditByField['partnerName']![auditByField['partnerName']!.length - 1].id}`}
                >
                  <Text style={styles.revertLabel}>{reverting ? '복원 중…' : '직전 값으로 복원'}</Text>
                </TouchableOpacity>
              ) : null}
            </View>
          </View>

          <View style={styles.auditFieldRow}>
            <Text style={styles.auditFieldLabel}>상태</Text>
            <View style={styles.auditFieldValue}>
              {statusLabel ? (
                <Text style={badgeStyle('info')}>{statusLabel}</Text>
              ) : (
                <Text style={styles.subtitle}>(상태 미지정)</Text>
              )}
              {(auditByField['status']?.length ?? 0) > 0 ? (
                <AuditOverlay
                  field="status"
                  currentValue={statusLabel ?? null}
                  history={auditByField['status'] ?? []}
                />
              ) : null}
            </View>
          </View>

          <View style={styles.headerSub}>
            <Text style={[styles.subtitle, styles.realtimeBadge]}>
              {realtimeStatus === 'live'
                ? '실시간 연결됨'
                : realtimeStatus === 'connecting'
                  ? '실시간 연결 중…'
                  : '오프라인'}
            </Text>
          </View>
        </View>
      </View>

      {error && (
        <View style={styles.errorCard} testID="slip-detail-error-mobile">
          <Text style={[styles.errorText, badgeStyle('warn')]}>오류</Text>
          <Text style={styles.errorMessage}>{error}</Text>
        </View>
      )}

      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 80 : 0}
      >
        {loading ? (
          <View style={styles.center}>
            <ActivityIndicator size="large" color={colors.action.brand} />
            <Text style={styles.muted}>코멘트 불러오는 중…</Text>
          </View>
        ) : (
          <FlatList
            ref={listRef}
            data={comments}
            keyExtractor={(item) => item.id}
            contentContainerStyle={styles.list}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
            testID="slip-detail-comment-list-mobile"
            ListEmptyComponent={
              <View style={styles.empty}>
                <Text style={styles.muted}>아직 코멘트가 없습니다</Text>
              </View>
            }
            renderItem={({ item }) => (
              <CommentItem item={item} onDelete={() => onDelete(item.id)} />
            )}
          />
        )}

        <View style={styles.composer}>
          <TextInput
            style={styles.input}
            value={draft}
            onChangeText={setDraft}
            placeholder="코멘트를 입력하세요 (최대 2000자)"
            placeholderTextColor={colors.ink.tertiary}
            multiline
            maxLength={2000}
            editable={!submitting}
            testID="slip-detail-comment-input-mobile"
          />
          <TouchableOpacity
            onPress={onSubmit}
            disabled={submitting || draft.trim().length === 0}
            style={[
              styles.submitBtn,
              (submitting || draft.trim().length === 0) && styles.submitBtnDisabled,
            ]}
            testID="slip-detail-comment-submit-mobile"
          >
            <Text style={styles.submitLabel}>{submitting ? '전송 중…' : '전송'}</Text>
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

interface CommentItemProps {
  item: SlipCommentResponse;
  onDelete: () => void;
}

function CommentItem({ item, onDelete }: CommentItemProps): JSX.Element {
  const time = formatTime(item.createdAt);
  return (
    <View style={styles.commentCard} testID={`slip-detail-comment-item-mobile-${item.id}`}>
      <View style={styles.commentHead}>
        <Text style={styles.commentAuthor}>{item.authorFullName}</Text>
        <Text style={badgeStyle('info')}>{item.authorRole}</Text>
        <Text style={styles.commentTime}>{time}</Text>
      </View>
      <Text style={styles.commentBody}>
        {item.deleted ? '(삭제된 코멘트)' : item.body}
      </Text>
      {!item.deleted ? (
        <TouchableOpacity onPress={onDelete} style={styles.commentDelBtn}>
          <Text style={styles.commentDelLabel}>삭제</Text>
        </TouchableOpacity>
      ) : null}
    </View>
  );
}

function formatTime(iso: string): string {
  try {
    const d = new Date(iso);
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    return `${hh}:${mm}`;
  } catch {
    return iso;
  }
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.surface.app },
  flex: { flex: 1 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
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
  headerInfo: { flex: 1 },
  headerSub: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
    marginTop: spacing[1],
    flexWrap: 'wrap',
  },
  backBtn: {
    paddingVertical: spacing[1],
    paddingHorizontal: spacing[2],
    borderRadius: radii.button,
    borderWidth: 1,
    borderColor: colors.line.default,
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
  titleRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: spacing[2],
    flexWrap: 'wrap',
  },
  editCount: {
    fontSize: typography.fontSize.xs,
    color: colors.ink.tertiary,
    fontFamily: typography.fontFamily.sans,
  },
  auditFieldRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginTop: spacing[2],
    gap: spacing[2],
  },
  auditFieldLabel: {
    fontSize: typography.fontSize.xs,
    color: colors.ink.tertiary,
    fontFamily: typography.fontFamily.sans,
    width: 48,
    paddingTop: spacing[1],
  },
  auditFieldValue: {
    flex: 1,
    gap: spacing[1],
  },
  revertBtn: {
    alignSelf: 'flex-start',
    marginTop: spacing[1],
    paddingVertical: spacing[1],
    paddingHorizontal: spacing[2],
    borderWidth: 1,
    borderColor: colors.line.default,
    borderRadius: radii.button,
  },
  revertLabel: {
    fontSize: typography.fontSize.xs,
    color: colors.action.brand,
    fontFamily: typography.fontFamily.sans,
    fontWeight: typography.fontWeight.semibold,
  },
  subtitle: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.secondary,
    fontFamily: typography.fontFamily.sans,
  },
  realtimeBadge: {
    color: colors.ink.tertiary,
    fontSize: typography.fontSize.xs,
  },
  list: { padding: spacing[4], gap: spacing[2] },
  empty: { alignItems: 'center', paddingTop: spacing[10] },
  muted: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.tertiary,
    marginTop: spacing[2],
    fontFamily: typography.fontFamily.sans,
  },
  commentCard: {
    backgroundColor: colors.surface.card,
    borderRadius: radii.card,
    padding: spacing[3],
    marginBottom: spacing[2],
    borderWidth: 1,
    borderColor: colors.line.default,
  },
  commentHead: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
    marginBottom: spacing[2],
    flexWrap: 'wrap',
  },
  commentAuthor: {
    fontSize: typography.fontSize.sm,
    fontWeight: typography.fontWeight.semibold,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
  },
  commentTime: {
    fontSize: typography.fontSize.xs,
    color: colors.ink.tertiary,
    fontFamily: typography.fontFamily.sans,
  },
  commentBody: {
    fontSize: typography.fontSize.base,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
    lineHeight: typography.fontSize.base * typography.lineHeight.base,
  },
  commentDelBtn: {
    alignSelf: 'flex-end',
    marginTop: spacing[2],
    paddingVertical: spacing[1],
    paddingHorizontal: spacing[2],
  },
  commentDelLabel: {
    fontSize: typography.fontSize.xs,
    color: colors.state.danger,
    fontFamily: typography.fontFamily.sans,
  },
  composer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    padding: spacing[3],
    gap: spacing[2],
    backgroundColor: colors.surface.card,
    borderTopWidth: 1,
    borderTopColor: colors.line.default,
  },
  input: {
    flex: 1,
    minHeight: 40,
    maxHeight: 120,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
    borderWidth: 1,
    borderColor: colors.line.default,
    borderRadius: radii.button,
    fontSize: typography.fontSize.base,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
    backgroundColor: colors.surface.app,
  },
  submitBtn: {
    paddingVertical: spacing[2],
    paddingHorizontal: spacing[4],
    backgroundColor: colors.action.brand,
    borderRadius: radii.button,
  },
  submitBtnDisabled: {
    backgroundColor: colors.line.default,
  },
  submitLabel: {
    color: colors.ink.onPrimary,
    fontWeight: typography.fontWeight.semibold,
    fontFamily: typography.fontFamily.sans,
    fontSize: typography.fontSize.sm,
  },
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
});
