import {
  reportLocation,
  uploadStopPhoto,
  signAndSendCopy,
  type DispatchVehicleSummary,
  type LocationReportResponse,
  type SignAndSendCopyResult,
  type StopPhotoUploadResponse,
} from '../api/arologis';

const assignedVehicle: DispatchVehicleSummary = {
  dispatchDate: '2026-05-15',
  dispatchType: 'NIGHT',
  vehicleSequence: 1,
  tonnage: 'TONNAGE_1',
  label: '강남+서초',
  status: 'ASSIGNED',
  stops: [
    {
      stopSequence: 1,
      rawText: '서울 강남구 테스트로 1 (테스트상사-1234)',
      parsedAddress: '서울 강남구 테스트로 1',
      parsedPartnerName: '테스트상사',
      parsedKakaoSeq: 1234,
      notes: '문 앞 전달',
      status: 'PENDING',
    },
  ],
};

const stop = assignedVehicle.stops[0];

async function submitSignatureContract(token: string | null): Promise<SignAndSendCopyResult> {
  return signAndSendCopy(
    token,
    assignedVehicle.dispatchType,
    assignedVehicle.vehicleSequence,
    stop.stopSequence,
    {
      driverSignatureBase64: 'driver-png-base64',
      recipientSignatureBase64: 'recipient-png-base64',
      capturedAt: '2026-05-15T12:00:00',
      gpsLat: 37.5665,
      gpsLng: 126.978,
      parsedKakaoSeq: stop.parsedKakaoSeq,
    },
  );
}

async function readSuccessHeaders(token: string | null): Promise<string | null> {
  const result = await submitSignatureContract(token);
  if (result.kind === 'success') {
    return result.copyRecipientPhoneMasked ?? result.pngBase64;
  }
  return result.copyFailureReason ?? result.error ?? null;
}

void readSuccessHeaders(null);

async function readForbiddenSignatureFields(token: string | null): Promise<string> {
  const result = await submitSignatureContract(token);
  // @ts-expect-error 내부 서명 UUID 는 기사 앱 공개 응답 타입에 포함되면 안 된다.
  const forbiddenSignatureId = result.signatureId;
  // @ts-expect-error raw downloadUrl 은 기사 앱 공개 응답 타입에 포함되면 안 된다.
  const forbiddenDownloadUrl = result.downloadUrl;
  // @ts-expect-error storageKey 는 기사 앱 공개 응답 타입에 포함되면 안 된다.
  const forbiddenStorageKey = result.storageKey;
  return [forbiddenSignatureId, forbiddenDownloadUrl, forbiddenStorageKey].join('|');
}

void readForbiddenSignatureFields(null);

async function submitLocationContract(token: string | null): Promise<LocationReportResponse> {
  return reportLocation(token, {
    latitude: 37.5665,
    longitude: 126.978,
    capturedAt: '2026-05-15T12:00:00',
    source: 'APP_GPS_ACTIVE',
  });
}

async function readLocationPublicFields(token: string | null): Promise<string> {
  const result = await submitLocationContract(token);
  // @ts-expect-error 내부 location UUID 는 기사 앱 공개 응답 타입에 포함되면 안 된다.
  const forbiddenLocationId = result.locationId;
  // @ts-expect-error raw downloadUrl 은 기사 앱 공개 응답 타입에 포함되면 안 된다.
  const forbiddenDownloadUrl = result.downloadUrl;
  // @ts-expect-error storageKey 는 기사 앱 공개 응답 타입에 포함되면 안 된다.
  const forbiddenStorageKey = result.storageKey;
  return [result.capturedAt, forbiddenLocationId, forbiddenDownloadUrl, forbiddenStorageKey].join('|');
}

void readLocationPublicFields(null);

async function submitPhotoContract(token: string | null): Promise<StopPhotoUploadResponse> {
  return uploadStopPhoto(
    token,
    assignedVehicle.dispatchType,
    assignedVehicle.vehicleSequence,
    stop.stopSequence,
    'DELIVERY',
    {
      uri: 'file:///cache/delivery.jpg',
      fileName: 'delivery.jpg',
      mimeType: 'image/jpeg',
      exifGpsLat: 37.5665,
      exifGpsLng: 126.978,
      capturedAt: '2026-05-15T12:00:00',
      parsedKakaoSeq: stop.parsedKakaoSeq,
    },
  );
}

async function readPhotoPublicFields(token: string | null): Promise<string> {
  const result = await submitPhotoContract(token);
  // @ts-expect-error UUID 필드는 기사 앱 공개 응답 타입에 포함되면 안 된다.
  const forbiddenUuid = result.id;
  // @ts-expect-error presigned URL 은 기사 앱 공개 응답 타입에 포함되면 안 된다.
  const forbiddenDownloadUrl = result.downloadUrl;
  return [
    result.attachmentType,
    result.fileName,
    result.uploadedAt,
    forbiddenDownloadUrl ?? '',
    forbiddenUuid,
  ].join('|');
}

void readPhotoPublicFields(null);
