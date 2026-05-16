# SP-02 회계 마감 메뉴 gap 도메인 정합성 체크

## 목적

SP-02는 frontend menu/route gap 보정이지만, 마감 화면은 회계 기간 잠금과 감사 이력에 연결된다. route 수정이 legacy 화면 또는 내부 id 노출 회귀로 이어지지 않는지 확인한다.

## 정적 계약 확인

```powershell
rg -n "sidebar-sales-closing|sidebar-accounting-sales-closing|sidebar-accounting-period-close|/sales/closing|/accounting/period-close|/warehouse/closing" clients/desktop/src/renderer/components/AppLayout.tsx clients/desktop/src/renderer/routes/index.tsx
```

기대:

- `sidebar-sales-closing` → `/sales/closing`
- `sidebar-accounting-sales-closing` → `/sales/closing`
- `sidebar-accounting-period-close` → `/accounting/period-close`
- 회계 사이드바 `매출 마감` entry가 `/warehouse/closing`을 직접 목적지로 쓰지 않는다.

```powershell
rg -n "path: '/sales/closing'|path: '/accounting/period-close'|RoleGuard allow=\\{ACCOUNTING_ROLES\\}" clients/desktop/src/renderer/routes/index.tsx
```

기대: 두 route 모두 `ACCOUNTING_ROLES` guard 유지.

## 백엔드 조회 권한 / 필터 정합성

```powershell
$env:DOCKER_HOST='tcp://localhost:2375'
.\gradlew.bat :services:accounting-service:test --tests "*MonthEndCloseControllerIT" --no-daemon --rerun-tasks
.\gradlew.bat :services:accounting-service:test --no-daemon --rerun-tasks
```

기대:

- `MANAGER`는 `GET /accounting/closings?periodType=MONTHLY&year=YYYY` 조회 200.
- `MANAGER`는 `POST /accounting/closings` 실행 403.
- PostgreSQL/Testcontainers 환경에서 nullable JPQL 파라미터 타입 추론 실패로 500이 발생하지 않는다.
- 전체 accounting-service 테스트는 `tests=204 failures=0 errors=0 skipped=0`이어야 한다.
- 홈택스 batch preview 응답은 저장된 `batchId`를 반환하며, history/xlsx download가 `/batch/null/...`로 흐르지 않는다.

## UUID 비노출 검색 가드

```powershell
rg -n "closingId|periodId|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}" docs/qa/sp-02-accounting-closing-menu-gap-audit/screenshots clients/desktop/playwright/accounting-close-menu-gap
```

기대: 검색 결과 0건.

## 수동 데이터 정합성 확인안

운영 DB에서 마감 row를 확인할 때 화면 표시값은 내부 UUID가 아니라 기간/상태/실행자 중심이어야 한다.

```sql
select
  period_type,
  period_date,
  status,
  closed_by_name,
  closed_at
from accounting_periods
where is_deleted = false
order by period_date desc;
```

## PASS 기준

- 메뉴 entry와 route 계약이 매뉴얼과 일치한다.
- 매출 마감 legacy route는 deep-link 호환으로 남더라도 사이드바 정식 목적지가 아니다.
- MANAGER 조회 전용 화면이 backend list/realtime 권한과 충돌하지 않는다.
- Docker/Testcontainers accounting-service gate가 skip 없이 통과한다.
- 마감 화면/QA 캡처에는 내부 UUID와 내부 id key가 노출되지 않는다.
