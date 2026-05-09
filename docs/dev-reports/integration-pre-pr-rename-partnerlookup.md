# PR-E 진입 전 선행 — R2 parsedPartnerCode rename + BE-E PartnerLookupClient 실 구현

> 본 dev-report 는 PR (`feature/integrated-pre-rename-partnerlookup`) 의 종합 작업 보고. D-P10-17 후속 backlog 2건 (R2 + BE-E) 을 PR-E1 (slip+arologis+inventory 7건) 진입 전 단일 PR 로 정리.

## 1. 배경

PR #115 (W10-step-9) 머지 시점 잔존 backlog 2건 (D-P10-17 후속):

1. **R2** — KakaoDispatchParser 의 `parsed_partner_code` (Long, 카톡 슬립번호 "(에스엠하나공조-214)" 의 214) 와 partner-service 의 `partner_code` (String, "P-2026-0001" 비즈니스 식별자) 가 동일 명칭으로 PR-E1 의 RegionClassifier + PartnerLookupClient 통합 시점에 의미 혼동 위험.
2. **BE-E** — D-P10-17 시점 NoopPartnerLookupClient (placeholder) 가 production 에서 활성되어 ChatRoom/BlockedPartner CSV import 가 noop empty 반환 → 모든 row reject 회귀.

PR-E1 (slip+arologis+inventory 7건) 진입 전 두 작업을 단일 PR 로 통합해야 lookup 결과 컬럼 신설 시 충돌 0 + production CSV import 정상 동작 확보.

## 2. 산출물

### 2-1. R2 — arologis parsedPartnerCode 명칭 분리

**Flyway**:
- `services/arologis-service/src/main/resources/db/migration/V4__rename_parsed_partner_code.sql` 신규
  - `vehicle_stops.parsed_partner_code` (BIGINT) → `parsed_kakao_seq` rename
  - 신규 `parsed_partner_code` (VARCHAR(50)) 컬럼 — PR-E1 lookup 결과
  - `ix_vehicle_stops_partner_code_active` → `ix_vehicle_stops_kakao_seq_active` rename + 신규 partial index `ix_vehicle_stops_partner_code_active` (NULL 제외, 활성 행)

**Domain / Parser**:
- `VehicleStop` entity — `parsedKakaoSeq` (Long) + `parsedPartnerCode` (String) 분리, 9-인자 factory 추가, `updateParsedPartnerCode` setter 신규 (PR-E1 lookup 후속 갱신용)
- `ParsedDispatch.ParsedStop` record — `parsedPartnerCode` → `parsedKakaoSeq` (Long) rename, 7-인자 호환 생성자 보존
- `KakaoDispatchParser` — `parsePartnerCode` → `parseKakaoSeq` 메서드 rename + Javadoc 정정

**Service / Controller / DTO**:
- `SlipResolver.resolveByPartnerCode(Long)` → `resolveByKakaoSeq(Long)` rename (의미 동일, naming 만 — slip-service 측 endpoint path 변수는 PR-E1 별도 정합)
- `ArologisDriverAppController.sign` — SlipResolver 호출 이름 정합 + log 메시지 정정
- `ManualDispatchRequest.ManualStop`, `ManualDispatchPreviewResponse.PreviewStop` — Long 카톡 식별자 필드 `partnerCode` → `kakaoSeq` rename
- `DispatchDetailResponse.StopDetail` — `parsedKakaoSeq` (Long) 와 `parsedPartnerCode` (String) 분리 응답 (PR-E1 의 lookup 결과 사용자 노출 경로 확보)
- `ParsedDispatchResponse.ParsedStopDto` — `parsedKakaoSeq` rename
- `DispatchService`, `DispatchManualService` — VehicleStop 저장 시 `kakaoSeq` 전달

**Test**:
- `KakaoDispatchParserTest` case 3 (정차 partner 추출) + case 8 (정확도 회귀) — `parsedKakaoSeq()` 호출로 정정
- `SignatureIntegrationIT` — 코멘트 / docstring 정정 (`resolveByKakaoSeq`)

### 2-2. BE-E — PartnerLookupClient 실 구현

**신규 RestClientPartnerLookupClient**:
- `services/notification-service/src/main/java/.../client/RestClientPartnerLookupClient.java` 신규
  - partner-service `GET /internal/partners/{partnerCode}` 호출 → `verifyPartnerCode(String)`
  - partner-service `GET /internal/partners/by-name?name=` 호출 (한글 query UriUtils.encode UTF-8) → `findPartnerCodeByName(String)`
  - X-Internal-Token 헤더 인증 (`app.security.internal.token` ← `SAMHAN_INTERNAL_TOKEN`)
  - 응답 ApiResponse wrapper 의 `data.partnerCode` 추출 (Jackson)
  - 404 (미존재) / 409 (다중 매칭) / 5xx / 네트워크 실패 → fail-soft empty (호출 측 row 단위 reject)

