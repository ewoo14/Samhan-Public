# Realtime Broker — in-memory vs Redis 가이드 (PR-H2)

> Phase 12 Step 2 (PR-H2) — DevOps 운영 가이드
>
> slip-service 의 SSE 실시간 broker 구현체 (in-memory / Redis pub/sub) 선택 + 다중
> 노드 확장 cutover 절차 + AWS ElastiCache 운영 비용 가이드.

---

## 1. 배경

PR-H1 (Phase 12 Step 1) 에서 도입한 `SlipRealtimeBroker` 는 **단일 노드 in-memory**
구현 (`ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>`) 이다.

PR-H2 부터는 BE 측에 `SamhanRealtimeBroker` 인터페이스를 추출하고 두 구현체를
`@ConditionalOnProperty(samhan.realtime.broker)` 로 분기한다.

| 구현체 | 환경변수 값 | 용도 | 추가 의존 |
|--------|-------------|------|-----------|
| InMemorySamhanRealtimeBroker | `in-memory` (default) | 단일 노드, 추가 의존 0 | 없음 |
| RedisSamhanRealtimeBroker    | `redis`               | 다중 노드 fan-out     | Redis 7+ |

**원칙**: 운영 모델이 단일 노드 (Phase 11 AWS 단일 EC2 m5.xlarge / cafe24 단일
호스팅) 인 동안에는 `in-memory` 를 유지한다. 다중 노드 (ECS / Auto Scaling Group
≥2) 시점에서만 `redis` 로 cutover.

---

## 2. 환경변수

| 변수 | 위치 | 기본값 | 의미 |
|------|------|--------|------|
| `SAMHAN_REALTIME_BROKER` | slip-service | `in-memory` | broker 구현체 (`in-memory` / `redis`) |
| `REDIS_HOST` | slip-service | (empty → localhost) | Redis endpoint host |
| `REDIS_PORT` | slip-service | `6379` | Redis port |
| `SAMHAN_REALTIME_HEARTBEAT_SECONDS` | slip-service | `30` | SSE keep-alive heartbeat (PR-H1) |

> **주의**: `samhan.realtime.broker=redis` 인데 Redis 가 미가용이면 broker bean
> 초기화 실패 → slip-service 부팅 실패. cutover 전 반드시 §4 절차 검증.

---

## 3. 단일 vs 다중 노드 의사결정

### 3-1. 단일 노드 (default, 권장 — 현재 운영)

**언제**:
- Phase 11 AWS 단일 EC2 m5.xlarge 운영 (현재 결정).
- cafe24 단일 호스팅 운영 (legacy 병행 기간).
- 동시 SSE 연결 < 500 (단일 instance JVM 충분).

**장점**:
- Redis 의존 0, 운영 비용 ₩0 추가, 장애 표면 축소.
- Latency = JVM 내 method call (~µs).

**단점**:
- instance 재시작 시 모든 구독자 강제 재연결 (re-subscribe).
- Auto Scaling 불가 (다중 instance 시 fan-out 불일치).

### 3-2. 다중 노드 (Redis pub/sub)

**언제**:
- 동시 SSE 연결 ≥ 500 (단일 m5.xlarge 한계 근접).
- 무중단 배포 의무 (rolling deploy 시 SSE 재연결 회피).
- Auto Scaling Group ≥ 2 instance.

**장점**:
- 모든 instance 가 모든 event 를 fan-out 수신 (publish 어느 노드 → 구독 어느 노드 OK).
- Rolling deploy 시 일부 instance drain 중에도 다른 instance 가 fan-out 지속.

**단점**:
- Redis 운영 비용 (cache.t3.micro ~₩30K/월).
- Latency 추가 (publish → Redis → subscriber: ~ms 단위).
- Redis 장애 시 cross-node fan-out 중단 (단, 동일 instance 내 fan-out 은 유지 — fallback 가능).

---

## 4. AWS ElastiCache Redis 설정 (Phase 11 cutover 시)

### 4-1. 준비 단계

