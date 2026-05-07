/**
 * useGpsPermission — Phase 10 W10-3 신규.
 *
 * 사용자 결정 4 GPS 하이브리드 (2026-05-07) + 사용자 명시 권한 정책:
 *   - foreground 권한 = **의무** (배송 도중 위치 추적)
 *   - background 권한 = 선택 (운영 시점 결정)
 *   - 거부 fallback = **어플 사용 불가** (차단 화면 표시)
 *
 * 본 hook 은 driver tab 진입 시점에 호출 — `granted=false` 일 때 호출 측이 차단 화면을 노출.
 *
 * 의존성:
 *   - `expo-location` (foreground + background permission API).
 *
 * graceful guard:
 *   - `expo-location` 미설치 환경 (e.g. Expo Go SDK 53 일부 platform) → granted=false 반환,
 *     UI 측 차단 화면이 안내 표시 (어플 사용 불가).
 */

import { useEffect, useState } from 'react';

export type GpsPermissionStatus = 'unknown' | 'granted' | 'denied' | 'unavailable';

export interface GpsPermissionState {
  status: GpsPermissionStatus;
  /** foreground 권한 OK 여부 (사용자 명시 — 의무). */
  foregroundGranted: boolean;
  /** background 권한 OK 여부 (선택 — 운영 시점 결정). */
  backgroundGranted: boolean;
  /** 차단 화면 표시 신호. status === 'denied' || 'unavailable' 시 true. */
  blocked: boolean;
}

/**
 * GPS 권한 hook. mount 직후 1회 비동기 요청.
 *
 * Hooks Rules — 조건부 hook 호출 X.
 */
export function useGpsPermission(): GpsPermissionState {
  const [state, setState] = useState<GpsPermissionState>({
    status: 'unknown',
    foregroundGranted: false,
    backgroundGranted: false,
    blocked: false,
  });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        // graceful require — expo-location 미설치 환경에서 throw 대신 unavailable 처리.
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const Location = require('expo-location') as typeof import('expo-location');

        const fg = await Location.requestForegroundPermissionsAsync();
        if (cancelled) return;
        if (fg.status !== 'granted') {
          // 사용자 명시 — 거부 fallback = 어플 사용 불가.
          setState({
            status: 'denied',
            foregroundGranted: false,
            backgroundGranted: false,
            blocked: true,
          });
          return;
        }

        // foreground OK — background 는 선택 (실패해도 진행).
        let bgGranted = false;
        try {
          const bg = await Location.requestBackgroundPermissionsAsync();
          if (cancelled) return;
          bgGranted = bg.status === 'granted';
        } catch {
          // background 권한 미지원 platform — 무시 (foreground 만으로 진행).
          bgGranted = false;
        }

        setState({
          status: 'granted',
          foregroundGranted: true,
          backgroundGranted: bgGranted,
          blocked: false,
        });
      } catch {
        if (cancelled) return;
        // expo-location 미설치 또는 기타 platform 미지원 → 차단 화면.
        setState({
          status: 'unavailable',
          foregroundGranted: false,
          backgroundGranted: false,
          blocked: true,
        });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}

/**
 * 현재 1회 GPS 위치 조회 (latitude, longitude). expo-location 미설치 시 throw.
 *
 * 사용 위치:
 *   - DriverLocationTrackingScreen 30초 주기 보고
 *   - DriverSignatureScreen 서명 시점 위치 캡처
 */
export async function getCurrentPositionAsync(): Promise<{ latitude: number; longitude: number; capturedAt: string }> {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const Location = require('expo-location') as typeof import('expo-location');
  const pos = await Location.getCurrentPositionAsync({});
  return {
    latitude: pos.coords.latitude,
    longitude: pos.coords.longitude,
    capturedAt: new Date(pos.timestamp).toISOString(),
  };
}
