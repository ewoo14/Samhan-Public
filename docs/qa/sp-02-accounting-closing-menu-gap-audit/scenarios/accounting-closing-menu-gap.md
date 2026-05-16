# SP-02 회계 마감 메뉴 gap QA 시나리오

## 목적

Samhan Public 데스크톱에서 매뉴얼이 안내하는 `매출 마감`과 `월말 마감` 화면을 사용자가 사이드바에서 찾을 수 있는지 검증한다. 핵심 route는 `/sales/closing`과 `/accounting/period-close`다.

## 현재 계약 기준

| 영역 | 계약 |
|---|---|
| 매출 마감 | `/sales/closing`, 판매 그룹과 회계 그룹에서 발견 가능 |
| 월말 마감 | `/accounting/period-close`, 회계 그룹에서 발견 가능 |
| 권한 | route guard = `ACCOUNTANT / MANAGER / MASTER` |
| 실행 권한 | 화면 내부 정책: `ACCOUNTANT / MASTER` 실행, `MASTER` 역마감, `MANAGER` 조회 전용 |
| 사용자 노출 식별자 | 기간(`YYYY-MM`/`YYYY-MM-DD`), 상태, 합계, 실행자명 중심. 내부 UUID 노출 금지 |

## 위험 가설

| ID | 위험 | 검증 목표 |
|---|---|---|
| SP02-R1 | 매출 마감 메뉴가 legacy `/warehouse/closing`으로 이동한다. | 판매/회계 양쪽 entry가 `/sales/closing`을 가리키는지 확인한다. |
| SP02-R2 | 월말 마감 route는 있지만 사이드바 entry가 없어 매뉴얼을 따라갈 수 없다. | 회계 그룹에 `월말 마감` entry가 보이고 `/accounting/period-close`로 이동하는지 확인한다. |
| SP02-R3 | MANAGER가 route에는 진입하지만 실행 버튼 정책을 오해할 수 있다. | MANAGER는 조회 전용임을 문서/캡처에 표시한다. |
| SP02-R4 | 마감 row 내부 id가 화면에 노출될 수 있다. | 메뉴/테이블/캡처 텍스트에서 UUID 및 내부 id key가 보이지 않는지 확인한다. |
| SP02-R5 | MANAGER route는 열리지만 backend list/realtime이 403 또는 500을 반환한다. | `GET /accounting/closings` MANAGER 200, `POST /accounting/closings` MANAGER 403을 Docker/Testcontainers로 확인한다. |

## 시나리오 매트릭스

| ID | 역할 | 진입 | 절차 | 핵심 assertion | 산출 캡처 |
|---|---|---|---|---|---|
| SP02-01 | ACCOUNTANT | `/` | 판매 그룹에서 `매출 마감`을 찾는다. | entry가 보이고 `/sales/closing`으로 이동한다. | `01-sales-closing-sales-group.png` |
| SP02-02 | ACCOUNTANT | `/` | 회계 그룹에서 `매출 마감`을 찾는다. | entry가 보이고 `/sales/closing`으로 이동한다. | `02-sales-closing-accounting-group.png` |
| SP02-03 | ACCOUNTANT | `/` | 회계 그룹에서 `월말 마감`을 찾는다. | entry가 보이고 `/accounting/period-close`로 이동한다. | `03-period-close-accounting-group.png` |
| SP02-04 | MANAGER | `/accounting/period-close` | 월말 마감 화면에 진입한다. | 목록/이력은 보이지만 실행 버튼은 정책상 제한된다. | `04-manager-period-close-readonly.png` |
| SP02-05 | MASTER | `/sales/closing` | 매출 마감 화면에 진입한다. | 역마감/감사 이력 정책이 보이고 route가 legacy가 아니다. | `05-master-sales-closing-route.png` |
| SP02-06 | ACCOUNTANT/MANAGER/MASTER | 전체 | body text, button label, aria-label을 스캔한다. | UUID regex 0건, 내부 `closingId` 원문 노출 0건. | `06-uuid-hidden-closing-menu-matrix.png` |

## Playwright spec

정적 contract spec 위치:

```text
clients/desktop/playwright/accounting-close-menu-gap/accounting-close-menu-gap.spec.ts
```

실행:

```powershell
cd clients/desktop
npx playwright test playwright/accounting-close-menu-gap/accounting-close-menu-gap.spec.ts --reporter=line
```

## Backend gate

```powershell
$env:DOCKER_HOST='tcp://localhost:2375'
.\gradlew.bat :services:accounting-service:test --tests "*MonthEndCloseControllerIT" --no-daemon --rerun-tasks
```

## PASS 기준

- `매출 마감`은 `/sales/closing`으로만 안내된다.
- `월말 마감`은 회계 사이드바에서 발견 가능하다.
- 두 route는 `ACCOUNTING_ROLES`로 보호된다.
- MANAGER 조회 전용 backend 계약은 list/realtime 조회만 허용하고 실행/역마감은 열지 않는다.
- 캡처와 화면 텍스트에 내부 UUID가 표시되지 않는다.
