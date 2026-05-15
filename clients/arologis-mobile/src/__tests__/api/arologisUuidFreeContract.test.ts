jest.mock('../../api/client', () => ({
  apiFetch: jest.fn(),
  apiFetchRaw: jest.fn(),
}));

import { apiFetch, apiFetchRaw } from '../../api/client';
import { reportLocation, signAndSendCopy } from '../../api/arologis';

const leakedUuid = '11111111-2222-3333-4444-555555555555';
const leakedDownloadUrl = 'https://storage.example/private/signature-copy.png';
const leakedStorageKey = 'signature-copies/internal-key.png';

function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/json' : null),
    },
    json: jest.fn().mockResolvedValue(body),
  } as unknown as Response;
}

function pngResponseWithInternalHeaders(): Response {
  return {
    ok: true,
    status: 200,
    headers: {
      get: (name: string) => {
        const normalized = name.toLowerCase();
        if (normalized === 'content-type') return 'image/png';
        if (normalized === 'x-signature-id') return leakedUuid;
        if (normalized === 'x-copy-sent-at') return '2026-05-16T09:00:00';
        if (normalized === 'x-copy-recipient-phone-masked') return '010-****-5678';
        return null;
      },
    },
    arrayBuffer: jest.fn().mockResolvedValue(Uint8Array.from([1, 2, 3]).buffer),
  } as unknown as Response;
}

function expectNoPrivateFields(value: unknown): void {
  const serialized = JSON.stringify(value);
  expect(serialized).not.toContain('locationId');
  expect(serialized).not.toContain('signatureId');
  expect(serialized).not.toContain('downloadUrl');
  expect(serialized).not.toContain('storageKey');
  expect(serialized).not.toContain(leakedUuid);
  expect(serialized).not.toContain(leakedDownloadUrl);
  expect(serialized).not.toContain(leakedStorageKey);
}

describe('arologis driver-facing UUID-free contract', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('GPS 보고 응답은 capturedAt만 공개하고 locationId/downloadUrl/storageKey를 버린다', async () => {
    (apiFetch as jest.Mock).mockResolvedValue({
      success: true,
      data: {
        locationId: leakedUuid,
        capturedAt: '2026-05-16T08:00:00',
        downloadUrl: leakedDownloadUrl,
        storageKey: leakedStorageKey,
      },
    });

    const result = await reportLocation('jwt-x', {
      latitude: 37.1234567,
      longitude: 127.7654321,
      capturedAt: '2026-05-16T08:00:00',
      source: 'APP_GPS_ACTIVE',
    });

    expect(result).toEqual({ capturedAt: '2026-05-16T08:00:00' });
    expectNoPrivateFields(result);
  });

  it('PNG 사본 성공 응답은 signatureId header를 공개 결과에 싣지 않는다', async () => {
    (apiFetchRaw as jest.Mock).mockResolvedValue(pngResponseWithInternalHeaders());

    const result = await signAndSendCopy('jwt-x', 'NIGHT', 1, 1, {
      driverSignatureBase64: 'driver-png-base64',
      recipientSignatureBase64: 'recipient-png-base64',
      capturedAt: '2026-05-16T09:00:00',
    });

    expect(result.kind).toBe('success');
    expect(result).toMatchObject({
      copySentAt: '2026-05-16T09:00:00',
      copyRecipientPhoneMasked: '010-****-5678',
    });
    expectNoPrivateFields(result);
  });

  it('JSON 실패 응답에 내부 signatureId/downloadUrl/storageKey가 섞여도 공개 결과에서 제거한다', async () => {
    (apiFetchRaw as jest.Mock).mockResolvedValue(jsonResponse(500, {
      signatureId: leakedUuid,
      copyFailureReason: 'RENDERER_ERROR',
      error: 'RENDERER_ERROR',
      retryable: true,
      downloadUrl: leakedDownloadUrl,
      storageKey: leakedStorageKey,
    }));

    const result = await signAndSendCopy('jwt-x', 'NIGHT', 1, 1, {
      driverSignatureBase64: 'driver-png-base64',
      recipientSignatureBase64: 'recipient-png-base64',
      capturedAt: '2026-05-16T09:00:00',
    });

    expect(result).toMatchObject({
      kind: 'fail',
      status: 500,
      copyFailureReason: 'RENDERER_ERROR',
      error: 'RENDERER_ERROR',
      retryable: true,
    });
    expectNoPrivateFields(result);
  });
});
