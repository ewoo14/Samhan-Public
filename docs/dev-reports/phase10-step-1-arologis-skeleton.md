# Phase 10 W10-1 — arologis-service skeleton + Phase 10/11 renumber

> **Phase 번호 renumber 적용 (D-P10-05, 사용자 결정 2026-05-07)** — 신규 Phase 10 = arologis-service / 기존 Phase 10 (AWS migration cutover) → Phase 11 으로 이동.

## 1. 슬라이스 요약

| 항목 | 값 |
|---|---|
| 슬라이스 | Phase 10 W10-1 (arologis-service skeleton) |
| 도입 service | `services/arologis-service/` (port 8097, DB `arologis_db`) |
| 분류 | 신규 도메인 마이크로서비스 (배차) |
| 5 entity | Dispatch / Vehicle / VehicleStop / Driver / Signature + DriverLocation GPS |
| 7 enum | DispatchType / VehicleTonnage / VehicleStatus / StopStatus / DriverSource / MatchSource / SignatureSource |
| 핵심 기능 | KakaoDispatchParser (사용자 카톡 13 차량 80% 정확도) + DriverMatcher 추상화 (Mock + Insung Quick placeholder) |
| 외부 의존성 | partner / user / slip / notification (4 client, skeleton-mode default) |
| 테스트 | 단위 20 case + IT 13 case (Docker 가용 시 활성) |
| docs 동기화 | 9+ 영역 (README / ROADMAP / DECISIONS / arologis README / dev-report / phase10 readiness 재작성 / phase11 readiness 신규 / env-template / postgres init / prometheus) |
| Phase renumber | Phase 10 = arologis / Phase 11 = AWS cutover (D-P10-05) |

## 2. 도메인 모델

### 2-1. 5 entity + DriverLocation

**Dispatch** — 배차 1건 = 카톡 1 메시지. 헤더 ("8일착 야상입니다") 추출 → `dispatchDate` (LocalDate) + `dispatchType` (NIGHT). `rawKakaoText` 는 audit 용 원본 보존 (TEXT).

**Vehicle** — 차량 1대 = 카톡 "1." 그룹. `(dispatchId, sequence)` 활성 unique. `tonnage` enum 매핑 (1톤 / 1.4톤 / ...). `assignedDriverId` 는 매칭 완료 후 set (UUID 비공개 — 응답 시 driverCode 로 변환). `matchSource` 는 매칭 경로 추적 (INTERNAL_APP / EXTERNAL_INSUNG_QUICK / ...).

**VehicleStop** — 정차 1건 = 카톡 라인. `(vehicleId, sequence)` 활성 unique. 정규표현식 `^[-–]\s*([^()]+)\(([^()]+?)-(\d+)\)(.*)$` 로 4 group 추출 (주소 / 사업자명 / 전표번호 / notes). 미해석 라인 ("상일상차") 은 `status=UNPARSED` + rawText 만 보존.

**Driver** — `driverCode` (사용자 노출 식별자) + `phoneNumber` 활성 unique. `source` enum 으로 본 어플 사용 (INTERNAL) vs 외부 vendor 매칭 (EXTERNAL_*) 구분. `appInstalled` true → `appUserId` (user-service userId) 매핑.

**Signature** — slip-service 통합은 W10-4 시점. `source=APP` 일 때만 GPS 위도/경도 캡처 (NUMERIC(10,7) ~1.1cm 정확도).

**DriverLocation** — BaseEntity 미상속 (대용량 GPS 데이터 + 30일 hard DELETE 정책). `capturedDate` (DATE) 는 30일 cleanup partition key.

### 2-2. 7 enum

