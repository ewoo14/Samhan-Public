# M-PHASE-10-readiness — Phase 10 진입 plan (AWS migration cutover)

본 문서는 Phase 9 완료 시점에서 Phase 10 (AWS 마이그레이션 + Migration Service + 운영 안정화) 진입을 위한 전제 조건, 작업 분해, 슬라이스 roadmap, 가드를 정리한다. Phase 9 회고 (`docs/dev-reports/phase9-retrospective.md`) + Phase 10 dry-run plan (`docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`) 과 짝을 이룬다.

---

## 1. 진입 조건 (Phase 9 완료 시점)

| 항목 | 상태 | 산출 |
|---|---|---|
| 14 service skeleton 완료 (Phase 0~9) | OK | 14 service + migration (8096) Phase 10 신규 예정 |
| ServiceDiscoveryClient 추상화 + 4 service 소비자 | OK | partner / groupware / notification / dashboard, `samhan.discovery.provider` toggle |
| Caffeine vs Redis 결정 | OK | D-P9-12, `samhan.cache.provider=caffeine\|redis` |
| Materialized view + ShedLock multi-instance race 가드 | OK | D-P9-13 + W4 후속 fix `445a1a0` (ShedLock 5.13.0 + Flyway V2) |
| AWS RDS 호환 Postgres 표준 SQL | OK | Phase 8 22 file 검증 + Phase 9 Flyway V1 (4 service) 일관 |
| 12-factor + chained-default 환경변수 | OK | D-P8-07, 14 service env-template + chained-default fallback |
| QA 캡처 + raw URL HEAD 가드 | OK | Phase 9 W3 회고로 강화 |
| 사용자 가드 (fix 본 PR 채택) | OK | `feedback_integrated_pr_pattern.md` § fix 후속 PR/Phase 위임 금지 (W4+W5 정착) |
| partner findByCodes bulk endpoint | OK | W5 본 PR (D-P9-16) — fan-out 직렬 RPC 회피 backing |
| Secrets Manager rotation lambda spec | 보유 | Phase 8 spec 보유, Phase 10 P10-1 시점 실 배포 |
| AWS account + IAM baseline | 대기 | Phase 10 진입 시점 사용자 발급 |

---

## 2. Phase 10 작업 분해 (3 슬라이스 P10-1 ~ P10-3 권장)

> 슬라이스 분할 근거 — Phase 9 의 5 슬라이스 패턴 (W1~W5, 1 슬라이스 = 1 통합 PR) 을 Phase 10 에 일관 적용. 각 슬라이스는 single-domain cutover 로 회귀 위험 최소화 + roll-back 단위 명확화.

### 2-1. P10-1: Secrets Manager + Cache 전환 (1 통합 PR)

**범위**:
- AWS Secrets Manager 도입 (`spring.config.import: aws-secretsmanager:samhan/<env>/...`)
- `samhan.secrets.provider=aws-secretsmanager` toggle 활성 (Phase 8 spec → 실 lambda 배포)
- Caffeine → Redis 전환 (`samhan.cache.provider=redis`) — multi-instance scaling 시점
- ShedLock cluster (`shedlock` 테이블 RDS 공유) — W4 도입 ShedLock 5.13.0 기반
- 14 service `application.yml` `spring.config.import` 일괄 추가 (chained-default 보존)

**산출**:
- 14 service `spring.config.import` 추가 + 환경변수 표준 보강
- Redis cache config (`shared:cache-abstraction` 신규 모듈 또는 service 별 RedisCacheConfig)
- Secrets rotation lambda 배포 + IAM role
- env-template 14건 갱신 (`SAMHAN_SECRETS_PROVIDER` / `SAMHAN_REDIS_*`)
- dev-report + DECISIONS D-P10-01~05

**진입 조건**:
- AWS account 발급 + IAM baseline 정의
- Secrets Manager 인스턴스 1개 + Redis cluster (ElastiCache) 1개 사전 준비

### 2-2. P10-2: Discovery + Resilience (1 통합 PR)

**범위**:
- `aws-cloud-map` provider 활성 (`shared:discovery-abstraction` placeholder impl → 실 SDK)
- 4 service (partner / groupware / notification / dashboard) discovery toggle = aws-cloud-map
- Resilience4j 4 client + adapter (Aligo / FCM / SES) — timeout / circuit breaker / retry
- Eureka cluster (자체 EC2 multi-AZ 2 노드) 또는 aws-cloud-map 단독 결정