**활성 가드**:
- `@Profile("!test")` — test profile 에서 비활성, 기존 IT `@MockBean PartnerLookupClient` 격리 패턴 보존 (memory `feedback_it_mockbean_external_clients`)
- `@ConditionalOnProperty(samhan.notification.partner-lookup.enabled, default true)` — 단순 환경 외부 호출 회피 토글
- `NoopPartnerLookupClient` 의 `@ConditionalOnMissingBean(PartnerLookupClient.class)` 가 본 RestClient 활성 시 자동 비활성화 → production 에서 단일 활성 구현체

**application.yml** (`services/notification-service/src/main/resources/application.yml`):
```yaml
samhan:
  partner-service:
    url: ${SAMHAN_PARTNER_SERVICE_URL:http://localhost:8095}
  notification:
    partner-lookup:
      enabled: ${SAMHAN_NOTIFICATION_PARTNER_LOOKUP_ENABLED:true}
```

**Test**:
- `RestClientPartnerLookupClientTest` 신규 (MockRestServiceServer 5 case)
  - case 1: verifyPartnerCode 200 응답 + ApiResponse wrapper data.partnerCode 추출
  - case 2: verifyPartnerCode 404 → fail-soft empty
  - case 3: findPartnerCodeByName 200 + 한글 query UriUtils encode 검증 (URL prefix matcher)
  - case 4: findPartnerCodeByName 409 (다중 매칭) → fail-soft empty
  - case 5: internalToken 미설정 시 외부 호출 회피 + empty (warn log)

### 2-3. partner-service findByCode endpoint

기존 `PartnerInternalController.lookup(@PathVariable String partnerCode)` (D-P9-16) 가 이미 존재 — 본 PR 신규 작업 없음. 검증 완료 (`PartnerInternalControllerIT` 기존 케이스 회귀 0).

## 3. 검증

### 3-1. 풀빌드
- `./gradlew assemble -x test` → BUILD SUCCESSFUL (95 actionable tasks)

### 3-2. 단위 테스트
- `./gradlew :services:arologis-service:test` → BUILD SUCCESSFUL (KakaoDispatchParserTest case 1-8 + DispatchManualServiceTest + 기타 회귀 0)
- `./gradlew :services:notification-service:test` → BUILD SUCCESSFUL (RestClientPartnerLookupClientTest 5/5 PASS + 기존 ChatRoomImportServiceTest 회귀 0)
- `./gradlew :services:partner-service:test --tests "*PartnerInternalControllerIT"` → BUILD SUCCESSFUL (Korean path Testcontainers skip 정상)

### 3-3. Korean path JDK 17 트랩 회피
Windows + 한글 경로 + JDK 17 환경에서 Testcontainers 가 자동 skip 됨 (memory `feedback_korean_path_jdk` / `feedback_testcontainers_windows_docker`). CI Linux runner 에서 실 IT 동작 검증 자동 (CI 재실행 시).

## 4. 후속 (PR-E1)

- arologis V4 신규 String 컬럼 `parsed_partner_code` 채우기 — RegionClassifier + PartnerLookupClient 결과를 KakaoDispatchParser 에서 즉시 lookup 또는 별도 batch 로 채움 (성능 / 운영 패턴은 PR-E1 plan 결정)
- slip-service `/internal/slips/by-partner-code/{code}/recent` endpoint path variable 명칭 정합 (kakaoSeq 의미 분리) — slip 측 별도 PR
- ChatRoomImportService / PartnerBlockImportService 의 production 환경 실 lookup 동작 모니터링 (RestClient 5xx fail rate / 한글 query 인코딩 호환)

## 5. 제약 / 가드 일관

- BaseEntity 7 audit fields 의무 (V4 영향 없음)
- Soft Delete 일관 (V4 partial index `is_deleted = FALSE` 가드)
- 한국어 Javadoc — 모든 신규 코드
- ROLE 풀네임 (MASTER) 일관
- IT 외부 client `@MockBean` 격리 — RestClientPartnerLookupClient 의 `@Profile("!test")` 로 보존
- partner-service Internal endpoint X-Internal-Token 가드 일관
- production 우선 / test 격리 패턴 — RestClient 활성 시 Noop 자동 비활성

## 6. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR 은 D-P10-17 후속 backlog 분리 PR 패턴 (별도 docs PR 회피, 단일 통합 PR + 5-team 리뷰) 일관. 머지 후 PR-E1 진입.
