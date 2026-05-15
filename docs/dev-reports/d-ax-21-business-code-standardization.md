# D-AX-21 업무번호 범위형 표준화 개발 리포트

> 작성일: 2026-05-16
> 범위: 전표번호/배차번호 `YYYY/MM/DD-{순번}` 표준화, 판매/구매 전표번호 중복 허용, CI workflow 문법 복구

## 결정

- UUID는 Samhan Public/아로로지스 내부 PK이며 화면, URL, 기사 앱 응답에 노출하지 않는다.
- 전표번호와 배차번호는 `YYYY/MM/DD-{순번}` 형식을 사용한다.
- 전표번호는 전역 unique가 아니라 메뉴/업무 속성별 unique다. 판매전표 `2026/05/16-1` 과 구매전표 `2026/05/16-1` 은 동시에 존재할 수 있다.
- 복구/이력은 UUID PK, `slip_type`, soft-delete, audit으로 보존한다.

## 구현

- `SlipNumberSequence`에 `slipType`을 추가하고 `slipDate + slipType` 단위로 순번을 관리한다.
- `SlipNumberService.next(LocalDate, SlipType)`를 추가하고 기존 `next(LocalDate)`는 OUTBOUND 호환 경로로 유지했다.
- `slips` 활성 unique 기준을 `slip_type + slip_no`로 전환하는 Flyway `V24__business_number_scope.sql`을 추가했다.
- `SlipService`, publish/estimate/mobile partner order 변환 경로는 호출 업무 유형을 명시해 채번한다.
- `DispatchTaskService`는 `DT-YYYYMMDD-NNN` 대신 `yyyy/MM/dd-N` 형식으로 배차번호를 생성한다.
- dev seed와 notification/partner-order sample도 `yyyy/MM/dd-N`로 정렬했다.
- GitHub Actions YAML parse 오류 3건과 actionlint shellcheck 지적 2건을 함께 수정했다.

## 테스트

| 항목 | 결과 |
|---|---|
| Docker targeted Gradle | PASS — SlipNumber/SlipService/DispatchTask focused tests |
| Docker `:services:slip-service:test` | PASS — 463 tests, failure 0, error 0, existing skipped 172 |
| Docker `:services:arologis-service:test` | PASS — 236 tests, failure 0, error 0, existing skipped 75 |
| V24 PostgreSQL smoke | PASS — 임시 DB에 V1 최소 구조 + V24 적용, constraint/index 확인 |
| `clients/arologis-mobile` Jest | PASS — 2 suites / 8 tests |
| `clients/arologis-mobile` typecheck | PASS |
| `clients/desktop` typecheck | PASS |
| actionlint | PASS — `.github/workflows/*.yml` |

## 남은 Debt

- 기존 Testcontainers IT skip은 신규 skip이 아니라 Docker Desktop/Testcontainers provider hardening debt다.
- 삼한 퍼블릭 거래처 생성/관리 UI gap, 아로로지스 실제 기기 QA, 전표 상세 comments/audit/SSE proxy는 후속 슬라이스로 유지한다.
