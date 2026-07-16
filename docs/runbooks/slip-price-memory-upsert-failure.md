# slip-service 가격기억 upsert 실패 runbook

## 경보

- Alert: `SlipPriceMemoryUpsertFailure`
- 조건: `sum(increase(slip_price_memory_upsert_failed_total{job="slip-service"}[5m])) > 0`
- 영향: 원 전표/견적 저장은 fail-soft 로 성공하지만, 실패한 라인의 최근단가 자동채움은 이전 값 또는 miss 로 남을 수 있다. `/actuator/health` 는 계속 `UP` 일 수 있다.

> 위 Alert 이름은 dev 로컬 Prometheus rule
> (`infrastructure/prometheus/rules/slip-price-memory.yml`) 기준이다. prod(Phase 11
> AWS)는 slip-service 컨테이너 로그를 `docker-compose.prod.yml` 의 `awslogs` driver 가
> `/samhanlogis/production/docker` (stream `slip-service`) 로 직접 전달하고,
> `monitoring.tf` 의 metric filter 2건(`batch upsert failed`, `queue rejected`)과
> alarm 2건이 등가 감지를 담당하도록 **설계**되어 있다. 단, 실 EC2 부재로 라이브
> 실측이 아직 없어 — **cutover M-19 의 양성 도달 검사(인위 감시 문자열 → CloudWatch
> `filter-log-events` 도달 + alarm 발화 확인)를 통과하기 전까지 이 등가성은
> 미확증이다** (2026-07-16, R6-H5). CloudWatch Agent 의 json 와일드카드 tail 은
> "최신 수정 파일만 push" 되는 AWS 문서 제약으로 alarm 원천이 아니다.
> 배포 확인 절차는 `infrastructure/terraform/CUTOVER.md` **M-19**를 따른다.

## 실제 Prometheus metric 이름

| 목적 | metric |
|---|---|
| 성공 command 수 | `slip_price_memory_upsert_success_total` |
| 실패/queue 거부 command 수 | `slip_price_memory_upsert_failed_total` |
| **recency guard 미갱신(skip) command 수** | **`slip_price_memory_upsert_skipped_total`** (R8-BE-6 신규) |
| batch 크기 | `slip_price_memory_batch_size_count`, `_sum`, `_max` |
| REQUIRES_NEW 지연 | `slip_price_memory_upsert_duration_seconds_count`, `_sum`, `_max` |

> 🔴 **`success` 와 `skipped` 의 의미 차이 (R8-BE-6)** — `remembered_at` 이 기존 행보다 오래된
> command 는 upsert 의 `WHERE ... remembered_at <= EXCLUDED.remembered_at` 절이 갱신을 건너뛴다.
> 이때도 **statement 자체는 성공**이므로 `success` 는 오른다 (D-R4-2 — 최신성 권위 = `remembered_at`
> 캡처 시각). 종전에는 그것이 유일한 신호라 **전량 skip 돼도 100% 성공으로 보였다.**
> `skipped` 가 꾸준히 증가하면 **다중 인스턴스 clock skew** 를 의심하라 — 뒤처진 인스턴스의 저장이
> 조용히 유실된다. 정상 운영에서는 같은 pair 를 짧은 간격으로 두 번 저장할 때만 간헐 발생한다.

Micrometer meter 이름과 Prometheus export 변환은 `PartnerProductPriceMemoryMetricsTest` 가 실제 registry `scrape()` 결과로 잠근다.

## 관련 운영 노브

`SAMHAN_PRICE_MEMORY_*` 노브와 `DB_CONNECTION_TIMEOUT_MS` 는
`infrastructure/env-templates/slip-service.env` 에 주석과 함께 정의되어 있고,
`infrastructure/docker-compose.local-all.yml` · `infrastructure/docker-compose.prod.yml`
(slip-service environment) · `infrastructure/terraform/templates/user_data.sh`
(.env.production) 가 명시 매핑한다 (R5-M6 / R6-M2).

### 🔴 커넥션 획득 상한 — **전용 pool 로 격리됨** (D-R8-2, 2026-07-16)

