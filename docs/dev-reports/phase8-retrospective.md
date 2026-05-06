# Phase 8 회고 보고서

## 1. 개요

Phase 8 (AWS 호환성 가드) 의 모든 슬라이스가 머지를 완료하여,
본 보고서로 슬라이스별 산출, 학습 매트릭스, Phase 9 / Phase 10 위임 항목을 정리한다.

- 시작 commit (Phase 7 종료 후 첫 머지): PR #88 (Phase 8 1차)
- 종료 commit: 본 PR (Phase 8 3차 — 마무리)
- 총 머지 PR 수: 3건 (#88 / #89 / 본 PR)

## 2. Phase 8 머지된 PR

| PR | 차수 | 제목/요지 | 주요 산출물 |
|---|---|---|---|
| #88 | 1차 | AWS 호환성 가드 plan + 12-factor 검증 + 환경변수 표준 + ROADMAP/DECISIONS | docs only (5 file), 12-factor 12/12 OK, RDS 호환 22 file 검증 |
| #89 | 2차 | ServiceDiscoveryClient interface (Eureka + AWS placeholder) + 환경변수 통일 chained-default + Secrets Manager spec | shared:discovery-abstraction 신규 + 10 yml chained-default + Secrets spec |
| 본 PR | 3차 | AWS 마이그레이션 dry-run + 회고 + Phase 9 진입 plan | docs only (4 file), Phase 8 마무리 + Phase 9 진입 가능 선언 |

## 3. 학습 사항

### 3.1 vendor 추상화 layer 신규 + impl placeholder 패턴

| 학습 | 트리거 | 적용 |
|---|---|---|
| vendor lock-in 회피 = 추상화 interface + 다중 impl | Phase 8 D-P8-05 호환성 가드 의무 | `ServiceDiscoveryClient` interface 4 operation |
| Phase 시점 분리 = `UnsupportedOperationException("Phase 10 cutover 시점 구현")` | AWS 리소스 미생성 + AWS SDK 의존 회피 | `AwsCloudMapServiceDiscoveryClient` placeholder |
| impl 토글 = `@ConditionalOnProperty` + `matchIfMissing = true` | 기존 운영 (Eureka) default 보존 | `samhan.discovery.provider=eureka` default |
| 신규 모듈 의존성 추가 시점 분리 | 14 service 변경 0 + Phase 10 위임 | `shared:discovery-abstraction` 본 PR 시점 단독 보유 |

### 3.2 chained-default 환경변수 (`${SAMHAN_NEW:${LEGACY:default}}`) 무중단 호환

| 학습 | 트리거 | 적용 |
|---|---|---|
| 신규 표준 + legacy 호환 100% 보존 | Phase 8 1차 검출 불일치 3건 (`INTERNAL_AUTH_TOKEN` vs `INTERNAL_TOKEN` 등) | yml chained-default 패턴 의무 |
| 표준 일관성 = `SAMHAN_<SERVICE>_<KEY>` prefix | 12 service 환경변수 grep 결과 7가지 변형 발견 | `SAMHAN_INTERNAL_TOKEN` / `SAMHAN_JWT_SECRET` / `SAMHAN_<SERVICE>_SERVICE_URL` 표준 |
| 모든 service `infrastructure/env-templates/<service>.env` 의무 | template 부재 시 신규 환경 부트스트랩 시간 손실 | 12/12 service template 보유 (10 신규 + 2 갱신) |
| Java 코드 변경 0 = yml level 표준화만 | `@ConfigurationProperties` 바인딩 / Guard 코드 안정성 보존 | yml + env-template 만 변경 |

### 3.3 GitGuardian 회고 — 신규 env template `CHANGE_ME_LOCAL_ONLY` placeholder 의무

| 학습 | 트리거 | 적용 |
|---|---|---|
| 신규 env template 의 placeholder = `CHANGE_ME_LOCAL_ONLY` 의무 | GitGuardian 패턴 정리 (Phase 6) 일관 적용 | 12 env-template 모두 placeholder 사용 |
| fixture 키 이름 = test-only naming | Testcontainers default password 회피 | `samhan-test-token` / `dummy-jwt-secret-not-real` |
| historic commit 도 GitGuardian 검사 대상 | PR #76 1차 GG fail 회고 | sub 별 단일 commit 권장 (`git merge --squash` x N) |

### 3.4 Eureka 자체 EC2 운영 = AWS Cloud Map wrapper 회피 결정

| 학습 | 트리거 | 적용 |
|---|---|---|
| AWS managed service 채택 vs 자체 운영 비교 | Phase 8 1차 doc 의 "Eureka 자체 EC2 운영 권장" | Phase 10 dry-run section 4 = Eureka multi-AZ 2 노드 |
| 추상화 layer 는 보유, 활성은 보류 | vendor lock-in 회피 + 운영 단순성 양립 | wrapper 보유 + provider toggle 만 활용 |
| Phase 11 또는 운영 부담 임계 도달 시점에 활성 결정 | 현재 Eureka 운영 부담 작음 | placeholder 그대로 (Phase 10 활성 X) |

## 4. 미결 (Phase 10 위임)

| ID | 영역 | 항목 | 위임 사유 |
|---|---|---|---|
| P10-BE-01 | BE | DiscoveryConfiguration auto-registration imports 등록 | 14 service 의존성 추가 시점 = Phase 10 cutover |
| P10-BE-02 | BE | EurekaServiceDiscoveryClient.lookup null-safety 보강 | EurekaClient 응답 contract 검증 = Phase 10 통합 IT 시점 |
| P10-QA-01 | QA | chained-default 동시 설정 silent override → WARN log | 운영 환경 설정 일관성 가드 = production 진입 후 |
| P10-QA-02 | QA | AWS Cloud Map Spring context IT | AWS 리소스 + AWS SDK 의존 = Phase 10 |
| P10-QA-03 | QA | Secrets rotation testSecret 실패 rollback 절차 | 실 lambda + Secrets Manager 활성 후 = Phase 10 |
| P10-DEVOPS-01 | DevOps | ECS Fargate `server.shutdown=graceful` | ECS task definition = Phase 10 |
| P10-DEVOPS-02 | DevOps | RDS / EC2 / S3 / Route 53 리소스 생성 | AWS account 발급 후 |
| P10-DEVOPS-03 | DevOps | Secrets Manager / Parameter Store 도입 | spec → 실 lambda = Phase 10 |
| P10-DEVOPS-04 | DevOps | AWS WAF / Managed Prometheus / Managed Grafana | 운영 안정화 = Phase 10 |

## 5. 미결 (Phase 9 위임)

| ID | 영역 | 항목 | 위임 사유 |
|---|---|---|---|
| P9-FE-01 | FE | frontend 5 client prefix 표준 (`VITE_*` / `EXPO_PUBLIC_*`) | 본 PR 시점 backend 환경변수만 표준화 |
| P9-FE-02 | FE | frontend `.env.example` 가드 | 본 PR 시점 backend env-template 만 정착 |
| P9-FE-03 | FE | AWS mobile/desktop 배포 매핑 | mobile 배포 = AWS 마이그 시점 (Phase 10) 인접 |
| P9-BE-01 | BE | partner-service / groupware-service / notification-service / dashboard-service skeleton | Phase 9 4 service 신규 |
| P9-BE-02 | BE | 신규 service 모두 ServiceDiscoveryClient 도입 (Phase 10 활성 대비) | Phase 8 wrapper 보유 → Phase 9 신규 service 의무 적용 |

## 6. 산출물 누적 (Phase 8 종료 시점)

### 6-1. docs (Phase 8 신규/갱신)

| 파일 | 차수 | 내용 |
|---|---|---|
| `docs/migration/phase8/M-PHASE-8-readiness.md` | 1차 | 8 작업 plan + 호스팅 옵션 비교 |
| `docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md` | 1차 | 12-factor 검증 + RDS 호환 + AWS 매핑 표 |
| `docs/migration/phase8/M-ENV-STANDARDIZATION.md` | 1차 | 환경변수 표준 + secrets/config 분리 |
| `docs/migration/phase8/M-SECRETS-ROTATION-spec.md` | 2차 | Secrets Manager rotation spec |
| `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md` | 3차 (본 PR) | Phase 10 cutover 전 dry-run plan (14 section) |
| `docs/migration/phase9/M-PHASE-9-readiness.md` | 3차 (본 PR) | Phase 9 진입 plan + 4 service skeleton |
| `docs/dev-reports/phase8-step-1-aws-readiness.md` | 1차 | 1차 dev report |
| `docs/dev-reports/phase8-step-2-discovery-secrets.md` | 2차 | 2차 dev report |
| `docs/dev-reports/phase8-retrospective.md` | 3차 (본 PR) | 본 회고 |
| `docs/dev-reports/phase8-step-3-completion-phase-9-readiness.md` | 3차 (본 PR) | 3차 dev report |

### 6-2. code (Phase 8 신규)

| 모듈 | 차수 | 내용 |
|---|---|---|
| `shared/discovery-abstraction/` | 2차 | ServiceDiscoveryClient interface + 2 impl + 단위 테스트 13 case |
| `infrastructure/env-templates/<service>.env` | 2차 | 12/12 service env template (10 신규 + 2 갱신) |
| service yml chained-default 패턴 | 2차 | 10 yml 적용 (eureka-server / logging-service 제외) |

## 7. Phase 9 진입 가능 선언

- Phase 8 1차 + 2차 + 3차 (본 PR) 모두 머지
- 호환성 가드 검증 완료 (AWS 마이그레이션 가능성 보존)
- 14 service 환경변수 통일 (chained-default)
- ServiceDiscoveryClient wrapper 보유 (Phase 10 활성 대기)
- Phase 9 plan = `docs/migration/phase9/M-PHASE-9-readiness.md`

## 8. 참조

- Phase 8 ROADMAP: `ROADMAP.md` Phase 8 섹션
- 누적 결정: `migration/decisions/DECISIONS.md` (D-P8-01 ~ D-P8-11)
- Phase 9 진입 plan: `docs/migration/phase9/M-PHASE-9-readiness.md`
- Phase 10 dry-run plan: `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`