1. **VPC 확인**: slip-service EC2 와 동일 VPC + 동일 Subnet Group (private 권장).
2. **Security Group**: ElastiCache SG 의 inbound 6379 를 slip-service EC2 SG 에서만 허용.
3. **AWS 콘솔 → ElastiCache → Redis → Create**.

### 4-2. 권장 설정

| 항목 | 값 | 비고 |
|------|----|----|
| Cluster mode | Disabled | 단일 primary 충분 (단일 region) |
| Node type | `cache.t3.micro` | 0.5 GB RAM — pub/sub 메시지만 → 충분 |
| Number of replicas | 0 (개발/staging) / 1 (production) | replica = 자동 failover |
| Engine version | 7.x (latest stable) | Spring Data Redis 호환 |
| Encryption in transit | Enabled | TLS — 내부 VPC 라도 권장 |
| Encryption at rest | Enabled | EBS-level |
| Backup | Disabled | pub/sub only — 영구 데이터 없음 |
| Maintenance window | Sun 04:00 KST | 트래픽 최저 시간 |

### 4-3. 환경변수 주입 (EC2 systemd unit / Docker env)

```bash
# /etc/samhan/slip-service.env (또는 Docker compose env)
SAMHAN_REALTIME_BROKER=redis
REDIS_HOST=my-redis.abc123.ng.0001.apne2.cache.amazonaws.com
REDIS_PORT=6379
```

### 4-4. 검증

```bash
# slip-service EC2 에서 Redis 연결 검증
redis-cli -h $REDIS_HOST -p 6379 ping
# → PONG

# pub/sub round-trip 검증 (subscriber 1 / publisher 1 = 별도 터미널)
redis-cli -h $REDIS_HOST subscribe samhan:slip:test
redis-cli -h $REDIS_HOST publish samhan:slip:test "hello"
```

---

## 5. 운영 비용 (Phase 11 + ElastiCache)

| 항목 | 단가 | 월 비용 (KRW) |
|------|------|---------------|
| EC2 m5.xlarge (Seoul, on-demand) | $0.236/h × 730h | ~₩232K |
| RDS db.t3.medium (Seoul) | ~$0.092/h × 730h | ~₩90K |
| EBS gp3 200GB + snapshot | | ~₩30K |
| Data transfer + misc | | ~₩50K |
| **Phase 11 Base 합계** | | **~₩405K** |
| **ElastiCache cache.t3.micro** (replica 1) | $0.026/h × 730h × 2 | **~₩50K** |
| **ElastiCache cache.t3.micro** (replica 0) | $0.026/h × 730h × 1 | **~₩25K** |

> Phase 11 base ₩405K 는 [project_phase11_aws] 결정 기준. Redis cutover 시 +₩25~50K
> 추가 (replica 정책에 따라). 단일 노드 유지 시 추가 비용 ₩0.

---

## 6. cutover 절차 (in-memory → Redis)

> **전제**: BE 가 RedisRealtimeBroker 신규 + `@ConditionalOnProperty` 분기 PR 머지 완료.

1. **사전**: ElastiCache cluster 생성 + endpoint 발급 (§4).
2. **검증 환경 (staging) 적용**:
   - staging slip-service env 에 `SAMHAN_REALTIME_BROKER=redis` + `REDIS_HOST=...` 주입.
   - 재시작 후 actuator/health UP 확인.
   - desktop 클라이언트 multi-context 2개 띄워 cross-instance fan-out smoke (instance A 에서
     publish → instance B 의 구독자 수신 확인).
3. **운영 (production) cutover**:
   - 사전 공지 — desktop/mobile 클라이언트 재연결 발생 (heartbeat 30s 안에 자동 복구).
   - 1차: instance 1대만 `redis` 로 전환 → 30분 모니터링 (publish/heartbeat 카운터).
   - 2차: 잔여 instance 전환 (rolling).
4. **롤백 절차**: `SAMHAN_REALTIME_BROKER=in-memory` 로 환경변수 원복 + 재시작.
   - in-memory 는 Redis 미가용도 무해 → 즉시 롤백 가능.