**산출**:
- `shared:discovery-abstraction` AwsCloudMapServiceDiscoveryClient 실 SDK 구현
- Resilience4j 의존성 추가 + `@CircuitBreaker` / `@Retry` / `@TimeLimiter`
- env-template 갱신 (`SAMHAN_DISCOVERY_PROVIDER=aws-cloud-map` + Resilience4j 임계치)
- dev-report + DECISIONS D-P10-06~10

**진입 조건**:
- P10-1 머지 + 14 service Secrets Manager / Redis 전환 PASS
- AWS Cloud Map namespace 사전 등록

### 2-3. P10-3: RDS migration + Cutover (1 통합 PR)

**범위**:
- Aurora PostgreSQL 16 cluster (multi-AZ) 1대 + 14 schema 분리
- 14 service `SPRING_DATASOURCE_URL` Aurora endpoint 로 override (Secrets Manager 경유)
- Cutover dry-run 3단계 — staging Flyway PASS / target group health / DNS TTL 60s
- 8 subdomain 점진 cutover (10 → 50 → 100%)
- Migration service (8096) ECount 일괄 이관 첫 슬라이스 (별도 슬라이스 분할 가능)

**산출**:
- Aurora cluster 생성 + 14 schema seed
- 14 service Flyway V1~VN baseline 자동 적용 검증
- ALB target group + WAF + Route 53 (8 subdomain)
- CloudWatch alarm 5건 (5xx / 응답시간 / RDS CPU / DB connection / Disk)
- dev-report + DECISIONS D-P10-11~15

**진입 조건**:
- P10-1 + P10-2 머지
- Aurora cluster 사전 생성 + staging RDS dry-run (M-AWS-MIGRATION-DRY-RUN.md 14 section) PASS

---

## 3. 가드 / 학습 적용 (Phase 9 회고 도출)

| 가드 | 출처 | Phase 10 적용 |
|---|---|---|
| 사용자 가드 (fix 본 PR 채택) | W4+W5 정착 | 모든 슬라이스 일관 — backlog 누적 금지 |
| 5 reviewer + TM 종합 | W1~W4 일관 | P10-1/2/3 모두 BE/FE/Designer/QA/DevOps 토론 |
| 임시 브랜치 push 회피 | W2 회고 | 정공법 PR 브랜치 직접 작업 |
| QA 캡처 + raw URL HEAD pin | W3 회고 | 모든 PR 본문 raw URL 머지 후 재검증 |
| 단편 PR / 단독 PR 회피 | Phase 7 학습 | TM 자체 1 통합 PR |
| 한국어 commit + Javadoc + dev-report | Phase 6 표준 | 일관 |
| BaseEntity 7 audit + Soft Delete | 영구 가드 | 일관 |
| VARCHAR(N) only / NUMERIC(20,4) money | 영구 가드 | 일관 |
| UUID 비공개 (Q-W4-2) | W4 정착 | 사용자 응답 DTO partnerCode 만 |
| Internal token prod 부팅 거부 | Phase 7 도입 | 일관 |
| chained-default 환경변수 (`SAMHAN_*` + `LEGACY_*`) | Phase 8 표준 | 일관 |
| 시간 의존 회피 (`LocalDate.now()`) | W4 후속 fix `cde6db9` | 일관 |
| ShedLock multi-instance race 가드 | W4 후속 fix `445a1a0` | P10-1 cluster 활성 |

---

## 4. 일정 / 마일스톤 (TBD)

> 실 일정은 Phase 9 종료 시점 + AWS account 준비 시점에 따라 결정. 본 § 는 슬라이스 의존 매트릭스만 정리.

| 슬라이스 | 의존 | 예상 기간 | 결정 시점 |
|---|---|---|---|
| P10-1 (Secrets + Cache) | AWS account + Secrets Manager + ElastiCache | 1~2주 | 본 PR 머지 후 |
| P10-2 (Discovery + Resilience) | P10-1 + AWS Cloud Map namespace | 1주 | P10-1 머지 후 |
| P10-3 (RDS + Cutover) | P10-1 + P10-2 + Aurora cluster + ALB + Route 53 | 2~3주 (dry-run 포함) | P10-2 머지 후 |
| Phase 10 마무리 (회고 + 운영 안정화) | P10-3 머지 + 14 service production traffic 안정 | 1주 | P10-3 머지 후 |

