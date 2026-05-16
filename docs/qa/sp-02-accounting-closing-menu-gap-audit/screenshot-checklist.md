# SP-02 회계 마감 메뉴 gap 스크린샷 체크리스트

## 저장 위치

```text
docs/qa/sp-02-accounting-closing-menu-gap-audit/screenshots/
```

## 필수 캡처 목록

| 파일명 | 화면 | 역할 | 체크 포인트 |
|---|---|---|---|
| `01-sales-closing-sales-group.png` | `/` | ACCOUNTANT | 판매 그룹 `매출 마감` entry → `/sales/closing` |
| `02-sales-closing-accounting-group.png` | `/` | ACCOUNTANT | 회계 그룹 `매출 마감` entry → `/sales/closing` |
| `03-period-close-accounting-group.png` | `/` | ACCOUNTANT | 회계 그룹 `월말 마감` entry → `/accounting/period-close` |
| `04-manager-period-close-readonly.png` | `/accounting/period-close` | MANAGER | 조회 전용, 실행 제한 정책 |
| `05-master-sales-closing-route.png` | `/sales/closing` | MASTER | legacy `/warehouse/closing` 미사용, 역마감 정책 |
| `06-uuid-hidden-closing-menu-matrix.png` | 검증 요약 | ACCOUNTANT/MANAGER/MASTER | UUID regex 0건, 내부 id key 0건 |

## 캡처 품질 기준

- 사이드바 메뉴와 목적 route가 한 화면에 보여야 한다.
- 역할 badge는 `ACCOUNTANT`, `MANAGER`, `MASTER` 풀네임을 사용한다.
- 내부 UUID, `closingId`, `periodId`, stack trace는 캡처에 표시하지 않는다.
- PR 본문에는 6장 모두 raw 링크로 인라인 첨부한다.

## 생성 명령

```powershell
.\scripts\generate-sp-02-accounting-closing-menu-gap-screenshots.ps1
```

## 최종 확인 명령

```powershell
Get-ChildItem docs/qa/sp-02-accounting-closing-menu-gap-audit/screenshots -Filter *.png | Select-Object Name, Length
```

```powershell
rg -n "closingId|periodId|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}" docs/qa/sp-02-accounting-closing-menu-gap-audit/screenshots
```
