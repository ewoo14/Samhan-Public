# D-AX-22 UUID 비노출 계약 hardening 개발 리포트

## 목적

D-AX-22 는 D-AX-21에서 정리한 업무번호 원칙을 driver-facing API 와 모바일/데스크톱 클라이언트 계약까지 확장한다. UUID 는 내부 PK 로 유지하되, 기사 앱과 운영자 화면에는 전표번호, 배차 target sequence, 표시명, 마스킹된 연락처만 노출한다.

## 구현 요약

- slip-service full detail 응답에서 `sourceWarehouseName` 이 창고 UUID 문자열로 내려오던 fallback 을 제거했다.
- 아로로지스 GPS 보고 응답에서 내부 위치 row key 를 제거하고 `accepted/capturedAt/source` 만 반환한다.
- 아로로지스 서명 저장 응답과 sign-and-send-copy 응답 header/body 에서 서명 내부키를 제거했다.
- sign-and-send-copy 실패 JSON 은 운영 원인 코드만 반환하고 저장 경로, 원본 URL, 내부키를 노출하지 않는다.
- 모바일 API normalize 계층은 서버가 실수로 내부 필드를 내려도 반환 타입과 UI 결과에서 제거한다.
- 모바일/데스크톱 typecheck 파일에 내부키 접근 금지 assertion 을 추가했다.

## 업무번호 원칙

- 공개 전표번호/배차번호 형식은 `YYYY/MM/DD-{순번}` 이다.
- 순번은 전역 unique 가 아니라 메뉴/업무 속성별 scope 다.
- 예: 판매전표 `2026/05/16-1` 과 구매전표 `2026/05/16-1` 은 동시에 존재할 수 있다.
- 내부 정합성은 UUID PK, 업무 타입, soft-delete audit 으로 보존한다.

## 테스트 범위

- `SlipInternalControllerIT`: 창고 UUID 문자열 fallback 차단.
- `ArologisDriverAppControllerIT`: GPS/서명 driver-facing 응답 내부키 제거.
- `SignatureIntegrationIT`, `SignAndSendCopyIT`, `SignatureCopyMissingPhoneIT`, `SignatureCopyRendererTimeoutIT`: 성공/실패/timeout 경로의 내부키 비노출 assertion.
- `clients/arologis-mobile` Jest/typecheck: GPS, 서명, 공유, 실패 JSON normalize.
- `clients/desktop` typecheck/lint/build: 운영자 UI 계약 회귀 방지.

## 검증 결과

- Docker/JDK `:services:slip-service:test :services:arologis-service:test` PASS.
- XML 집계: `slip-service` 464 tests / 0 failures / 0 errors / 0 skipped.
- XML 집계: `arologis-service` 236 tests / 0 failures / 0 errors / 0 skipped.
- `clients/arologis-mobile` Jest PASS: 7 suites / 23 tests / 0 skipped.
- `clients/arologis-mobile` typecheck PASS, `npx expo install --check` PASS.
- `clients/desktop` typecheck PASS, lint PASS(기존 warning 3건, error 0), build PASS.
- `git diff --check` PASS.
- 로컬 `actionlint` 명령은 PATH 에 없어 실행하지 못했다. 이번 PR 은 workflow 파일 변경이 없으며, PR CI 에서 workflow 실행 결과를 재점검한다.

## PR 캡처

`docs/qa/d-ax-22-uuid-free-contract-hardening/screenshots/` 아래 8장 PNG 를 생성해 PR 본문에 인라인으로 첨부한다. 캡처는 API 계약, 모바일 UI, 실패 경로, 검증 매트릭스를 나눠 보여준다.
