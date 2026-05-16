# SP-01 Partner UI Menu Gap QA 시나리오

## 목적

Samhan Public 데스크톱의 거래처 UI가 backend 권한 계약과 다르게 발견되거나 실행되는 gap을 검증한다. 핵심 대상은 `/admin/partners`, `/admin/partners/new`, MASTER 전용 `AdminLayout`, 그리고 거래처 4탭 생성 폼이다.

## 현재 계약 기준

| 영역 | 계약 |
|---|---|
| 거래처 4탭 조회 | `GET /api/v1/partners/{partnerCode}/full` = `SALES / MANAGER / MASTER` |
| 거래처 4탭 등록 | `POST /api/v1/partners/full` = `SALES / MANAGER / MASTER` |
| 거래처 4탭 수정/배송지/담당자 mutation | `MANAGER / MASTER` |
| Admin 목록 `/admin/partners` | `AdminLayout` 밖 공용 route, 자체 `RoleGuard(PARTNER_FULL_ROLES)` 영향권 |
| 신규 등록 `/admin/partners/new` | `AdminLayout` 밖 공용 route, 자체 `RoleGuard(PARTNER_FULL_ROLES)` 영향권 |
| 사용자 노출 식별자 | `partnerCode`, `name`, `bizNo`, `phone` 등 업무 식별자만. 내부 UUID 화면 노출 금지 |

## 위험 가설

| ID | 위험 | 검증 목표 |
|---|---|---|
| SP01-R1 | SALES/MANAGER가 backend에서 허용된 조회/일부 업무를 UI에서 찾지 못한다. | 사이드바/직접 URL/대체 CTA 기준 discoverability를 role별로 기록한다. |
| SP01-R2 | SALES가 문서상 등록 가능해야 하는데 UI/backend 중 한쪽만 막을 수 있다. | SALES도 신규 등록 CTA와 submit 성공 흐름을 사용하며, 실패 시 raw 403/stack trace가 노출되지 않는지 확인한다. |
| SP01-R3 | MANAGER는 등록 성공 후 목록으로 돌아가야 하나 `/admin/partners`가 MASTER `AdminLayout`에 막힐 수 있다. | 성공 return path가 `SALES / MANAGER / MASTER` 공용 목록으로 이어지는지 확인한다. |
| SP01-R4 | MASTER 대표실 인사 셸의 기존 메뉴가 거래처 공용 목록 분리로 깨질 수 있다. | MASTER+대표실에서 인사 셸 메뉴는 유지하고, 거래처 quick link는 공용 `거래처 관리` 화면으로 이동한다. |
| SP01-R5 | 상세/배송지/담당자 응답의 내부 UUID가 화면, aria-label, screenshot에 노출된다. | rendered text, accessibility name, screenshot source에 UUID 패턴 0건을 강제한다. |

## 테스트 데이터

| 값 | 용도 |
|---|---|
| `P-SP01-0001` | 신규 등록 성공 케이스 partnerCode |
| `123-45-67890` | 정상 사업자등록번호 |
| `(주)SP01검증공조` | 정상 거래처명 |
| `서울특별시 강남구 테헤란로 123` | 정상 사업장/배송지 주소 |
| `qa-primary@samhan.test` | 정상 세금계산서/담당자 이메일 |
| `010-1111-2222` | 정상 담당자 휴대전화 |

## 시나리오 매트릭스

