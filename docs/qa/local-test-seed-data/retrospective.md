# 로컬 풀-수준 검증 회고 — PR #100 머지 후 4 issue 회고 + W10-5 fix

> **branch**: `feature/integrated-phase-10-step-5-retrospective`
> **선행 PR**: #100 (`feature/local-test-setup`, 머지 commit `67e552b`)
> **회고 시점**: 2026-05-09 (Phase 10 W10-5 본 PR 작업 직전)
> **목적**: PR #100 머지 후 실제 로컬 환경 (개발책임자 PC) 에서 14+1 service 풀 스택 부팅 검증 시 발견된 4 issue 정리 + W10-5 본 PR 의 fix 매핑.

---

## 0. 회고 배경

PR #100 (`feature/local-test-setup`) 은 11 시드 toggle + 풀 스택 docker-compose + start-local-full.ps1 + .env.dev-seed + 7 시나리오 + 도메인 정합성 SQL 통합 PR 로 머지 완료 (`67e552b`, 2026-05-08).

머지 직후 개발책임자 (Windows 11 + JDK 17 + Docker Desktop) 의 실제 로컬 환경에서 풀 스택 부팅 검증 시 4 issue 발견:

| # | Issue | 영향 service | 회복 시간 | W10-5 fix |
|---|---|---|---|---|
| 1 | env 변수 prefix mismatch | 4 service (partner / slip / inventory / product) | 즉시 (env 추가) | backend-engineer agent — application.yml chained-default fallback |
| 2 | partner-service `CHANGE_ME_LOCAL_ONLY` placeholder | partner-service | 즉시 (env 추가) | backend-engineer agent — default 'samhan' fallback chain |
| 3 | service startup 의존순 (user-service 가 auth-service 보다 먼저 시작) | user-service (OrgChartSeeder) | user-service 1회 재시작 | devops-engineer agent — start-local-full.ps1 health-gated startup |
| 4 | InfluxDB port 8086 충돌 | slip-service | SERVER_PORT=8186 env 후 재시작 | devops-engineer agent — start-local-full.ps1 pre-flight port 검사 |

회고 핵심 = "통합 PR 의 단위/IT 검증 PASS 와 실제 로컬 풀 스택 부팅 검증은 **별개**" → CI 에 `start-local-full.ps1` 통합 검증 step 추가 plan (Phase 11 진입 시 도입 권고).

---

## 1. Issue 1 — env 변수 prefix mismatch

### 1-1. 현상

PR #100 머지 후 `.env.dev-seed` 의 11 시드 toggle 을 export 하고 `start-local-full.ps1` 실행 → 4 service 의 시드 데이터가 일부만 활성화됨:

