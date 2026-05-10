/**
 * DriverSignatureScreen — Phase 10 W10-3 신규.
 * Phase 12 PR-H4c 보강 — 서명 등록 직후 actor (driver) audit overlay 표시. backend slip-service
 * 가 동기화한 audit log 가 있으면 우선 사용, 없으면 local 합성 audit 1건을 만들어 desktop /
 * SlipDetailScreen 과 시각적 동등성 보장 (사용자 색상 dot + actorFullName + 시각).
 *
 * 정차 도착 시 전자서명 캡처 + GPS 위치 동시 캡처 + POST 전송.
 *
 * 동작:
 *   1. 사용자 = 손가락 (또는 stylus) 으로 PNG 서명 캡처.
 *   2. 서명 시점에 GPS 위치 1회 캡처 (NUMERIC(10,7) ~1.1cm 정확도).
 *   3. POST `/driver-app/arologis/dispatches/{id}/vehicles/{seq}/stops/{stopSeq}/sign`.
 *   4. backend SignatureSource = APP (LINK 는 외부 링크 서명, 본 어플 = APP).
 *   5. (PR-H4c) 등록 성공 시 AuditOverlay 1건을 'signature' 필드 이력으로 노출.
 *
 * 본 PR (W10-3) 시점:
 *   - signature canvas = `react-native-signature-canvas` 의존성 가용 시 활성, 미가용 시 fallback
 *     "서명 placeholder" UI (graceful guard).
 *   - W10-4 slip-service 통합 시점에 imageRef → file-server / S3 업로드 활성.
 *
 * data-testid (PR-H4c 추가):
 *   - `driver-signature-audit-mobile` — 서명 등록 audit overlay wrapper
 */