---

## 5. roll-back 절차 (Phase 8 D-P8-08 일관)

- **P10-1 roll-back**: `samhan.secrets.provider=local` + `samhan.cache.provider=caffeine` (chained-default fallback)
- **P10-2 roll-back**: `samhan.discovery.provider=eureka` (Eureka cluster 보존) + Resilience4j `@CircuitBreaker` annotation 단계적 제거
- **P10-3 roll-back**: DNS TTL 60s + Route 53 weighted routing → 100% legacy traffic / Aurora 단계적 부팅 / Flyway out-of-order 비활성

---

## 5-1. frontend cutover (P10-1/2/3 통합)

> Phase 9 W5 reviewer FE 의견 1 채택 — frontend client 측 P10 cutover 사전 plan 명시. clients/* 코드 변경 없이 빌드/배포 매트릭스만 정리.

### P10-1 frontend 영역
- **외부 CDN self-host 또는 SPOF 회피 약속**: `clients/web/order-app/index.html` 의 외부 CDN 2건 (kakaocdn / cdnjs cloudflare) → P10-1 진입 시 self-host 또는 Cloudflare Pages 자체 배포 매핑
- **Pretendard 폰트 self-host 정식 도입**: 현재 `clients/web/design-system/src/styles/tokens.css` 시스템 fallback (`font-family: 'Pretendard', system-ui, sans-serif`). P10-1 시점 `clients/web/design-system/public/fonts/` 에 self-host + jsdelivr CDN 회피 (Phase 7 패턴 일관)

### P10-2 frontend 영역
- **VITE_API_BASE_URL build-time toggle 매트릭스**:
  - `dev`: `http://localhost:8080` (gateway)
  - `staging`: AWS staging 환경 + Cloudflare Pages preview URL
  - `prod`: `https://api.samhan-air.com` (samhan-air.com subdomain — Phase 0 Domain Strategy 일관)
- **Cloudflare Pages 환경 분리**: dev / staging / prod 별 `wrangler.toml` 또는 `_routes.json` 분기

### P10-3 frontend 영역
- **production cutover dry-run**: dev → staging → prod 단계적 전환 (frontend client 3종 — desktop / web / mobile)
- **rollback 전략**: Cloudflare Pages 이전 deployment 즉시 rollback + DNS TTL 60s 유지 (cutover 1시간 동안)

### Phase 10 흡수 backlog (Designer / QA / DevOps)

본 § frontend cutover 와 짝을 이루는 추가 backlog (Phase 9 W5 reviewer 식별, 본 PR scope 외):

- **Designer #1 ~ #3** (Phase 10 W1) — ChannelBadge 일관성 / slice accent 토큰 정식화 / QA2 mobile overflow 정정
- **QA Q-P10-1** (Phase 10 plan slice 명시 의무) — skeleton-mode IT sweep / Caffeine→Redis testcontainer / aws-cloud-map mock
- **DevOps #2 / 추가 backlog** — `partner_client_fail_total` Micrometer counter / find-by-codes 호출 사이즈 metric / user-service `DEFAULT_HIRE_DATE` 의도 주석 (Phase 10 운영 진입 후 또는 별도 user-service 슬라이스)

---

## 6. 참조

- Phase 9 회고: `docs/dev-reports/phase9-retrospective.md` (본 PR 신규)
- Phase 10 dry-run: `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md` (Phase 8 도입, 14 section)
- Phase 9 진입 plan: `docs/migration/phase9/M-PHASE-9-readiness.md`
- 환경변수 표준: `docs/migration/phase8/M-ENV-STANDARDIZATION.md`
- AWS 호환성 가드: `docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md`
- Secrets Manager rotation spec: `docs/migration/phase8/M-SECRETS-ROTATION-spec.md` (Phase 8 2차 산출)
- shared:discovery-abstraction: `shared/discovery-abstraction/`
- shared:user-client-abstraction: `shared/user-client-abstraction/` (Phase 9 W4 신규)
- DECISIONS D-P9-02 ~ D-P9-20: `migration/decisions/DECISIONS.md`