5. **데이터 일관성 가이드**:
   - SSE event 는 fire-and-forget (영구 저장 X). cutover 순간 1~2초 publish 손실 허용.
   - 영속 audit / 알림은 별도 DB / RabbitMQ 채널 → SSE broker 와 무관.
   - cutover 직후 5분간 publishFailureCount 모니터링 (정상 = 0~소수).

---

## 7. CI 영향

### 7-1. Unit / 단위 테스트

- `samhan.realtime.broker` 미설정 → default `in-memory` → Redis 의존 0.
- 기존 PR-H1 단위 테스트 (`SlipRealtimeBrokerTest`) 회귀 영향 0.

### 7-2. Integration 테스트 (BE 가 Redis broker IT 추가 시)

- 권고: **Testcontainers Redis** 활용.
  ```java
  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
          .withExposedPorts(6379);
  ```
- `@DynamicPropertySource` 로 `spring.data.redis.host` / `port` 주입.
- Windows + Docker Desktop 환경: npipe 한계 → `DOCKER_HOST=tcp://localhost:2375`
  fallback 권장 ([feedback_testcontainers_windows_docker]).

### 7-3. CI matrix 영향

- 현재 `.github/workflows/ci.yml` 은 ubuntu-latest + Docker 가용 → Testcontainers Redis 무리 없음.
- 신규 IT 추가 시 `slip-it-core` group 에 통합 (별도 group 분리 불필요 — heavyweight 아님).
- nightly-slip-it.yml 동일 (60분 timeout 충분).

---

## 8. 트러블슈팅

| 증상 | 원인 후보 | 조치 |
|------|----------|------|
| slip-service 부팅 실패 (RedisConnectionFailureException) | `SAMHAN_REALTIME_BROKER=redis` 인데 Redis 미가용 | `in-memory` 로 원복 또는 Redis endpoint 점검 |
| 다중 instance 에서 일부 client event 누락 | broker 가 in-memory 인데 다중 instance 운영 | `redis` 로 cutover 또는 LB sticky session 적용 |
| Redis CPU/Memory 급증 | 다수 slip 동시 publish 폭주 | cache.t3.small 로 upgrade 검토 (~₩50K/월) |
| publishFailureCount 증가 | Redis 연결 일시 단절 | ElastiCache 콘솔 메트릭 확인 + maintenance window 점검 |

---

---

## 9. PR-H4a — `shared-realtime` 모듈 + 14 service 공유 (PR-H4a 보강)

### 9.1 shared-realtime 모듈 → broker config toggle 활용

PR-H4a 부터 BE-1 agent 가 `services/shared-realtime/` 공통 Gradle 모듈을 신설한다 (slip-service 의 `realtime` 패키지 추출). 본 모듈은 **broker config toggle (`SAMHAN_REALTIME_BROKER`) 그대로 활용** — slip-service 외 다른 13 service 도 동일 환경변수를 읽어 in-memory / Redis 분기.

**모듈 구조 (BE-1 산출 예정)**:

```
services/shared-realtime/
  build.gradle                                # 공통 의존 (lombok / slf4j / spring-data-redis optional)
  src/main/java/com/samhanair/logis/shared/realtime/
    SamhanRealtimeBroker.java                 # interface (publish / subscribe / publishLocal / heartbeat)
    InMemorySamhanRealtimeBroker.java         # default 구현 (slip-service SlipRealtimeBroker 추출)
    RedisSamhanRealtimeBroker.java            # Redis 구현 (slip-service RedisRealtimeBroker 추출)
    RealtimePublishHook.java                  # cross-node hook (slip-service 시드)
    RealtimeBrokerAutoConfig.java             # @ConditionalOnProperty 자동 등록
```

**14 service 의존 추가** (각 service 의 `build.gradle`):

```gradle
dependencies {
    implementation project(':services:shared-realtime')
}
```

> 의존 추가만으로 broker bean 자동 등록 — service 별 코드 0 변경. event name 만 도메인별 다르게 (`partner:edit` / `journal:edit` 등).

