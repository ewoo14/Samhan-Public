# M-PHASE-10-readiness — Phase 10 진입 plan (arologis-service 배차 마이크로서비스)

> **Phase 번호 renumber 적용 (D-P10-05, 사용자 결정 2026-05-07)** — 기존 Phase 10 (AWS migration cutover) → Phase 11 으로 이동, 신규 Phase 10 = arologis-service.

본 문서는 Phase 9 완료 시점에서 신규 Phase 10 (arologis-service 배차 마이크로서비스 + 카톡 파싱 + DriverMatcher 추상화 + GPS 추적 + 모바일 어플 통합) 진입을 위한 전제 조건, 작업 분해, 5 슬라이스 roadmap, 가드를 정리한다. Phase 9 회고 (`docs/dev-reports/phase9-retrospective.md`) + Phase 11 readiness (`docs/migration/phase11/M-PHASE-11-readiness.md`) 와 짝을 이룬다.

---

## 1. 진입 조건 (Phase 9 완료 시점)

| 항목 | 상태 | 산출 |
|---|---|---|
| 14 service skeleton 완료 (Phase 0~9) | OK | partner / groupware / notification / dashboard W4 + post-W5 backlog #96 |
| ServiceDiscoveryClient 추상화 + 4 service 소비자 | OK | partner / groupware / notification / dashboard, `samhan.discovery.provider` toggle |
| BaseEntity 7 audit + Soft Delete + partial unique index | OK | 모든 W4/W5 entity 일관 |
| Internal endpoint `/internal/**` prefix 한정 + InternalTokenFilter | OK | PR #91 fix 패턴 |
| chained-default 환경변수 표준 | OK | D-P8-07 + 14 service env-template |
| ShedLock multi-instance race 가드 | OK | D-P9-13 + W4 후속 fix |
| AWS RDS 호환 Postgres standard SQL (NUMERIC + VARCHAR + partial unique) | OK | Phase 8 22 file + Phase 9 Flyway V1 일관 |
| QA 캡처 + commit-pinned raw URL HEAD 가드 | OK | PR #92 회고 강화 |
| 통합 PR + agent discussion 패턴 | OK | `feedback_tm_led_agent_discussion.md` |
| 사용자 가드 (fix 본 PR 채택) | OK | `feedback_integrated_pr_pattern.md` |
| 인성데이타 vendor account | 대기 | W10-2 진입 시점 사용자 발급 |

---

## 2. Phase 10 작업 분해 (5 슬라이스 W10-1 ~ W10-5)

> 슬라이스 분할 근거 — Phase 9 의 5 슬라이스 패턴 (W1~W5) 일관 + arologis-service 도메인 복잡도 (parser + matcher + 모바일 + 통합) 가 5 단계로 자연 분할.

### 2-1. W10-1: arologis-service skeleton (1 통합 PR — 본 PR)

**범위**:
- `services/arologis-service/` 신규 모듈 (port 8097, DB `arologis_db`)
- 5 entity (Dispatch / Vehicle / VehicleStop / Driver / Signature) + DriverLocation GPS 테이블
- 7 enum (DispatchType / VehicleTonnage / VehicleStatus / StopStatus / DriverSource / MatchSource / SignatureSource)
- KakaoDispatchParser (정규표현식 + heuristic, 사용자 카톡 예시 13 차량 80% 정확도 검증)
- DriverMatcher 추상화 + MockDriverMatcher (default) + InsungQuickDriverMatcher placeholder
- 4 client (partner / user / slip / notification, skeleton-mode default)
- Internal + Admin + Driver-app 3 controller (Driver-app endpoint 정의만)
- ShedLock daily 30일 GPS cleanup scheduler
- 단위 18 + IT 13 case
- Phase 10/11 renumber 처리 (모든 docs / DECISIONS / readiness)

**산출**: 본 PR.

**진입 조건**: PR #96 머지 완료 (5d6609f).

### 2-2. W10-2: 인성데이타 vendor 통합 (1 통합 PR)

**범위**:
- InsungQuickDriverMatcher 실 vendor API 호출 구현 (POST /api/orders + match trigger)
- callback 수신 endpoint 활성 (Internal `/dispatches/sync` 실 처리)
- Driver upsert + matchSource = EXTERNAL_INSUNG_QUICK 활성
- `samhan.arologis.matcher.provider=insung-quick` 토글 prod 활성 가능
- 4 client skeleton-mode 일부 false 전환 (notification 우선)
- IT 시나리오 추가 (Mock vendor server / WireMock 권장)

**진입 조건**: 인성데이타 API 키 + partner-id 발급, vendor 문서 사용자 제공.