| Enum | 값 | 출처 |
|---|---|---|
| DispatchType | DAY / NIGHT / EXPRESS | 카톡 헤더 ("야상" → NIGHT 등) |
| VehicleTonnage | TONNAGE_1 / TONNAGE_1_4 / TONNAGE_2_5 / TONNAGE_5 / TONNAGE_BIG | 카톡 그룹 끝 라인 ("1톤" / "1.4톤" → TONNAGE_1_4) |
| VehicleStatus | PENDING / MATCHING / ASSIGNED / DEPARTED / DELIVERED / CANCELLED | 라이프사이클 6단계 |
| StopStatus | PENDING / ARRIVED / DELIVERED / FAILED / UNPARSED | 정차 상태 + 미해석 라인 |
| DriverSource | INTERNAL / EXTERNAL_INSUNG_QUICK / EXTERNAL_SMS / EXTERNAL_KAKAO / MANUAL | Driver 등록 경로 |
| MatchSource | INTERNAL_APP / EXTERNAL_INSUNG_QUICK / EXTERNAL_SMS / EXTERNAL_KAKAO / MANUAL | 매칭 경로 |
| SignatureSource | LINK / APP | 서명 경로 (외부 링크 vs 본 어플) |

## 3. KakaoDispatchParser (정규표현식 + heuristic)

### 3-1. 4 정규표현식 + heuristic 5단계

```
HEADER       = ^(\d+)일착\s*(.+?)입니다\s*$
VEHICLE_HDR  = ^(\d+)\.\s*(.*)$
TONNAGE      = ^(\d+(?:\.\d+)?)톤\s*$
STOP         = ^[-–]\s*([^()]+)\(([^()]+?)-(\d+)\)(.*)$
```

heuristic 핵심:

1. **헤더 1회 추출** — 최초 매칭 라인에서 `dispatchDate` (LocalDate.now() 기준 가까운 월 추정) + `dispatchType` 결정.
2. **TONNAGE 우선 매칭 (W10-1 fix)** — VEHICLE_HDR 보다 먼저 시도. "1.4톤" 라인이 `^\d+\.` 패턴 매칭으로 신규 vehicle 헤더로 오인되는 경로 차단.
3. **차량 그룹 헤더 + 첫 정차 동시 처리** — "2. -경기 김포시(...)..." 같은 single-line vehicle 도 parser 가 STOP 패턴 hint 로 첫 정차로 처리.
4. **미해석 라인 group label 보존** — "상일상차" / "초월상차" 등 정차 패턴 미매칭 라인은 `unparsed=true` + rawText 보존 (parsedAddress=null).
5. **날짜 추정** — referenceDate (입력 시점 = LocalDate.now()) 기준 day 가 가장 가까운 월/년 (현재 월 또는 다음 월). 7일 이상 과거이면 다음 월.

### 3-2. 사용자 제공 카톡 예시 13 차량 검증

`KakaoDispatchParserTest` 8 case:

1. 헤더 추출 (8일착 야상 → NIGHT, day=8 → 2026-05-08)
2. 차량 그룹 분리 정확히 13 차량
3. 정차 라인 정규표현식 — 첫 차량 6 element (group label 2 + 정차 4), partnerCode 218/214/170/76 등 정확 추출
4. 톤수 인식 — 1톤 12 + 1.4톤 1 (7번 차량이 1.4톤)
5. 미해석 라인 — "상일상차" / "초월상차" group label 보존
6. notes 다양 패턴 — "9시하차" / "오전일찍" / "아침7시" / "9시까지배송요망" 보존
7. edge case — 헤더 누락 시 IllegalArgumentException
8. 정확도 회귀 — 80% 이상

## 4. DriverMatcher 추상화

### 4-1. interface + 2 impl

- `DriverMatcher` interface — `match(Vehicle, List<VehicleStop>) → DriverMatchResult`
- `MockDriverMatcher` (default, `provider=mock`) — MOCK-001 / 010-0000-0000 driver 자동 upsert + 매칭 성공 응답
- `InsungQuickDriverMatcher` (placeholder, `provider=insung-quick`) — W10-1 단계는 `UnsupportedOperationException` throw, W10-2 시점 실 vendor API 통합

