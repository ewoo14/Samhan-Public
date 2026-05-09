# 도메인 정합성 검증 SQL 모음

> **목적**: 시나리오 1~7 완료 후 시드/실시간 데이터 전반의 cross-DB 정합성을 일괄 검증
> **선행 조건**: 시나리오 1~7 happy path 모두 통과
> **소요 시간**: 약 5분
> **인용**: README §4 의 10건 check 표 매핑

---

## 0. 실행 패턴

각 SQL 은 `docker exec -it samhan-postgres psql -U samhan -d <DB> -c "<SQL>"` 형식으로 실행.
모든 mismatch 결과는 **0 row** 가 기대값. mismatch ≥ 1 시 즉시 BE 팀 alert + root cause 파악.

```sh
# 본 문서 SQL 일괄 실행 헬퍼 (PowerShell)
function Invoke-IntegrityCheck {
    param([string]$DB, [string]$SQL, [string]$Name)
    Write-Host "===== $Name (DB=$DB) ====="
    docker exec -i samhan-postgres psql -U samhan -d $DB -c $SQL
}
```

---

## 1. C1 — Slip.partner_id ↔ partners.id 정합성 (slip_db ↔ partner_db)

### 1.1 단일 DB 검증 (slip_db 측 dangling)

slip_db 안에서 partner_id 가 NULL 이 아닌 행 중 — 같은 DB 의 partner table 미존재 (cross-DB 이므로 직접 FK 불가, application-side logical reference).

```sql
-- slip_db
SELECT count(*) AS dangling_partner
FROM slips s
WHERE s.partner_id IS NOT NULL AND NOT s.is_deleted;
```

**기대값**: 정상 row 의 count 반환. mismatch 자체 검증은 cross-DB 필요.

### 1.2 cross-DB 검증 (partner_id sample 1건씩)

slip_db 에서 unique partner_id 추출 → partner_db 에서 존재 확인.

```sh
# slip_db 측 unique partner_id list
docker exec -t samhan-postgres psql -U samhan -d slip_db -At \
  -c "SELECT DISTINCT partner_id FROM slips WHERE partner_id IS NOT NULL AND NOT is_deleted;" \
  > /tmp/slip-partner-ids.txt

# partner_db 측 존재 검증 (각 id 별로 SELECT 또는 IN clause 일괄)
PARTNER_IDS=$(cat /tmp/slip-partner-ids.txt | tr '\n' ',' | sed 's/,$//' | sed "s/[a-f0-9-]\{36\}/'\0'/g")

docker exec -t samhan-postgres psql -U samhan -d partner_db \
  -c "SELECT count(*) AS missing_partners FROM (
        SELECT unnest(ARRAY[$PARTNER_IDS]::uuid[]) AS sid
      ) s
      WHERE NOT EXISTS (SELECT 1 FROM partners p WHERE p.id = s.sid AND NOT p.is_deleted);"
```

**기대값**: `missing_partners == 0`

### 1.3 (대안) dev-tool 단일 query 검증

production cutover 시 단일 query 로 검증할 수 있도록 — application-level dev-tool 또는 CTE.
PostgreSQL 16 의 `dblink` 또는 `postgres_fdw` 사용 시:

```sql
-- 1회만 setup (DBA 권한)
CREATE EXTENSION IF NOT EXISTS postgres_fdw;
CREATE SERVER partner_srv FOREIGN DATA WRAPPER postgres_fdw OPTIONS (host 'localhost', dbname 'partner_db');
CREATE USER MAPPING FOR samhan SERVER partner_srv OPTIONS (user 'samhan', password 'samhan_dev_pw');
IMPORT FOREIGN SCHEMA public LIMIT TO (partners) FROM SERVER partner_srv INTO public;

-- 정합성 검증
SELECT count(*) AS missing_partners
FROM slips s
WHERE s.partner_id IS NOT NULL AND NOT s.is_deleted
  AND NOT EXISTS (SELECT 1 FROM partners_remote p WHERE p.id = s.partner_id AND NOT p.is_deleted);
```

**기대값**: `missing_partners == 0` (dev-tool 한정 — production 에서는 partner-service Feign client 의 batch lookup endpoint 권장).

---

## 2. C2 — SlipLine.product_id ↔ products.id (slip_db ↔ product_db)

### 2.1 cross-DB 검증

