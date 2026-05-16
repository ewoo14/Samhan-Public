# SP-01 Partner UI Menu Gap 도메인 정합성 체크

## 목적

SP-01은 UI 메뉴 gap 감사지만, PASS 판정은 partner-service 데이터 계약과 함께 확인해야 한다. UI가 어떤 역할에게 어떤 CTA를 노출하든 DB에는 활성 거래처 식별자 중복, 다중 기본 배송지, 다중 주 담당자, 음수 정책값이 남으면 안 된다.

## SQL 점검안

```sql
-- 1. 활성 partner_code 중복 금지.
select partner_code, count(*) as active_count
from partners
where is_deleted = false
group by partner_code
having count(*) > 1;

-- 기대: 0 rows. partner_code 는 사용자 노출 식별자이며 활성 행 기준 unique.
```

```sql
-- 2. 활성 biz_no 중복 금지.
select biz_no, count(*) as active_count
from partners
where is_deleted = false
group by biz_no
having count(*) > 1;

-- 기대: 0 rows. UI validation 우회 또는 중복 submit race 발견용.
```

```sql
-- 3. 거래처 기본 배송지는 활성 row 기준 최대 1개.
select partner_id, count(*) as default_address_count
from partner_shipping_addresses
where is_deleted = false
  and is_default = true
group by partner_id
having count(*) > 1;

-- 기대: 0 rows. UI radio와 service layer 기본 배송지 해제 로직이 일치해야 한다.
```

```sql
-- 4. 거래처 주 담당자는 활성 row 기준 최대 1명.
select partner_id, count(*) as primary_contact_count
from partner_contacts
where is_deleted = false
  and is_primary = true
group by partner_id
having count(*) > 1;

-- 기대: 0 rows. UI radio와 service layer 주 담당자 해제 로직이 일치해야 한다.
```

```sql
-- 5. 단가/할인 정책 값 범위.
select partner_id, basic_discount_rate, payment_term_days
from partner_price_discounts
where is_deleted = false
  and (
    basic_discount_rate < 0
    or basic_discount_rate > 100
    or payment_term_days < 0
  );

-- 기대: 0 rows. UI validation과 backend validation을 모두 통과한 데이터만 남아야 한다.
```

```sql
-- 6. 신규 등록 성공 후 4탭 row 연결성.
-- :partner_code 는 Playwright/JUnit fixture 값. 예: P-SP01-0001
select
  p.partner_code,
  p.name,
  p.biz_no,
  pd.id is not null as has_price_discount,
  count(distinct sa.id) filter (where sa.is_deleted = false) as shipping_address_count,
  count(distinct pc.id) filter (where pc.is_deleted = false) as contact_count
from partners p
left join partner_price_discounts pd
  on pd.partner_id = p.id
 and pd.is_deleted = false
left join partner_shipping_addresses sa
  on sa.partner_id = p.id
 and sa.is_deleted = false
left join partner_contacts pc
  on pc.partner_id = p.id
 and pc.is_deleted = false
where p.is_deleted = false
  and p.partner_code = :partner_code
group by p.partner_code, p.name, p.biz_no, pd.id;

-- 기대: exactly 1 row.
-- 정상 4탭 등록 케이스는 has_price_discount=true, shipping_address_count>=1, contact_count>=1.
```

```sql
-- 7. 삭제/수정 이력 화면의 actor 노출은 actor_name 중심이어야 한다.
select entity_id, actor_id, actor_name, field_name, changed_at
from partner_audit_logs
where is_deleted = false
  and actor_name is null;

-- 기대: 0 rows. 화면에는 actor_id(UUID)가 아니라 actor_name을 표시한다.
```

## API/권한 계약 확인

```powershell
# 거래처 4탭 등록은 SALES / MANAGER / MASTER 모두 허용되어야 한다.
rg -n "@PreAuthorize\\(\"hasAnyRole\\('MASTER','MANAGER','SALES'\\)\"\\)|POST /api/v1/partners/full|registerFull" services/partner-service/src/main/java/com/samhanair/logis/partner/tab/web/Partner4TabController.java
```

```powershell
# SALES 직접 등록 시 201 + partnerCode 응답 IT가 유지되는지 확인한다.
rg -n "SALES 역할 201|registerFull_with_sales_role_returns_201" services/partner-service/src/test/java/com/samhanair/logis/partner/it services/partner-service/src/test/java/com/samhanair/logis/partner/tab/it
```

```powershell
# Desktop role guard가 backend 등록 권한과 다르면 SP01 blocker 후보로 기록한다.
rg -n "PARTNER_FULL_ROLES|RoleGuard allow=\\{PARTNER_FULL_ROLES\\}|partner-create-submit|createPartnerFull" clients/desktop/src/renderer
```

## UUID 비노출 검색 가드

```powershell
# QA 캡처와 Playwright source fixture에는 실제 UUID/raw 내부 key가 없어야 한다.
rg -n "partnerId|addressId|contactId|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}" docs/qa/sp-01-partner-ui-menu-gap-audit/screenshots clients/desktop/playwright/partner-ui-menu-gap
```

```powershell
# 화면에 표시해도 되는 식별자는 partnerCode/name/bizNo/phone 중심이어야 한다.
rg -n "partnerCode|name|bizNo|phone" clients/desktop/src/renderer/routes/admin clients/desktop/src/renderer/api/partnerApi.ts clients/desktop/src/renderer/api/adminApi.ts
```

## 정합성 PASS 기준

- 활성 `partners.partner_code`, `partners.biz_no` 중복이 없다.
- 거래처별 활성 기본 배송지와 주 담당자는 각각 최대 1건이다.
- 단가/할인 정책은 할인율 0~100, 결제 기간 0 이상 범위를 벗어나지 않는다.
- MANAGER/MASTER 등록 성공 후 4탭 관련 row가 같은 partner row에 연결된다.
- SALES 등록 허용은 backend IT와 UI route/CTA가 같은 방향이다.
- UI/캡처에는 내부 UUID와 내부 id key가 표시되지 않는다.
