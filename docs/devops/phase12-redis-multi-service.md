# Phase 12 PR-H4b — 13 service 단일 ElastiCache + channel namespace + cutover 단계

> Phase 12 Step 4b — `shared-realtime` BE 13 service rollout 슬라이스의 DevOps 가이드.
> PR-H4a 에서 추출한 `shared-realtime` 모듈을 **slip-service 외 9 backend service** 가
> 의존 추가만으로 도입할 때, **단일 ElastiCache instance 1대로 13 service 전체 fan-out**
> 을 안전하게 운영하기 위한 channel namespace 정책 + 단계적 cutover + 모니터링 + 롤백 절차.
> 본 가이드는 `docs/devops/redis-realtime-broker.md` § 9 (PR-H4a 보강) 의 후속 — 실제 13 service
> roll-out 단계의 운영 절차를 명시.

---

## 1. 배경 (PR-H4a 시드 → PR-H4b 13 service 확장)

PR-H4a 머지 완료 시점에 `services/shared-realtime/` 모듈이 등장하면서 다음 자산이 14 service 공통 가용 상태:

- `SamhanRealtimeBroker` interface — 5 메서드 표면 (`subscribe` / `publish` / `publishLocal` / `heartbeat` / `subscriberCount`)
- `InMemorySamhanRealtimeBroker` (default — `samhan.realtime.broker=in-memory`)
- `RedisSamhanRealtimeBroker` (`samhan.realtime.broker=redis` + `samhan.realtime.service-name=<service>`)
- `RealtimeBrokerAutoConfig` (자동 등록 — `@ConditionalOnProperty`)
- `RealtimePublishHook` (cross-node fan-out)

본 PR-H4b 는 **9 backend service** 가 위 모듈에 의존만 추가 + `samhan.realtime.service-name` 환경변수 1줄 주입으로 broker bean 자동 등록. service 별 코드 변경 0.

> **9 service 적용 후보** (Designer § 1.1 참고): partner / inventory / accounting / arologis / product / dc-config / partner-order / user / groupware. dashboard / notification 은 `broker only` (audit 미도입). auth / partner-auth / logging / api-gateway / eureka-server 는 적용 제외.

---

## 2. 단일 ElastiCache instance + channel namespace 정책

### 2.1 instance 선택 (PR-H4a § 9.2 결정 그대로)

**채택**: 옵션 A — 단일 cache.t3.micro instance 1대 + channel prefix 분리.

| 옵션 | 설명 | 채택 여부 | 비용/월 |
| --- | --- | --- | --- |
| **A. 단일 instance + channel prefix** | cache.t3.micro 1대, channel = `samhan:<service>:<eventName>:{entityId}` | ✅ **채택** | ~₩25-50K |
| B. service 별 instance | 9 service × instance 9대 | ❌ 비추 (overkill) | ~₩225-450K |
| C. service 별 Redis DB index | DB 0~8 (Redis 16 DB) | ❌ 비추 (cluster mode 비호환) | ~₩25-50K |

> **근거**: PR-H4a § 9.3 — 14 service 합계 publish ~70 msg/s peak ~700, Redis CPU <5%. cache.t3.micro 0.5 GB RAM 으로 13 service 충분.

### 2.2 channel namespace 규칙 (Designer § 3 합의)

```
samhan:<serviceName>:<eventName>:{entityId}
```

- `<serviceName>` = `samhan.realtime.service-name` 환경변수 값 (slip / partner / inventory / accounting / arologis / product / dc-config / partner-order / user / groupware / dashboard / notification)
- `<eventName>` = 도메인별 event name (Designer § 3.1 / § 3.2 / § 3.3 표 일치)
- `{entityId}` = UUID (도메인 본체 식별자)

**예시 (9 service rollout 후)**:

