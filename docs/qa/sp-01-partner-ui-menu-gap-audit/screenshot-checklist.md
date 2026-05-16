# SP-01 Partner UI Menu Gap 스크린샷 체크리스트

## 저장 위치

PR 본문 인라인 첨부용 PNG는 아래 경로에 저장한다.

```text
docs/qa/sp-01-partner-ui-menu-gap-audit/screenshots/
```

## 필수 캡처 목록

| 파일명 | 화면 | 역할 | 체크 포인트 |
|---|---|---|---|
| `01-sales-discoverability.png` | `/` | SALES | `판매 > 거래처 관리` entry 발견 가능 여부 |
| `02-sales-create-success-return.png` | `/admin/partners/new` | SALES | 등록 성공 후 공용 목록 복귀, raw 403/stack trace/UUID 미노출 |
| `03-manager-discoverability.png` | `/` | MANAGER | 거래처 등록/조회 경로 발견 가능 여부 |
| `04-create-validation-name.png` | `/admin/partners/new` | MANAGER | 거래처명 필수 validation, 탭 1 활성화 |
| `05-create-validation-bizno.png` | `/admin/partners/new` | MANAGER | 사업자등록번호 형식 validation |
| `06-create-validation-discount.png` | `/admin/partners/new` | MANAGER | 할인율 0~100 validation, 탭 2 활성화 |
| `07-create-validation-payment-term.png` | `/admin/partners/new` | MANAGER | 결제 기간 0 이상 validation |
| `08-create-validation-shipping.png` | `/admin/partners/new` | MANAGER | 배송지 별칭/주소 validation, 탭 3 활성화 |
| `09-create-validation-contact.png` | `/admin/partners/new` | MANAGER | 담당자 이름/휴대전화/주 담당자 validation, 탭 4 활성화 |
| `10-manager-create-success-return.png` | 등록 성공 후 | MANAGER | `/forbidden` 미진입, 생성 거래처 코드/상호 확인 |
| `11-master-create-success-return.png` | 등록 성공 후 | MASTER | `/admin/partners` 공용 목록 복귀, 생성 거래처 확인 |
| `12-master-adminlayout-quicklink.png` | `/admin/users` → `/admin/partners` | MASTER | 인사 셸 quick link `거래처 관리` 유지, 공용 목록 이동 |
| `13-master-adminlayout-menu-set.png` | `/admin/users` 또는 `/admin/partners` | MASTER | 인사 메뉴 7건 유지 |
| `14-uuid-hidden-assertions.png` | 거래처 UI 전체 검증 요약 | SALES/MANAGER/MASTER | UUID regex 0건, 내부 id key 0건 |

## 캡처 전 실행 순서

```powershell
cd clients/desktop
$env:VITE_MOCK_MODE='1'
$env:AUDIT_BASE_URL='http://127.0.0.1:5173'
npx vite --port 5173
```

다른 PowerShell 창:

```powershell
cd clients/desktop
$env:AUDIT_BASE_URL='http://127.0.0.1:5173'
npx playwright test playwright/partner-ui-menu-gap/partner-ui-menu-gap.spec.ts --reporter=line
```

## 캡처 품질 기준

- 캡처는 browser viewport `1440x900` 기준 full page 또는 문제 영역이 모두 보이는 crop으로 저장한다.
- 각 role은 URL query `?mockRole=SALES`, `?mockRole=MANAGER`, `?mockRole=MASTER&mockDepartment=대표실` 중 하나를 명확히 사용한다.
- validation 캡처는 alert 메시지와 활성 탭 label이 한 화면에 보여야 한다.
- 성공 return path 캡처는 생성된 `partnerCode`와 `name`이 함께 보여야 한다.
- MASTER AdminLayout 캡처는 좌측 `인사 (대표실 전용)` 영역과 `거래처 관리` quick link가 보여야 한다.
- UUID-hidden 캡처는 테스트 결과 요약 또는 Playwright report 중 금지 패턴 0건이 보이는 장면을 사용한다.

## 금지 패턴

캡처와 PR 본문에는 아래 값이 노출되면 실패다.

```text
[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}
partnerId
addressId
contactId
downloadUrl
storageKey
objectKey
stack trace
```

## PR 본문 첨부 기준

PR 본문에는 최소 1장 이상을 아래 형식으로 인라인 첨부한다. SP-01 권장 대표 캡처는 `10-manager-create-success-return.png` 또는 `12-master-adminlayout-quicklink.png`다.

```markdown
![SP-01 manager create success](docs/qa/sp-01-partner-ui-menu-gap-audit/screenshots/10-manager-create-success-return.png)
```

## 최종 확인 명령

```powershell
Get-ChildItem docs/qa/sp-01-partner-ui-menu-gap-audit/screenshots -Filter *.png | Select-Object Name, Length
```

```powershell
rg -n "partnerId|addressId|contactId|downloadUrl|storageKey|objectKey|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}" docs/qa/sp-01-partner-ui-menu-gap-audit/screenshots
```

PASS 조건은 PNG 1장 이상 존재, 대표 캡처가 PR 본문에 인라인 첨부, 금지 패턴 검색 결과 0건이다.
