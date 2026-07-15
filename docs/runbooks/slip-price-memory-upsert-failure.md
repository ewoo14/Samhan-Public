# slip-service 가격기억 upsert 실패 runbook

## 경보

- Alert: `SlipPriceMemoryUpsertFailure`
- 조건: `sum(increase(slip_price_memory_upsert_failed_total{job="slip-service"}[5m])) > 0`
- 영향: 원 전표/견적 저장은 fail-soft 로 성공하지만, 실패한 라인의 최근단가 자동채움은 이전 값 또는 miss 로 남을 수 있다. `/actuator/health` 는 계속 `UP` 일 수 있다.

> 위 Alert 이름은 dev 로컬 Prometheus rule
> (`infrastructure/prometheus/rules/slip-price-memory.yml`) 기준이다. prod(Phase 11
> AWS)는 기존 CloudWatch Agent 가 Docker json-file 로그를
> `/samhanlogis/production/docker` 로 수집하고, `monitoring.tf` 의 metric filter 2건
> (`batch upsert failed`, `queue rejected`)과 alarm 2건이 등가 감지를 담당한다.
> 배포 확인 절차는 `infrastructure/terraform/CUTOVER.md` **M-19**를 따른다.

## 실제 Prometheus metric 이름

| 목적 | metric |
|---|---|
| 성공 command 수 | `slip_price_memory_upsert_success_total` |
| 실패/queue 거부 command 수 | `slip_price_memory_upsert_failed_total` |
| batch 크기 | `slip_price_memory_batch_size_count`, `_sum`, `_max` |
| REQUIRES_NEW 지연 | `slip_price_memory_upsert_duration_seconds_count`, `_sum`, `_max` |

Micrometer meter 이름과 Prometheus export 변환은 `PartnerProductPriceMemoryMetricsTest` 가 실제 registry `scrape()` 결과로 잠근다.

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