### 2-3. W10-3: 모바일 어플 (RN Expo, mobile-staff 내부 driver tab) — **완료 (본 PR)**

> **진입 조건 정정 (2026-05-07)** — W10-2 (인성데이타 협약) 의존 X. W10-1 완료 후 진입 가능.
> 본 어플 GPS only 활성 (인성 LBS 통합은 W10-2 시점).

**산출 (본 PR)**:
- `clients/mobile-staff` 내부 driver tab 채택 (별도 mobile-driver 신규 X) — `AppRootNavigator` 의 estimate / driver mode 분기로 통합
- Driver-app endpoint 3 종 client 통합 — `src/api/arologis.ts` (today / locations / sign)
- JWT 인증 (user-service 발급, Bearer header) + base URL = `EXPO_PUBLIC_API_BASE_URL` (default gateway 8080)
- driver tab 3 화면:
  - `DriverDashboardScreen` — 오늘 배정 vehicle 목록 + 톤수/상태 badge (b-channel-* / slice-accent-* 일관)
  - `DriverLocationTrackingScreen` — 30초 주기 GPS 보고 (foreground = APP_GPS_ACTIVE)
  - `DriverSignatureScreen` — 전자서명 + GPS 동시 캡처 (NUMERIC(10,7))
- GPS 권한 거부 차단 화면 — `GpsBlockedScreen` (foreground 거부 fallback = 어플 사용 불가)

**GPS 권한 정책 (사용자 결정 4 GPS 하이브리드, 2026-05-07) — 본 PR 적용 완료**:
- foreground 권한 = **의무** (배송 도중 위치 추적, `useGpsPermission` hook 처리)
- background 권한 = 선택 (운영 시점 결정, `requestBackgroundPermissionsAsync` graceful)
- 거부 fallback = **어플 사용 불가** (`GpsBlockedScreen` 노출, driver tab 차단)
- 본 PR (W10-3) 시점 = **본 어플 GPS only 활성** (`APP_GPS_ACTIVE`)
- 인성 LBS 통합 = W10-2 시점 별도 callback endpoint 활성 (`EXTERNAL_INSUNG_LBS`)

**Design baseline (Designer-2 채택, 2026-05-07) — 본 PR 적용 완료**:
- **Pretendard self-host 정식 도입** — jsdelivr CDN 회피 + `usePretendardFontGuarded()` 정식 활성 (graceful guard 보존)
- `app.json` plugin = `expo-font` + `expo-location` 정식 등록 + iOS NSLocation* + Android permissions
- **W3+W4+W5+post-W5+W10-1 토큰 1:1 복제** — `src/theme/tokens.ts` 신규 (`web/design-system/tokens.css` RGB 1:1)

