/**
 * PhotoAttachmentCapture — P1-8 (Stage 4) 사진 첨부 통합 컴포넌트.
 *
 * <p>책임:
 * <ul>
 *   <li>카메라 권한 요청 (한국어 다이얼로그) + 거부 graceful fallback (갤러리만 사용 가능 안내).</li>
 *   <li>3 진입 — 촬영 / 갤러리 선택 / 파일 선택 (PDF 등 image 외).</li>
 *   <li>자동 압축 (max 1920x1080, JPEG quality 0.8) — expo-image-manipulator.</li>
 *   <li>EXIF GPS 추출 (image-picker `exif` 옵션) — 촬영 사진 기준, 갤러리 선택은 EXIF 보존.</li>
 *   <li>썸네일 미리보기 + 단건 삭제.</li>
 *   <li>multi-photo 결과는 부모 컴포넌트에 onChange callback 으로 전달 (업로드는 부모 책임).</li>
 * </ul>
 *
 * <p>제약:
 * <ul>
 *   <li>Expo SDK 53 — `expo-image-picker` ~16, `expo-image-manipulator` ~13 가용 시 활성.</li>
 *   <li>패키지 미설치 / native module 미가용 시 graceful guard (안내 + 비활성).</li>
 *   <li>최대 첨부 수 = 5 (BE 가드 20 보다 보수적, UI 피로 방지).</li>
 *   <li>단일 파일 = 5MB 가드 (BE 가드 일관, 압축 후도 초과 시 사용자 안내).</li>
 * </ul>
 *
 * <p>data-testid (RN = `testID`):
 * <ul>
 *   <li>{@code attachment-camera-button} — 촬영 진입</li>
 *   <li>{@code attachment-gallery-button} — 갤러리 진입</li>
 *   <li>{@code attachment-file-button} — 파일 진입 (PDF 등)</li>
 *   <li>{@code attachment-preview-{i}} — i 번째 미리보기 카드</li>
 *   <li>{@code attachment-delete-{i}} — i 번째 삭제 버튼</li>
 * </ul>
 *
 * <p>매뉴얼 출처: {@code docs/manual/04-모바일/04-사진-첨부.md}.
 */

