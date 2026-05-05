# Phase 6 M2 — partner-auth-service BE skeleton 구현 보고서

> Phase 6 BE M2 sub-team agent 산출.
> Branch: `feature/migration-be-m2-partner-auth-service`. base: `origin/main` 의 PR #67 머지 (`3c4faa6`).

## 1. 작업 범위 (W2 단계)

거래처(파트너) 자체 인증·세션 서비스의 신규 BE skeleton:
- 자체 PostgreSQL DB (`partner_auth_db`) 소유
- 7개 endpoint 구현 (CONSISTENCY-MATRIX 결정 적용)
- 외부 의존: M3 dc-config-service (RPC stub) + sms-service (Phase 7+ stub)

**포트 8091** 충돌 회피 (M3 8089 / M4 8088 / slip 8086 / product 8084 와 분리).

## 2. 신규 파일 매트릭스

| 카테고리 | 파일 | 역할 |
|---|---|---|
| Gradle | `services/partner-auth-service/build.gradle` | Spring Boot 3.3.5 + JPA + Flyway + Eureka client + JWT + Testcontainers |
| Gradle root | `settings.gradle`, `build.gradle` | `:services:partner-auth-service` 모듈 등록 |
| Application | `PartnerAuthServiceApplication.java` | entry point + `@EnableConfigurationProperties` |
| Config | `config/PartnerAuthJwtProperties.java` | `samhan.jwt.*` (HS256 secret, 8h TTL) |
| Config | `config/DcConfigClientProperties.java` | `samhan.dc-config.*` (M3 base URL) |
| Config | `config/SecurityConfig.java` | DelegatingPasswordEncoder + 모든 `/api/v1/auth/partner-**` permit (W2) |
| Domain | `domain/PartnerAuth.java` | 메인 aggregate — bizNo UNIQUE + status + history jsonb + failed_attempts + tutorial flags |
| Domain | `domain/PartnerLoginAttempt.java` | 로그인 시도 audit (auth_id + bizNo + result + IP + UA + mobile) |
| Domain | `domain/PartnerSession.java` | JWT JTI UNIQUE + 만료/취소 시각 |
| Domain enum | `domain/PartnerStatus.java` | 10 enum (NOT_FOUND_SYSTEM/NOT_FOUND_AUTH/PENDING/LOCKED/LONG_UNUSED/ACCESS_DENIED/PW_EXPIRED/NEED_PW_SET/NEED_PW_INPUT/OK) |
| Domain enum | `domain/LoginAttemptResult.java` | 7 enum (audit) |
| Repository | `repository/PartnerAuthRepository.java` | findByBizNo / existsByBizNo |
| Repository | `repository/PartnerLoginAttemptRepository.java` | top20 by bizNo desc |
| Repository | `repository/PartnerSessionRepository.java` | findByJti |
| DTO | `dto/CheckAuthStatusResponse.java` | 8 status enum 응답 |
| DTO | `dto/PartnerRegisterRequest/Response.java` | 가입 신청 |
| DTO | `dto/SetPasswordRequest/Response.java` | OK / USED_PW |
| DTO | `dto/TryLoginRequest.java`, `TryLoginResponse.java` | status + token + config nested |
| DTO | `dto/TempPasswordRequest/Response.java` | 202 + masked mobile |
| DTO | `dto/ExpirationResponse.java` | 30일 슬라이딩 만료 |
| DTO | `dto/TutorialUpdateRequest/Response.java` | PC / MOBILE |
| Client | `client/DcConfigClient.java` | M3 RPC stub (W2: empty Optional) |
| Client | `client/PartnerConfigDto.java` | M3 응답 placeholder |
| Client | `client/SmsClient.java` | sms-service stub (log 큐잉) |
| Service | `service/PartnerAuthService.java` | 7 endpoint 비즈니스 로직 (legacy 100% 보존) |
| Controller | `controller/PartnerAuthController.java` | 7 endpoint REST mapping |
| Exception | `exception/PartnerAuthExceptionHandler.java` | ApiResponse 표준 매핑 |
| Resources | `resources/application.yml` | port 8091 + DB + Eureka + JWT + dc-config + local profile |
| Flyway | `resources/db/migration/V1__init_partner_auth.sql` | 3 entity + 7 audit columns + 인덱스 7개 |
| Docker | `Dockerfile` | eclipse-temurin:17-jre-alpine (port 8091) |
| 테스트 | `src/test/.../service/PartnerAuthServiceTest.java` | 13 단위 테스트 (legacy 비즈니스 보존 검증) |
| 테스트 | `src/test/.../it/AbstractPostgresIT.java` | Singleton-container 패턴 (PR #13 회고) |
| 테스트 | `src/test/.../it/PartnerAuthControllerIT.java` | 10 IT (7 endpoint × happy/edge, @MockBean DcConfigClient + SmsClient) |
| 보고서 | `docs/dev-reports/migration-be-m2-partner-auth-service.md` | 본 파일 |

총: 27 신규 파일 + 2 수정 (settings.gradle / build.gradle).

## 3. 설계서 §1~§11 → 코드 매핑 검증

| 설계서 절 | 결정 항목 | 본 PR 의 구현 위치 |
|---|---|---|
| §3 endpoint 1 | `GET /partner-status?bizNo` 8 status 응답 | `PartnerAuthController.partnerStatus` + `PartnerAuthService.checkStatus` |
| §3 endpoint 2 | `POST /partner-register` 201 PENDING / 409 | `partnerRegister` + `register` (CONFLICT on dup) |
| §3 endpoint 3 | `PATCH /partner-password` OK / USED_PW | `partnerPassword` + `setPassword` (5건 FIFO 검사) |
| §3 endpoint 4 | `POST /partner-login` status + token + config | `partnerLogin` + `tryLogin` (M3 RPC + JTI 발급) |
| §3 endpoint 5 | `POST /partner-temp-password` 202 + sms 큐잉 | `partnerTempPassword` + `issueTempPassword` (마스킹) |
| §3 endpoint 6 | `GET /partner-expiration?bizNo` 30일 슬라이딩 | `partnerExpiration` + `getExpiration` |
| §3 endpoint 7 | `PATCH /partner-tutorial` PC/MOBILE | `partnerTutorial` + `updateTutorial` |
| §4 schema | 3 table + jsonb history + UK biz_no | `V1__init_partner_auth.sql` |
| §5 SecurityConfig | DelegatingPasswordEncoder + permit | `config/SecurityConfig.java` |
| §6 외부 client | DcConfigClient + SmsClient | `client/` 패키지 (둘 다 stub) |

## 4. legacy 비즈니스 로직 보존 (Code.js 매핑)

| 규칙 | Code.js 위치 | 본 PR 의 구현 위치 | 검증 단위 테스트 |
|---|---|---|---|
| 3회 연속 실패 → LOCKED | `Code.js:2847` | `PartnerAuth.markLoginFailure` (FAIL_LOCK_THRESHOLD=3) | `login_3회_실패_시_LOCKED` ✓ |
| 30일 미사용 → LONG_UNUSED | `Code.js:2957` | `PartnerAuth.expirationAt` + `PartnerAuthService.evaluateEffectiveStatus` | `login_30일_미사용_시_LONG_UNUSED` ✓ |
| password_history 5건 FIFO | (legacy 보존) | `PartnerAuth.changePassword` ArrayDeque + `setPassword` matches loop | `setPassword_history_5건_재사용_차단` ✓ |
| BCrypt + SHA-256 호환 | DelegatingPasswordEncoder | `SecurityConfig.passwordEncoder` | `password_encoder_BCrypt_및_legacy_SHA256_호환` ✓ |

## 5. 빌드 / 테스트 결과

| 명령 | 결과 |
|---|---|
| `./gradlew :services:partner-auth-service:compileJava` | BUILD SUCCESSFUL (15s) |
| `./gradlew :services:partner-auth-service:compileTestJava` | BUILD SUCCESSFUL (5s) |
| `./gradlew :services:partner-auth-service:assemble` | BUILD SUCCESSFUL (3s) — `partner-auth-service.jar` 생성 |
| `./gradlew :services:partner-auth-service:test --tests PartnerAuthServiceTest` | 13/13 passed (단위) |
| `./gradlew :services:partner-auth-service:test` (전체) | 13 passed + 10 IT skipped (Docker 미가용 환경 — `feedback_testcontainers_windows_docker.md` 가드) |
| `./gradlew assemble` (full project) | BUILD SUCCESSFUL (10s) — partner-auth + 9 기존 서비스 동시 빌드 |

## 6. 가드 적용 요약

| 가드 | 적용 |
|---|---|
| Layer 1 BACKEND 컴파일 | ✓ assemble + compileTestJava 모두 성공 |
| Layer 2 QA IT | ✓ 10 IT (PartnerAuthControllerIT) 작성 — Docker 가용 환경 자동 실행 |
| Layer 4 도메인 메서드 의미 정렬 | ✓ PartnerAuth/Service 한국어 Javadoc + Code.js 출처 라인 명시 |
| Layer 5 schema validation | ✓ ddl-auto=validate, V1 SQL ↔ entity 1:1 매핑 + BaseEntity 7 audit fields |
| `feedback_function_documentation.md` (3-layer) | (1) 한국어 Javadoc 모든 service/entity/controller, (2) springdoc-openapi 자동 생성 활성, (3) 본 보고서 |
| `feedback_uuid_no_user_visibility.md` | ✓ 모든 응답이 bizNo / partnerCode 만 노출, internal id (UUID) 미노출 |
| `feedback_korean_path_jdk.md` | worktree 영문 path → assemble + 단위 test 모두 성공 |
| `feedback_testcontainers_windows_docker.md` | ✓ AbstractPostgresIT DockerAvailableCondition 활용, IT 는 Docker 가용 시만 실행 |
| `feedback_it_mockbean_external_clients.md` | ✓ DcConfigClient + SmsClient 모두 `@MockBean` 격리 + lenient default |
| `feedback_korean_commits.md` | ✓ commit + PR + dev-reports 모두 한국어 |
| `feedback_role_naming_full.md` | ✓ 본 PR 은 role 미언급 (M2 W2 단계는 role 분기 없음) |
| `project_build_conventions.md` (BaseEntity 7) | ✓ V1 SQL 의 모든 3 table 이 7 audit columns 포함 |
| `project_domain_strategy.md` | port 8091 (samhan-air.com 의 internal MSA — public 도메인 미할당) |

## 7. 후속 작업

### W3 (정식 JWT + Feign 정식 구현)
- shared:common 의 JwtTokenProvider 외 별도 partner-token claim 추가 (jti UNIQUE 검증 filter)
- DcConfigClient → Eureka `lb://dc-config-service` + 실 endpoint mapping
- SecurityConfig 정정 — partner-status / partner-register / partner-temp-password 만 permit, 나머지는 partner-JWT 인증 필요

### W4 (legacy 시드 batch)
- 기존 거래처 ↔ partner_auth row 1:1 시드 (legacy SHA-256 password → `{sha256}` prefix 시드, 첫 로그인 시 BCrypt 재인코딩)
- `--seed.dry-run=true` 모드 + report 생성 (m1a 패턴 따름)

### W5 (IT 풀 + mock 제거)
- Docker 가용 CI runner 에서 IT 풀 실행 (현 PR 은 단위 테스트 + IT skeleton)
- DcConfigClient mock 제거 — M3 dc-config-service 컨테이너 통합 IT
- SmsClient mock 제거 — Phase 7 sms-service 구축 후

## 8. 미결 / 모호 항목

1. **설계 문서 부재**: 작업 지시에 `docs/migration/phase6/M2-partner-auth-service.md` 가 referenced 되었으나 worktree base (`origin/main`) 에 해당 파일이 없었다. 본 구현은 작업 지시 본문의 §3~§7 (7 endpoint 명세 + 핵심 비즈니스 규칙 + application.yml) 을 단일 spec source 로 사용했다. 설계 문서가 추후 추가되면 §3 endpoint 응답 contract 의 nested config 구조 정정 PR 필요.
2. **PartnerStatus 10개 vs 8개**: 작업 지시는 "10 status enum" 으로 enum 정의, "8 status enum 응답" 으로 CheckAuthStatusResponse 명시 — 본 PR 은 enum 10개 정의 (entity-only 인 OK 포함). 응답으로 OK 가 나가지 않음을 service 의 evaluateEffectiveStatus 분기로 보장 (login response 에서만 OK 사용).
3. **DcConfig RPC contract**: M3 미구축 시점이라 `PartnerConfigDto` 는 placeholder (partnerName / representativeName / mobileNo / allowedFeatures / options Map). M3 구축 후 W3 에서 DTO 1:1 align 필요.
4. **임시 비밀번호 정책**: 길이 10 (대문자/숫자 confusable 제외 32자 알파벳). legacy 와 동일 여부 미확인 — 후속 W4 시드 배치 시점에 legacy Code.js 와 cross-check.

## 9. CI / PR 후속

- `feature/migration-be-m2-partner-auth-service` push + PR 발행 → `gh pr checks --watch` 자동 모니터링 (memory `feedback_pr_ci_monitoring.md`)
- 한국어 PR 본문 (memory `feedback_korean_commits.md`) — 이름: `.pr-body-be-m2-partner-auth.md` (gitignored)
- 머지 후 후속: W3 BE PR (정식 JWT + Feign) → W4 시드 batch → W5 IT 풀
