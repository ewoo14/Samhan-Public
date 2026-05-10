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

## 관련 문서

- [`infrastructure/env-templates/slip-service.env`](../../infrastructure/env-templates/slip-service.env)
- [`services/slip-service/src/main/resources/application.yml`](../../services/slip-service/src/main/resources/application.yml)
- [`docs/devops/realtime-sse-production.md`](./realtime-sse-production.md) (PR-H1 nginx / ALB 가이드)
- Phase 11 AWS 단일 환경 결정 (DECISIONS — Phase 11 entry)
- [feedback_testcontainers_windows_docker] (Windows Docker IT 가이드)