import { useCallback, useEffect, useState } from 'react';
import { Alert, Image, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { badgeStyle, colors, radii, spacing, typography } from '../theme/tokens';

const MAX_ATTACHMENTS = 5;
const MAX_FILE_BYTES = 5 * 1024 * 1024; // 5MB BE 가드 일관
const COMPRESS_MAX_WIDTH = 1920;
const COMPRESS_MAX_HEIGHT = 1080;
const COMPRESS_QUALITY = 0.8;

/** 정규화된 사진 첨부 1건. */
export interface PhotoItem {
  /** local file URI (file:// 접두). */
  uri: string;
  /** 자동 또는 picker 제공 파일명. */
  fileName: string;
  /** image/jpeg / image/png / application/pdf */
  mimeType: string;
  /** 파일 크기 (bytes, 추정 — picker 제공 또는 압축 후 계산). */
  sizeBytes: number;
  /** EXIF GPS 위도. */
  exifGpsLat?: number | null;
  /** EXIF GPS 경도. */
  exifGpsLng?: number | null;
  /** 촬영 시각 ISO. */
  capturedAt?: string | null;
}

interface Props {
  /** 현재 첨부 목록 (controlled — 부모 보유). */
  value: PhotoItem[];
  /** 변경 시 부모 callback (업로드 / persist 는 부모 책임). */
  onChange: (next: PhotoItem[]) => void;
  /** 사용자 표시 헤더 (예: "배송 사진" / "검수 사진"). 옵션. */
  title?: string;
  /** 최대 첨부 수 — default {@link MAX_ATTACHMENTS}. */
  maxItems?: number;
  /** 첨부 1건의 status overlay (업로드 진행률 / 실패 표시) — i 번째 item 기준. 옵션. */
  itemStatus?: (Array<{ uploading: boolean; uploaded: boolean; error?: string | null } | undefined>);
}

type LibStatus = 'unknown' | 'available' | 'missing';

interface LibState {
  picker: LibStatus;
  manipulator: LibStatus;
  cameraGranted: boolean | null;  // null=미요청, true=허용, false=거부
  galleryGranted: boolean | null;
}

export default function PhotoAttachmentCapture({
  value, onChange, title, maxItems = MAX_ATTACHMENTS, itemStatus,
}: Props): JSX.Element {
  const [lib, setLib] = useState<LibState>({
    picker: 'unknown',
    manipulator: 'unknown',
    cameraGranted: null,
    galleryGranted: null,
  });

  // mount 시 expo-image-picker / expo-image-manipulator 가용성 확인 (graceful guard).
  useEffect(() => {
    let cancelled = false;
    (async () => {
      let pickerStatus: LibStatus = 'missing';
      let manipulatorStatus: LibStatus = 'missing';
      try {
        // eslint-disable-next-line @typescript-eslint/no-var-requires, @typescript-eslint/no-unused-vars
        const _ = require('expo-image-picker');
        pickerStatus = 'available';
      } catch { pickerStatus = 'missing'; }
      try {
        // eslint-disable-next-line @typescript-eslint/no-var-requires, @typescript-eslint/no-unused-vars
        const _ = require('expo-image-manipulator');
        manipulatorStatus = 'available';
      } catch { manipulatorStatus = 'missing'; }
      if (cancelled) return;
      setLib((s) => ({ ...s, picker: pickerStatus, manipulator: manipulatorStatus }));
    })();
    return () => { cancelled = true; };
  }, []);

  const ensureCameraPermission = useCallback(async (): Promise<boolean> => {
    if (lib.picker !== 'available') return false;
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const ImagePicker = require('expo-image-picker') as typeof import('expo-image-picker');
      const cur = await ImagePicker.getCameraPermissionsAsync();
      if (cur.granted) {
        setLib((s) => ({ ...s, cameraGranted: true }));
        return true;
      }
      const req = await ImagePicker.requestCameraPermissionsAsync();
      const granted = req.status === 'granted';
      setLib((s) => ({ ...s, cameraGranted: granted }));
      if (!granted) {
        Alert.alert(
          '카메라 권한 필요',
          '현장 사진 촬영을 위해 카메라 권한이 필요합니다. 거부 시 갤러리 / 파일 첨부만 사용 가능합니다.',
          [{ text: '확인' }],
        );
      }
      return granted;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      Alert.alert('카메라 사용 불가', `카메라를 시작할 수 없습니다 — ${msg}`);
      return false;
    }
  }, [lib.picker]);

  const ensureGalleryPermission = useCallback(async (): Promise<boolean> => {
    if (lib.picker !== 'available') return false;
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const ImagePicker = require('expo-image-picker') as typeof import('expo-image-picker');
      const cur = await ImagePicker.getMediaLibraryPermissionsAsync();
      if (cur.granted) {
        setLib((s) => ({ ...s, galleryGranted: true }));
        return true;
      }
      const req = await ImagePicker.requestMediaLibraryPermissionsAsync();
      const granted = req.status === 'granted';
      setLib((s) => ({ ...s, galleryGranted: granted }));
      if (!granted) {
        Alert.alert('갤러리 권한 필요', '갤러리에서 사진을 선택하려면 사진 접근 권한이 필요합니다.');
      }
      return granted;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      Alert.alert('갤러리 사용 불가', `갤러리를 열 수 없습니다 — ${msg}`);
      return false;
    }
  }, [lib.picker]);

  /** 압축 — expo-image-manipulator 가용 시 max 1920x1080 / JPEG quality 0.8. */
  const compress = useCallback(async (uri: string): Promise<{ uri: string; sizeBytes: number }> => {
    if (lib.manipulator !== 'available') {
      return { uri, sizeBytes: 0 };
    }
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const Manipulator = require('expo-image-manipulator') as typeof import('expo-image-manipulator');
      const result = await Manipulator.manipulateAsync(
        uri,
        [{ resize: { width: COMPRESS_MAX_WIDTH, height: COMPRESS_MAX_HEIGHT } }],
        { compress: COMPRESS_QUALITY, format: Manipulator.SaveFormat.JPEG },
      );
      // 압축 후 정확한 크기는 fetch HEAD 로 확인 (RN 환경 file:// → blob 변환).
      let sizeBytes = 0;
      try {
        const resp = await fetch(result.uri);
        const blob = await resp.blob();
        sizeBytes = blob.size ?? 0;
      } catch {
        sizeBytes = 0; // 크기 확인 실패해도 업로드 시도 (BE 가 5MB 검증).
      }
      return { uri: result.uri, sizeBytes };
    } catch (e) {
      // 압축 실패 시 원본 사용 (graceful) + 사용자 안내 X (자동 fallback).
      // eslint-disable-next-line no-console
      console.warn('[PhotoAttachmentCapture] compress 실패, 원본 사용:', e);
      return { uri, sizeBytes: 0 };
    }
  }, [lib.manipulator]);

  const addPhoto = useCallback((photo: PhotoItem) => {
    if (photo.sizeBytes > MAX_FILE_BYTES) {
      Alert.alert(
        '파일 크기 초과',
        `압축 후에도 5MB 를 초과합니다 (현재 약 ${Math.round(photo.sizeBytes / 1024)}KB). 다른 사진을 선택해주세요.`,
      );
      return;
    }
    if (value.length >= maxItems) {
      Alert.alert('첨부 한도 초과', `최대 ${maxItems}장까지 첨부 가능합니다. 기존 사진을 삭제 후 다시 시도해주세요.`);
      return;
    }
    onChange([...value, photo]);
  }, [maxItems, onChange, value]);

  const handleCamera = useCallback(async () => {
    const ok = await ensureCameraPermission();
    if (!ok) return;
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const ImagePicker = require('expo-image-picker') as typeof import('expo-image-picker');
      const result = await ImagePicker.launchCameraAsync({
        // SDK 53 = MediaType[] 권장 ('images' | 'videos' | 'livePhotos'). MediaTypeOptions 는 deprecated.
        mediaTypes: ['images'],
        quality: 1, // 압축은 Manipulator 가 단계 수행 (picker 의 quality 와 분리).
        exif: true,
      });
      if (result.canceled || result.assets.length === 0) return;
      const asset = result.assets[0];
      const compressed = await compress(asset.uri);
      const fileName = asset.fileName ?? `capture-${Date.now()}.jpg`;
      const mimeType = inferMimeType(asset.mimeType, fileName);
      const exif = asset.exif ?? {};
      addPhoto({
        uri: compressed.uri,
        fileName,
        mimeType,
        sizeBytes: compressed.sizeBytes || asset.fileSize || 0,
        exifGpsLat: extractExifGps(exif, 'latitude'),
        exifGpsLng: extractExifGps(exif, 'longitude'),
        capturedAt: extractExifDate(exif) ?? new Date().toISOString(),
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      Alert.alert('촬영 실패', msg);
    }
  }, [addPhoto, compress, ensureCameraPermission]);

  const handleGallery = useCallback(async () => {
    const ok = await ensureGalleryPermission();
    if (!ok) return;
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const ImagePicker = require('expo-image-picker') as typeof import('expo-image-picker');
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        quality: 1,
        exif: true,
        allowsMultipleSelection: false, // 다건 선택은 향후 확장 — 본 PR 은 1장씩.
      });
      if (result.canceled || result.assets.length === 0) return;
      const asset = result.assets[0];
      const compressed = await compress(asset.uri);
      const fileName = asset.fileName ?? `gallery-${Date.now()}.jpg`;
      const mimeType = inferMimeType(asset.mimeType, fileName);
      const exif = asset.exif ?? {};
      addPhoto({
        uri: compressed.uri,
        fileName,
        mimeType,
        sizeBytes: compressed.sizeBytes || asset.fileSize || 0,
        exifGpsLat: extractExifGps(exif, 'latitude'),
        exifGpsLng: extractExifGps(exif, 'longitude'),
        capturedAt: extractExifDate(exif),
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      Alert.alert('갤러리 선택 실패', msg);
    }
  }, [addPhoto, compress, ensureGalleryPermission]);

  /** 파일 선택 (PDF / 비-이미지) — image-picker 만 가용 시 안내, document-picker 미설치 시 stub. */
  const handleFile = useCallback(async () => {
    Alert.alert(
      '파일 첨부',
      'PDF / 문서 첨부는 추후 지원 예정입니다. 현재는 카메라 또는 갤러리만 사용 가능합니다.',
    );
  }, []);

  const handleDelete = useCallback((index: number) => {
    Alert.alert(
      '사진 삭제',
      '본 사진을 첨부 목록에서 삭제하시겠습니까?',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '삭제',
          style: 'destructive',
          onPress: () => onChange(value.filter((_, i) => i !== index)),
        },
      ],
    );
  }, [onChange, value]);

  const pickerMissing = lib.picker === 'missing';

  return (
    <View style={styles.container}>
      {title && <Text style={styles.title}>{title}</Text>}
      {pickerMissing && (
        <View style={styles.warnCard}>
          <Text style={badgeStyle('warn')}>의존성 미설치</Text>
          <Text style={styles.warnText}>
            expo-image-picker 가 설치되지 않았습니다. {'\n'}
            `npm install` 후 앱을 다시 실행해주세요.
          </Text>
        </View>
      )}

      <View style={styles.actionRow}>
        <TouchableOpacity
          style={[styles.actionBtn, styles.actionPrimary, pickerMissing && styles.actionDisabled]}
          onPress={handleCamera}
          disabled={pickerMissing}
          testID="attachment-camera-button"
          accessibilityLabel="현장 사진 촬영"
        >
          <Text style={styles.actionPrimaryText}>촬영</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.actionBtn, styles.actionSecondary, pickerMissing && styles.actionDisabled]}
          onPress={handleGallery}
          disabled={pickerMissing}
          testID="attachment-gallery-button"
          accessibilityLabel="갤러리에서 사진 선택"
        >
          <Text style={styles.actionSecondaryText}>갤러리</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.actionBtn, styles.actionGhost]}
          onPress={handleFile}
          testID="attachment-file-button"
          accessibilityLabel="문서 / PDF 첨부"
        >
          <Text style={styles.actionGhostText}>파일</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.hint}>
        최대 {maxItems}장 / 5MB 이하 (자동 압축 1920x1080, JPEG 80%) — EXIF GPS 자동 추출
      </Text>

      {value.length > 0 ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.previewRow}>
          {value.map((photo, i) => {
            const status = itemStatus?.[i];
            return (
              <View key={`${photo.uri}-${i}`} style={styles.previewCard} testID={`attachment-preview-${i}`}>
                <Image source={{ uri: photo.uri }} style={styles.previewImage} resizeMode="cover" />
                <View style={styles.previewMeta}>
                  <Text style={styles.previewFileName} numberOfLines={1}>{photo.fileName}</Text>
                  <Text style={styles.previewSize}>
                    {photo.sizeBytes > 0 ? `${Math.round(photo.sizeBytes / 1024)}KB` : '-'}
                  </Text>
                  {status?.uploading && (
                    <Text
                      style={badgeStyle('slicePending')}
                      testID="attachment-upload-progress"
                    >업로드 중</Text>
                  )}
                  {status?.uploaded && (
                    <Text style={badgeStyle('sliceSuccess')}>업로드 완료</Text>
                  )}
                  {status?.error && (
                    <Text style={[badgeStyle('warn'), styles.previewErrorBadge]} numberOfLines={2}>
                      {status.error}
                    </Text>
                  )}
                </View>
                <TouchableOpacity
                  style={styles.previewDelete}
                  onPress={() => handleDelete(i)}
                  testID={`attachment-delete-${i}`}
                  accessibilityLabel="사진 삭제"
                >
                  <Text style={styles.previewDeleteText}>삭제</Text>
                </TouchableOpacity>
              </View>
            );
          })}
        </ScrollView>
      ) : (
        <View style={styles.emptyCard}>
          <Text style={styles.emptyText}>첨부된 사진이 없습니다.</Text>
        </View>
      )}
    </View>
  );
}

