# Phase 10 W10-5 — 회고 dev-report

본 dev-report 는 Phase 10 W10-5 슬라이스 (PR #100 + PR #102 머지 후) 의 로컬 검증 backlog 정리 결과를 정리한다. backend / devops / qa 3 영역으로 분리되며, 본 문서는 **backend (env 변수 prefix 통일 + DB user fallback chain 표준화)** 섹션을 채택한다. devops / qa 섹션은 후속 commit 에서 채워진다.

---

## 1. 슬라이스 개요

| 항목 | 값 |
|---|---|
| 슬라이스 | Phase 10 W10-5 회고 (backend 영역) |
| Branch | `feature/integrated-phase-10-step-5-retrospective` |
| Base | `main` (PR #100 + PR #102 머지 후 HEAD) |
| 변경 service | inventory / slip / user / partner / arologis / dashboard / groupware / notification / dc-config / partner-order — **10 service** |
| 변경 file | services/<10>/src/main/resources/application.yml — **10 파일** |
| 코드 변경 | 없음 (yaml env 변수 wiring 만) |

---

## 2. 배경 — PR #100 + PR #102 로컬 검증 backlog 2건

PR #100 (Phase 10 backend 통합) + PR #102 머지 후 개발책임자 로컬 검증 시점에 다음 2건 backlog 발견:

### 2-1. seed toggle env 변수 prefix 불일치 (10 service)

PR #100 시점 BE 4-team agent 들이 service 별 application.yml 에 seed toggle 을 추가하면서 env 변수 이름 표준이 분기:

| service | 기존 env 변수 (W10-5 이전) | 표준 (W10-5 이후) |
|---|---|---|
| partner-service | `SAMHAN_PARTNER_SEED_TEST_DATA` | (변경 없음 — 표준) |
| product-service | `SAMHAN_PRODUCT_SEED_TEST_DATA` | (변경 없음 — 표준) |
| accounting-service | `SAMHAN_ACCOUNTING_SEED_TEST_DATA` | (변경 없음 — 표준) |
| dashboard-service | `SAMHAN_DASHBOARD_SEED_TEST_DATA` | (변경 없음 — 표준) |
| groupware-service | `SAMHAN_GROUPWARE_SEED_TEST_DATA` | (변경 없음 — 표준) |
| notification-service | `SAMHAN_NOTIFICATION_SEED_TEST_DATA` | (변경 없음 — 표준) |
| arologis-service | `SAMHAN_AROLOGIS_SEED_TEST_DATA` | (변경 없음 — 표준) |
| partner-order-service | `SAMHAN_PARTNER_ORDER_SEED_TEST_DATA` | (변경 없음 — 표준) |
| **inventory-service** | `INVENTORY_SEED_TEST_DATA` (SAMHAN prefix 누락) | `SAMHAN_INVENTORY_SEED_TEST_DATA` (chained-default + legacy fallback) |
| **slip-service** | `SLIP_SEED_TEST_DATA` (SAMHAN prefix 누락) | `SAMHAN_SLIP_SEED_TEST_DATA` (chained-default + legacy fallback) |
| **user-service** | `USER_SEED_ORG` (SAMHAN prefix 누락) | `SAMHAN_USER_SEED_ORG` (chained-default + legacy fallback) |

**표준 통일 규칙**: `SAMHAN_<SERVICE>_SEED_TEST_DATA` (모두 `SAMHAN_` prefix). 이유:
1. DevOps `start-local-full.ps1` 의 chained-default 패턴 (Phase 8 표준) 일관
2. production 침입 차단 명확화 — `SAMHAN_` prefix = 명시적 Samhan Public 환경 (다른 namespace 와 충돌 차단)
3. user-service 의 `seed-org` 도 동일 패턴 적용 (`SAMHAN_USER_SEED_ORG`)

**chained-default fallback** — 기존 변수도 fallback 으로 유지 (운영 외부 wrapper 가 전환 전 안전):

```yaml
seed-test-data: ${SAMHAN_INVENTORY_SEED_TEST_DATA:${INVENTORY_SEED_TEST_DATA:false}}
seed-test-data: ${SAMHAN_SLIP_SEED_TEST_DATA:${SLIP_SEED_TEST_DATA:false}}
seed-org:       ${SAMHAN_USER_SEED_ORG:${USER_SEED_ORG:false}}
```

이미 표준인 8 service 는 본 PR 변경 없음 (env 이름 일치 확인만).

### 2-2. DB user/password placeholder 안전 가드 (7 service)

partner / arologis / dashboard / groupware / notification / dc-config / partner-order 의 DB datasource username/password 가 dev placeholder `CHANGE_ME_LOCAL_ONLY` 를 default 로 두는 패턴. env 미설정 시 PostgreSQL connection refused 발생 (개발책임자 로컬 검증 시점 재현).

**기존 패턴 (W10-5 이전)**:
```yaml
username: ${SAMHAN_PARTNER_DB_USER:${LEGACY_DB_USER:CHANGE_ME_LOCAL_ONLY}}
password: ${SAMHAN_PARTNER_DB_PASSWORD:${LEGACY_DB_PASSWORD:CHANGE_ME_LOCAL_ONLY}}
```

**표준 통일 (W10-5 이후)** — 다른 service (inventory/slip/user/product/accounting) 와 동일한 `samhan` / `samhan_dev_pw` default fallback (chained-default 끝 단계에 `DB_USER` / `DB_PASSWORD` 추가):

```yaml
username: ${SAMHAN_PARTNER_DB_USER:${LEGACY_DB_USER:${DB_USER:samhan}}}
password: ${SAMHAN_PARTNER_DB_PASSWORD:${LEGACY_DB_PASSWORD:${DB_PASSWORD:samhan_dev_pw}}}
```

이유:
- 로컬 docker-compose 와의 호환성 (다른 5 service 와 동일한 default credential `samhan` / `samhan_dev_pw`)
- env 미설정 시 `CHANGE_ME_LOCAL_ONLY` 라는 의미 없는 username 으로 connection 시도 → connection refused (의미 없는 stack trace) 회피
- production 침입 차단은 `SAMHAN_<SERVICE>_DB_USER` 명시적 설정 의무로 보장 (default 가 `samhan` 이라도 production env 변수 override 의무는 동일)

**dc-config / partner-order** — 기존에 chained-default 패턴 자체가 누락 (`${DB_USER:CHANGE_ME_LOCAL_ONLY}` 단일 chain). 본 PR 에서 다른 service 와 동일한 `SAMHAN_<X>_DB_*` → `LEGACY_DB_*` → `DB_*` → `samhan` 4-단계 chained-default 표준으로 통일.

---

## 3. 변경 file 목록 (10 service)

| # | service | application.yml 변경 |
|---|---|---|
| 1 | inventory-service | seed-test-data env 변수 prefix 통일 (line 37) |
| 2 | slip-service | seed-test-data env 변수 prefix 통일 (line 38) |
| 3 | user-service | seed-org env 변수 prefix 통일 (line 35) |
| 4 | partner-service | DB user/password chained-default + samhan default (line 12-13) |
| 5 | arologis-service | DB user/password chained-default + samhan default (line 11-12) |
| 6 | dashboard-service | DB user/password chained-default + samhan default (line 12-13) |
| 7 | groupware-service | DB user/password chained-default + samhan default (line 12-13) |
| 8 | notification-service | DB user/password chained-default + samhan default (line 12-13) |
| 9 | dc-config-service | DB url + user/password 4-단계 chained-default 표준 통일 (line 10-12) |
| 10 | partner-order-service | DB url + user/password 4-단계 chained-default 표준 통일 (line 10-12) |

코드 변경 없음 (Java / Kotlin 변경 0). yaml 만 변경.

---

## 4. 검증

### 4-1. yaml lint — 별도 도구 없음

- 모든 변경은 단일 라인 placeholder 치환. yaml 들여쓰기 / key 변화 없음 — 구조 영향 없음.

### 4-2. 컴파일 검증

| 명령 | 대상 | 결과 |
|---|---|---|
| `./gradlew :services:inventory-service:compileJava :services:slip-service:compileJava :services:user-service:compileJava :services:partner-service:compileJava :services:dc-config-service:compileJava :services:partner-order-service:compileJava` | 6 service | **BUILD SUCCESSFUL in 10s** (UP-TO-DATE — yaml 만 변경) |
| `./gradlew :services:arologis-service:compileJava :services:dashboard-service:compileJava :services:groupware-service:compileJava :services:notification-service:compileJava` | 4 service | **BUILD SUCCESSFUL in 3s** (UP-TO-DATE — yaml 만 변경) |

총 10 service compileJava 통과 — yaml 만 변경이므로 binary 재컴파일 없이 UP-TO-DATE 확인. 풀빌드 (test 포함) 는 본 PR CI 에서 검증.

### 4-3. 런타임 검증 약속 (Phase 10 W10-6 시점)

env 미설정 + dev profile 진입 시 connection refused 재현 X 확인 — DevOps `start-local-full.ps1` 실행 후 7 service 모두 정상 부팅 의무.

---

## 5. 가드 (메모리 일관)

- **feedback_korean_commits** — 본 PR 의 모든 commit / PR 본문 / Issue 본문 한국어 작성.
- **feedback_continuous_docs_sync** — 본 dev-report 동기화 (별도 docs PR 금지). devops / qa 영역 후속 commit 에서 본 dev-report 에 섹션 추가.
- **feedback_pm_integration_build_check** — 컴파일 검증 의무 (본 dev-report § 4-2). yaml 만 변경이므로 풀빌드는 CI 에 위임.
- **feedback_no_dev_director_mention** — PR / commit / Issue 본문에 "개발책임자" 변형 (결정/명시/요청/승인) 금지. 본 dev-report 는 § 2 에서 "개발책임자 로컬 검증" 만 사실 기술 (결정/명시/요청/승인 무관).

---

## 6. 다음 슬라이스 (W10-6) 약속

- DevOps: `start-local-full.ps1` 의 env wrapper 도 본 PR 의 chained-default 표준에 맞춰 `SAMHAN_<X>_SEED_TEST_DATA` 변수만 export (legacy `INVENTORY_SEED_TEST_DATA` 등은 wrapper 에서 제거 — application.yml fallback 만으로 안전).
- QA: env 미설정 + 설정 시 양쪽 부팅 검증 시나리오 1건 추가 (Stage 0 환경 검증 case).

---

## 7. DevOps 섹션 — 인프라 강화 + nightly workflow 신규

### 7-1. 배경 — PR #100 머지 후 로컬 검증 회고 3건

PR #100 (풀 수준 로컬 테스트 인프라) 머지 후 로컬 검증 시 다음 3건 회귀 발견:

1. **user-service 가 auth-service 보다 먼저 시작 시 OrgChartSeeder 16명 모두 fail**
   - 원인: `EmployeeProvisioningService` 가 `auth-service.createAccount` RPC 를 16회 호출하지만, sequential bootRun 이 health check 통과를 보장하지 않음. auth-service ApplicationContext 초기화 중에 user-service RPC 실행 → connection refused / 503.
   - 영향: 마스터 로그인 (CEO 김미선) 자체가 불가 → QA 시나리오 #01 fail.

2. **partner-service env mismatch 로 startup fail**
   - 원인: `partner-service/application.yml` 의 datasource chained-default 중 `LEGACY_DB_USER` export 누락. § 2-2 와 동일 backlog — 본 PR 에서 backend-engineer 가 application.yml fallback 으로 해결, devops 측에서 자동 export 로 이중 보강.

3. **InfluxDB 가 port 8086 점유 시 slip-service 충돌**
   - 원인: slip-service default `SERVER_PORT=8086` 이 InfluxDB OSS default HTTP API port 와 충돌. 사용자가 시계열 모니터링 도구 별도 가동 시 무경고 fail.

### 7-2. DevOps 산출물

| 파일 | 신규/갱신 | 역할 |
| ---- | --------- | ---- |
| `infrastructure/scripts/start-local-full.ps1` | 갱신 | health-gated startup + Pre-flight port 검사 + LEGACY_DB_* 자동 export |
| `.github/workflows/nightly-slip-it.yml` | 신규 | slip-it-public + slip-it-core nightly 회귀 검증 (60분, 매일 02:00 KST) + fail 시 GitHub Issue 자동 생성 |
| `.github/workflows/ci.yml` | 갱신 | slip-it-* matrix PR 재활성 (timeout 30분, V11 fix 후 빠른 통과 예상) |
| `infrastructure/env-templates/.env.dev-seed` | 갱신 | 12 toggle SAMHAN_<X>_ prefix 통일 + legacy fallback 보존 |

### 7-3. `start-local-full.ps1` 강화 흐름

```
[0/6] Pre-flight (신규)
    ├─ Docker daemon 가용성 검증 (docker info)
    └─ 8080~8200 + 8761 port 점유 검사
       └─ 8086 충돌 → InfluxDB 안내 + SERVER_PORT=8186 권장 메시지

[2/6] env load (강화)
    └─ LEGACY_DB_USER / LEGACY_DB_PASSWORD / LEGACY_DB_HOST / LEGACY_DB_PORT 자동 export
       (application.yml chained-default 의 두 번째 단계 호환)

[3/6] 14 service sequential startup (강화)
    ├─ ServiceTimeoutSec default 60s → 300s (5분) 상향
    │   사유: bootRun cold start + Flyway migration + Eureka registry 합산 시 60s 부족 사례 다수
    ├─ 필수 service (eureka-server, auth-service) fail 시 후속 service abort
    │   사유: 후속 service 가 미작동 service 에 의존 → cascade fail. 조기 abort 로 디버그 시간 단축.
    └─ Job 상태 (Failed/Stopped) 사전 감지 → timeout 회피

[6/6] 사용 가이드 (강화)
    └─ 필수 service fail 시 종합 안내 + log 경로 출력
```

추가 옵션: `-SkipPortCheck` (외부 의존 서비스 사전 가동 인지 시 Pre-flight 생략).

### 7-4. `nightly-slip-it.yml` — 야간 회귀 보강

PR #99 5/6/7차 fix 회고 — slip-it-public + slip-it-core matrix 가 ubuntu-latest (2-core/7GB) 환경에서 60분 timeout 도 빠듯한 정황. PR #102 V11 fix (CONCURRENTLY 제거 → idle in transaction deadlock 차단) 이후 30분 안에 통과 예상이지만, 보강 회귀 검증을 위해 nightly 분리.

**구조 (3 job)**:

1. `discover-branches`: `git branch -r` 에서 `main` + `feature/integrated-*` + `feature/legacy-*` 패턴 활성 branch 자동 추출 → matrix branch 입력.
2. `slip-it-nightly`: matrix (branch × group). PR #99 옵션 B 시점 matrix 분할안 (slip-it-public + slip-it-core) 그대로 nightly 60분 timeout 으로 실행.
3. `open-issue-on-failure`: `if: failure()` 조건 — 1개 이상 matrix fail 시 `gh issue create` 자동 발행 (label: ci, nightly, slip-service). feedback_pr_ci_monitoring 가드 준수.

**스케줄**: `0 17 * * *` UTC = 02:00 KST 매일. 사용자 활동 거의 없는 시간대.

**워크플로우 분리 패턴 (2-track)**:

```
PR CI (ci.yml)
  └─ slip-it-public + slip-it-core matrix (timeout 30분)  ← W10-5 재활성
     → 통과 시 PR 머지 가능
     → fail 시 즉시 fix (PR 회로)

nightly (nightly-slip-it.yml)
  └─ main + 활성 feature/integrated-* + feature/legacy-* matrix (timeout 60분)
     → 통과 시 회귀 없음 확인
     → fail 시 GitHub Issue 자동 생성 → PM 알림
```

### 7-5. `.env.dev-seed` prefix 통일

§ 2-1 의 backend-engineer chained-default 표준 (`${SAMHAN_<X>_SEED_TEST_DATA:${<X>_SEED_TEST_DATA:false}}`) 에 동기화하여 `.env.dev-seed` 도 SAMHAN_ prefix 표준 + legacy fallback 양쪽 export.

12 toggle (W10-5 추가: `SAMHAN_PARTNER_SEED_TEST_ATTACHMENTS`):

```
SAMHAN_USER_SEED_ORG=true
SAMHAN_PARTNER_SEED_TEST_DATA=true
SAMHAN_PARTNER_SEED_TEST_ATTACHMENTS=true
SAMHAN_PRODUCT_SEED_TEST_DATA=true
SAMHAN_INVENTORY_SEED_TEST_DATA=true
SAMHAN_SLIP_SEED_TEST_DATA=true
SAMHAN_PARTNER_ORDER_SEED_TEST_DATA=true
SAMHAN_AROLOGIS_SEED_TEST_DATA=true
SAMHAN_ACCOUNTING_SEED_TEST_DATA=true
SAMHAN_GROUPWARE_SEED_TEST_DATA=true
SAMHAN_NOTIFICATION_SEED_TEST_DATA=true
SAMHAN_DASHBOARD_SEED_TEST_DATA=true
```

기존 `USER_SEED_ORG` / `PARTNER_SEED_TEST_DATA` / ... 11 변수는 그대로 보존 (사용자 셸 history / 외부 도구 / 기존 문서 호환). application.yml chained-default 가 SAMHAN_ 우선 → legacy fallback 순으로 resolve.

### 7-6. 가드 — DevOps

- **feedback_powershell_utf8_writes**: 본 PR 의 신규/갱신 .ps1 / .yml / .md / .env 일체 Write/Edit/heredoc 으로 작성. `Set-Content` 사용 회피.
- **feedback_korean_commits**: commit 메시지 한국어 (prefix 와 trailer 만 영문).
- **feedback_pr_ci_monitoring**: nightly fail 시 `gh issue create` 자동 Issue 발행 (open-issue-on-failure job).
- **feedback_continuous_docs_sync**: 본 § 7 가 backend §1~6 와 동일 PR 에 통합. 별도 docs PR 없음.
- **feedback_no_dev_director_mention**: 본 § 7 본문 "개발책임자" 변형 (결정/명시/요청/승인) 미사용 — § 7-1 회귀 회고는 사실 기술만.

### 7-7. 향후 작업 (W10-6 이후)

1. **nightly self-hosted runner 도입 검토** — ubuntu-latest 2-core 가 slip-service IT 60분도 빠듯한 경우, self-hosted (8-core/16GB) 로 전환하여 timeout 30분으로 단축.
2. **Pre-flight 자동 회피 옵션** — 8086 InfluxDB 충돌 감지 시 `SERVER_PORT=8186` 자동 export (현재는 안내만). 사용자 confirm prompt 추가 검토.
3. **start-local-full.ps1 의 service tier 병렬화** — 현재 sequential. tier 2~5 는 병렬 가능 (auth UP 후 user/product/partner 동시 시작) → 총 시간 30~50% 단축 여지.

### 7-8. DevOps 변경 파일 요약

```
infrastructure/scripts/start-local-full.ps1                | (갱신, +Pre-flight, health-gated, LEGACY_DB_*)
.github/workflows/nightly-slip-it.yml                      | (신규, 60분 nightly + auto-issue)
.github/workflows/ci.yml                                   | (갱신, slip-it-* matrix PR 재활성)
infrastructure/env-templates/.env.dev-seed                 | (갱신, SAMHAN_ prefix 통일 + legacy fallback)
docs/dev-reports/w10-5-retrospective.md                    | (갱신, 본 § 7 추가)
```