### 4-2. MatcherConfig — `@Primary` Bean 선택

`MatcherConfig` 가 `samhan.arologis.matcher.provider` property 기반으로 활성 impl 의 `@Primary` Bean 등록. 잘못된 provider 값 → mock fallback + warn log.

## 5. 4 외부 client (skeleton-mode default)

| Client | 호출 endpoint | 활성 시점 |
|---|---|---|
| **PartnerClient** | `POST /internal/partners/find-by-codes` (W5 PR #95) | W10-2 자동 매칭 단계 |
| **UserClient** | `GET /internal/users/{userId}` | W10-3 driver-app 인증 시점 |
| **SlipClient** | `POST /internal/slips/{slipId}/signatures` (W10-4 신규) | W10-4 slip 통합 시점 |
| **NotificationClient** | `POST /internal/notifications/send` (W3) | 자동 매칭 단계 알림 |

`samhan.arologis.client.skeleton-mode=true` (default) — 4 client 모두 외부 호출 회피 + Optional.empty / false 반환. W10-2 / W10-4 시점에 `false` 전환.

`shared:user-client-abstraction` 5번째 소비자 (notification + groupware + dashboard + partner-service + arologis = 5).

## 6. ShedLock daily 30일 GPS cleanup

- `DriverLocationCleanupScheduler` — `@Scheduled(cron="0 0 3 * * *", zone="Asia/Seoul")` 매일 03:00 KST
- `@SchedulerLock(name="arologis-location-cleanup", lockAtMostFor=PT15M, lockAtLeastFor=PT5M)` — multi-instance race 가드 (W4 dashboard ShedLockConfig 패턴 일관)
- `repository.deleteOlderThan(LocalDate.now().minusDays(retention))` — Hard DELETE (BaseEntity 미상속 entity 의 30일 정책)
- retentionDays property 음수/0 → 1로 fallback (단위 테스트 검증)

## 7. 테스트

### 7-1. 단위 20 case

- `KakaoDispatchParserTest` 8 — 사용자 카톡 13 차량 80% 정확도 회귀 검증
- `MockDriverMatcherTest` 3 — 정상 매칭 / 기존 driver 재사용 / vehicle null fail-soft
- `InsungQuickDriverMatcherTest` 2 — placeholder UnsupportedOperationException + source enum
- `DispatchServiceTest` 5 — 생성 / 조회 / 자동 매칭 (Mock matcher) / 수동 배정 / 미존재 NOT_FOUND
- `DriverLocationCleanupSchedulerTest` 2 — 30일 cleanup 호출 / retention fallback

### 7-2. IT 13 case (Docker 가용 시)

- `ArologisInternalControllerIT` 4 — 토큰 누락 403 / 불일치 401 / 일치 200 / 빈 body 200
- `ArologisAdminControllerIT` 9 — parse-kakao 정상 / 잘못된 텍스트 400 / dispatch 저장 / list / 미존재 404 / auto-match / unknown driver 404 / stop-status missing 404 / drivers list

4 외부 client 모두 `@MockBean` 격리 (`feedback_it_mockbean_external_clients`).

## 8. docs 동기화 (9+ 영역)

| 영역 | 갱신 |
|---|---|
| 루트 `README.md` | service 인벤토리 8097 arologis 행 추가 + bootRun + 디렉토리 트리 + Phase 매트릭스 (Phase 10 = arologis / Phase 11 = AWS) |
| `ROADMAP.md` | Phase 10 (arologis 5 슬라이스) + Phase 11 (renumber AWS cutover) 분리 |
| `migration/decisions/DECISIONS.md` | D-P10-01 ~ D-P10-05 신규 (arologis 도입 / port 8097 + DB / DriverMatcher 추상화 / RN Expo / Phase renumber) |
| `services/arologis-service/README.md` | 신규 — 6 섹션 (도입 배경 / Domain / REST API / 환경변수 / 테스트 / Phase 11 영향) |
| `docs/dev-reports/phase10-step-1-arologis-skeleton.md` | 본 문서 |
| `docs/migration/phase10/M-PHASE-10-readiness.md` | **재작성** — arologis 5 슬라이스 plan |
| `docs/migration/phase11/M-PHASE-11-readiness.md` | **신규** (이동) — 기존 phase10 readiness 의 AWS migration plan |
| `docs/migration/phase11/M-AWS-MIGRATION-DRY-RUN.md` | **이동** (기존 phase10) |
| `infrastructure/env-templates/arologis-service.env` | 신규 — 모든 시크릿 `CHANGE_ME_LOCAL_ONLY` |
| `infrastructure/postgres/init/01-create-databases.sql` | `arologis_db` 추가 |
| `infrastructure/prometheus/prometheus.yml` | `arologis-service:8097` scrape target 추가 |
| `settings.gradle` + 루트 `build.gradle` | `:services:arologis-service` 추가 |

## 9. § GPS 하이브리드 정책 (사용자 결정 4, 2026-05-07)

W10-1 BE-1 / QA-3 / Designer-2 통합 채택 fix — `DriverLocationSource` enum 4값 (`APP_GPS_BACKGROUND` / `APP_GPS_ACTIVE` / `EXTERNAL_INSUNG_LBS` / `MANUAL`) + `samhan.arologis.gps.priority` env (default `insung-lbs,app-gps,manual`).

W10-3 모바일 어플 권한 정책:

- foreground 권한 = 의무 (배송 도중 위치 추적)
- background 권한 = 선택 (운영 시점 결정)
- 거부 fallback = 어플 사용 불가 (사용자 명시 2026-05-07)
- 인성 LBS 우선 + 본 어플 GPS 보강

V1 SQL `driver_locations.source` 컬럼 = VARCHAR(30) 보존 (Flyway 변경 0). `@Enumerated(EnumType.STRING)` 으로 enum 이름 그대로 string 매핑.

## 10. § 알림 분담 (사용자 결정 3, 2026-05-07)

W10-1 BE-2 / QA-3 통합 채택 fix — 배차 단계 알림과 본 시스템 알림 분리.

배차 단계 알림 = **인성 알림톡** (W10-2 시점 인성 vendor 직접 호출, notification-service 우회)
본 시스템 알림 (어플 설치 invite / 일반 사용자 push) = **notification-service Aligo**

W10-1 시점: notification-service skeleton-mode 토글 (`samhan.arologis.client.skeleton-mode=true`) 로 호출 차단.
W10-2 진입 시점: 인성 알림톡 직접 호출 + notification-service 호출 = 어플 설치 invite 만 (분리 정책).

DECISIONS — `D-P10-06`.

## 11. 가드 체크리스트

- [x] worktree origin/main 동기화 (HEAD `5d6609f Merge PR #96`)
- [x] BaseEntity 7 audit + Soft Delete + 한국어 comment
- [x] VARCHAR(N) only / NUMERIC GPS / partial unique index `WHERE is_deleted = FALSE`
- [x] UUID 사용자 비공개 (driverCode / partnerCode 노출, UUID 비공개)
- [x] 한국어 Javadoc + dev-report
- [x] IT 외부 client `@MockBean` 격리 (4 client 모두)
- [x] AbstractPostgresIT + Docker skip
- [x] 한글 path 회피 (worktree 경로 ASCII, 단위 PASS)
- [x] InternalTokenFilter `/internal/**` prefix 한정
- [x] GitGuardian — 모든 시크릿 `CHANGE_ME_LOCAL_ONLY`
- [x] 다른 service 회귀 0 (partner / notification / groupware / dashboard / user / slip / shared)
- [x] Phase 10/11 renumber 적용 (모든 docs / DECISIONS / readiness)
