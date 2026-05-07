# Phase 10 W10-3 — 모바일 어플 driver tab (clients/mobile-staff 내부)

> **진입 조건 정정 (2026-05-07)** — W10-2 (인성데이타 협약) 의존 X. W10-1 완료 후 진입 가능. 본 어플 GPS only 활성 (인성 LBS 통합은 W10-2 시점).

## 1. 슬라이스 요약

| 항목 | 값 |
|---|---|
| 슬라이스 | Phase 10 W10-3 (모바일 어플 driver tab) |
| 도입 모듈 | `clients/mobile-staff/` 내부 driver tab (별도 mobile-driver 신규 X — 사용자 결정 2026-05-07) |
| 분류 | RN Expo (SDK 53) — v3 (estimate WebView only) → v4 (estimate / driver mode 분기) |
| 신규 화면 | DriverDashboardScreen / DriverLocationTrackingScreen / DriverSignatureScreen / GpsBlockedScreen / DriverTabNavigator / AppRootNavigator |
| 신규 hook | useGpsPermission (foreground 의무 + background 선택 + graceful guard) |
| 신규 client | `src/api/arologis.ts` (3 endpoint — today / locations / sign) |
| 신규 theme | `src/theme/tokens.ts` (W3+W4+W5+post-W5+W10-1 토큰 1:1 복제) |
| Pretendard | `usePretendardFontGuarded()` 정식 활성 + `app.json` expo-font plugin |
| BE 영향 | 0 (W10-1 endpoint 정의 그대로 호출, IT 활성은 후속 fix) |
| docs 동기화 | 9 영역 (README × 3 + ROADMAP + DECISIONS + readiness + dev-report + 환경변수 + QA) |

## 2. 사용자 결정 (2026-05-07) — 본 PR 적용

| # | 결정 | 본 PR 반영 |
|---|---|---|
| 1 | mobile-staff 내부 driver tab 채택 (별도 mobile-driver 신규 X) | `AppRootNavigator` estimate / driver mode 분기 |
| 2 | GPS foreground = 의무, background = 선택, 거부 fallback = 어플 사용 불가 | `useGpsPermission` + `GpsBlockedScreen` |
| 3 | 본 어플 GPS only (인성 LBS 는 W10-2 시점) | source = `APP_GPS_ACTIVE` 만 활성 |
| 4 | Pretendard self-host (jsdelivr CDN 회피) | `usePretendardFontGuarded()` 정식 + `app.json` plugin |
| 5 | W3+W4+W5+post-W5+W10-1 토큰 1:1 복제 | `theme/tokens.ts` (`tokens.css` RGB 1:1) |

## 3. 모바일 어플 구조

### 3-1. AppRootNavigator (entry 신규)

```
AppRootNavigator
├── mode = 'estimate' (default — 영업직원 견적 WebView, v2/v3 100% 보존)
│   └── EstimateWebViewScreen (변경 0)
└── mode = 'driver' (W10-3 신규)
    └── DriverTabNavigator
        ├── (GPS 권한 거부 / 미가용) → GpsBlockedScreen (어플 사용 불가)
        └── (GPS OK)
            ├── tab = 'dashboard'  → DriverDashboardScreen
            ├── tab = 'tracking'   → DriverLocationTrackingScreen
            └── tab = 'signature'  → DriverSignatureScreen
```

production = user-service `/api/v1/auth/me` 응답의 `roles[]` 에 ROLE_DRIVER 포함 시 `initialMode='driver'`
자동 결정 (본 PR 단계 = mode bar 토글로 검증).

### 3-2. arologis API client (`src/api/arologis.ts`)

backend `ArologisDriverAppController.java` (W10-1 정의) 의 3 endpoint 1:1:

| 함수 | endpoint | 응답 |
|---|---|---|
| `fetchTodayDispatches(token)` | GET `/driver-app/arologis/dispatches/today` | `[{vehicleSequence, tonnage, status}]` |
| `reportLocation(token, payload)` | POST `/driver-app/arologis/locations` | `{locationId, capturedAt}` |
| `submitSignature(token, dispatchId, vehicleSeq, stopSeq, payload)` | POST `/driver-app/arologis/dispatches/{id}/vehicles/{seq}/stops/{stopSeq}/sign` | `{signatureId}` |

base URL = `EXPO_PUBLIC_API_BASE_URL` (default `http://localhost:8080` = api-gateway). gateway 가 JWT verify
+ ROLE_DRIVER 확인 + X-User-* 주입 후 arologis-service 8097 으로 forward.