```
samhan:slip:slip:edit:{slipId}                              # PR-H4a 시드
samhan:slip:slip:edit-request:created:{slipId}              # PR-H4a 시드
samhan:partner:partner:edit:{partnerId}                     # PR-H4b 신규
samhan:inventory:stock-adjust:edit:{adjustId}               # PR-H4b 신규
samhan:accounting:journal:edit:{journalId}                  # PR-H4b 신규
samhan:arologis:dispatch:edit:{dispatchId}                  # PR-H4b 신규
samhan:product:product:edit:{productId}                     # PR-H4b 신규
samhan:dc-config:dc-rule:edit:{ruleId}                      # PR-H4b 신규
samhan:partner-order:partner-order:edit:{orderId}           # PR-H4b 신규
samhan:user:user:edit:{userId}                              # PR-H4b 신규
samhan:groupware:memo:edit:{memoId}                         # PR-H4b 신규
samhan:dashboard:dashboard:metric:updated:{dashboardId}     # PR-H4b 신규 (broker only)
samhan:notification:notification:delivered:{notifyId}       # PR-H4b 신규 (broker only)
```

### 2.3 channel collision 방지 가드

`RedisSamhanRealtimeBroker` 의 `publish` 호출 시 service-name prefix 자동 prepend (도메인별 코드 신경 X). 단, 다음 운영 가드 의무:

- **service-name 환경변수 누락 → startup 명확 실패** (PR-H4a § 4.5 가드 — `IllegalStateException` + 한국어 로그). 13 service env 템플릿에 `SAMHAN_REALTIME_SERVICE_NAME=<service>` 라인 의무.
- **service-name 중복 금지** — 13 service 가 모두 고유 값 사용 (matrix 표 기준). `partner` 와 `partner-order` 처럼 prefix 가 부분 일치하는 경우, 항상 정확 match (full string equals) 로 판단되므로 channel `samhan:partner:*` subscribe 가 `samhan:partner-order:*` 메시지를 수신할 위험 0건.
- **wildcard subscribe 금지** — 각 service 는 자신의 service-name 으로 시작하는 channel 만 subscribe (`psubscribe samhan:slip:*` 같은 cross-service wildcard 금지). cross-service 통신은 `notification-service` 의 별도 RabbitMQ 채널 / Eureka HTTP 호출 사용.

---

## 3. 단계적 cutover 절차 (in-memory → Redis, 13 service 일괄)

> **전제**: 본 PR-H4b 머지 = 9 service 가 shared 모듈 의존 추가 + LockPolicy/EditRequestService specialization 등록 + ApplicationContextLoadIT GREEN. 이 시점까지는 모든 service `SAMHAN_REALTIME_BROKER=in-memory` (default) 로 운영 — Redis 전환은 본 § 3 절차에 따라 **점진적**.

### 3.1 Day 0 — ElastiCache 준비

1. AWS ElastiCache cluster 1대 신규 생성 (PR-H4a § 4 절차 그대로)
   - cache.t3.micro / Cluster mode disabled / replicas=1 / encryption in/at rest / maintenance Sun 04:00 KST
2. ElastiCache SG inbound 6379 — 13 service EC2 SG 모두 whitelist
3. endpoint 발급 → infrastructure secret manager 에 `REDIS_HOST` 등록 (전 service 동일 값 공유)
4. (선택) Redis AUTH 토큰 발급 → `REDIS_AUTH_TOKEN` secret 등록

### 3.2 Day 1 — slip-service 단일 cutover (smoke)

PR-H4a 시드라 가장 안정. 1대만 먼저 전환하여 channel namespace / hook / publishCount metric 검증.

```bash
# slip-service env 추가
SAMHAN_REALTIME_BROKER=redis
SAMHAN_REALTIME_SERVICE_NAME=slip
REDIS_HOST=my-redis.abc123.ng.0001.apne2.cache.amazonaws.com
REDIS_PORT=6379
REDIS_AUTH_TOKEN=<token>     # 선택
```