**진입 조건 (정정)**: W10-1 완료 (PR #97 머지 `a98048e`). W10-2 의존 X.

### 2-4. W10-4: slip-service 전자서명 통합 (1 통합 PR)

**범위**:
- slip-service 신규 endpoint `POST /internal/slips/{slipId}/signatures` 활성
- arologis SlipClient.registerSignature 실 호출 활성
- signatures 테이블 → slip-service 저장 매핑 (parsed_partner_code → slipId 변환)
- file-server / S3 imageRef 업로드 (별도 결정 — Phase 11 cutover 시점 S3 권장)
- IT 시나리오 강화 — 전자서명 → slip-service 통합 전체 시나리오

**진입 조건**: W10-3 완료. slip-service 사용자 노출 식별자 lookup 완성.

### 2-5. W10-5: 회고 + 통합 운영 안정화 (1 통합 PR)

**범위**:
- Phase 10 회고 (`docs/dev-reports/phase10-retrospective.md`)
- 운영 모니터링 (Prometheus arologis 추가, Grafana dashboard)
- 정확도 회귀 검증 (실 카톡 데이터 100건 이상 → 90% 정확도 목표)
- Phase 11 (AWS cutover) 진입 가드 점검

---

## 3. arologis-service 도메인 spec (W10-1 baseline)

### 3-1. 5 entity + DriverLocation

| Entity | 설명 |
|---|---|
| Dispatch | 배차 1건 = 카톡 1 메시지. `dispatchDate` (8일착) + `dispatchType` (DAY/NIGHT/EXPRESS) + `rawKakaoText` (audit) |
| Vehicle | 차량 1대 = 카톡 "1." 그룹. `(dispatchId, sequence)` unique. `tonnage` / `assignedDriverId` / `matchSource` / `status` |
| VehicleStop | 정차 1건 = 카톡 라인. `(vehicleId, sequence)` unique. `rawText` / `parsedAddress` / `parsedPartnerName` / `parsedPartnerCode` (전표번호) / `notes` / `status` |
| Driver | 배송기사. `driverCode` / `phoneNumber` (활성 unique). `source` / `appInstalled` / `appUserId` |
| Signature | 전자서명 (slip-service 통합 W10-4). `stopId` / `source` (LINK/APP) / `imageRef` / GPS |
| DriverLocation | GPS 추적 (BaseEntity 미상속, 30일 hard DELETE). NUMERIC(10,7) ~1.1cm 정확도 |

### 3-2. 7 enum

| Enum | 값 |
|---|---|
| DispatchType | DAY / NIGHT / EXPRESS |
| VehicleTonnage | TONNAGE_1 / TONNAGE_1_4 / TONNAGE_2_5 / TONNAGE_5 / TONNAGE_BIG |
| VehicleStatus | PENDING / MATCHING / ASSIGNED / DEPARTED / DELIVERED / CANCELLED |
| StopStatus | PENDING / ARRIVED / DELIVERED / FAILED / UNPARSED |
| DriverSource | INTERNAL / EXTERNAL_INSUNG_QUICK / EXTERNAL_SMS / EXTERNAL_KAKAO / MANUAL |
| MatchSource | INTERNAL_APP / EXTERNAL_INSUNG_QUICK / EXTERNAL_SMS / EXTERNAL_KAKAO / MANUAL |
| SignatureSource | LINK / APP |

### 3-3. KakaoDispatchParser

- 헤더 정규표현식 — `^(\d+)일착\s*(.+?)입니다\s*$`
- 차량 헤더 — `^(\d+)\.\s*(.*)$` (TONNAGE 매칭 후 시도 — "1.4톤" 라인이 vehicle 로 오인되는 경로 차단)
- 톤수 — `^(\d+(?:\.\d+)?)톤\s*$`
- 정차 — `^[-–]\s*([^()]+)\(([^()]+?)-(\d+)\)(.*)$`
- 미해석 라인 — group label `unparsed=true` 보존

W10-1 정확도 목표 = 80% (실 카톡 사용자 제공 13 차량 / ~50 정차 검증). W10-5 시점 90% 회귀.

### 3-4. DriverMatcher 추상화

- interface — `match(Vehicle, List<VehicleStop>) → DriverMatchResult`
- W10-1: MockDriverMatcher (MOCK-001 / 010-0000-0000) + InsungQuickDriverMatcher placeholder
- W10-2: InsungQuickDriverMatcher 실 구현
- 토글 — `samhan.arologis.matcher.provider=mock|insung-quick`

---

## 4. 환경변수 표준 (chained-default — D-P8-07 일관)

```yaml
SAMHAN_AROLOGIS_PORT=8097
SAMHAN_AROLOGIS_DB_URL=jdbc:postgresql://.../arologis_db
SAMHAN_AROLOGIS_DB_USERNAME=...
SAMHAN_AROLOGIS_DB_PASSWORD=...
SAMHAN_INTERNAL_TOKEN=...
SAMHAN_AROLOGIS_MATCHER_PROVIDER=mock          # mock | insung-quick (W10-2)
SAMHAN_INSUNG_QUICK_API_URL=...                # W10-2 활성
SAMHAN_INSUNG_QUICK_API_KEY=...                # W10-2 활성
SAMHAN_INSUNG_QUICK_PARTNER_ID=...             # W10-2 활성
SAMHAN_AROLOGIS_LOCATION_RETENTION_DAYS=30
SAMHAN_AROLOGIS_CLIENT_SKELETON_MODE=true      # W10-2 / W10-4 시점 false 전환
```

---

## 5. 가드

- 컬럼 타입 — VARCHAR(N) only / NUMERIC GPS / partial unique index `WHERE is_deleted = FALSE` 의무
- BaseEntity 7 audit 의무 (DriverLocation 만 예외 — 30일 hard DELETE 정책)
- UUID 비공개 가드 — driverCode / partnerCode / vehicle sequence / stop sequence 노출, UUID 비공개
- 한국어 Javadoc + dev-report
- IT 외부 client `@MockBean` 격리 (4 client 모두)
- 한글 path 회피 (assemble + 단위 PASS, IT CI Linux runner)
- AWS RDS 호환 Postgres standard SQL only (Phase 11 cutover 의무)

---

## 6. Phase 11 (AWS cutover) 영향

- arologis_db RDS 호환 — Postgres standard SQL only (검증 완료)
- ShedLock 클러스터 — Phase 11 W11-2 (기존 P10-1) cutover 시점에 통합
- DriverMatcher 추상화 — Phase 11 cutover 시 vendor 변경 영향 0 (provider 토글)
- 모바일 어플 (RN Expo) — Phase 11 cutover 시 deep link / push token 갱신만
