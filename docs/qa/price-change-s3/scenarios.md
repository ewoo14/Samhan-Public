# PR #688 S3 주문 자동전환 QA 시나리오 결과

브랜치: feat/price-change-s3-order-switch  
HEAD: 9b07853ea  
실행일: 2026-07-01  
QA 담당: QA agent (자동)

---

## Step 1 — BE 테스트 [PASS]

### 실행 명령
```
DOCKER_HOST=tcp://localhost:2375 ./gradlew :services:partner-order-service:test \
  --tests "*Bootstrap*" --tests "*EstimateCatalogClientTest" --rerun-tasks
```

### 결과 (JUnit XML 실증)

| 테스트 파일 | 건수 | PASS | FAIL | SKIP |
|---|---|---|---|---|
| EstimateCatalogClientTest | 2 | 2 | 0 | 0 |
| BootstrapServiceTest | 4 | 4 | 0 | 0 |
| PartnerOrderBootstrapIT | 1 | 1 | 0 | 0 |
| **합계** | **7** | **7** | **0** | **0** |

주요 테스트:
- `catalog_scope를_query_param으로_전달하고_data를_언랩한다()` — PASS
- `components_materialPrices_priceBaseline_priceChangeSchedule_모두_data를_언랩한다()` — PASS  
- `bootstrap_17_keys_seeded_and_dc_secrets_stripped_from_config()` — PASS (Testcontainers Postgres 16-alpine, Docker 가용)
- `fetch_productDb_catalog를_legacy_bootstrap_shape로_변환한다()` — PASS
- `prefetch_시트read_성공시_GAS와_동일하게_base와_단가인상_source가_seed보다_우선하고_config는_seed_fallback()` — PASS

---

## Step 2 — order-app vitest [PASS]

### 실행 명령
```
npm --prefix clients/web/order-app test
```

### 결과

```
Test Files  5 passed (5)
      Tests  14 passed (14)
   Duration  510ms
```

- `priceChangeSchedule.test.ts` 3건 포함 전수 PASS

---

## Step 3 — 금액 전환 실증 [PASS]

### 구현 확인 (index.html L1388-1392)

```js
function incActive(categoryKey, due) {
  const effectiveDate = PRICE_CHANGE_SCHEDULE && PRICE_CHANGE_SCHEDULE[categoryKey];
  if (!effectiveDate || !due) return false;
  return due < String(effectiveDate);
}
```

**판정 로직**: `due < effectiveDate` → INC 인상전 단가, `due >= effectiveDate` → base 인상후 단가.

### vitest 케이스 전환 증거

schedule 주입: `homemulti='2026-12-01', commercialMulti='2026-12-01', singleSets='2026-12-01'`

**케이스 A: due=2026-11-30 (변동일 전) → 인상전(INC) 단가 사용**

| 함수 | 기대값(INC) | 실제값 | 판정 |
|---|---|---|---|
| homeUnitPrice('HM1') | 1000 | 1000 | PASS |
| commUnitPrice('CM1') | 2000 | 2000 | PASS |
| singleUnitPrice({model:'SS1'}) | 3000 | 3000 | PASS |
| partUnitPrice({model:'SP1'}) | 4000 | 4000 | PASS |
| setBasePriceRightFirst({model:'SS1'}) | 3000 | 3000 | PASS |

**케이스 B: due=2026-12-01 (변동일 이상) → 인상후(base) 단가 사용**

| 함수 | 기대값(base) | 실제값 | 판정 |
|---|---|---|---|
| homeUnitPrice('HM1') | 1100 | 1100 | PASS |
| commUnitPrice('CM1') | 2100 | 2100 | PASS |
| singleUnitPrice({model:'SS1'}) | 3100 | 3100 | PASS |
| partUnitPrice({model:'SP1'}) | 4100 | 4100 | PASS |
| setBasePriceRightFirst({model:'SS1'}) | 3100 | 3100 | PASS |

**케이스 C: schedule 없음 → 항상 base(인상후) 단가**

| 함수 | 기대값(base) | 실제값 | 판정 |
|---|---|---|---|
| homeUnitPrice('HM1') | 1100 | 1100 | PASS |
| commUnitPrice('CM1') | 2100 | 2100 | PASS |
| singleUnitPrice({model:'SS1'}) | 3100 | 3100 | PASS |
| partUnitPrice({model:'SP1'}) | 4100 | 4100 | PASS |

**baseline 결측 모델 유지**: `incActive && HOME_INC[model]` 단락평가 — INC 맵에 모델 없으면 false → base 유지. PASS.

---

## Step 4 — 실 렌더 스크린샷 [PASS]

### 서비스 상태

| 서비스 | 포트 | 상태 |
|---|---|---|
| samhan-partner-order-service | 18088→8088 | Up, healthy |
| samhan-product-service | 8084 | Up, healthy |
| samhan-api-gateway | 8080 | Up, healthy |

### Flyway V22 적용

```sql
-- price_change_schedule 테이블 생성 + 4종 시드
INSERT INTO price_change_schedule (category, effective_date)
VALUES
  ('homemulti',       '2026-04-01'),
  ('singleSets',      '2026-04-01'),
  ('commercialMulti', '2026-04-01'),
  ('oldProducts',     '2026-04-01');
```

적용 일시: 2026-07-01 06:30:02

### Bootstrap API 응답 확인

```
GET /api/v1/partner-orders/bootstrap  →  17 keys
priceChangeSchedule: {
  "homemulti": "2026-04-01",
  "singleSets": "2026-04-01",
  "commercialMulti": "2026-04-01",
  "oldProducts": "2026-04-01"
}
```

스크린샷: `docs/qa/price-change-s3/01-order-app-initial.png`  
(삼한공조시스템 주문서 — 사업자등록번호 입력 화면, bootstrap 정상 로드)

---

## 도메인 정합성 확인

- price_change_schedule 테이블 4행 (category CHECK 제약 통과)
- bootstrap 17키 — priceChangeSchedule 포함 단일 응답 (16→17 확인)
- incActive() = `due < effectiveDate` 문자열 비교 (yyyy-MM-dd 정렬 보장)

## 참고: homeInc/commInc 현황

현재 `price_history` 0행 → `homeInc/commInc/singleInc = {}`.  
이는 dev 환경 설계 상태 (실 운영 시 인상전 단가 데이터 추가 예정).  
incActive 가 true 일 때 `INC[model]` 도 undefined → false → base 사용 — 정상.
