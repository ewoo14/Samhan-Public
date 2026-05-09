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
2. production 침입 차단 명확화 — `SAMHAN_` prefix = 명시적 SamhanLogis 환경 (다른 namespace 와 충돌 차단)
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
- (devops / qa 섹션은 본 dev-report 의 후속 commit 에서 채워질 예정.)
