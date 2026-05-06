# Phase 8 2차 — Discovery wrapper + 환경변수 통일 + Secrets Manager rotation spec

본 보고서는 Phase 8 (AWS 호환성 가드) 2차 작업 결과를 정리한다.

- 1차 = AWS 호환성 가드 plan (PR #88) — docs only
- **2차 = 본 PR — wrapper module 신규 + 환경변수 통일 + Secrets spec**
- 3차 예정 = AWS 마이그레이션 dry-run + Phase 8 회고 + Phase 9 진입 plan

---

## 1. ServiceDiscoveryClient interface + Eureka wrapper

### 1-1. 신규 모듈 `shared:discovery-abstraction`

vendor 추상화 layer 도입. Eureka (현재 운영) 와 AWS Cloud Map (Phase 10
cutover 시점 예정) 사이의 vendor 전환 비용을 격리한다.

```text
shared/discovery-abstraction/
├── build.gradle
├── src/main/java/com/samhanair/logis/discovery/
│   ├── ServiceDiscoveryClient.java       (interface — register/deregister/lookup/healthcheck)
│   ├── ServiceInstance.java              (record — vendor 결과 정규화)
│   ├── EurekaServiceDiscoveryClient.java (Eureka impl — 현재 운영)
│   ├── AwsCloudMapServiceDiscoveryClient.java (placeholder — Phase 10 구현)
│   └── DiscoveryConfiguration.java       (@ConditionalOnProperty 기반 impl 선택)
└── src/test/java/com/samhanair/logis/discovery/
    ├── EurekaServiceDiscoveryClientTest.java
    ├── AwsCloudMapServiceDiscoveryClientTest.java
    └── ServiceInstanceTest.java
```

### 1-2. 인터페이스 4 operation

```java
public interface ServiceDiscoveryClient {
    void register(String serviceName, String host, int port);
    void deregister(String serviceName);
    List<ServiceInstance> lookup(String serviceName);
    boolean healthcheck(String serviceName);
}
```

### 1-3. impl 토글

`@ConditionalOnProperty(name = "samhan.discovery.provider", havingValue = "eureka", matchIfMissing = true)`

- default = `eureka` → `EurekaServiceDiscoveryClient`
- AWS 마이그 = `aws-cloud-map` → `AwsCloudMapServiceDiscoveryClient` (현재 placeholder, Phase 10 cutover 시점 실 구현)

### 1-4. AWS placeholder 동작

```java
public class AwsCloudMapServiceDiscoveryClient implements ServiceDiscoveryClient {
    private static final String UNSUPPORTED_MESSAGE =
            "Phase 10 cutover 시점 구현 — AWS Cloud Map 추상화 placeholder";

    @Override
    public void register(String serviceName, String host, int port) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }
    // deregister / lookup / healthcheck 동일
}
```

### 1-5. 14 service 의존성 추가는 Phase 10 위임

본 PR = wrapper module 신규 + 단위 테스트만. settings.gradle / root build.gradle
에 `:shared:discovery-abstraction` 등록되어 컴파일 + 테스트는 진행되지만,
14 service 중 어느 것도 본 모듈을 implementation 의존성으로 추가하지 않는다.
실 통합 = Phase 10 AWS cutover 시점.

### 1-6. 단위 테스트 결과

| 테스트 | 결과 |
|---|---|
| `EurekaServiceDiscoveryClientTest` (4 case) | PASS |
| `AwsCloudMapServiceDiscoveryClientTest` (4 case) | PASS |
| `ServiceInstanceTest` (5 case) | PASS |
| 합계 | 13 PASS / 0 FAIL |

`./gradlew :shared:discovery-abstraction:test` 통과.

---

## 2. 환경변수 통일 (chained-default 패턴)

### 2-1. INTERNAL_TOKEN 표준화

Phase 8 1차 doc 에서 검출한 불일치 (`INTERNAL_AUTH_TOKEN` 6 service vs
`INTERNAL_TOKEN` 1 service) 를 `SAMHAN_INTERNAL_TOKEN` 표준으로 통일.
**chained-default 패턴 적용** — 신규 표준 우선, legacy fallback 보존:

```yaml
# auth-service / user-service / product / inventory / slip / accounting / dc-config
app:
  security:
    internal:
      token: ${SAMHAN_INTERNAL_TOKEN:${INTERNAL_AUTH_TOKEN:dev-internal-token-change-me}}

# partner-order-service
samhan:
  internal-token: ${SAMHAN_INTERNAL_TOKEN:${INTERNAL_TOKEN:dev-only-token-replace}}
```

영향: `SAMHAN_INTERNAL_TOKEN` 환경변수가 설정되어 있으면 우선 사용, 없으면
legacy `INTERNAL_AUTH_TOKEN` (또는 partner-order 의 `INTERNAL_TOKEN`) 으로
fallback. 기존 배포 환경 호환 100% 보존.

### 2-2. JWT_SECRET 표준화

```yaml
# auth-service / api-gateway
app:
  security:
    jwt:
      secret: ${SAMHAN_JWT_SECRET:${JWT_SECRET:dev-secret-change-me-in-production-32bytes-min!}}

# partner-auth-service
samhan:
  jwt:
    secret: ${SAMHAN_JWT_SECRET:${JWT_SECRET:dev-only-partner-jwt-secret-replace-in-prod-32bytes-min!!}}
```

### 2-3. service URL 표준화

partner-order-service `<NAME>_HOST` (host only) + partner-auth-service `DC_CONFIG_URL`
(full URL) 혼재 → `SAMHAN_<SERVICE>_SERVICE_URL` (full URL) 표준 + legacy
fallback 보존:

```yaml
# partner-order-service external block
external:
  product-service: ${SAMHAN_PRODUCT_SERVICE_URL:http://${PRODUCT_HOST:product-service}:8084}
  inventory-service: ${SAMHAN_INVENTORY_SERVICE_URL:http://${INVENTORY_HOST:inventory-service}:8085}
  slip-service: ${SAMHAN_SLIP_SERVICE_URL:http://${SLIP_HOST:slip-service}:8086}
  dc-config-service: ${SAMHAN_DC_CONFIG_SERVICE_URL:http://${DC_CONFIG_HOST:dc-config-service}:8089}
  partner-auth-service: ${SAMHAN_PARTNER_AUTH_SERVICE_URL:http://${PARTNER_AUTH_HOST:partner-auth-service}:8091}

# partner-auth-service samhan.dc-config block
samhan:
  dc-config:
    url: ${SAMHAN_DC_CONFIG_SERVICE_URL:${DC_CONFIG_URL:http://dc-config-service:8089}}
```

### 2-4. 영향 범위 (yml 변경 10 file)

| service | yml 변경 항목 |
|---|---|
| auth-service | `INTERNAL_AUTH_TOKEN` → `SAMHAN_INTERNAL_TOKEN` chained / `JWT_SECRET` → `SAMHAN_JWT_SECRET` chained |
| user-service | `INTERNAL_AUTH_TOKEN` → `SAMHAN_INTERNAL_TOKEN` chained |
| product-service | `INTERNAL_AUTH_TOKEN` → `SAMHAN_INTERNAL_TOKEN` chained |
| inventory-service | `INTERNAL_AUTH_TOKEN` → `SAMHAN_INTERNAL_TOKEN` chained |
| slip-service | `INTERNAL_AUTH_TOKEN` → `SAMHAN_INTERNAL_TOKEN` chained |
| accounting-service | `INTERNAL_AUTH_TOKEN` → `SAMHAN_INTERNAL_TOKEN` chained |
| dc-config-service | `INTERNAL_AUTH_TOKEN` → `SAMHAN_INTERNAL_TOKEN` chained |
| partner-auth-service | `JWT_SECRET` → `SAMHAN_JWT_SECRET` chained / `DC_CONFIG_URL` → `SAMHAN_DC_CONFIG_SERVICE_URL` chained |
| partner-order-service | `INTERNAL_TOKEN` → `SAMHAN_INTERNAL_TOKEN` chained / 5 service URL 표준화 |
| api-gateway | `JWT_SECRET` → `SAMHAN_JWT_SECRET` chained |
| eureka-server | (변경 X — `EUREKA_PEER_URL` / `EUREKA_INSTANCE_HOSTNAME` 그대로) |
| logging-service | (변경 X — `RABBIT_*` / `ES_URI` 그대로) |

> **Java 코드 변경 0 file** — yml level 표준화만, `@ConfigurationProperties` 바인딩
> + `InternalTokenGuard` + `InternalAuthProperties` 모두 그대로. 테스트 영향 0.

### 2-5. .env.example + per-service env templates

`infrastructure/.env.example` — `SAMHAN_INTERNAL_TOKEN` + `SAMHAN_JWT_SECRET` 추가.

`infrastructure/env-templates/<service>.env` — 12 service 모두 보유 의무 적용:

| service | 신규 / 갱신 |
|---|---|
| auth-service.env | 신규 |
| user-service.env | 신규 |
| product-service.env | 신규 |
| inventory-service.env | 신규 |
| dc-config-service.env | 신규 |
| partner-auth-service.env | 신규 |
| partner-order-service.env | 신규 |
| api-gateway.env | 신규 |
| eureka-server.env | 신규 |
| logging-service.env | 신규 |
| slip-service.env | 갱신 (Phase 8 표준 추가) |
| accounting-service.env | 갱신 (Phase 8 표준 추가) |

12 / 12 service env template 보유. Phase 8 1차 검출 "통일 권장 3건" 중 .env.example
일관성 확보 완료.

---

## 3. AWS Secrets Manager rotation lambda spec

`docs/migration/phase8/M-SECRETS-ROTATION-spec.md` 신규 (~ 230 line):

- 대상 secrets 7건 (`SAMHAN_DB_PASSWORD` / `SAMHAN_INTERNAL_TOKEN` / `SAMHAN_JWT_SECRET` / `SAMHAN_GOOGLE_SERVICE_ACCOUNT_KEY` / `ALIGO_API_KEY` / `SAMHAN_SLACK_WEBHOOK_URL` / `RABBIT_PASSWORD`)
- rotation 주기 = secret 별 30 / 90 일
- lambda 구조 = Python 3.12, `secretsmanager:RotateSecret` + `rds:ModifyDBInstance` IAM, CloudWatch Events 트리거
- 4 단계 (`createSecret` / `setSecret` / `testSecret` / `finishSecret`) Python 코드 sample
- service 측 fetch 패턴 = `spring-cloud-aws-starter-secrets-manager` (Phase 10 적용)
- monitoring + alert 매트릭스 5건
- Phase 10 cutover 활성 절차 6 단계

본 PR = spec only, lambda 코드 X. Phase 10 진입 시점에 실 lambda 발행.

---

## 4. ROADMAP / DECISIONS 갱신

### 4-1. ROADMAP.md

Phase 8 진행 표시:
- 1차 PR #88 (AWS 호환성 가드) ✓ 머지 완료
- 2차 본 PR (Discovery wrapper + 환경변수 통일 + Secrets spec) — 발행
- 3차 예정 (AWS 마이그레이션 dry-run + Phase 8 회고 + Phase 9 진입 plan)

### 4-2. DECISIONS.md

Phase 8 2차 결정 3건 추가:
- D-P8-07: ServiceDiscoveryClient interface 도입 (Eureka default + AWS Cloud Map placeholder)
- D-P8-08: 환경변수 표준 `SAMHAN_<SERVICE>_<KEY>` 적용 (chained-default fallback 패턴 = legacy 호환 100%)
- D-P8-09: Secrets Manager rotation = Phase 10 cutover 시점 활성 (본 PR = spec only)

---

## 5. 산출물 합계

| 카테고리 | 신규 | 변경 |
|---|---|---|
| Java source | 5 file | 0 |
| Java test | 3 file | 0 |
| build.gradle (root) | 0 | 1 (leafProjects 추가) |
| settings.gradle | 0 | 1 (include / projectDir) |
| shared/discovery-abstraction/build.gradle | 1 | 0 |
| application.yml | 0 | 10 (12 service 중 10) |
| infrastructure/.env.example | 0 | 1 |
| infrastructure/env-templates/*.env | 10 | 2 |
| docs/migration/phase8/M-SECRETS-ROTATION-spec.md | 1 | 0 |
| docs/dev-reports/phase8-step-2-discovery-secrets.md | 1 (본 file) | 0 |
| ROADMAP.md | 0 | 1 |
| migration/decisions/DECISIONS.md | 0 | 1 |
| **합계** | **21** | **17** |

---

## 6. 검증

| 항목 | 결과 |
|---|---|
| `./gradlew :shared:discovery-abstraction:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :shared:discovery-abstraction:test` | BUILD SUCCESSFUL (13 case PASS) |
| `./gradlew :services:auth-service:test` | BUILD SUCCESSFUL (yml 표준화 검증) |
| 12 service `processResources` | BUILD SUCCESSFUL (yml syntax 검증) |
| Eureka classpath 의존성 부재 시 (`@ConditionalOnClass`) | 자동 구성 스킵 (소비자 명시 의존성 추가 시점에만 활성) |

---

## 7. 후속 작업

### Phase 8 3차 (예정)

- AWS 마이그레이션 dry-run plan (선택)
- Phase 8 회고 + Phase 9 진입 plan
- ServiceDiscoveryClient 14 service 도입 시점 결정 (Phase 9 신규 service vs Phase 10 cutover 일괄)

### Phase 10 (AWS cutover 시점)

- AWS Cloud Map impl 실 구현 (`AwsCloudMapServiceDiscoveryClient` placeholder 교체)
- Secrets Manager rotation lambda 코드 작성 + 배포
- 14 service 의 yml 에 `spring.config.import` 추가
- legacy env var (`INTERNAL_AUTH_TOKEN`, `JWT_SECRET`, `INTERNAL_TOKEN`) fallback Phase 11 시점 폐기

---

## 8. 참조

- AWS 호환성 가드: `docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md`
- 환경변수 표준화 plan: `docs/migration/phase8/M-ENV-STANDARDIZATION.md`
- Secrets Manager rotation spec: `docs/migration/phase8/M-SECRETS-ROTATION-spec.md`
- 누적 결정: `migration/decisions/DECISIONS.md` (D-P8-07 ~ D-P8-09)
- Phase 8 1차 보고서: `docs/dev-reports/phase8-step-1-aws-readiness.md`
- ROADMAP: `ROADMAP.md`