**검증 (24h)**:
- actuator `/actuator/metrics/samhan.realtime.publish` 카운터 증가 확인
- `publishFailureCount` = 0 유지
- desktop multi-context 2개 띄워 multi-context 1초 sync (PR-H4a § 5.5.2 회귀 case) 작동 확인
- ElastiCache CPU / Memory 모니터링 — peak <5% 유지

### 3.3 Day 2~3 — 1순위 3 service 추가 cutover

🔴 1순위: partner / inventory / accounting (한국 회계 + 사용 빈도 최고)

```bash
# 각 service env (3 service 각각 별도)
SAMHAN_REALTIME_BROKER=redis
SAMHAN_REALTIME_SERVICE_NAME=partner       # 또는 inventory / accounting
REDIS_HOST=my-redis.abc123.ng.0001.apne2.cache.amazonaws.com
REDIS_PORT=6379
```

**검증 (48h)**:
- 4 service (slip + partner + inventory + accounting) 모두 publishCount 정상
- channel collision 0 (Redis MONITOR 1분 sample → channel 패턴 확인)
- Redis CPU peak <10% 유지 (4 service 합계 ~30 msg/s)

### 3.4 Day 4~5 — 2순위 4 service + 3순위 2 service + broker only 2 service 추가 cutover

🟠 2순위: arologis / product / dc-config / partner-order
🟡 3순위: user / groupware
🟢 broker only: dashboard / notification

```bash
# 8 service 동시 cutover (2순위~broker only 묶음)
SAMHAN_REALTIME_BROKER=redis
SAMHAN_REALTIME_SERVICE_NAME=<service>
```

**검증 (72h)**:
- 13 service 모두 publishCount 정상 (slip + 9 audit/edit-request + 2 broker only + 1 시드 = 13)
- Redis CPU peak <5% 유지 (cache.t3.micro 충분 — § 5 비용 분석 참고)
- ElastiCache memory <50% 유지

### 3.5 Day 6 — 종합 모니터링 + 회귀 검증

- 13 service 전체 actuator metric 종합 dashboard (Grafana / CloudWatch)
- QA 회귀 시나리오 65 case 재실행 (PR-H4b QA scenarios.md)
- multi-context 1초 sync 모든 도메인 검증 (slip + partner + inventory + accounting + arologis 5 도메인 multi-context 캡처)

### 3.6 롤백 절차 (service 단위)

특정 service 만 회귀 발생 시 즉시 in-memory 원복 가능 (다른 service 무영향):

```bash
# 해당 service env 만 변경
SAMHAN_REALTIME_BROKER=in-memory
# REDIS_HOST / REDIS_PORT 환경변수는 유지 (다른 service 가 사용 중)
```

→ 해당 service 만 재시작. 단일 노드 운영 모드로 즉시 복귀 (PR-H4a § 6 단일 노드 모드 — 추가 의존 0).

---

## 4. 환경변수 (13 service 동일 — env 템플릿 일관)

각 service 의 `infrastructure/env-templates/<service>.env` 에 다음 라인 일관 의무:

```bash
# Phase 12 PR-H4a/H4b — shared-realtime broker 13 service 공유
SAMHAN_REALTIME_BROKER=in-memory                 # default. production 전환 시 redis
SAMHAN_REALTIME_SERVICE_NAME=<service>            # 13 service 고유 값 (slip / partner / inventory / accounting / arologis / product / dc-config / partner-order / user / groupware / dashboard / notification)
SAMHAN_REALTIME_HEARTBEAT_SECONDS=30              # PR-H1 SSE keep-alive
REDIS_HOST=                                        # ElastiCache endpoint (전 service 동일)
REDIS_PORT=6379                                    # 전 service 동일
REDIS_AUTH_TOKEN=                                  # (선택) Redis 6+ AUTH
```

### 4.1 service-name 매핑 표 (13 service)