```sh
# slip_db 측 unique product_id
docker exec -t samhan-postgres psql -U samhan -d slip_db -At \
  -c "SELECT DISTINCT product_id FROM slip_lines WHERE NOT is_deleted;" > /tmp/slip-product-ids.txt

PRODUCT_IDS=$(cat /tmp/slip-product-ids.txt | tr '\n' ',' | sed 's/,$//' | sed "s/[a-f0-9-]\{36\}/'\0'/g")

docker exec -t samhan-postgres psql -U samhan -d product_db \
  -c "SELECT count(*) AS missing_products FROM (
        SELECT unnest(ARRAY[$PRODUCT_IDS]::uuid[]) AS sid
      ) s
      WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id = s.sid AND NOT p.is_deleted);"
```

**기대값**: `missing_products == 0`

### 2.2 (대안) dev-tool

위 §1.3 패턴 재사용 (`product_srv` 신규 server 생성).

---

## 3. C3 — Journal 복식부기 차/대 합계 일치 (accounting_db)

```sql
-- accounting_db
SELECT journal_id, SUM(debit_amount) AS total_debit, SUM(credit_amount) AS total_credit
FROM journal_lines
WHERE NOT is_deleted
GROUP BY journal_id
HAVING SUM(debit_amount) <> SUM(credit_amount);
```

**기대값**: **0 row** (모든 분개 차/대 일치)

mismatch 발견 시 → 해당 journal_id 의 lines 전체 + Journal.status 확인.
DRAFT 단계의 임시 입력 가능성 — service 의 도메인 메서드 검증 우회 여부 확인.

---

## 4. C4 — Journal accountCode 한국 표준 65 코드 한정 (accounting_db)

```sql
-- accounting_db
SELECT DISTINCT account_code
FROM journal_lines jl
WHERE NOT jl.is_deleted
  AND NOT EXISTS (
    SELECT 1 FROM chart_of_accounts c
    WHERE c.code = jl.account_code AND NOT c.is_deleted
  );
```

**기대값**: **0 row**

mismatch 발견 시 → accountCode 가 V1 시드 65 코드 외 — service 의 leaf 검증 우회 또는 시드 누락 확인.

### 4.1 통제 계정 (is_leaf=false) 사용 검증

```sql
SELECT jl.journal_id, jl.line_no, jl.account_code, c.name, c.is_leaf
FROM journal_lines jl
JOIN chart_of_accounts c ON c.code = jl.account_code
WHERE NOT c.is_leaf AND NOT jl.is_deleted;
```

**기대값**: **0 row** (통제 계정 — 100/200/300/400/500/800/900 — 분개 사용 금지)

---

## 5. C5 — DeliveryBatch (driver_phone, batch_date) partial unique (slip_db)

```sql
-- slip_db
SELECT driver_phone, batch_date, count(*) AS dup_count
FROM delivery_batches
WHERE NOT is_deleted
GROUP BY 1,2
HAVING count(*) > 1;
```

**기대값**: **0 row** (V4 partial unique index `uk_delivery_batches_driver_date` 가드)

### 5.1 token UNIQUE 검증

```sql
SELECT batch_token, count(*) FROM delivery_batches GROUP BY 1 HAVING count(*) > 1;
```

**기대값**: **0 row** (`uk_delivery_batches_token` UNIQUE 제약)

### 5.2 slips.delivery_batch_id FK 정합성

```sql
SELECT count(*) AS dangling_slips
FROM slips s
WHERE s.delivery_batch_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM delivery_batches db WHERE db.id = s.delivery_batch_id);
```

**기대값**: **0 row**

---

## 6. C6 — Idempotency 검증 (seeder 2회 실행 후 row count 동일)

각 seeder 가 `existsBy*` 또는 `count() > 0` 가드를 갖춘 idempotency 검증.
**실행 절차**:

1. 시드 toggle 활성화 상태에서 14 service 1회 기동 → row count 기록
2. 14 service stop → 재기동 (시드 toggle 유지)
3. row count 재기록 → 1 과 비교