| 노브 | 적용 대상 | 기본 |
|---|---|---|
| `DB_CONNECTION_TIMEOUT_MS` | slip-service **메인** DataSource (사용자 요청 경로 전체) | **30000ms** (fleet 표준) |
| `SAMHAN_PRICE_MEMORY_DB_CONNECTION_TIMEOUT_MS` | **가격기억 전용** pool (`price-memory-pool`) | **4000ms** |
| `SAMHAN_PRICE_MEMORY_DB_POOL_MAX` / `_MIN_IDLE` | 전용 pool 사이징 (`async-max-pool-size` 와 1:1) | **4** / **0** |

가격기억 worker 는 **메인 pool 과 격리된 별도 Hikari pool**(`SlipDataSourceConfig.priceMemoryDataSource`)
을 쓴다. url/username/password/driver 는 `spring.datasource.*` 를 공유한다 — **같은 DB, 다른 pool** 이다.
4초 상한은 이 전용 pool 안에 갇혀 있어 **원 전표/견적 저장 경로(전역 30초)를 오염시키지 않는다.**

> ⚠️ **이력 (R6-M1 → R8-DEVOPS-2 → D-R8-2)**: 이전에는 `DB_CONNECTION_TIMEOUT_MS` **4초가 slip-service
> 전역**에 적용됐고, 이는 **fleet 26모듈 중 slip-service 유일**한 이탈이었다. R8-DEVOPS-2 가 그 대가를
> 실측했다 — pool 포화 시 21번째 요청이 `SQLTransientConnectionException` → `handleUnknown` →
> **사용자에게 HTTP 500**. 즉 가격기억(부가기능)의 fail-soft 예산이 **전표 저장(핵심기능)의 가용성을
> 깎고 있었다.** D-R8-2 로 전용 DataSource 를 격리하고 전역을 30s 로 복원했다.

- 🔴 **전용 pool 의 `connection-timeout` 은 획득 대기 상한이고, `lock-timeout-ms`/`statement-timeout-ms`/
  `transaction-timeout-seconds` 는 획득 *후* 트랜잭션에 적용된다. 둘을 합친 단일 wall-clock 상한이 아니다.**
- 전용 pool 사이징 변경 시 PostgreSQL `max_connections` 여유를 확인한다(R8-DEVOPS-2 실측: `max_connections=300`,
  당시 사용 141, 전용 4 추가 시 ≈154 — 안전).
- `SAMHAN_PRICE_MEMORY_DB_CONNECTION_TIMEOUT_MS` 상향은 fail-soft 경계를 늦춰 async queue 적체를 키우고,
  하향은 `queue rejected`/실패 계측을 늘린다. **`DB_CONNECTION_TIMEOUT_MS`(전역) 를 가격기억 목적으로
  건드리지 말 것** — 그것이 D-R8-2 가 되돌린 실수다.

## 0차 확인 — 🔴 **경보가 실제로 존재하는가** (1차 확인보다 먼저)