UUID 비공개 — driverId / dispatchId UUID 는 path/header 만, UI 에는 sequence + tonnage + status + parsed
identifier (driverCode / partnerCode) 만 노출.

### 3-3. GPS 권한 hook (`src/hooks/useGpsPermission.ts`)

상태 머신:

```
mount
 │
 ├─ status = 'unknown'  (loading)
 │
 ├─ requestForegroundPermissionsAsync() = granted ?
 │   ├─ NO  → status = 'denied', blocked = true (GpsBlockedScreen)
 │   └─ YES → requestBackgroundPermissionsAsync() (선택)
 │             ├─ granted → backgroundGranted = true
 │             └─ failed  → backgroundGranted = false (graceful, foreground 만으로 진행)
 │
 └─ expo-location 미설치 → status = 'unavailable', blocked = true
```

`status === 'unknown' || 'denied' || 'unavailable'` 시점 driver tab 진입 자체 차단 (사용자 결정 — 거부
fallback = 어플 사용 불가).

### 3-4. 30초 GPS 보고 (DriverLocationTrackingScreen)

- mount 시점 = manual 토글 (Switch). 시작 직후 즉시 1회 보고 + 30초 주기 setInterval.
- source = `APP_GPS_ACTIVE` (foreground 권한 O 시점, BE-1 / QA-3 / Designer-2 통합 채택 fix 일관).
- background 권한 OK 시점 운영 후속 결정으로 `APP_GPS_BACKGROUND` 보강 활성 가능 (본 PR 시점 = foreground only).
- 보고 실패 시 lastReport 카드에 오류 노출 (badge `warn`).

### 3-5. 전자서명 캡처 (DriverSignatureScreen)

- 본 PR 시점 = signature canvas placeholder (1x1 transparent PNG dataURL) — `react-native-signature-canvas`
  미설치 환경에서 graceful guard.
- 캡처 시점에 `getCurrentPositionAsync()` 1회 호출 → latitude / longitude 동시 보관.
- POST `/driver-app/arologis/dispatches/{id}/vehicles/{seq}/stops/{stopSeq}/sign` 으로 imageRef + GPS 전송.
- backend `SignatureSource = APP` (LINK = 외부 링크 서명).
- W10-4 slip-service 통합 시점에 imageRef → file-server / S3 업로드 활성.

## 4. theme tokens (Designer-2 1:1 복제)

`src/theme/tokens.ts` 가 `clients/web/design-system/src/tokens/tokens.css` 의 다음 토큰을 RGB 동등으로
JS 객체로 복제:

| 출처 layer | 토큰 그룹 | 본 파일 export |
|---|---|---|
| post-W5 sales-form-polish-slice | surface / ink / line / action / state | `colors.surface` / `colors.ink` / `colors.line` / `colors.action` / `colors.state` |
| W3 dashboard | Google Material method (GET/POST/PUT/DELETE) | `colors.method` |
| W3 dashboard | status badge (b-ok/b-warn/b-info/b-new) | `colors.badge` |
| W4 notification | 3 channel (b-channel-push/email/sms) | `colors.channel` |
| post-W5 D-W5-2 | slice accent (success/pending/deferred) | `colors.sliceAccent` |
| W10-1 | unparsed peach (b-unparsed) | `colors.unparsed` |

`badgeStyle(kind)` 헬퍼 — RN inline style 객체 반환 (CSS class `b-channel-push` / `slice-accent-success`
와 1:1 매핑). spacing (4-base), radii (badge 4px / card 8px / button 4px / modal 8px), typography
(Pretendard family + 8 size + 4 weight + 3 line-height) 도 동등 export.

## 5. Pretendard self-host 정식 도입 (Designer-2)

- 변경 전 (v2/v3) — `usePretendardFontGuarded()` 가 즉시 `true` 반환 (no-op, RN UI 미차단).
- 변경 후 (v4 / 본 PR) — `expo-font` 의존성 정식 추가 + `useFonts` 패턴 + graceful guard:
  - `expo-font` 가용 + asset 등록 OK → useFonts 결과 반환.
  - `expo-font` 미설치 또는 asset 누락 → ready=true 유지 (RN UI 미차단, WebView 안 legacy 자체 web font 사용).
