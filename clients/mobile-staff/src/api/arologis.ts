/**
 * arologis-service driver-app API client — Phase 10 W10-3 신규.
 *
 * 출처: `services/arologis-service/.../ArologisDriverAppController.java` 3 endpoint 1:1.
 *
 * Base URL = `EXPO_PUBLIC_API_BASE_URL` (default `http://localhost:8080` = api-gateway 진입).
 * gateway 가 JWT verify + ROLE_DRIVER 확인 + X-User-* 주입 후 arologis-service 8097 으로 forward.
 *
 * 인증:
 *   - JWT = user-service 발급 (Bearer access token).
 *   - WebView 안 legacy estimate 의 sessionStorage 저장 token 을 RN driver tab 에서 별도 보관 X.
 *     → driver tab 진입 시점에 user-service `/api/v1/auth/me` 호출하여 ROLE_DRIVER 확인 후 token 보관.
 *
 * UUID 비공개:
 *   - 응답에 driverCode + 정차 식별자 (parsed_partner_code 전표번호) 만 노출.
 *   - dispatch UUID 는 path parameter 로만 사용 (UI 에 표시 X).
 */

const DEFAULT_DEV_API = 'http://localhost:8080';
const DEFAULT_PROD_API = 'https://api.samhan-air.com';

function resolveApiBaseUrl(): string {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const proc = (globalThis as any).process as { env?: Record<string, string | undefined> } | undefined;
  const envUrl = proc?.env?.EXPO_PUBLIC_API_BASE_URL;
  if (envUrl) return envUrl;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const isDev = typeof (globalThis as any).__DEV__ !== 'undefined' ? (globalThis as any).__DEV__ : false;
  return isDev ? DEFAULT_DEV_API : DEFAULT_PROD_API;
}

export const API_BASE_URL = resolveApiBaseUrl();

// ----------------------------------------------------------------------
// 응답 타입 — backend ArologisDriverAppController.java 와 1:1.
// ----------------------------------------------------------------------

export interface DispatchVehicleSummary {
  vehicleSequence: number;
  tonnage: 'TONNAGE_1' | 'TONNAGE_1_4' | 'TONNAGE_2_5' | 'TONNAGE_5' | 'TONNAGE_BIG';
  status: 'PENDING' | 'MATCHING' | 'ASSIGNED' | 'DEPARTED' | 'DELIVERED' | 'CANCELLED';
}

export interface DispatchStopSummary {
  stopSequence: number;
  rawText: string;
  parsedAddress: string | null;
  parsedPartnerName: string | null;
  parsedPartnerCode: string | null;
  notes: string | null;
  status: 'PENDING' | 'ARRIVED' | 'DELIVERED' | 'FAILED' | 'UNPARSED';
}

/**
 * 본 어플 driver 의 오늘 배정 vehicle 목록.
 *
 * 본 PR (W10-3) 시점 backend 응답 = `[{vehicleSequence, tonnage, status}]` (W10-1 단순화).
 * UI 표시는 backend 응답을 그대로 사용 + stops 는 향후 W10-3 backend 확장 시 활성.
 */
export async function fetchTodayDispatches(token: string | null): Promise<DispatchVehicleSummary[]> {
  const url = `${API_BASE_URL}/driver-app/arologis/dispatches/today`;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(url, { method: 'GET', headers });
  if (!res.ok) {
    throw new ArologisApiError(res.status, `today fetch failed: HTTP ${res.status}`);
  }
  const json = await res.json();
  // ApiResponse<T> wrapper → data
  return (json?.data ?? json) as DispatchVehicleSummary[];
}

/**
 * GPS 위치 보고 (driver-app foreground 30초 주기).
 *
 * source = APP_GPS_ACTIVE (foreground 권한 O 시점). background 시 source = APP_GPS_BACKGROUND.
 *
 * BE-1 / QA-3 / Designer-2 통합 채택 fix 일관 — backend `DriverLocationSource` enum 4값.
 */
export interface ReportLocationPayload {
  latitude: number;
  longitude: number;
  capturedAt: string; // ISO8601 — `new Date().toISOString()`
  source?: 'APP_GPS_ACTIVE' | 'APP_GPS_BACKGROUND';
}

export async function reportLocation(
  token: string | null,
  payload: ReportLocationPayload,
): Promise<{ locationId: string; capturedAt: string }> {
  const url = `${API_BASE_URL}/driver-app/arologis/locations`;
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      latitude: String(payload.latitude),
      longitude: String(payload.longitude),
      capturedAt: payload.capturedAt,
      source: payload.source ?? 'APP_GPS_ACTIVE',
    }),
  });
  if (!res.ok) {
    throw new ArologisApiError(res.status, `location report failed: HTTP ${res.status}`);
  }
  const json = await res.json();
  return (json?.data ?? json) as { locationId: string; capturedAt: string };
}

/**
 * 전자서명 등록 (정차 도착 시점).
 *
 * imageRef = base64 PNG dataURL (`data:image/png;base64,...`).
 * latitude/longitude = 서명 시점 GPS 위치 (정확도 NUMERIC(10,7) ~1.1cm).
 * signatureSource = APP (backend SignatureSource enum).
 */
export interface SignaturePayload {
  imageRef: string;       // base64 PNG dataURL or storage ref
  latitude?: number;
  longitude?: number;
}

export async function submitSignature(
  token: string | null,
  dispatchId: string,
  vehicleSeq: number,
  stopSeq: number,
  payload: SignaturePayload,
): Promise<{ signatureId: string }> {
  const url = `${API_BASE_URL}/driver-app/arologis/dispatches/${dispatchId}/vehicles/${vehicleSeq}/stops/${stopSeq}/sign`;
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  const body: Record<string, string> = { imageRef: payload.imageRef };
  if (payload.latitude !== undefined) body.latitude = String(payload.latitude);
  if (payload.longitude !== undefined) body.longitude = String(payload.longitude);
  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new ArologisApiError(res.status, `signature submit failed: HTTP ${res.status}`);
  }
  const json = await res.json();
  return (json?.data ?? json) as { signatureId: string };
}

export class ArologisApiError extends Error {
  public readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'ArologisApiError';
    this.status = status;
  }
}