### 9.2 14 service 공유 — Redis 호스트 단일 vs service 별 namespace

**원칙**: AWS ElastiCache **단일 instance 1대로 14 service 공유**. service 별 namespace 분리는 channel name prefix 로만 처리 (별도 instance 불필요).

| 옵션 | 설명 | 권고 | 비용 |
| --- | --- | --- | --- |
| **A. 단일 instance + channel prefix** | cache.t3.micro 1대, channel = `samhan:<service>:<entity>:<id>` | ✅ **권장 (default)** | ~₩25-50K/월 (1 instance) |
| B. service 별 instance | service 14개 × instance 14대 | ❌ 비추 (overkill) | ~₩350-700K/월 (14 instance) |
| C. service 별 Redis DB index | DB 0~13 (Redis 기본 16 DB) | ❌ 비추 (cluster mode 비호환) | ~₩25-50K/월 |

**채택: 옵션 A** — 단일 instance + channel prefix.

**channel naming convention**:

```
samhan:slip:edit:{slipId}              # PR-H2 시드
samhan:slip:reverted:{slipId}          # PR-H2 시드
samhan:slip:edit-request:created:{slipId}   # PR-H3 시드
samhan:slip:edit-request:decided:{slipId}   # PR-H3 시드
samhan:partner:edit:{partnerId}        # PR-H4b/H4c 신규
samhan:inventory:adjust:{adjustId}     # PR-H4b/H4c 신규
samhan:accounting:journal:edit:{journalId}  # PR-H4b/H4c 신규
samhan:arologis:dispatch:edit:{dispatchId}  # PR-H4b/H4c 신규
```

> RedisSamhanRealtimeBroker 는 service prefix 자동 prepend (도메인별 코드 신경 X).

### 9.3 14 service 공유 — Redis 메시지량 추정 + 비용 영향

| 측정 | 단일 service (slip) | 14 service 합계 (full rollout 후) |
| --- | --- | --- |
| 평균 publish/sec | ~5 msg/s (peak ~50) | ~70 msg/s (peak ~700) |
| 평균 channel 수 (활성 slip/entity) | ~50 | ~700 |
| Redis CPU 점유 | <1% | <5% (cache.t3.micro 충분) |
| Redis 네트워크 in/out | ~10 KB/s | ~140 KB/s (원거리 region 도 무리 없음) |

> **결론**: cache.t3.micro 0.5 GB RAM 으로 14 service 공유 충분. 동시 SSE 구독자 ≥ 5,000 (전 service 합계) 시 cache.t3.small (~₩50K/월) upgrade 검토.

### 9.4 운영 비용 (Phase 11 AWS + 14 service shared Redis)

| 항목 | 단가 | 월 비용 (KRW) |
| --- | --- | --- |
| EC2 m5.xlarge + RDS db.t3.medium + EBS + 기타 (Phase 11 base) | | ~₩405K |
| **ElastiCache cache.t3.micro** (replica 0) — 14 service 공유 | $0.026/h × 730h × 1 | **~₩25K** |
| **ElastiCache cache.t3.micro** (replica 1, production) — 14 service 공유 | $0.026/h × 730h × 2 | **~₩50K** |

> **최종 권고**: production = cache.t3.micro replica 1 (자동 failover) — **월 ₩50K 추가로 14 service 전체 다중 노드 확장 가능**. instance 14대 분리 대비 **₩300~650K/월 절감**.

### 9.5 cutover 절차 (14 service 일괄 → in-memory → Redis)

**전제**: PR-H4a BE-1 의 `shared-realtime` 모듈 머지 + 14 service 의존 추가 PR (PR-H4b) 머지 완료.

1. **사전**: ElastiCache cluster 1대 생성 (§ 4 절차).
2. **단계적 적용** (한꺼번에 14 service 전환 X — 회귀 위험):
   - **Day 1**: slip-service 1대만 `SAMHAN_REALTIME_BROKER=redis` 전환 → 24h 모니터링 (publishCount / publishFailureCount / heartbeat).
   - **Day 2~3**: partner / inventory / accounting 3대 추가 전환 → 48h 모니터링.
   - **Day 4~5**: 잔여 10 service 전환.
