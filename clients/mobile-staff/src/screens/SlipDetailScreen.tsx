/**
 * SlipDetailScreen — Phase 12 PR-H1 신규 (mobile-staff FE-2).
 *
 * mobile-staff v3 (Phase 10 W10-3) 시점 = driver/estimate tab 만 보유 → slip 상세 화면 부재.
 * 본 PR (Phase 12 PR-H1) = SSE 실시간 + 코멘트 의 사용처로서 최소 화면 신규.
 *
 * 본 PR 범위 (최소):
 *   - slip 정보 영역 (slipNo / 상태 / 거래처명 — UUID 미노출)
 *   - 코멘트 영역 (목록 + 입력창 + 전송 버튼)
 *   - SSE 구독 (`subscribeToSlip`) 으로 코멘트 변경 실시간 반영
 *
 * 후속 (PR-H2+):
 *   - slip 라인 / 서명 / 사진 영역 통합
 *   - DriverDashboard 의 slip card 와 정식 navigation library 결합
 *
 * 한국어 UI / ROLE 풀네임 / UUID 비공개 가드 일관.
 *
 * data-testid:
 *   - `slip-detail-comment-list-mobile`
 *   - `slip-detail-comment-input-mobile`
 *   - `slip-detail-comment-submit-mobile`
 *   - `slip-detail-comment-item-mobile-${id}` (id = 코멘트 식별자, UI 미노출 — testID only)
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
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
}

export default function SlipDetailScreen({
  token,
  slipId,
  slipNo,
  partnerName,
  statusLabel,
  onBack,
}: Props): JSX.Element {
  const [comments, setComments] = useState<SlipCommentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [realtimeStatus, setRealtimeStatus] = useState<'connecting' | 'live' | 'offline'>(
    'connecting',
  );
  const listRef = useRef<FlatList<SlipCommentResponse>>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const data = await listSlipComments(token, slipId);
      setComments(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [token, slipId]);

  useEffect(() => {
    load();
  }, [load]);

  // SSE 구독 — comment.* 이벤트 도착 시 목록 재조회 (간단 invalidate 전략).
  useEffect(() => {
    const sub = subscribeToSlip(slipId, token, (evt: SlipRealtimeEvent) => {
      if (evt.type === 'heartbeat') {
        setRealtimeStatus('live');
        return;
      }
      if (
        evt.type === 'comment.created' ||
        evt.type === 'comment.updated' ||
        evt.type === 'comment.deleted'
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

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        {onBack ? (
          <TouchableOpacity onPress={onBack} style={styles.backBtn} testID="slip-detail-back-mobile">
            <Text style={styles.backLabel}>{'< 뒤로'}</Text>
          </TouchableOpacity>
        ) : null}
        <View style={styles.headerInfo}>
          <Text style={styles.h1}>전표 {slipNo ?? '상세'}</Text>
          <View style={styles.headerSub}>
            {partnerName ? (
              <Text style={styles.subtitle}>{partnerName}</Text>
            ) : null}
            {statusLabel ? (
              <Text style={badgeStyle('info')}>{statusLabel}</Text>
            ) : null}
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