| service | `SAMHAN_REALTIME_SERVICE_NAME` 값 | 비고 |
| --- | --- | --- |
| slip-service | `slip` | PR-H4a 시드 |
| partner-service | `partner` | PR-H4b 신규 |
| inventory-service | `inventory` | PR-H4b 신규 |
| accounting-service | `accounting` | PR-H4b 신규 |
| arologis-service | `arologis` | PR-H4b 신규 |
| product-service | `product` | PR-H4b 신규 |
| dc-config-service | `dc-config` | PR-H4b 신규 (hyphen 허용 — channel name 호환) |
| partner-order-service | `partner-order` | PR-H4b 신규 (`partner` prefix 충돌 방지) |
| user-service | `user` | PR-H4b 신규 |
| groupware-service | `groupware` | PR-H4b 신규 |
| dashboard-service | `dashboard` | PR-H4b 신규 (broker only) |
| notification-service | `notification` | PR-H4b 신규 (broker only) |
| auth-service / partner-auth-service / logging-service | (미적용 — 환경변수 미주입) | shared 의존 0 |

---

## 5. 운영 비용 (13 service 합계)

| 항목 | 단가 | 월 비용 (KRW) |
| --- | --- | --- |
| Phase 11 base (EC2 m5.xlarge + RDS db.t3.medium + EBS + 기타) | | ~₩405K |
| **ElastiCache cache.t3.micro** (replica 0, staging) | $0.026/h × 730h × 1 | **~₩25K** |
| **ElastiCache cache.t3.micro** (replica 1, production) — 13 service 공유 | $0.026/h × 730h × 2 | **~₩50K** |

### 5.1 비용 비교 — 13 service 분리 vs 단일 instance

| 옵션 | 월 비용 | 절감 |
| --- | --- | --- |
| 본 § 2.1 단일 instance (replica 1) | ₩50K | base |
| service 별 instance 13대 (replica 1 each) | ₩650K | 13배 |
| **차액 (단일 instance 채택 효과)** | | **~₩600K/월 절감** |

### 5.2 upgrade trigger

다음 조건 중 1개라도 충족 시 cache.t3.small (~₩50K → ~₩100K/월) upgrade 검토:

- ElastiCache CPU peak >50% (현재 예상 <5%)
- ElastiCache memory >70% (현재 예상 <30%)
- 동시 SSE 구독자 >5000 (전 service 합계, 현재 예상 <500)
- publishFailureCount > 0.1% (정상 = 0~소수)

---

## 6. 모니터링 의무 (13 service rollout 후)

### 6.1 ElastiCache 콘솔 (CloudWatch metric)

- `EngineCPUUtilization` — alarm 임계 80%
- `BytesUsedForCache` — alarm 임계 70% (memory)
- `NetworkBytesIn` / `NetworkBytesOut` — peak 모니터링 (140 KB/s 예상)
- `CacheMisses` — pub/sub 만 사용 → 0 유지
- `CurrConnections` — 13 service × instance 수 × Lettuce pool 크기 (예상 <100)

### 6.2 service actuator metric (13 service 동일)

각 service 의 `/actuator/metrics/` endpoint 노출:

- `samhan.realtime.publish` (counter) — service 별 publish 횟수 누계
- `samhan.realtime.publish.failure` (counter) — publish 실패 횟수 (alarm 임계 = 0 유지)
- `samhan.realtime.subscribe` (gauge) — 활성 SSE 구독자 수
- `samhan.realtime.heartbeat` (counter) — heartbeat 발송 횟수

### 6.3 CloudWatch / Prometheus alarm 권고

| metric | 임계 | 조치 |
| --- | --- | --- |
| ElastiCache `EngineCPUUtilization` > 80% (5분 평균) | Critical | cache.t3.small upgrade |
| ElastiCache `BytesUsedForCache` > 70% (5분 평균) | Major | TTL 정책 확인 (pub/sub only — 정상 0 유지) |
| `samhan.realtime.publish.failure` > 0.1% (1시간 합계) | Critical | Redis 연결 점검 + service 별 in-memory 롤백 |
| ElastiCache `CurrConnections` > 100 | Major | Lettuce pool 크기 점검 |
| ElastiCache health UP → DOWN | Critical | failover replica 자동 승격 (replica=1 시) + 5분 alert |