3. **롤백 (service 별)**: `SAMHAN_REALTIME_BROKER=in-memory` 환경변수 원복 + 해당 service 만 재시작 (다른 service 무영향).
4. **모니터링 의무**:
   - ElastiCache 콘솔 — CPU / Memory / Network / EngineCPUUtilization
   - 각 service actuator — `subscriberCount` / `publishCount` / `publishFailureCount` / `heartbeatCount`
   - publishFailureCount 이상 (정상 = 0~소수) 발생 시 즉시 진단

### 9.6 환경변수 (14 service 동일 — env 템플릿 14개 일관)

각 service 의 `infrastructure/env-templates/<service>.env` 에 다음 라인 일관 의무:

```bash
# Phase 12 PR-H4a — shared-realtime broker 14 service 공유
SAMHAN_REALTIME_BROKER=in-memory      # default. production 전환 시 redis
REDIS_HOST=                           # ElastiCache endpoint (전 service 동일)
REDIS_PORT=6379                       # 전 service 동일
```

> `SAMHAN_REALTIME_BROKER` / `REDIS_HOST` / `REDIS_PORT` 3 변수 전 service 동일 — 운영 중복 0건. infrastructure/secrets/* 단일 출처에서 14 service 모두 주입.

### 9.7 보안 (14 service 공유 환경)

- **VPC 격리**: ElastiCache SG 의 inbound 6379 = slip / partner / inventory / accounting / arologis / 기타 EC2 SG 만 허용 (whitelist).
- **TLS in transit**: 권장 활성 (내부 VPC 라도) — `spring.data.redis.ssl.enabled=true` + Jedis client 설정.
- **AUTH**: cache.t3.micro 도 Redis AUTH 토큰 권장 — `spring.data.redis.password=${REDIS_AUTH_TOKEN}` 환경변수.
- **channel access**: Redis 6+ ACL 로 service 별 channel pattern 제한 가능 (예: slip-service 는 `samhan:slip:*` 만 publish/subscribe). 단, 운영 복잡도 vs 보안 trade-off — Phase 11 단일 VPC 환경에서는 채택 보류 권고.

### 9.8 Testcontainers Redis IT (14 service 공통 패턴)

`shared-realtime` 모듈 신설 후 BE-1 agent 가 시드한 IT 패턴을 14 service 모두 재사용:

```java
// services/shared-realtime/src/test/java/.../RedisSamhanRealtimeBrokerIT.java
@SpringBootTest
@Testcontainers
class RedisSamhanRealtimeBrokerIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("samhan.realtime.broker", () -> "redis");
    }
    // 다중 노드 fan-out IT (PR-H2 SlipRealtimeBrokerConcurrencyIT 패턴 일반화)
}
```

> **Windows Docker caveat** (`feedback_testcontainers_windows_docker`): `DOCKER_HOST=tcp://localhost:2375` 우회 필요. CI ubuntu-latest 는 무리 없음.

---

## 관련 문서

- [`infrastructure/env-templates/slip-service.env`](../../infrastructure/env-templates/slip-service.env)
- [`services/slip-service/src/main/resources/application.yml`](../../services/slip-service/src/main/resources/application.yml)
- [`docs/devops/realtime-sse-production.md`](./realtime-sse-production.md) (PR-H1 nginx / ALB 가이드)
- [`docs/uiux/phase12/H4a-shared-realtime-pattern.md`](../uiux/phase12/H4a-shared-realtime-pattern.md) (PR-H4a Designer 패턴 가이드 — 14 service / 50+ page)
- [`docs/qa/phase-12-step-4a-shared-realtime-module/scenarios.md`](../qa/phase-12-step-4a-shared-realtime-module/scenarios.md) (PR-H4a QA 시나리오 — shared module 단위 + slip-service 회귀)
- Phase 11 AWS 단일 환경 결정 (DECISIONS — Phase 11 entry)
- [feedback_testcontainers_windows_docker] (Windows Docker IT 가이드)