> **이 절이 존재하는 이유** (#809 R8-DEVOPS-1, 2026-07-16): 이 runbook 전체가 *"경보가 울린다"* 를
> 전제하는데, **그 전제가 실제로 거짓이었던 적이 있다.** dev 스택에서 rule 이 **13일간 로드되지 않은
> 상태**로 방치됐고 `GET /api/v1/rules` 가 `{"groups":[]}` 였다. 즉 **가격기억 fail-soft 유실을 탐지할
> 수단이 아예 없었다.** 🔴 **"경보가 안 울렸다 = 정상" 이 아니라 "경보가 아예 없었다" 를 먼저 배제한다.**

```powershell
# 한 번에 검증 (git 의 rule 파일 ↔ 런타임 로드 목록 대조 + health + promtool)
.\infrastructure\scripts\verify-prometheus-rules.ps1
# exit 0 = 경보가 실제로 살아있음. exit 1 = 경보 부재/불일치 → 아래 복구.
```

수동 확인:

```bash
curl -s http://localhost:9090/api/v1/rules
```

| 출력 | 해석 |
|---|---|
| `{"groups":[]}` | 🔴 **경보 런타임 부재** — 아래 복구를 먼저 수행한다. 이 상태에서는 "실패 0건" 이 **아무 의미가 없다** |
| `groups[].rules[].name = SlipPriceMemoryUpsertFailure` + `health: "ok"` | ✅ 경보 생존 — 1차 확인으로 진행 |

**복구** — 🔴 `docker restart` 로는 **안 고쳐진다**:

```bash
docker compose -p infrastructure --project-directory <repo>/infrastructure \
  -f docker-compose.yml -f docker-compose.local-all.yml \
  up -d --force-recreate --no-deps prometheus
```

`prometheus.yml` 은 bind-mount 된 **파일**이라 restart 로 반영되지만, `./prometheus/rules` **디렉토리
바인드는 컨테이너 생성 시점**에만 붙는다. compose 에 마운트를 나중에 추가하면 기존 컨테이너는 그대로
비어 있다. 게다가 **Prometheus 는 `rule_files` glob 이 0개 매치해도 오류를 내지 않아** 로그·헬스체크에
아무 신호가 없다. 자세한 배경은 `infrastructure/README.md` §Alerting rules 참조.

> ⚠️ **rule 이 로드된 것만으로는 불충분하다** — selector 가 실 job 에 붙어야 발화한다. 함께 확인:
> `curl -s 'http://localhost:9090/api/v1/query?query=slip_price_memory_upsert_failed_total'` 가
> `job="slip-service"` label 을 포함한 결과를 반환해야 한다(빈 결과 = scrape target down 또는 label 불일치).

## 1차 확인

1. 실패 시작 시각과 배포/DB 지연 시각을 맞춘다.
2. 로컬은 `docker logs samhan-slip-service --since 10m 2>&1 | grep "partner-product price memory"`, prod는 `aws logs tail /samhanlogis/production/docker --since 10m --filter-pattern '"partner-product price memory"' --region ap-northeast-2` 로 `batch upsert failed` 또는 `queue rejected` 를 구분한다.
3. 로컬 Grafana/Prometheus 또는 prod CloudWatch alarm에서 아래를 확인한다.
   - 실패 증가량: `increase(slip_price_memory_upsert_failed_total{job="slip-service"}[5m])`
   - 성공률: `rate(slip_price_memory_upsert_success_total{job="slip-service"}[5m])`
   - 평균 batch: `rate(slip_price_memory_batch_size_sum[5m]) / rate(slip_price_memory_batch_size_count[5m])`
   - 평균 지연: `rate(slip_price_memory_upsert_duration_seconds_sum[5m]) / rate(slip_price_memory_upsert_duration_seconds_count[5m])`

## 원인별 조치

| 신호 | 해석 | 조치 |
|---|---|---|
| `canceling statement due to lock timeout` | 인기 `(partner_id, product_id)` row 경합이 1초를 초과 | 동시 대량 저장 원인을 확인하고 재시도 폭주 여부를 차단한다. 원 전표 저장을 재실행하지 않는다. |
| `statement timeout` | 최대 100행 set-based upsert 가 3초를 초과 | DB CPU/IO, 장기 트랜잭션, 인덱스 `ux_partner_product_price_memory_pair` 상태를 확인한다. |
| `queue rejected` | 전용 4 worker + 100 queue 가 포화 | slip-service 저장 burst와 DB 지연을 함께 확인한다. 무작정 queue를 늘리면 메모리와 stale 작업만 늘어난다. |
| `no unique or exclusion constraint` | V58 제약 drift 또는 잘못된 repair | 배포를 중단하고 V58 적용 이력/실제 unique 제약을 대조한다. 기능만 조용히 실패한 기간을 산정한다. |

## 복구 확인

1. 원인 해소 후 테스트 전표 한 건을 새 가격으로 저장한다.
2. `GET /slips/price-memory` 단건과 `POST /slips/price-memory/bulk`가 같은 `unitPrice`, `source`, `updatedAt`을 반환하는지 확인한다.
3. 10분 동안 failure 증가가 0이고 success가 증가하는지 확인한다.
4. fail-soft 실패 기간의 가격기억 누락은 원 전표/견적 라인에서 backfill 해야 한다. 전표를 재저장해 회계/재고 side effect를 다시 만들지 않는다.