```sh
# 1회 기동 후
docker exec -t samhan-postgres psql -U samhan -d user_db        -At -c "SELECT count(*) FROM employees WHERE NOT is_deleted;"  > /tmp/count-1.txt
docker exec -t samhan-postgres psql -U samhan -d partner_db     -At -c "SELECT count(*) FROM partners WHERE NOT is_deleted;"   >> /tmp/count-1.txt
docker exec -t samhan-postgres psql -U samhan -d product_db     -At -c "SELECT count(*) FROM products WHERE NOT is_deleted;"   >> /tmp/count-1.txt
docker exec -t samhan-postgres psql -U samhan -d slip_db        -At -c "SELECT count(*) FROM slips WHERE NOT is_deleted;"      >> /tmp/count-1.txt
docker exec -t samhan-postgres psql -U samhan -d accounting_db  -At -c "SELECT count(*) FROM journals WHERE NOT is_deleted;"   >> /tmp/count-1.txt
docker exec -t samhan-postgres psql -U samhan -d arologis_db    -At -c "SELECT count(*) FROM dispatches WHERE NOT is_deleted;" >> /tmp/count-1.txt

# 14 service 재기동 후
docker exec -t samhan-postgres psql -U samhan -d user_db        -At -c "SELECT count(*) FROM employees WHERE NOT is_deleted;"  > /tmp/count-2.txt
# ... 동일 query

# 비교
diff /tmp/count-1.txt /tmp/count-2.txt
```

**기대값**: `diff` 결과 빈 출력 (양쪽 동일).

---

## 7. C7 — slip_publish_audit 멱등 (idempotency_key) (slip_db)

```sql
-- slip_db
SELECT idempotency_key, count(*) AS publish_count
FROM slip_publish_audit
WHERE idempotency_key IS NOT NULL AND NOT is_deleted
GROUP BY 1
HAVING count(*) > 1;
```

**기대값**: **0 row** (같은 idempotency_key 로 발행된 slip 은 1건만 — replay 시 신규 audit row 생성 X)

### 7.1 같은 source 중복 발행 검증

```sql
SELECT source_type, source_id, count(*) AS slip_count
FROM slip_publish_audit
WHERE NOT is_deleted
GROUP BY 1,2
HAVING count(*) > 1;
```

**기대값**: **0 row** (같은 (source_type, source_id) 는 1 slip 만 — partner_order 등)

---

## 8. C8 — Slip.requester_id ↔ employees / system (slip_db ↔ user_db)

```sh
# slip_db 측 unique requester_id (UUID 또는 'system')
docker exec -t samhan-postgres psql -U samhan -d slip_db -At \
  -c "SELECT DISTINCT requester_id FROM slips WHERE NOT is_deleted;" > /tmp/requester-ids.txt
```

`requester_id` 가 `system` 또는 UUID — UUID 인 경우 user_db.employees 에 존재 검증.

```sh
# UUID 만 추출 (length 36)
grep -E "^[a-f0-9-]{36}$" /tmp/requester-ids.txt > /tmp/requester-uuids.txt

# user_db 검증 (sample loop)
while read uuid; do
  EXISTS=$(docker exec -t samhan-postgres psql -U samhan -d user_db -At \
    -c "SELECT count(*) FROM employees WHERE id='$uuid';")
  if [ "$EXISTS" = "0" ]; then
    echo "MISSING: $uuid"
  fi
done < /tmp/requester-uuids.txt
```

**기대값**: 출력 빈 (모든 requester_id UUID 가 employees 에 존재)

---

## 9. C9 — vehicles.assigned_driver_id ↔ drivers.id (arologis_db, 단일 DB)

```sql
-- arologis_db
SELECT count(*) AS dangling_drivers
FROM vehicles v
WHERE v.assigned_driver_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM drivers d WHERE d.id = v.assigned_driver_id);
```

**기대값**: **0 row**

### 9.1 Vehicle dispatch_id FK 정합

```sql
SELECT count(*) AS dangling_dispatches
FROM vehicles v
WHERE NOT EXISTS (SELECT 1 FROM dispatches d WHERE d.id = v.dispatch_id);
```

**기대값**: **0 row** (FK 제약 보장)

### 9.2 VehicleStop vehicle_id FK 정합

```sql
SELECT count(*) AS dangling_vehicles
FROM vehicle_stops s
WHERE NOT EXISTS (SELECT 1 FROM vehicles v WHERE v.id = s.vehicle_id);
```

**기대값**: **0 row**

### 9.3 Driver app_user_id partial unique (V2)

```sql
SELECT app_user_id, count(*) FROM drivers
WHERE NOT is_deleted AND app_user_id IS NOT NULL
GROUP BY 1 HAVING count(*) > 1;
```

**기대값**: **0 row** (V2 partial unique index `ux_drivers_app_user_active`)

