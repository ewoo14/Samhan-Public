# Phase 9 W5 — 회고 + Phase 10 진입 plan + 잔존 backlog 1건 흡수 dev-report

본 dev-report 는 Phase 9 마지막 슬라이스 (W5) 의 산출, 결정, 가드, 검증 결과를 정리한다. Phase 9 종합 회고 (`docs/dev-reports/phase9-retrospective.md`) 와 Phase 10 진입 plan (`docs/migration/phase10/M-PHASE-10-readiness.md`) 두 신규 문서를 본 PR 에서 동시 발행하며, 잔존 backlog 1건 (BE 의견 3 — partner-service findByCodes bulk endpoint, D-P9-16) 을 본 PR 에서 채택한다.

---

## 1. 슬라이스 개요

| 항목 | 값 |
|---|---|
| 슬라이스 | Phase 9 W5 (5차, 마지막) |
| Service | partner-service (W1) + dashboard-service (W4) — 2 service 변경 |
| Branch | `feature/integrated-phase-9-step-5-retrospective-and-phase10` |
| Base | `main` (HEAD `b2f38ea` Merge PR #94) |
| 외부 의존 | 신규 0 (기존 4 client 유지, partner-service 측 endpoint 1건 추가 + dashboard 측 method 1건 추가) |
| 코드 변경 file | partner-service 4 (controller / service / repository / IT) + dashboard-service 3 (PartnerClient / PartnerCodeResolver / 신규 단위 Test) |
| 신규 도메인 | 0 (기존 PartnerInternalController + PartnerService + PartnerRepository 보강) |

---

## 2. 잔존 backlog 1건 채택 (D-P9-16)

### 2-1. 출처 — PR #94 dev-report

PR #94 W4 dev-report § Phase 10 cutover 약속 = "**PartnerClient.findByCodes(List<String>) bulk endpoint** — `DashboardAdminController.salesAggregate` 의 partner 정보 lookup 시 N 회 직렬 RPC 회피용. partner-service 측 `POST /internal/partners/find-by-codes` endpoint 추가."

W4 시점에는 1건 잔존 (`feedback_integrated_pr_pattern.md` § fix 후속 PR/Phase 위임 금지 가드 사용자 명시 적용 후 11건은 본 PR 채택). W5 본 PR 에서 채택.

### 2-2. partner-service 변경

#### 2-2-1. 신규 endpoint

- `POST /internal/partners/find-by-codes` — partnerCode N건 동시 조회
- 입력 = JSON 배열 (`["P-2026-0001","P-2026-0002"]`)
- 응답 = 매칭된 PartnerInternalResponse 리스트 (미존재 코드는 누락)
- 인증 = X-Internal-Token + ROLE_MASTER 일관 (단건 lookup 패턴 그대로)
- CSRF disabled / stateless (SecurityConfig 일관)

#### 2-2-2. PartnerService.findByCodes(Collection<String>)

- 빈/null 입력 short-circuit (DB 조회 회피)
- distinct 정규화 (Set<String>) — 입력 중복 제거 후 1회 IN 절 query
- blank/null 항목 제거
- @Transactional(readOnly = true)
- UUID 비공개 가드 — 응답 DTO 자체는 partnerId 보유 (internal endpoint 한정 노출), 호출 측이 사용자 응답에 첨부 금지

#### 2-2-3. PartnerRepository.findAllByPartnerCodeIn(Collection<String>)

- Spring Data JPA 자동 query 생성 (IN 절)
- @SQLRestriction("is_deleted = false") 가드 자동 적용 (Soft Delete 일관)

#### 2-2-4. IT 추가 4건

| Test | 검증 |
|---|---|
| `find_by_codes_with_valid_token_returns_matched_partners` | 정상 응답 — 2 fixture 동시 조회 시 2건 매칭 |
| `find_by_codes_with_empty_list_returns_200_empty` | 빈 배열 → 200 + 빈 리스트 (DB 조회 회피) |
| `find_by_codes_with_partial_missing_returns_only_existing` | 일부 미존재 코드 → 매칭만 응답 |
| `find_by_codes_without_internal_token_returns_403` | 토큰 누락 → 403 (Spring Security AccessDeniedException) |

총 partner-service IT = 기존 4 + 신규 4 = **8 case**.

### 2-3. dashboard-service 변경

#### 2-3-1. PartnerClient.findByCodes(List<String>)

- partner-service `POST /internal/partners/find-by-codes` 호출
- skeleton-mode 토글 일관 (`samhan.dashboard.client.skeleton-mode=true` default 시 외부 호출 회피 + 빈 리스트 반환)
- ApiResponse wrapper `data` 배열 → `PartnerSummary` record 리스트 파싱
- fail-soft — RestClientResponseException / 일반 Exception 모두 빈 리스트 반환 (단건 lookup 의 Optional.empty 일관)

#### 2-3-2. PartnerCodeResolver.resolveAll(List<String>)

- cache hit / miss 분리 — Caffeine 캐시 직접 조회 (Spring Cache 가 자동 unwrap 한 UUID 또는 Optional<UUID> 모두 안전 정규화)
- miss 만 `partnerClient.findByCodes(miss)` 1회 bulk RPC
- 응답을 단건 form (`cache.put(code, partnerId)`) 으로 cache 적재 (W5 후속 fix BE-2 채택 — Spring Cache 단건 `@Cacheable` unwrap 결과와 wrapper 형태 일관)
- 빈/null 입력 short-circuit
- 미존재 partnerCode 는 결과 Map 에 누락 (호출 측이 Map containsKey 분기)

> **인프라 선제 도입 (W5)**: `resolveAll(List<String>)` 의 실 호출자 (`DashboardAdminController.salesAggregate`) 는 W5 시점 단건 partnerCode 입력 유지. fan-out consumer 전환 (예: 매출 집계 응답에 partner 정보 batch 첨부) = Phase 10 또는 W6+ 시점 도입. 본 PR 의 bulk endpoint + resolveAll 메서드는 운영 진입 전 인프라 보강 (W5 reviewer BE 의견 1 채택).

#### 2-3-3. PartnerCodeResolverTest 신규 4 case

| Test | 검증 |
|---|---|
| `resolveAll_with_empty_list_returns_empty_map_and_skips_client` | 빈 리스트 → 빈 Map + client 호출 0 |
| `resolveAll_with_all_miss_calls_bulk_client_once_and_populates_cache` | 전체 miss → 1회 bulk + 재호출 시 cache hit (client 호출 0회) |
| `resolveAll_separates_hit_and_miss_calling_client_only_for_miss` | hit (사전 적재) + miss → miss 만 client |
| `resolveAll_with_partial_missing_returns_only_matched_codes` | 일부 미존재 → 매칭 항목만 결과 |

총 dashboard-service 단위 = 기존 17 + 신규 4 = **21 case**.

---

## 3. Phase 9 회고 보고서 신규

`docs/dev-reports/phase9-retrospective.md` 신규 (10 섹션):

1. Phase 9 요약 (5 슬라이스 W1~W5)
2. 산출 통계 (누적 매트릭스)
3. 핵심 결정 D-P9-02 ~ D-P9-20 (19건)
4. 누적 backlog 채택 결과
5. 핵심 회고 (성공 + 학습)
6. Phase 10 진입 준비 상태
7. Phase 10 진입 plan 요약
8. 잔존 backlog (본 PR 흡수 1건 + Phase 10 위임 N건)
9. 관련 PR + 문서
10. 마무리 메시지

---

## 4. Phase 10 진입 plan 신규

`docs/migration/phase10/M-PHASE-10-readiness.md` 신규 (6 섹션):

1. 진입 조건 (Phase 9 완료 시점)
2. Phase 10 작업 분해 (P10-1 ~ P10-3 권장)
3. 가드 / 학습 적용 (Phase 9 회고 도출)
4. 일정 / 마일스톤
5. roll-back 절차
6. 참조

작업 분해:
- **P10-1**: AWS Secrets Manager + Caffeine → Redis 전환 + ShedLock cluster
- **P10-2**: aws-cloud-map provider 활성 + Resilience4j (4 client + adapter)
- **P10-3**: Aurora PostgreSQL migration + Cutover dry-run 3단계 + DNS 8 subdomain

---

## 5. DECISIONS D-P9-16 ~ D-P9-20 신규

| ID | 결정 |
|---|---|
| D-P9-16 | partner-service findByCodes bulk endpoint + dashboard PartnerCodeResolver bulk 전환 (W4 BE 의견 3 채택) |
| D-P9-17 | slip-service 시간 의존 design fix (LocalDate.now()) — main 도 영향 받았을 회귀 사전 예방 |
| D-P9-18 | 사용자 가드 적용 (`feedback_integrated_pr_pattern.md` § fix 후속 PR/Phase 위임 금지) |
| D-P9-19 | Phase 10 진입 준비 완료 — AWS migration cutover plan 채택 |
| D-P9-20 | Phase 9 회고 종합 + Phase 10 시점 결정 |

---

## 6. 가드 일관 적용 (W5 본 PR 검증)

- BaseEntity 7 audit + Soft Delete (PartnerRepository.findAllByPartnerCodeIn 자동 적용)
- VARCHAR(N) only (DB 컬럼 추가 0 — 기존 schema 그대로)
- UUID 비공개 — 응답 DTO partnerCode 만 사용자 노출, partnerId 는 internal endpoint 한정
- 한국어 Javadoc + dev-report (본 문서) + springdoc-openapi (`@Operation` / `@ApiResponses`)
- IT 외부 client `@MockBean` 격리 — 본 PR 신규 IT 는 partner-service self-contained (외부 client 0)
- AbstractPostgresIT + Docker skip (한글 path 회피)
- gradlew exec bit 보존
- InternalTokenFilter `/internal/**` prefix 한정 (PR #91 fix 일관)
- prod + dev 기본 토큰 부팅 거부 가드
- 한국어 commit + dev-report
- chained-default 환경변수 (변경 없음 — 기존 W1/W4 표준 일관)
- **사용자 가드 적용** — 잔존 backlog 1건 본 PR 채택, Phase 10 위임 X
- **slip-service 시간 의존 fix grep 가드** — 다른 service test 에 `LocalDate.of(2026, 5, 5)` 기반 만료 비교 패턴 없음 검증 (단순 fixture 데이터는 회귀 영향 없음)

---

## 7. 테스트 결과 (회귀 0)

```
./gradlew assemble                                                       # PASS (전체 14 service)
./gradlew :services:partner-service:test :services:dashboard-service:test  # PASS
```

| 영역 | 단위 | IT |
|---|---|---|
| partner-service | 8 case (PartnerServiceTest, PartnerDomainTest 등) | 8 case (Internal 8 + Admin 5 = 13 case, 본 PR +4) |
| dashboard-service | 21 case (KPI 6 + Stock 4 + Sales 5 + Refresh 2 + PartnerCodeResolver 4) | 9 case (Internal 4 + Admin 5) |
| 회귀 검증 | 12 + 16 + 17 = 45 + 본 PR +4 = **49 단위 PASS** | 4 + 4 + 9 + 9 = **26 IT** (Docker skip / Linux CI PASS 예정) |

---

## 8. 후속 backlog (Phase 10 위임)

본 PR 시점 잔존 backlog 0건 (사용자 가드 일관 적용). Phase 10 위임 항목은 `docs/dev-reports/phase9-retrospective.md` § 8-2 참조.

---

## 9. 관련 문서

- `docs/dev-reports/phase9-retrospective.md` — Phase 9 종합 회고 (본 PR 신규)
- `docs/migration/phase10/M-PHASE-10-readiness.md` — Phase 10 진입 plan (본 PR 신규)
- `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md` — Phase 8 도입 (14 section dry-run plan)
- `migration/decisions/DECISIONS.md` D-P9-16 ~ D-P9-20 (본 PR 추가)
- `services/partner-service/README.md` (W5 findByCodes 섹션 추가)
- `services/dashboard-service/README.md` (W5 PartnerCodeResolver bulk 섹션 추가)
- `docs/migration/phase9/M-PHASE-9-readiness.md` (W5 = 완료 + Phase 9 종합 = 완료 표기)