| ID | 역할 | 진입 | 절차 | 핵심 assertion | 산출 캡처 |
|---|---|---|---|---|---|
| SP01-01 | SALES | `/` | 좌측 사이드바에서 거래처 관련 항목을 탐색한다. | `판매 > 거래처 관리` entry가 보이고 `/admin/partners` 공용 목록으로 이동한다. | `01-sales-discoverability.png` |
| SP01-02 | SALES | `/admin/partners/new` | 직접 URL 진입 후 필수값을 정상 입력하고 등록을 누른다. | `POST /api/v1/partners/full` 성공 후 `/admin/partners` 공용 목록으로 복귀한다. UUID/stack trace/raw endpoint 노출 금지. | `02-sales-create-success-return.png` |
| SP01-03 | MANAGER | `/` | 좌측 사이드바에서 거래처 조회/등록 경로를 찾는다. | MANAGER는 backend 계약상 생성 가능하므로 등록 경로가 발견 가능해야 한다. `/admin/partners`가 필요하면 AdminLayout에 막히지 않아야 한다. | `03-manager-discoverability.png` |
| SP01-04 | MANAGER | `/admin/partners/new` | 거래처명 공란 상태로 등록한다. | 탭 1 활성화, alert `거래처명을 입력하세요.` 표시, network mutation 0건. | `04-create-validation-name.png` |
| SP01-05 | MANAGER | `/admin/partners/new` | 사업자등록번호를 `1234567890`으로 입력하고 등록한다. | 탭 1 활성화, alert `사업자등록번호 형식이 올바르지 않습니다.` 표시, network mutation 0건. | `05-create-validation-bizno.png` |
| SP01-06 | MANAGER | `/admin/partners/new` | 할인율을 `101`로 입력하고 등록한다. | 탭 2 활성화, alert `기본 할인율은 0~100 사이 숫자여야 합니다.` 표시. | `06-create-validation-discount.png` |
| SP01-07 | MANAGER | `/admin/partners/new` | 결제 기간을 `-1`로 입력하고 등록한다. | 탭 2 활성화, alert `결제 기간(일수)은 0 이상 정수여야 합니다.` 표시. | `07-create-validation-payment-term.png` |
| SP01-08 | MANAGER | `/admin/partners/new` | 배송지를 추가하고 별칭 또는 주소를 비운 채 등록한다. | 탭 3 활성화, `배송지 1: 별칭을 입력하세요.` 또는 `배송지 1: 주소를 입력하세요.` 표시. | `08-create-validation-shipping.png` |
| SP01-09 | MANAGER | `/admin/partners/new` | 담당자를 추가하고 이름/휴대전화/주담당자 지정을 누락한다. | 탭 4 활성화, 담당자 validation 메시지 표시. 주담당자는 0명/2명 모두 실패. | `09-create-validation-contact.png` |
| SP01-10 | MANAGER | `/admin/partners/new` | 정상 4탭 입력 후 등록한다. | 201 이후 사용자에게 성공 상태가 보이고, `/admin/partners` 공용 목록으로 복귀한다. `/forbidden` 이동은 blocker. | `10-manager-create-success-return.png` |
| SP01-11 | MASTER | `/admin/partners/new` | 정상 4탭 입력 후 등록한다. | 201 이후 `/admin/partners` 공용 목록으로 복귀하고 생성된 `partnerCode/name` 표시. | `11-master-create-success-return.png` |
| SP01-12 | MASTER | `/admin/users` | MASTER+대표실 인사 셸에서 거래처 quick link 클릭. | `admin-nav-partners` 라벨이 `거래처 관리`로 보이고, 클릭 후 공용 `/admin/partners` 화면으로 이동한다. pageerror 0건. | `12-master-adminlayout-quicklink.png` |
| SP01-13 | MASTER | `/admin/users` | 인사 AdminLayout 기존 메뉴 확인. | `admin-nav-users-new`, `admin-nav-roles`, `admin-nav-departments`, `admin-nav-chat-rooms`, `admin-nav-dc-config`, `admin-nav-partners`, `admin-nav-warehouses` 유지. | `13-master-adminlayout-menu-set.png` |
| SP01-14 | SALES/MANAGER/MASTER | 거래처 UI 전체 | body text, button label, aria-label, table cell, dialog text를 스캔한다. | UUID regex 0건. `partnerId`, `addressId`, `contactId` 같은 내부 key 텍스트 노출 0건. | `14-uuid-hidden-assertions.png` |

## Playwright spec

정적 contract spec 위치는 `clients/desktop/playwright/partner-ui-menu-gap/partner-ui-menu-gap.spec.ts`다. UI 캡처는 mock dev server를 켠 뒤 별도 캡처 스크립트로 생성한다.

핵심 helper:

```ts
const UUID_REGEX =
  /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}/

async function pageText(page: Page): Promise<string> {
  return (await page.locator('body').innerText()).trim()
}

async function expectUuidHidden(page: Page): Promise<void> {
  const text = await pageText(page)
  expect(text).not.toMatch(UUID_REGEX)
  expect(text).not.toMatch(/\b(partnerId|addressId|contactId)\b/i)
}
```

성공 return path assertion:

```ts
await page.getByTestId('partner-create-submit').click()
await page.waitForLoadState('networkidle').catch(() => {})

const text = await pageText(page)
expect(page.url()).not.toContain('/forbidden')
expect(text).toContain('(주)SP01검증공조')
expect(text).toContain('P-SP01-0001')
await expectUuidHidden(page)
```

## 실행 명령

### Desktop static/code 계약 확인

```powershell
rg -n "path: '/admin/partners/new'|PARTNER_FULL_ROLES|admin-nav-partners|canAccessAdmin|/api/v1/partners/full|hasAnyRole\\('MASTER','MANAGER'\\)" clients/desktop/src/renderer services/partner-service/src/main/java
```

### Desktop Playwright UI 검증

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

### Partner-service 권한/validation IT

```powershell
$env:DOCKER_HOST='tcp://localhost:2375'
.\gradlew.bat :services:partner-service:test --tests "*P06ValidationIT" --tests "*PartnerAdminControllerIT" --no-daemon --rerun-tasks
```

### Full frontend sanity

```powershell
cd clients/web/design-system
npm run build
```

```powershell
cd clients/desktop
npm run typecheck
npm run lint
npm run build
```

## PASS 기준

- SALES/MANAGER/MASTER 모두 자신에게 허용된 거래처 업무 경로를 UI에서 찾을 수 있다.
- SALES는 등록 mutation을 완료할 수 있고, 성공 후 공용 거래처 목록으로 복귀한다.
- MANAGER/MASTER는 유효한 4탭 입력으로 등록 성공 후 권한에 맞는 안전한 return path에 도착한다.
- MASTER+대표실 `AdminLayout`의 기존 인사 메뉴와 `거래처 관리` quick link가 유지된다.
- 모든 거래처 UI 화면, aria-label, 캡처 산출물에 내부 UUID와 내부 id key가 표시되지 않는다.