| service | env 변수 (PR #100) | 기대 row | 실제 row |
|---|---|---|---|
| partner-service | `SAMHAN_PARTNER_SEED_TEST_DATA` | 50 | 0 |
| slip-service | `SLIP_SEED_TEST_DATA` (SAMHAN 없음) | 100 | 0 |
| inventory-service | `INVENTORY_SEED_TEST_DATA` (SAMHAN 없음) | 200 | 0 |
| product-service | `SAMHAN_PRODUCT_SEED_TEST_DATA` | 100 | 100 (정상) |

→ partner / product = `SAMHAN_*` prefix / slip / inventory = SAMHAN 없는 prefix → `.env.dev-seed` 의 변수 (PR #100 시점 표준) 와 service `application.yml` 의 변수 mismatch.

### 1-2. 진단

- BE 1 (partner) / BE 2 (product) / BE 3 (slip) / BE 4 (inventory) agent 들의 application.yml 작성 시 prefix 통일 안 됨
- `.env.dev-seed` 는 DevOps agent 가 PR #100 시점 발행 — agent 간 표준 결정 미완료
- chained-default fallback 패턴이 partner / product 만 적용 — slip / inventory 누락
- 유일하게 동작한 product-service 는 우연히 `application.yml` 의 prefix 가 .env.dev-seed 와 일치

### 1-3. W10-5 fix (`a1a2a3`-style placeholder, 본 PR 작업)

backend-engineer agent 가 표준 `SAMHAN_<X>_SEED_TEST_DATA` 통일 + chained-default fallback 적용:

```yaml
# 기존 (Issue)
seed-test-data: ${INVENTORY_SEED_TEST_DATA:false}

# W10-5 fix
# W10-5 회고 — env 변수 prefix 통일 (SAMHAN_INVENTORY_SEED_TEST_DATA 우선, INVENTORY_SEED_TEST_DATA legacy fallback).
seed-test-data: ${SAMHAN_INVENTORY_SEED_TEST_DATA:${INVENTORY_SEED_TEST_DATA:false}}
```

| service | 본 PR 변경 |
|---|---|
| inventory-service `application.yml:36~38` | `SAMHAN_INVENTORY_SEED_TEST_DATA` 우선 + `INVENTORY_SEED_TEST_DATA` legacy fallback |
| slip-service `application.yml:37~39` | `SAMHAN_SLIP_SEED_TEST_DATA` 우선 + `SLIP_SEED_TEST_DATA` legacy fallback |
| user-service `application.yml:34~36` | `SAMHAN_USER_SEED_ORG` 우선 + `USER_SEED_ORG` legacy fallback |
| partner-service `application.yml:9~14` | username/password chained-default + samhan default fallback (Issue 2 와 묶음) |

### 1-4. 회귀 검증

- chained-default 첫 매개변수 (SAMHAN_*) 미설정 시 두 번째 매개변수 (legacy *) 로 fallback → 기존 .env / CI 모두 회귀 0
- `.env.dev-seed` 도 본 PR 시점 동일 표준 갱신 의무 (W10-5 backlog → DevOps 차후 PR)
- 14 service 모두 동일 패턴 적용 검증 (`backend-engineer` agent grep) — 본 PR scope = 4 service 한정, 다른 service (auth/user-service Internal token / accounting / groupware / notification / dashboard / arologis) 는 이미 chained-default 패턴 적용

### 1-5. 학습

- 다중 agent 통합 PR 에서 환경변수 prefix 표준 결정 = TM 의 사전 검증 의무 (PR #16/17/21 회고 패턴 응용)
- 4 service 모두 동일 패턴 적용 후 grep 가드 자동화 (Phase 11 진입 시 CI step 추가 plan)
- chained-default 패턴은 **회귀 0** 보장 — 새 표준 + 기존 표준 양쪽 동시 동작

---

## 2. Issue 2 — partner-service `CHANGE_ME_LOCAL_ONLY` placeholder

### 2-1. 현상

`start-local-full.ps1` 실행 시 partner-service 만 startup fail:

```
o.s.boot.SpringApplication: Application run failed
org.postgresql.util.PSQLException: FATAL: password authentication failed for user "CHANGE_ME_LOCAL_ONLY"
```

다른 service (user / product / inventory / slip / accounting / 등) 는 정상 부팅 → partner-service `application.yml` 의 username/password default 가 `CHANGE_ME_LOCAL_ONLY` placeholder 인 채로 보존 (Phase 8 명시 — 환경변수 설정 강제용).

### 2-2. 진단

```yaml
# 기존 (Issue)
username: ${SAMHAN_PARTNER_DB_USER:${LEGACY_DB_USER:CHANGE_ME_LOCAL_ONLY}}
password: ${SAMHAN_PARTNER_DB_PASSWORD:${LEGACY_DB_PASSWORD:CHANGE_ME_LOCAL_ONLY}}
```

→ env 미설정 시 PostgreSQL `password authentication failed` → startup fail.
→ 다른 service 는 default = `samhan` (또는 `samhan_dev_pw`) 로 fallback chain 보유 → 정상 부팅.
→ partner-service 만 환경변수 강제 의도로 placeholder 유지 → 로컬 풀 스택 부팅 시점 fail.

### 2-3. W10-5 fix

backend-engineer agent 가 default 'samhan' fallback chain 적용 (다른 service 일관):

```yaml
# W10-5 회고 — chained-default + samhan default fallback (다른 service 와 동일 패턴, env 미설정 시 connection refused 회피)
username: ${SAMHAN_PARTNER_DB_USER:${LEGACY_DB_USER:${DB_USER:samhan}}}
password: ${SAMHAN_PARTNER_DB_PASSWORD:${LEGACY_DB_PASSWORD:${DB_PASSWORD:samhan_dev_pw}}}
```

3 단계 fallback chain:
1. `SAMHAN_PARTNER_DB_USER` (Phase 11 표준)
2. `LEGACY_DB_USER` (Phase 8 cutover 호환)
3. `DB_USER` (로컬 default) — 미설정 시 `samhan` 리터럴

### 2-4. 회귀 검증

- prod 환경에서는 SAMHAN_PARTNER_DB_USER 명시 설정 필수 → default 'samhan' 미사용 (기존 운영 가드 보존)
- dev 로컬에서 env 미설정 시 default 'samhan' 으로 정상 부팅
- `.env.dev-seed` 에 SAMHAN_PARTNER_DB_USER / SAMHAN_PARTNER_DB_PASSWORD 명시 추가 의무 (W10-5 backlog → DevOps 차후 PR)
- `feedback_gitguardian_false_positive.md` 일관 적용 — 'samhan_dev_pw' 는 dev-only test 비밀번호 (이미 main 의 docker-compose.yml + OrgChartSeeder.java 에 평문 존재), GitGuardian dashboard 에서 false positive mark 처리

### 2-5. 학습

- Phase 8 의 placeholder 강제 의도 (환경변수 설정 강제) = production 진입 시점 의도였으나 dev 로컬 부팅 차단 부수효과 발생
- 14 service 모두 default fallback 일관 패턴 = dev 로컬 부팅 안전성 + production 환경 강제 분리 (chained-default 의 prod 환경에서 명시 설정 가드 일관)
- `CHANGE_ME_LOCAL_ONLY` placeholder 는 Phase 11 cutover 시점 production env 검증용 grep 가드로 재활용 가능 (별도 plan)

---

## 3. Issue 3 — service startup 의존순 (user-service 의 OrgChartSeeder)

### 3-1. 현상

`start-local-full.ps1` 의 의존순 startup 시점 user-service 가 auth-service 보다 먼저 시작 → user-service 의 `OrgChartSeeder` 가 16 employee 생성 시점 `AuthClient.createAccount` 호출 fail:

```
o.s.boot.SpringApplication: Application started — but OrgChartSeeder failed
java.net.ConnectException: Connection refused — http://auth-service/api/v1/auth/internal/accounts
```

→ Eureka 등록은 완료 / Spring Boot 자체 부팅은 OK / OrgChartSeeder 만 fail → 16 employee 생성 0건.

### 3-2. 진단

`OrgChartSeeder` 동작 순서:
1. user-service 부팅 후 `@PostConstruct` 또는 `@EventListener(ApplicationReadyEvent)` trigger
2. 16 employee + 5 department row INSERT
3. **auth-service `/internal/accounts` 호출** (loginId / password 등록)
4. employee row 의 `auth_account_id` 컬럼 link

문제 = 3번 단계의 auth-service 가 아직 부팅 중 → ConnectException → seeder 전체 rollback 또는 partial fail.

`start-local-full.ps1` 의 의존순:
```
eureka -> auth -> user -> product -> inventory -> slip -> accounting
       -> partner -> partner-order -> arologis -> groupware
       -> notification -> dashboard -> api-gateway
```

→ auth → user 의존순은 정의되어 있으나 auth-service 의 **`/actuator/health` 200 폴링 health-gated 진입 가드** 누락. 단순히 `Start-Sleep -Seconds N` 또는 background job 시작 후 즉시 다음 service 진입 → race condition.

### 3-3. 회복 (사용자 작업)

```powershell
# user-service 만 재시작 1회
.\infrastructure\scripts\stop-local-full.ps1 -ServiceOnly user-service
# auth-service ready 확인 후 user-service 재시작
Start-Process -FilePath "java" -ArgumentList "-jar services\user-service\build\libs\user-service.jar"
```

→ 16 employee 정상 생성 + auth_account_id link 정상.

### 3-4. W10-5 fix (DevOps backlog)

devops-engineer agent 가 `start-local-full.ps1` health-gated startup 적용 (W10-5 plan, 별도 commit):

```powershell
# 각 service 부팅 후 /actuator/health 200 폴링 (최대 60초)
function Wait-ServiceHealthy {
    param([string] $ServiceName, [int] $Port, [int] $TimeoutSec = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        try {
            $resp = Invoke-RestMethod "http://localhost:$Port/actuator/health" -TimeoutSec 3
            if ($resp.status -eq 'UP') {
                Write-Host "[$ServiceName] healthy" -ForegroundColor Green
                return $true
            }
        } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "[$ServiceName] $TimeoutSec 초 timeout"
}

# auth-service 부팅 + health-gated
Start-Process -FilePath "java" -ArgumentList "-jar services\auth-service\build\libs\auth-service.jar"
Wait-ServiceHealthy -ServiceName 'auth-service' -Port 8081 -TimeoutSec 60

# user-service 부팅 (auth ready 후만 진행)
Start-Process -FilePath "java" -ArgumentList "-jar services\user-service\build\libs\user-service.jar"
Wait-ServiceHealthy -ServiceName 'user-service' -Port 8083 -TimeoutSec 60
```

> 본 PR (W10-5) scope = retrospective docs 만. 실제 `start-local-full.ps1` 변경은 DevOps team 차후 PR (W10-5 backlog 위임).

### 3-5. 회귀 검증

- health-gated 진입 후 auth-service ready 확인 → user-service OrgChartSeeder 의 `AuthClient.createAccount` 호출 PASS
- 다른 의존 관계 (partner / product → inventory / slip → partner-order / arologis 등) 모두 동일 패턴 적용 가드 plan
- timeout 60초 = 14 service 모든 부팅 시점 안정 (Eureka registration ~ 60초 일관)

### 3-6. 학습

- `start-local-full.ps1` 의 단순 background job + sleep 패턴은 race condition 위험 → health-gated 진입 의무
- `feedback_pm_integration_build_check.md` 에 "PM 통합 풀빌드 가드" 보강 필요 — 기존은 `gradle assemble` 만 검증, 향후 풀 스택 부팅 검증 step 추가
- `OrgChartSeeder` 의 fail 시 retry 정책 검토 (현재 단발 호출 → fail 시 manual 재시작 필요)

---

## 4. Issue 4 — InfluxDB port 8086 충돌

### 4-1. 현상

`start-local-full.ps1` 실행 시 slip-service startup fail:

```
o.s.boot.SpringApplication: Web server failed to start
java.net.BindException: Address already in use: bind — port 8086
```

→ 다른 service 정상, slip-service 만 fail.

### 4-2. 진단

```powershell
PS> netstat -ano | findstr :8086
  TCP    0.0.0.0:8086    0.0.0.0:0    LISTENING    12345
PS> tasklist | findstr 12345
  influxd.exe    12345 ...    InfluxDB 2.x (사용자 PC 에 사전 설치)
```

→ 사용자 PC 에 InfluxDB 가 port 8086 LISTENING (사용자 다른 프로젝트 metric 수집용 사전 설치).
→ slip-service `application.yml` 의 server.port = 8086 (Phase 3 시점 표준 포트, InfluxDB default 와 충돌).

| service | 표준 포트 | 충돌 가능성 |
|---|---|---|
| eureka-server | 8761 | 거의 없음 (Eureka 전용) |
| api-gateway | 8080 | 높음 (Tomcat / 다른 web app default) |
| auth-service | 8081 | 보통 |
| user-service | 8083 | 보통 |
| product-service | 8084 | 보통 |
| inventory-service | 8085 | 보통 |
| **slip-service** | **8086** | **높음 (InfluxDB / Apache 등 default)** |
| partner-order-service | 8087 | 낮음 |
| accounting-service | 8088 | 낮음 |

### 4-3. 회복 (사용자 작업)

```powershell
$env:SERVER_PORT = "8186"
Start-Process -FilePath "java" -ArgumentList "-jar services\slip-service\build\libs\slip-service.jar"
```

→ slip-service port 8186 으로 정상 부팅. arologis-service / dashboard-service 등 slip-service 호출자도 SLIP_SERVICE_BASE_URL 갱신 의무 (사용자 검증 = Eureka 등록 후 자동 lookup 으로 무영향).

### 4-4. W10-5 fix (DevOps backlog)

devops-engineer agent 가 `start-local-full.ps1` 의 pre-flight port 검사 추가 (W10-5 plan, 별도 commit):

```powershell
# 14+1 service 표준 포트 + 인프라 포트 사전 검사
$RequiredPorts = @{
    'eureka-server'        = 8761
    'api-gateway'          = 8080
    'auth-service'         = 8081
    'user-service'         = 8083
    'product-service'      = 8084
    'inventory-service'    = 8085
    'slip-service'         = 8086
    'partner-order-service'= 8087
    'accounting-service'   = 8088
    'partner-service'      = 8095
    'partner-auth-service' = 8089
    'groupware-service'    = 8092
    'notification-service' = 8093
    'dashboard-service'    = 8094
    'arologis-service'     = 8097
}

foreach ($svc in $RequiredPorts.Keys) {
    $port = $RequiredPorts[$svc]
    $listening = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($listening) {
        $proc = Get-Process -Id $listening.OwningProcess
        Write-Warning "[$svc] port $port 충돌 — 프로세스 $($proc.ProcessName) (PID $($proc.Id))"
        # 옵션: 사용자에게 -ServiceName <X>:<altPort> override 안내
    }
}
```

→ 충돌 발견 시 사용자에게 alert + 대안 포트 override 안내. `slip-service` 의 경우 8186 포트 fallback 자동 적용 옵션 plan.

> 본 PR (W10-5) scope = retrospective docs 만. 실제 `start-local-full.ps1` 변경은 DevOps team 차후 PR (W10-5 backlog 위임).

### 4-5. 회귀 검증

- pre-flight port 검사 후 충돌 발견 시 사용자에게 명시적 alert → 사용자가 SERVER_PORT override 또는 충돌 process 중지 결정
- Eureka registration 자동 lookup → port override 시 다른 service 의 client 호출은 무영향 (slip-service base-url 자동 resolve)
- nginx / api-gateway 의 `application.yml` 에 SLIP_SERVICE_BASE_URL 환경변수화 가드 (Phase 11 진입 시 cutover plan)

### 4-6. 학습

- 14+1 service 표준 포트 (8080-8097 범위) 와 사용자 PC 의 다른 application (InfluxDB / Grafana / Apache / Tomcat / 등) 충돌 가능성 = 로컬 환경 변수 → DevOps 사전 검사 의무
- `start-local-full.ps1` 의 pre-flight port 검사 = race condition 회피 + 사용자 alert 일관 패턴
- 향후 service 추가 시 표준 포트 결정 시점 사용자 PC 보유 application port 확인 (Phase 11 P11-3 진입 시점 14 service → 15 service cascade 시 일관 적용)

---

## 5. 4 issue 종합 회고

### 5-1. 공통 학습

1. **통합 PR 의 단위/IT 검증 PASS ≠ 실제 로컬 풀 스택 부팅 검증**: PR #100 의 BE/FE/Designer/QA/DevOps 5-team 리뷰 + TM 종합 + CI green 모두 통과했으나 실제 사용자 PC 부팅 시 4 issue 발견. → CI 에 `start-local-full.ps1` 통합 검증 step 추가 plan (Phase 11 진입 시 도입 권고, 사용자 PC 환경 시뮬레이션은 한계).
2. **다중 agent 통합 PR 에서 환경변수 prefix 표준 결정 = TM 의 사전 검증 의무**: BE 1~4 agent 의 application.yml 작성 시 prefix 통일 안 됨 → TM 통합 단계에서 grep 가드 + 표준 명시 의무 (Phase 11 진입 시 `SAMHAN_<X>_*` 전 service 일관 grep 검증).
3. **chained-default 패턴은 회귀 0 보장**: 새 표준 + 기존 표준 양쪽 동시 동작 → 14 service 일관 적용 의무 (Phase 11 cutover 시 이중 표준 호환 보장).
4. **`start-local-full.ps1` 의 health-gated 진입 + pre-flight port 검사 = DevOps 의무**: 단순 background job + sleep 패턴은 race condition 위험. 14+1 service 표준 포트 사용자 PC 충돌 사전 검사 의무.
5. **`OrgChartSeeder` 의 dependency 호출 패턴 검토**: Spring Boot Application Ready Event 시점에 다른 service 호출 시 race condition 가능 → retry 정책 또는 polling 가드 도입.

### 5-2. W10-5 본 PR scope

| 작업 | 담당 agent | 본 PR 적용 여부 | 별도 commit |
|---|---|---|---|
| Issue 1 fix (4 service env prefix 통일) | backend-engineer | ✅ 적용 | `application.yml` 수정 4건 |
| Issue 2 fix (partner-service default fallback chain) | backend-engineer | ✅ 적용 | `application.yml` 수정 (Issue 1 과 묶음) |
| Issue 3 fix (start-local-full.ps1 health-gated) | devops-engineer | ⏳ 위임 | W10-5 backlog → DevOps team 차후 PR |
| Issue 4 fix (start-local-full.ps1 pre-flight port) | devops-engineer | ⏳ 위임 | W10-5 backlog → DevOps team 차후 PR |
| Phase 10 회고 docs | qa-tester | ✅ 적용 | `docs/dev-reports/phase-10-retrospective.md` 신규 |
| 로컬 검증 회고 docs | qa-tester | ✅ 적용 | `docs/qa/local-test-seed-data/retrospective.md` 신규 (본 문서) |
| 시나리오 검증 결과 보고 | qa-tester | ✅ 적용 | `docs/qa/local-test-seed-data/verification-report-2026-05-09.md` 신규 |
| slip-it nightly 시나리오 plan | qa-tester | ✅ 적용 | `docs/qa/local-test-seed-data/scenarios/08-nightly-slip-it.md` 신규 |

### 5-3. Phase 11 진입 시 가드 보강 plan

| 가드 항목 | Phase 11 P11-? | 비고 |
|---|---|---|
| CI 에 `start-local-full.ps1` 통합 검증 step 추가 | P11-3 (Production cutover 직전) | GitHub Actions ubuntu-latest 환경 재현 한계 → linux 등가 스크립트 신규 |
| 14+1 service `SAMHAN_<X>_*` 환경변수 grep 가드 | P11-1 (Secrets Manager 도입) | application.yml + .env-template + .env.dev-seed 일관 검증 |
| service startup 의존순 health-gated 표준화 | P11-3 (Production cutover) | docker-compose `depends_on.condition: service_healthy` 명시 |
| 표준 포트 충돌 사전 검사 (pre-flight) | P11-3 (Production cutover) | AWS EC2 환경에서는 충돌 가능성 낮음, 사용자 로컬 dev 환경 한정 |
| 일관된 `OrgChartSeeder` 같은 cross-service seeder 의 retry 정책 | P11-2 (Discovery + Resilience) | Resilience4j 적용 시 일관 적용 |
| `feedback_pm_integration_build_check.md` 보강 (풀 스택 부팅 검증 step 추가) | (메모리 갱신만) | 본 회고 docs 발행 후 갱신 |

---

## 6. 회귀 가드 (본 PR 머지 시 재검증)

| 가드 | 본 PR scope | 검증 방법 |
|---|---|---|
| chained-default fallback 회귀 0 | 4 service `application.yml` | 기존 .env (SAMHAN 없는 prefix) 도 정상 동작 |
| `samhan_dev_pw` GitGuardian false positive 처리 | partner-service `application.yml` | `feedback_gitguardian_false_positive.md` 일관 적용 |
| 한국어 docs 의무 | 본 회고 + 검증 보고 + nightly plan | `feedback_korean_commits.md` 일관 |
| dev-report 누적 의무 | `docs/dev-reports/phase-10-retrospective.md` 신규 | `feedback_function_documentation.md` 3-layer 일관 |
| QA 자체 산출 vs 위임 가드 | 본 회고는 docs only (Java code 0) | `feedback_multi_agent_team_pattern.md` 일관 (QA IT 직접 작성 금지) |

---

## 7. 관련 문서

- `docs/dev-reports/phase-10-retrospective.md` — Phase 10 종합 회고 (본 PR 신규)
- `docs/qa/local-test-seed-data/README.md` — 로컬 풀-수준 테스트 시나리오 가이드 (PR #100)
- `docs/qa/local-test-seed-data/verification-report-2026-05-09.md` — 검증 결과 보고 (본 PR 신규)
- `docs/qa/local-test-seed-data/scenarios/08-nightly-slip-it.md` — slip-it nightly 시나리오 plan (본 PR 신규)
- `docs/qa/local-test-seed-data/scenarios/01-master-login.md` — 시나리오 1 (마스터 로그인 + 기본 화면)
- `infrastructure/scripts/start-local-full.ps1` — 풀 스택 일괄 기동 스크립트 (PR #100, W10-5 fix 위임)
- `infrastructure/env-templates/.env.dev-seed` — 시드 toggle 일괄 정의 (PR #100, W10-5 갱신 위임)
- memory `feedback_gitguardian_false_positive.md` — dev-only 비밀번호 false positive mark
- memory `feedback_pr_review_workflow.md` — 5-team 리뷰 → TM → CI → PM → 사용자 머지
- memory `feedback_user_merge_authority.md` — PM admin 머지 금지
