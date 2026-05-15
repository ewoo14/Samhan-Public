# D-AX-22 도메인 정합성 체크

## 목적

UUID-free 계약은 단순 화면 치환이 아니라 데이터 경계 계약이다. DB 는 내부 UUID 를 유지하되 driver-facing/API/display/export 경계에서는 업무 식별자와 표시명만 노출해야 한다.

## SQL 점검안

```sql
-- 1. 오늘 기사앱 target 이 1개 정차로만 해석되는지 확인한다.
-- :driver_id, :dispatch_type, :vehicle_sequence, :stop_sequence, :parsed_kakao_seq 는 테스트 fixture 값.
select s.id, v.id as vehicle_id, d.id as dispatch_id
from vehicle_stops s
join vehicles v on v.id = s.vehicle_id
join dispatches d on d.id = v.dispatch_id
where s.is_deleted = false
  and v.is_deleted = false
  and d.is_deleted = false
  and v.assigned_driver_id = :driver_id
  and d.dispatch_date = current_date
  and d.dispatch_type = :dispatch_type
  and v.sequence = :vehicle_sequence
  and s.sequence = :stop_sequence
  and (:parsed_kakao_seq is null or s.parsed_kakao_seq = :parsed_kakao_seq);

-- 기대: exactly 1 row. 0 rows = 권한/날짜/정차 불일치, 2+ rows = today target unique 보강 필요.
```

```sql
-- 2. sign-and-send-copy 성공 row 는 copy_sent_at 이 있을 때 copy image path 를 함께 가진다.
-- Signature#isCopySent() 는 DB 컬럼이 아니라 copy_sent_at != null 계산값이다.
select id, stop_id, copy_image_path, copy_sent_at, copy_send_failure_count
from signatures
where is_deleted = false
  and (
    (copy_sent_at is not null and copy_image_path is null)
    or (copy_sent_at is null and copy_image_path is not null)
  );

-- 기대: 0 rows.
```

```sql
-- 3. renderer/storage 실패 row 는 재시도 가능해야 하며 copy_sent_at 이 없어야 한다.
-- 현재 DB 는 실패 사유 enum 을 저장하지 않고 copy_send_failure_count 만 누적한다.
select id, stop_id, copy_send_failure_count, copy_sent_at, copy_image_path
from signatures
where is_deleted = false
  and copy_send_failure_count > 0
  and (copy_sent_at is not null or copy_image_path is not null);

-- 기대: 0 rows.
```

```sql
-- 4. GPS 보고는 driver_id + captured_date 로 조회 가능하고 source enum 이 허용값이어야 한다.
select id, driver_id, latitude, longitude, captured_at, captured_date, source
from driver_locations
where captured_date = current_date
  and (
    latitude is null
    or longitude is null
    or captured_at is null
    or source not in ('APP_GPS_ACTIVE', 'APP_GPS_BACKGROUND', 'EXTERNAL_INSUNG_LBS', 'MANUAL')
  );

-- 기대: 0 rows.
```

```sql
-- 5. sourceWarehouseName 이 UUID placeholder 로 노출될 위험을 찾는다.
-- slip-service full detail 생성 전 단계에서 source_warehouse_id 만 있고 표시명이 join/lookup 되지 않는 fixture를 탐지한다.
select id, slip_no, source_warehouse_id
from slips
where is_deleted = false
  and source_warehouse_id is not null;

-- 기대: D-AX22 테스트에서는 이 row 를 사용해 full detail 응답의 sourceWarehouseName 이 UUID 문자열이 아닌지 별도 API assertion 으로 검증한다.
```

## API/문서 검색 가드

```powershell
# 캡처/fixture 원본에는 실제 UUID/raw URL/storage key 금지.
# 본 QA 문서는 금지어를 설명하므로 scan 대상에서 제외한다.
rg -n "downloadUrl|storageKey|objectKey|X-Amz-|presigned|https?://|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}" docs/qa/d-ax-22-uuid-free-contract-hardening/screenshots docs/qa/d-ax-22-uuid-free-contract-hardening/fixtures
```

```powershell
# 모바일 driver 화면 테스트는 금지 패턴 assertion 을 포함해야 한다.
rg -n "not\\.toMatch\\(UUID_REGEX\\)|not\\.toContain\\('downloadUrl'\\)|not\\.toMatch\\(/downloadUrl\\|storageKey" clients/arologis-mobile/src/__tests__
```

```powershell
# 데스크톱 Playwright privacy spec 은 rendered text 기준 금지 패턴을 검사해야 한다.
rg -n "downloadUrl|storageKey|UUID_PATTERN|not\\.toMatch\\(UUID_REGEX\\)|innerText\\(\\)" clients/desktop/playwright clients/desktop/src/renderer
```

## 정합성 PASS 기준

- today target SQL 은 테스트 fixture 별 exactly 1 row 다.
- `signatures.copy_sent_at` 이 있는 row 는 `copy_image_path` 를 함께 가지고, 실패 row 는 `copy_sent_at` 이 없다.
- `driver_locations` row 는 좌표/capturedAt/source/capturedDate 를 모두 가진다.
- slip full detail API 는 `source_warehouse_id` 를 그대로 문자열화하지 않는다.
- D-AX22 QA 문서/캡처/모바일 UI/데스크톱 UI 에 UUID/downloadUrl/storageKey 금지 패턴이 없다.
