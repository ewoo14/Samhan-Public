# SP-03 구매관리 입고 검수 CTA 스크린샷 체크리스트

## 저장 위치

```text
docs/qa/sp-03-purchase-inspection-cta/screenshots/
```

## 필수 캡처 목록

| 파일명 | 화면 | 역할 | 체크 포인트 |
|---|---|---|---|
| `01-warehouse-purchase-inspect-cta.png` | `/purchases` | WAREHOUSE | 검수 컬럼, SAVED/CONFIRMED 행 버튼, COMPLETED 행 `—`, 신규 입고전표 없음 |
| `02-warehouse-inspection-dialog.png` | `/purchases` → Dialog | WAREHOUSE | 전표번호/거래처/입고창고/라인/검수 저장/검수 완료 |
| `03-manager-purchase-dual-cta.png` | `/purchases` | MANAGER | 행 검수 CTA와 우상단 신규 입고전표 동시 노출 |
| `04-master-purchase-inspect-cta.png` | `/purchases` | MASTER | MASTER도 같은 검수 CTA 노출 |
| `05-inventory-no-inspect-cta.png` | `/purchases` | INVENTORY | 검수 컬럼/버튼/입고 검수 메뉴 미노출 |
| `06-business-number-uuid-hidden-matrix.png` | 검증 요약 | QA | `YYYY/MM/DD-N`, 서비스/메뉴별 중복 허용, UUID regex 0건 |

## 캡처 품질 기준

- 구매관리 table header와 행 우측 CTA가 한 화면에 보여야 한다.
- 역할 badge는 `WAREHOUSE`, `MANAGER`, `MASTER`, `INVENTORY` 풀네임을 사용한다.
- 전표번호는 `2026/05/10-1`처럼 zero-padding 없는 `YYYY/MM/DD-N` 형식만 사용한다.
- `slipId`, `lineId`, `inspectionId`, UUID, stack trace는 캡처에 표시하지 않는다.
- PR 본문에는 6장 모두 raw 링크로 인라인 첨부한다.

## 생성 명령

```powershell
.\scripts\generate-sp-03-purchase-inspection-cta-screenshots.ps1
```

## 최종 확인 명령

```powershell
Get-ChildItem docs/qa/sp-03-purchase-inspection-cta/screenshots -Filter *.png | Select-Object Name, Length
```

```powershell
rg -n "slipId|lineId|inspectionId|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}" docs/qa/sp-03-purchase-inspection-cta/screenshots
```