---

## 10. C10 — kpi_snapshots / realtime_stocks / sales_aggregates partial unique (dashboard_db)

```sql
-- dashboard_db
-- KPI
SELECT snapshot_date, category, count(*)
FROM kpi_snapshots WHERE NOT is_deleted
GROUP BY 1,2 HAVING count(*) > 1;

-- Realtime stock
SELECT product_id, warehouse_code, count(*)
FROM realtime_stocks WHERE NOT is_deleted
GROUP BY 1,2 HAVING count(*) > 1;

-- Sales aggregate
SELECT aggregate_date, partner_id, count(*)
FROM sales_aggregates WHERE NOT is_deleted
GROUP BY 1,2 HAVING count(*) > 1;
```

**기대값**: 3 query 모두 **0 row**

### 10.1 enum 가드 검증

```sql
SELECT DISTINCT category FROM kpi_snapshots;
```

**기대값**: `DAILY_SALES / WEEKLY_SALES / MONTHLY_SALES / ORDER_COUNT / ACTIVE_PARTNERS / STOCK_TURNOVER` 만.

---

## 11. 추가 — Slip 도메인 무결성 (slip_db)

### 11.1 status 전이 무결성 — confirmed_at 은 CONFIRMED 상태에서만

```sql
SELECT count(*) FROM slips
WHERE confirmed_at IS NOT NULL AND status NOT IN ('CONFIRMED','REVERSED') AND NOT is_deleted;
```

**기대값**: **0 row**

### 11.2 OUTBOUND 슬립의 source_warehouse_id 필수

```sql
SELECT count(*) FROM slips
WHERE slip_type='OUTBOUND' AND source_warehouse_id IS NULL AND status <> 'DRAFT' AND NOT is_deleted;
```

**기대값**: **0 row**

### 11.3 INBOUND 슬립의 destination_warehouse_id 필수

```sql
SELECT count(*) FROM slips
WHERE slip_type='INBOUND' AND destination_warehouse_id IS NULL AND status <> 'DRAFT' AND NOT is_deleted;
```

**기대값**: **0 row**

### 11.4 SlipLine 라인 합계 == quantity × unit_price

```sql
SELECT count(*) AS mismatch_lines
FROM slip_lines
WHERE NOT is_deleted
  AND ABS(line_total - (quantity * unit_price)) > 0.01;
```

**기대값**: **0 row** (소수점 0.01 까지 허용)

### 11.5 slip_no 활성 unique

```sql
SELECT slip_no, count(*) FROM slips WHERE NOT is_deleted GROUP BY 1 HAVING count(*) > 1;
```

**기대값**: **0 row** (V1 `ux_slips_slip_no_active`)

---

## 12. 추가 — Soft Delete 일관성 (모든 DB)

```sql
-- 각 DB 별 — soft-delete row 의 deleted_at + deleted_by null 검증
SELECT 'slips' AS tbl, count(*) FROM slips WHERE is_deleted AND (deleted_at IS NULL OR deleted_by IS NULL)
UNION ALL
SELECT 'partners', count(*) FROM partners WHERE is_deleted AND (deleted_at IS NULL OR deleted_by IS NULL)
UNION ALL
SELECT 'products', count(*) FROM products WHERE is_deleted AND (deleted_at IS NULL OR deleted_by IS NULL)
UNION ALL
SELECT 'journals', count(*) FROM journals WHERE is_deleted AND (deleted_at IS NULL OR deleted_by IS NULL);
```

**기대값**: 모든 row 의 count == 0 (soft-delete 시 audit field 동시 기입)

> 본 query 는 각 DB 별 분리 실행 필요 (cross-DB UNION 불가).
> partition: slips → slip_db, partners → partner_db, products → product_db, journals → accounting_db.

---

## 13. 종합 검증 스크립트 (PowerShell)