import { useMemo, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { submitSignature } from '../../api/arologis';
import AuditOverlay from '../../components/AuditOverlay';
import type { SlipAuditActorRole, SlipAuditLogResponse } from '../../api/slipAudit';
import { getCurrentPositionAsync } from '../../hooks/useGpsPermission';
import { badgeStyle, colors, radii, spacing, typography } from '../../theme/tokens';

interface Props {
  /** JWT access token. */
  token: string | null;
  /** 대상 dispatch UUID (path 만, UI 미노출). */
  dispatchId: string;
  /** vehicle sequence (1-base). */
  vehicleSeq: number;
  /** stop sequence (1-base). */
  stopSeq: number;
  /** 정차 표시명 — UI 노출용 (parsed_partner_name + parsedAddress). UUID 미노출 가드. */
  stopLabel?: string;
  /**
   * (PR-H4c) actor 정보 — audit overlay 의 색상 dot + 이름 표시용.
   * 미전달 시 driverCode='driver' / fullName='배송기사' / role='DRIVER' fallback (시각 일관 유지).
   */
  actor?: {
    driverCode?: string | null;
    fullName?: string | null;
    role?: SlipAuditActorRole | null;
  };
}

interface SignatureCaptureState {
  imageRef: string | null;
  capturedAt: string | null;
  latitude: number | null;
  longitude: number | null;
  submitted: boolean;
  signatureId: string | null;
  /** Phase 10 W10-4 종합 TM (FE-3 채택) — slip-service 양쪽 저장 성공 여부. */
  slipBridged: boolean | null;
  error: string | null;
}

export default function DriverSignatureScreen({
  token, dispatchId, vehicleSeq, stopSeq, stopLabel, actor,
}: Props): JSX.Element {
  const [state, setState] = useState<SignatureCaptureState>({
    imageRef: null,
    capturedAt: null,
    latitude: null,
    longitude: null,
    submitted: false,
    signatureId: null,
    slipBridged: null,
    error: null,
  });

  /**
   * 서명 placeholder — `react-native-signature-canvas` 미설치 환경의 graceful 캡처.
   * 실제 production = signature canvas component → onOK callback 으로 base64 PNG dataURL 반환.
   * 본 PR 진입 시점 = mock dataURL ("data:image/png;base64,iVBORw0KGgoAAA..." 1x1 PNG) 사용.
   */
  const captureSignature = async () => {
    try {
      const pos = await getCurrentPositionAsync();
      // 1x1 transparent PNG (signature canvas 미가용 graceful guard).
      const mockPng =
        'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';
      setState((s) => ({
        ...s,
        imageRef: mockPng,
        capturedAt: pos.capturedAt,
        latitude: pos.latitude,
        longitude: pos.longitude,
        error: null,
      }));
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setState((s) => ({ ...s, error: `GPS 캡처 실패 — ${msg}` }));
    }
  };

  const submit = async () => {
    if (!state.imageRef) {
      Alert.alert('서명 미캡처', '먼저 서명을 캡처해주세요.');
      return;
    }
    try {
      const res = await submitSignature(token, dispatchId, vehicleSeq, stopSeq, {
        imageRef: state.imageRef,
        latitude: state.latitude ?? undefined,
        longitude: state.longitude ?? undefined,
      });
      setState((s) => ({
        ...s,
        submitted: true,
        signatureId: res.signatureId,
        slipBridged: res.slipBridged,
        error: null,
      }));
      // FE-3 채택 fix — slipBridged 시각화 alert 분기
      Alert.alert(
        '전자서명 등록 완료',
        res.slipBridged
          ? `signatureId = ${res.signatureId}\nslip-service 동기화 완료`
          : `signatureId = ${res.signatureId}\n자체 저장만 완료 (slip-service 미연동)`,
      );
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setState((s) => ({ ...s, error: msg }));
    }
  };

  const reset = () => {
    setState({
      imageRef: null,
      capturedAt: null,
      latitude: null,
      longitude: null,
      submitted: false,
      signatureId: null,
      slipBridged: null,
      error: null,
    });
  };

  // PR-H4c — 서명 등록 audit 합성 1건 (slip-service 미연동 시점에도 시각 일관 보장).
  // submitted=true 가 되면 actor 정보 + 캡처 시각 기반 합성 SlipAuditLogResponse 1건 생성.
  // signatureId 가 응답에 포함되므로 이를 audit log id 로 사용 (UI 미노출, key 만).
  const signatureAuditHistory = useMemo<SlipAuditLogResponse[]>(() => {
    if (!state.submitted || !state.signatureId) return [];
    const actorIdHashInput = actor?.driverCode ?? 'driver';
    return [
      {
        id: state.signatureId,
        slipId: dispatchId, // path 만, UI 미노출.
        field: 'signature',
        previousValue: '(서명 전)',
        newValue: `APP 서명 / ${state.latitude?.toFixed(7) ?? '-'}, ${state.longitude?.toFixed(7) ?? '-'}`,
        actorId: actorIdHashInput,
        actorFullName: actor?.fullName ?? '배송기사',
        actorRole: (actor?.role ?? 'DRIVER') as SlipAuditActorRole,
        createdAt: state.capturedAt ?? new Date().toISOString(),
      },
    ];
  }, [
    state.submitted,
    state.signatureId,
    state.capturedAt,
    state.latitude,
    state.longitude,
    actor?.driverCode,
    actor?.fullName,
    actor?.role,
    dispatchId,
  ]);

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.h1}>전자서명</Text>
        <Text style={styles.subtitle}>
          정차 #{stopSeq} (차량 #{vehicleSeq}) — 인수자 서명 + GPS 위치 동시 캡처
        </Text>
        {stopLabel && (
          <View style={styles.labelCard}>
            <Text style={styles.labelHead}>정차 정보</Text>
            <Text style={styles.labelBody}>{stopLabel}</Text>
          </View>
        )}

        {/* 서명 캡처 영역 — production 은 react-native-signature-canvas, graceful = placeholder */}
        <View style={styles.canvas}>
          {state.imageRef ? (
            <View style={styles.canvasFilled}>
              <Text style={badgeStyle('sliceSuccess')}>서명 캡처됨</Text>
              <Text style={styles.canvasHint}>imageRef = base64 PNG (1x1 placeholder, production = canvas)</Text>
            </View>
          ) : (
            <View style={styles.canvasEmpty}>
              <Text style={styles.canvasPlaceholder}>여기에 서명</Text>
              <Text style={styles.canvasHint}>서명 캡처 버튼을 눌러 GPS 위치 + PNG dataURL 을 동시 생성</Text>
            </View>
          )}
        </View>

        {/* 캡처 시점 GPS — UUID 비공개 가드 (위도/경도만, driverId 미노출) */}
        {state.imageRef && (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>캡처 시점 GPS</Text>
            <View style={styles.row}>
              <Text style={styles.label}>위도</Text>
              <Text style={styles.valueMono}>{state.latitude?.toFixed(7) ?? '-'}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.label}>경도</Text>
              <Text style={styles.valueMono}>{state.longitude?.toFixed(7) ?? '-'}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.label}>캡처 시각</Text>
              <Text style={styles.valueMono}>{state.capturedAt}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.label}>signatureSource</Text>
              <Text style={badgeStyle('channelPush')}>APP</Text>
            </View>
          </View>
        )}

        {state.submitted && (
          <View style={styles.successCard}>
            <Text style={badgeStyle('sliceSuccess')}>등록 완료</Text>
            <Text style={styles.successText}>signatureId: {state.signatureId}</Text>
            {/* FE-3 채택 fix — slipBridged UX 시각화 (slice-accent success/pending 토큰) */}
            {state.slipBridged === true ? (
              <Text style={badgeStyle('sliceSuccess')}>slip-service 동기화 완료</Text>
            ) : (
              <Text style={badgeStyle('slicePending')}>slip-service 미연동 (자체 저장만)</Text>
            )}
          </View>
        )}

        {/* PR-H4c — 등록 완료 후 audit overlay (signature 필드 이력 1건). */}
        {state.submitted && signatureAuditHistory.length > 0 && (
          <View style={styles.auditCard} testID="driver-signature-audit-mobile">
            <Text style={styles.auditCardTitle}>변경 이력</Text>
            <View style={styles.auditRow}>
              <Text style={styles.auditFieldLabel}>서명</Text>
              <View style={styles.auditFieldValue}>
                <AuditOverlay
                  field="signature"
                  currentValue={`APP 서명 (정차 #${stopSeq})`}
                  history={signatureAuditHistory}
                />
              </View>
            </View>
          </View>
        )}

        {state.error && (
          <View style={styles.errorCard}>
            <Text style={badgeStyle('warn')}>오류</Text>
            <Text style={styles.errorText}>{state.error}</Text>
          </View>
        )}

        <View style={styles.actions}>
          <TouchableOpacity style={[styles.btn, styles.btnPrimary]} onPress={captureSignature}>
            <Text style={styles.btnPrimaryText}>서명 캡처 + GPS</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.btn, styles.btnSecondary, !state.imageRef && styles.btnDisabled]}
            onPress={submit}
            disabled={!state.imageRef || state.submitted}
          >
            <Text style={styles.btnSecondaryText}>등록</Text>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.btn, styles.btnGhost]} onPress={reset}>
            <Text style={styles.btnGhostText}>다시</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.surface.app },
  content: { padding: spacing[4], gap: spacing[3] },
  h1: {
    fontSize: typography.fontSize.h1,
    fontWeight: typography.fontWeight.bold,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
  },
  subtitle: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.secondary,
    marginBottom: spacing[3],
    fontFamily: typography.fontFamily.sans,
  },
  labelCard: {
    backgroundColor: colors.surface.subtle,
    borderRadius: radii.card,
    padding: spacing[3],
    marginBottom: spacing[3],
  },
  labelHead: {
    fontSize: typography.fontSize.xs,
    color: colors.ink.tertiary,
    fontWeight: typography.fontWeight.semibold,
    fontFamily: typography.fontFamily.sans,
  },
  labelBody: {
    marginTop: spacing[1],
    fontSize: typography.fontSize.base,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
  },
  canvas: {
    height: 200,
    backgroundColor: colors.surface.card,
    borderRadius: radii.card,
    borderWidth: 2,
    borderColor: colors.line.default,
    borderStyle: 'dashed',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: spacing[3],
  },
  canvasFilled: { alignItems: 'center', gap: spacing[2] },
  canvasEmpty: { alignItems: 'center', gap: spacing[2] },
  canvasPlaceholder: {
    fontSize: typography.fontSize.lg,
    color: colors.ink.tertiary,
    fontFamily: typography.fontFamily.sans,
  },
  canvasHint: {
    fontSize: typography.fontSize.xs,
    color: colors.ink.tertiary,
    fontFamily: typography.fontFamily.sans,
    textAlign: 'center',
    paddingHorizontal: spacing[4],
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
    marginBottom: spacing[2],
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
  valueMono: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.mono,
  },
  successCard: {
    backgroundColor: colors.state.successBg,
    borderRadius: radii.card,
    padding: spacing[4],
    borderLeftWidth: 4,
    borderLeftColor: colors.state.success,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    marginBottom: spacing[3],
  },
  successText: {
    color: colors.ink.primary,
    fontSize: typography.fontSize.sm,
    fontFamily: typography.fontFamily.sans,
  },
  errorCard: {
    backgroundColor: colors.state.warningBg,
    borderRadius: radii.card,
    padding: spacing[4],
    borderLeftWidth: 4,
    borderLeftColor: colors.state.warning,
    marginBottom: spacing[3],
  },
  errorText: {
    color: colors.ink.primary,
    fontSize: typography.fontSize.sm,
    fontFamily: typography.fontFamily.sans,
    marginTop: spacing[2],
  },
  actions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[3] },
  btn: {
    paddingVertical: spacing[3],
    paddingHorizontal: spacing[4],
    borderRadius: radii.button,
    alignItems: 'center',
    flex: 1,
  },
  btnPrimary: { backgroundColor: colors.action.brand },
  btnPrimaryText: {
    color: colors.ink.onPrimary,
    fontWeight: typography.fontWeight.semibold,
    fontFamily: typography.fontFamily.sans,
  },
  btnSecondary: { backgroundColor: colors.action.brandSubtle, borderWidth: 1, borderColor: colors.action.brand },
  btnSecondaryText: {
    color: colors.action.brandActive,
    fontWeight: typography.fontWeight.semibold,
    fontFamily: typography.fontFamily.sans,
  },
  btnGhost: { backgroundColor: 'transparent', borderWidth: 1, borderColor: colors.line.default },
  btnGhostText: {
    color: colors.ink.secondary,
    fontWeight: typography.fontWeight.medium,
    fontFamily: typography.fontFamily.sans,
  },
  btnDisabled: { opacity: 0.5 },
  // PR-H4c — audit overlay card styles (SlipDetailScreen audit field row 와 시각 일관).
  auditCard: {
    backgroundColor: colors.surface.card,
    borderRadius: radii.card,
    padding: spacing[4],
    borderWidth: 1,
    borderColor: colors.line.default,
    marginBottom: spacing[3],
    gap: spacing[2],
  },
  auditCardTitle: {
    fontSize: typography.fontSize.sm,
    fontWeight: typography.fontWeight.semibold,
    color: colors.ink.secondary,
    fontFamily: typography.fontFamily.sans,
  },
  auditRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
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
});
