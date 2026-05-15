# D-AX-21 5-agent + TM 통합 리뷰

> 작성일: 2026-05-16
> 부모 PM: Codex
> 팀 구성: Backend, Frontend, Designer, DevOps, QA + TM 통합

## Backend

- `SlipNumberService`의 전표번호 형식과 scope가 사용자 최신 결정과 달랐다. `slipDate + slipType` 시퀀스와 `yyyy/MM/dd-N` 포맷으로 수정했다.
- `slips` active unique 기준은 `slip_type + slip_no`로 변경했다.
- dispatch task code의 `DT-YYYYMMDD-NNN` 생성은 `yyyy/MM/dd-N`으로 변경했다.

## Frontend

- 아로로지스 모바일 전표 상세 fixture와 assertion을 `2026/05/15-1`로 갱신했다.
- 데스크톱 dispatch board/task 주석과 수정 요청 placeholder의 오래된 `SL`/`DT` 예시를 제거했다.
- UUID/read model 비공개 회귀 assertion은 유지했다.

## Designer

- PR 캡처는 정책, DB 계약, 판매/구매 중복 허용, 배차번호, seed flow, Docker 검증, 클라이언트/CI 검증까지 8장으로 분리했다.
- 모든 캡처는 실제 UUID 값이나 raw URL 없이 업무번호만 보이도록 구성했다.

## DevOps

- actionlint 기준 workflow syntax 오류 3건을 확인했다.
- `arologis-ci.yml`, `nightly-slip-it.yml`, `sa-rotation-reminder.yml` parse 오류를 수정했다.
- `arologis-deploy.yml` shellcheck warning도 함께 정리했고 actionlint PASS로 재검증했다.

## QA

- Docker JDK로 `slip-service`와 `arologis-service` 전체 테스트를 실행했다.
- 모바일 Jest/typecheck와 desktop typecheck를 실행했다.
- 기존 Testcontainers skip은 신규 skip이 아니며, no-skip hardening 별도 후속 과제로 기록했다.

## TM 결론

- D-AX21 변경은 사용자 최신 번호 정책과 일치한다.
- 판매/구매 전표번호 중복 허용은 테스트와 DB 제약 양쪽에서 방어한다.
- PR 발행 후 CI green이면 PM 재점검 후 머지 가능하다.