```powershell
# C:\dev\SamhanLogis\docs\qa\local-test-seed-data\run-integrity-check.ps1
# (PR 머지 전 PM 자동 실행 권장)

$checks = @(
    @{ Name = "C3 복식부기"; DB = "accounting_db"; Sql = "SELECT count(*) FROM (SELECT journal_id FROM journal_lines WHERE NOT is_deleted GROUP BY journal_id HAVING SUM(debit_amount) <> SUM(credit_amount)) x;" },
    @{ Name = "C4 비표준 accountCode"; DB = "accounting_db"; Sql = "SELECT count(DISTINCT account_code) FROM journal_lines jl WHERE NOT jl.is_deleted AND NOT EXISTS (SELECT 1 FROM chart_of_accounts c WHERE c.code = jl.account_code);" },
    @{ Name = "C5 DeliveryBatch partial unique"; DB = "slip_db"; Sql = "SELECT count(*) FROM (SELECT driver_phone, batch_date FROM delivery_batches WHERE NOT is_deleted GROUP BY 1,2 HAVING count(*) > 1) x;" },
    @{ Name = "C7 idempotency_key 멱등"; DB = "slip_db"; Sql = "SELECT count(*) FROM (SELECT idempotency_key FROM slip_publish_audit WHERE idempotency_key IS NOT NULL AND NOT is_deleted GROUP BY 1 HAVING count(*) > 1) x;" },
    @{ Name = "C9 Vehicle assigned_driver dangling"; DB = "arologis_db"; Sql = "SELECT count(*) FROM vehicles v WHERE v.assigned_driver_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM drivers d WHERE d.id = v.assigned_driver_id);" },
    @{ Name = "C10 KPI partial unique"; DB = "dashboard_db"; Sql = "SELECT count(*) FROM (SELECT snapshot_date, category FROM kpi_snapshots WHERE NOT is_deleted GROUP BY 1,2 HAVING count(*) > 1) x;" }
)

$failures = 0
foreach ($c in $checks) {
    $result = docker exec -i samhan-postgres psql -U samhan -d $c.DB -At -c $c.Sql
    if ([int]$result -ne 0) {
        Write-Host "FAIL: $($c.Name) — $result mismatches" -ForegroundColor Red
        $failures++
    } else {
        Write-Host "PASS: $($c.Name)" -ForegroundColor Green
    }
}

if ($failures -gt 0) {
    Write-Host "`n$failures integrity checks FAILED. Block PR merge." -ForegroundColor Red
    exit 1
} else {
    Write-Host "`nAll integrity checks PASSED." -ForegroundColor Green
    exit 0
}
```

> 위 스크립트는 PR 머지 전 PM 자동 호출 권장 (`feedback_pr_ci_monitoring.md` 패턴).

---

## 14. 종료 기준

- [ ] §1 C1 missing_partners == 0
- [ ] §2 C2 missing_products == 0
- [ ] §3 C3 복식부기 mismatch == 0
- [ ] §4 C4 비표준 accountCode == 0 + 통제 계정 사용 == 0
- [ ] §5 C5 (driver_phone, batch_date) 중복 == 0 + token 중복 == 0 + dangling slips == 0
- [ ] §6 C6 seeder 2회 실행 row count diff == 0
- [ ] §7 C7 idempotency_key 중복 == 0 + (source_type, source_id) 중복 == 0
- [ ] §8 C8 requester_id dangling == 0
- [ ] §9 C9 assigned_driver dangling == 0 + dispatch FK == 0 + app_user_id 중복 == 0
- [ ] §10 C10 dashboard 3 partial unique 모두 == 0
- [ ] §11 Slip 도메인 5 추가 검증 모두 == 0
- [ ] §12 Soft Delete audit field 4 DB 모두 == 0

위 13건 모두 PASS 시 — 본 시나리오 PR 머지 가능.
실패 1건 이상 시 — 즉시 BE 팀 alert + RCA + fix 후 재검증.

---

## 15. 회귀 가드 / 알려진 이슈

| 이슈 | 회피책 |
|---|---|
| Cross-DB FK 검증 (C1/C2/C8) | dev-tool 또는 postgres_fdw 활용. production 에서는 service-to-service Feign 호출로 graceful 검증 |
| postgres_fdw setup 비용 | dev 환경 1회 setup 후 영구 사용. cutover 시 별도 검증 불필요 |
| 시드 데이터 격리 (시나리오 1~7 가 신규 row 추가) | 본 검증은 시드 + 시나리오 통과 후 일괄 실행 — 중간 검증은 별도 baseline |
| Idempotency seeder 가드 누락 (`feedback_multi_agent_team_pattern.md`) | 모든 신규 seeder 의 `existsBy*` 가드 PR review 필수 |
| 한국어 데이터 깨짐 (PowerShell `Set-Content`) | 본 SQL 모음 모두 ASCII — UTF-8 트랩 회피 |