- `app.json` plugins 에 `expo-font` 등록 — `assets/fonts/Pretendard-{Regular,Medium,SemiBold,Bold}.otf` 4 weight.
- 정식 OTF 배치 = 본 PR 진입 시점은 graceful guard (asset 미배치 가능). 후속 fix 또는 W10-4 슬라이스에서 9 weight 일괄 배치 권장.
- jsdelivr CDN 회피 — `docs/qa/phase10-step-3-mobile-driver-tab/*.html` QA 캡처 도 self-host 방식 일관 (web QA 와 분리).

## 6. backend 영향 (0 — W10-1 endpoint 정의 그대로)

- `services/arologis-service/.../ArologisDriverAppController.java` (W10-1 PR #97 머지) 3 endpoint 변경 0.
- `samhan.arologis.client.skeleton-mode=true` (W10-1 default) 그대로 — 본 PR 은 client 측만 활성.
- IT 활성 (`ArologisDriverAppControllerIT`) 은 후속 fix 또는 W10-4 (slip 통합 시점) 일괄 도입 권장.

## 7. 가드 체크리스트

- [x] worktree origin/main 동기화 (HEAD `a98048e`)
- [x] 단계적 commit + 자주 push (PR #97 도중 종료 회피)
- [x] mobile-staff 내부 driver tab (별도 mobile-driver 신규 X) — 사용자 결정 2026-05-07
- [x] GPS foreground 의무 + background 선택 + 거부 fallback 어플 사용 불가
- [x] 본 어플 GPS only (W10-3 시점) — `APP_GPS_ACTIVE` 만 활성
- [x] Pretendard self-host 정식 (jsdelivr CDN 회피)
- [x] W3+W4+W5+post-W5+W10-1 토큰 1:1 복제 (`theme/tokens.ts`)
- [x] UUID 비공개 가드 — driverCode + sequence + parsed identifier 만 UI 노출
- [x] 한국어 commit / PR / Issue
- [x] PR 본문 가드 (개발책임자 멘트 0)
- [x] body-file UTF-8 (BOM 없음)
- [x] docs 동기화 9+ 영역
- [x] QA 캡처 3종 + commit-pinned raw URL HEAD 200 + re-pin
- [x] TM/PM 승인 섹션 의무

## 8. 후속 (W10-2 / W10-4 / 후속 fix)

| 항목 | 시점 | 내용 |
|---|---|---|
| 인성 LBS 통합 (`EXTERNAL_INSUNG_LBS`) | W10-2 | 인성데이타 vendor 의 LBS callback endpoint 활성, GPS source 우선순위 정정 |
| signature canvas 정식 | 후속 fix 또는 W10-4 | `react-native-signature-canvas` 의존성 추가 + dataURL 캡처 활성 |
| slip-service 통합 (imageRef 업로드) | W10-4 | file-server / S3 imageRef + signature 정식 등록 |
| Pretendard OTF 9 weight 일괄 배치 | 후속 fix 또는 W10-4 | `assets/fonts/Pretendard-*.otf` 9 weight 정식 (운영 권장) |
| `ArologisDriverAppControllerIT` 6 case | 후속 fix 또는 W10-4 | 인증 + today + reportLocation + signature + 미존재 + 권한 거부 |
| react-navigation 정식 | W10-4 또는 후속 슬라이스 | `@react-navigation/native` + `bottom-tabs` 도입 후 `DriverTabNavigator` 치환 |
| dashboard → signature deeplink | W10-4 | vehicleSeq + stopSeq param 전달 (현재 = MOCK_STOP_FOR_PR placeholder) |
| Detox e2e 시나리오 | W10-5 회고 | iOS sim / Android emulator + driver tab 3 화면 e2e PASS |

## 9. 환경변수 추가 (mobile-staff)

| 변수 | default (dev) | default (prod) | 비고 |
|---|---|---|---|
| `EXPO_PUBLIC_API_BASE_URL` | `http://localhost:8080` | `https://api.samhan-air.com` | api-gateway 진입 (W10-3 driver tab `/driver-app/arologis/*` 호출) |
| `EXPO_PUBLIC_ESTIMATE_APP_URL` | `http://localhost:5183/` | `https://estimate.samhan-air.com/` | estimate WebView 영업견적 화면 (v2/v3 보존) |

backend (`services/arologis-service/`) 환경변수는 W10-1 PR #97 그대로 (변경 0). `infrastructure/env-templates/arologis-service.env` 변경 X.