### 6.4 일일 점검 체크리스트 (Day 1~Day 6 기간)

- [ ] ElastiCache CPU / Memory peak 확인
- [ ] 13 service 의 publishFailureCount metric 합계 = 0 확인
- [ ] Redis MONITOR 30초 sample → channel pattern 정합 확인 (`samhan:<service>:*`)
- [ ] desktop multi-context 1초 sync smoke (slip + partner + inventory 3 도메인)
- [ ] mobile-staff 30s polling smoke (DispatchScreen / StockAdjustScreen)

---

## 7. 보안 (13 service 공유 환경)

### 7.1 VPC 격리 (PR-H4a § 9.7 그대로)

- ElastiCache SG inbound 6379 = 13 service EC2 SG 만 whitelist
- public access 차단 (default)
- subnet group = private subnet only

### 7.2 TLS in transit

`spring.data.redis.ssl.enabled=true` (전 service 동일):

```yaml
# 각 service application.yml (또는 환경변수)
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_AUTH_TOKEN:}
      ssl:
        enabled: true
```

> ElastiCache 의 "Encryption in transit" 옵션과 정합 의무.

### 7.3 Redis AUTH 토큰

- `REDIS_AUTH_TOKEN` 환경변수 (전 service 동일)
- secret manager 에서 단일 출처 주입
- 토큰 rotation 정책 — Phase 11 운영 후 90일 단위 권고

### 7.4 channel ACL (운영 복잡도 vs 보안 trade-off)

Redis 6+ ACL 로 service 별 channel pattern 제한 가능:

```
ACL SETUSER slip-service on >${SLIP_AUTH} +psubscribe ~samhan:slip:* +publish ~samhan:slip:*
ACL SETUSER partner-service on >${PARTNER_AUTH} +psubscribe ~samhan:partner:* +publish ~samhan:partner:*
...
```

> **채택 보류** — Phase 11 단일 VPC + 13 service trust boundary 동일하므로 운영 복잡도 vs 보안 이득 trade-off 에서 보류 권고. Phase 13+ multi-tenant 확장 시 재검토.

---

## 8. CI 영향 (13 service rollout)

### 8.1 단위 / IT 테스트

- `samhan.realtime.broker` 미설정 → default `in-memory` → Redis 의존 0
- 기존 9 service 의 `@MockBean` IT 패턴 그대로 (외부 client 격리 의무)
- shared 모듈 의 `RedisSamhanRealtimeBrokerIT` (Testcontainers Redis) 가 broker 동작 GREEN 보장 — 9 service 측 별도 broker IT 불필요

### 8.2 ApplicationContextLoadIT (필수)

각 9 service 의 `ApplicationContextLoadIT` 추가/보강 — shared 모듈 의존 추가만으로 startup 정상 검증:

```java
@SpringBootTest
class <Service>ApplicationContextLoadIT {
    @MockBean private NotificationClient notificationClient;
    @MockBean private AuthClient authClient;
    // ... 외부 client @MockBean 격리 (feedback_it_mockbean_external_clients)

    @Test
    void contextLoads() {
        // shared-realtime + shared-edit-request bean 자동 등록 검증
        // SamhanRealtimeBroker bean 단일성 (PR #119 회귀 가드)
    }
}
```

### 8.3 nightly IT (선택)

- nightly-shared-realtime.yml — Testcontainers Redis 1회 + 9 service 회귀 smoke (60분 timeout 충분)
- 단, 본 PR-H4b 단계에서는 PR-H4a 시드 nightly 만 유지 (추가 nightly 불필요)

### 8.4 Windows Docker caveat