// ----------------------------------------------------------------------
// EXIF / MIME 유틸 — image-picker `exif` 옵션 결과 정규화.
// ----------------------------------------------------------------------

/**
 * EXIF GPS 추출 — image-picker 가 기기 / OS 별로 다른 키를 노출하므로 다중 호환 처리.
 * - iOS: `{GPS: {Latitude, Longitude, LatitudeRef, LongitudeRef}}`
 * - Android: `GPSLatitude` / `GPSLongitude` 직접 노출 또는 nested `GPS` 객체.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractExifGps(exif: any, axis: 'latitude' | 'longitude'): number | null {
  if (!exif || typeof exif !== 'object') return null;
  // iOS nested GPS dict
  const gps = exif.GPS ?? exif.gps;
  if (gps && typeof gps === 'object') {
    if (axis === 'latitude') {
      const lat = pickNumber(gps.Latitude ?? gps.latitude);
      if (lat == null) return null;
      const ref = (gps.LatitudeRef ?? gps.latitudeRef ?? '').toString().toUpperCase();
      return ref === 'S' ? -lat : lat;
    }
    const lng = pickNumber(gps.Longitude ?? gps.longitude);
    if (lng == null) return null;
    const ref = (gps.LongitudeRef ?? gps.longitudeRef ?? '').toString().toUpperCase();
    return ref === 'W' ? -lng : lng;
  }
  // Android flat keys
  if (axis === 'latitude') {
    const lat = pickNumber(exif.GPSLatitude);
    if (lat == null) return null;
    const ref = (exif.GPSLatitudeRef ?? '').toString().toUpperCase();
    return ref === 'S' ? -lat : lat;
  }
  const lng = pickNumber(exif.GPSLongitude);
  if (lng == null) return null;
  const ref = (exif.GPSLongitudeRef ?? '').toString().toUpperCase();
  return ref === 'W' ? -lng : lng;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractExifDate(exif: any): string | null {
  if (!exif || typeof exif !== 'object') return null;
  // EXIF DateTimeOriginal 형식 = "YYYY:MM:DD HH:MM:SS" → ISO 변환.
  const raw = exif.DateTimeOriginal ?? exif.DateTime ?? exif.dateTimeOriginal ?? exif.dateTime;
  if (!raw || typeof raw !== 'string') return null;
  const m = raw.match(/^(\d{4}):(\d{2}):(\d{2})\s+(\d{2}):(\d{2}):(\d{2})/);
  if (!m) return null;
  // 로컬 시각으로 가정 → ISO (timezone 미포함, BE 가 ISO 파싱).
  return `${m[1]}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:${m[6]}`;
}

function pickNumber(v: unknown): number | null {
  if (v == null) return null;
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  if (typeof v === 'string') {
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

function inferMimeType(provided: string | null | undefined, fileName: string): string {
  if (provided && provided.startsWith('image/')) return provided;
  const lower = fileName.toLowerCase();
  if (lower.endsWith('.png')) return 'image/png';
  if (lower.endsWith('.pdf')) return 'application/pdf';
  return 'image/jpeg';
}

// ----------------------------------------------------------------------
// styles — theme tokens 1:1, RN StyleSheet.
// ----------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    backgroundColor: colors.surface.card,
    borderRadius: radii.card,
    padding: spacing[4],
    borderWidth: 1,
    borderColor: colors.line.default,
    gap: spacing[2],
  },
  title: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
    marginBottom: spacing[1],
  },
  warnCard: {
    backgroundColor: colors.state.warningBg,
    padding: spacing[3],
    borderRadius: radii.card,
    gap: spacing[1],
  },
  warnText: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
  },
  actionRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  actionBtn: {
    flex: 1,
    paddingVertical: spacing[3],
    paddingHorizontal: spacing[3],
    borderRadius: radii.button,
    alignItems: 'center',
  },
  actionPrimary: { backgroundColor: colors.action.brand },
  actionPrimaryText: {
    color: colors.ink.onPrimary,
    fontWeight: typography.fontWeight.semibold,
    fontFamily: typography.fontFamily.sans,
    fontSize: typography.fontSize.base,
  },
  actionSecondary: {
    backgroundColor: colors.action.brandSubtle,
    borderWidth: 1,
    borderColor: colors.action.brand,
  },
  actionSecondaryText: {
    color: colors.action.brandActive,
    fontWeight: typography.fontWeight.semibold,
    fontFamily: typography.fontFamily.sans,
    fontSize: typography.fontSize.base,
  },
  actionGhost: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: colors.line.default,
  },
  actionGhostText: {
    color: colors.ink.secondary,
    fontWeight: typography.fontWeight.medium,
    fontFamily: typography.fontFamily.sans,
    fontSize: typography.fontSize.base,
  },
  actionDisabled: {
    opacity: 0.5,
  },
  hint: {
    fontSize: typography.fontSize.xs,
    color: colors.ink.tertiary,
    fontFamily: typography.fontFamily.sans,
    marginTop: spacing[1],
  },
  emptyCard: {
    paddingVertical: spacing[4],
    alignItems: 'center',
  },
  emptyText: {
    fontSize: typography.fontSize.sm,
    color: colors.ink.tertiary,
    fontFamily: typography.fontFamily.sans,
  },
  previewRow: {
    marginTop: spacing[2],
  },
  previewCard: {
    width: 120,
    backgroundColor: colors.surface.subtle,
    borderRadius: radii.card,
    marginRight: spacing[2],
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: colors.line.default,
  },
  previewImage: {
    width: 120,
    height: 90,
    backgroundColor: colors.line.default,
  },
  previewMeta: {
    padding: spacing[2],
    gap: spacing[1],
  },
  previewFileName: {
    fontSize: typography.fontSize.xs,
    color: colors.ink.primary,
    fontFamily: typography.fontFamily.sans,
  },
  previewSize: {
    fontSize: typography.fontSize.xs,
    color: colors.ink.tertiary,
    fontFamily: typography.fontFamily.mono,
  },
  previewErrorBadge: {
    marginTop: spacing[1],
  },
  previewDelete: {
    paddingVertical: spacing[2],
    alignItems: 'center',
    backgroundColor: colors.state.dangerBg,
  },
  previewDeleteText: {
    color: colors.state.danger,
    fontSize: typography.fontSize.xs,
    fontWeight: typography.fontWeight.semibold,
    fontFamily: typography.fontFamily.sans,
  },
});
