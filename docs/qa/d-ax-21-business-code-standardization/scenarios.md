# D-AX-21 업무번호 범위형 표준화 QA 시나리오

## 목적

전표번호/배차번호가 `YYYY/MM/DD-{순번}`으로 통일되고, 판매/구매처럼 서로 다른 업무 메뉴에서는 같은 날짜 같은 순번이 허용되는지 검증한다.

## 시나리오

| ID | 항목 | 기대 결과 | 증빙 |
|---|---|---|---|
| D-AX21-01 | 판매/구매 전표번호 범위 | OUTBOUND `2026/05/07-1`, INBOUND `2026/05/07-1` 모두 허용 | `SlipNumberServiceTest`, `SlipNumberServiceIT` |
| D-AX21-02 | 전표 active unique | active unique 기준이 `slip_type + slip_no`로 변경 | `V24__business_number_scope.sql` |
| D-AX21-03 | 배차번호 | 신규 배차 작업은 `2026/05/14-1` 형식 | `DispatchTaskServiceTest` |
| D-AX21-04 | 모바일 상세 | 아로로지스 모바일 fixture가 `2026/05/15-1` 표시 | Jest 8 PASS |
| D-AX21-05 | UUID 비공개 | fixture와 QA 캡처에 실제 UUID 값, downloadUrl 노출 없음 | screenshot guard |
| D-AX21-06 | CI workflow 문법 | `.github/workflows/*.yml` actionlint PASS | actionlint |
| D-AX21-07 | Flyway SQL 문법 | PostgreSQL 임시 DB에서 V24 적용 후 constraint/index 확인 | psql smoke |

## 검증 명령

```powershell
docker run --rm -e GRADLE_USER_HOME=/gradle-cache -v C:\dev\SamhanLogis:/workspace -v C:\Users\user\.gradle:/gradle-cache -w /workspace eclipse-temurin:17-jdk /gradle-cache/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/bin/gradle :services:slip-service:test --no-daemon --rerun-tasks --offline
docker run --rm -e GRADLE_USER_HOME=/gradle-cache -v C:\dev\SamhanLogis:/workspace -v C:\Users\user\.gradle:/gradle-cache -w /workspace eclipse-temurin:17-jdk /gradle-cache/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/bin/gradle :services:arologis-service:test --no-daemon --rerun-tasks --offline
cd clients\arologis-mobile; npm test -- --runInBand src/__tests__/api/arologisSlipDetail.test.ts src/__tests__/screens/driver/DriverSlipDetailScreen.test.tsx
cd clients\arologis-mobile; npm run typecheck
cd clients\desktop; npm run typecheck
docker run --rm -v C:\dev\SamhanLogis:/repo -w /repo --entrypoint sh rhysd/actionlint:latest -c "actionlint -color=false .github/workflows/*.yml"
.\scripts\generate-d-ax-21-business-code-standardization-screenshots.ps1
```

## QA 캡처

- `screenshots/01-business-number-scope-policy.png`
- `screenshots/02-slip-sequence-contract.png`
- `screenshots/03-sales-purchase-duplicate-matrix.png`
- `screenshots/04-dispatch-number-standard.png`
- `screenshots/05-seed-and-cross-service-flow.png`
- `screenshots/06-docker-backend-verification.png`
- `screenshots/07-client-and-ci-verification.png`
- `screenshots/08-pr-capture-checklist.png`