PR-H4a § 9.8 그대로:
- `DOCKER_HOST=tcp://localhost:2375` 우회 (Windows + Docker Desktop 한계)
- CI ubuntu-latest 는 무리 없음

---

## 9. 트러블슈팅 (13 service rollout 신규 케이스)

| 증상 | 원인 후보 | 조치 |
| --- | --- | --- |
| 특정 service 부팅 실패 (RedisConnectionFailureException) | `SAMHAN_REALTIME_BROKER=redis` 인데 ElastiCache SG inbound 누락 | SG inbound 에 해당 service EC2 SG 추가 |
| 특정 service 부팅 실패 (IllegalStateException — service-name 누락) | `SAMHAN_REALTIME_SERVICE_NAME` 환경변수 미주입 | env 템플릿 § 4 라인 추가 |
| service A 가 service B 의 event 수신 | wildcard subscribe 사용 / channel prefix 누락 | `RedisSamhanRealtimeBroker` 의 `subscribe` 가 자기 service-name 으로 시작하는 channel 만 받도록 가드 확인 |
| 특정 service 의 publishCount 만 0 | shared 의존 미추가 / @ConditionalOnProperty 가드 발동 | service `build.gradle` `implementation project(':services:shared-realtime')` 확인 + env 변수 확인 |
| ElastiCache CPU spike (>50%) | 9 service 동시 publish 폭주 (sync 폭주) | cache.t3.small upgrade 검토 (~₩100K/월) |
| ElastiCache `CurrConnections` 폭증 | 각 service Lettuce pool 크기 과다 | `spring.data.redis.lettuce.pool.max-active=8` 표준 적용 |
| dashboard / notification service 가 publish 만 하고 subscribe 안 함 | broker only 적용 — 정상 동작 | metric `samhan.realtime.subscribe` = 0 정상 (publish 만 사용) |
| 회계 (accounting) audit-logs 응답이 한국 계정 코드 표기 누락 | accounting specialization 의 한국어 라벨 매핑 누락 | Designer § 4.1 표 1:1 일치 확인 |

---

## 10. PR-H4c (50+ page UI 통합) 진입 조건

본 PR-H4b 머지 + § 3 cutover Day 6 종합 모니터링 통과 후 PR-H4c 진입 가능. 진입 조건:

- [ ] § 3 단계적 cutover 완료 (Day 1~Day 6)
- [ ] § 6 모니터링 metric 6일간 정상 (publishFailureCount = 0 유지)
- [ ] § 9 트러블슈팅 case 발생 0 또는 모두 해결
- [ ] QA 회귀 65 case (본 PR-H4b QA scenarios.md) 모두 PASS

---

## 관련 문서

- [`docs/devops/redis-realtime-broker.md`](./redis-realtime-broker.md) (PR-H2 + PR-H4a § 9 시드)
- [`docs/devops/realtime-sse-production.md`](./realtime-sse-production.md) (PR-H1 nginx / ALB)
- [`docs/uiux/phase12/H4b-be-rollout-checklist.md`](../uiux/phase12/H4b-be-rollout-checklist.md) (PR-H4b Designer 매트릭스 + 잠금 정책 일람)
- [`docs/qa/phase-12-step-4b-be-realtime-rollout/scenarios.md`](../qa/phase-12-step-4b-be-realtime-rollout/scenarios.md) (PR-H4b QA 65 case)
- [`infrastructure/env-templates/`](../../infrastructure/env-templates/) (13 service env 템플릿 — § 4 라인 일관 의무)
- [`services/shared-realtime/`](../../services/shared-realtime/) (PR-H4a 머지된 BE 모듈)
- [`services/shared-edit-request/`](../../services/shared-edit-request/) (PR-H4a 머지된 BE 모듈)
- [project_phase11_aws] (Phase 11 AWS 단일 환경 결정)
- [feedback_testcontainers_windows_docker] (Windows Docker IT 가이드)
- [feedback_it_mockbean_external_clients] (IT 외부 client @MockBean 의무)
