# 2026-06-24 회계전표 B-게이트 결재 enforcement

## 범위

- PR #589 슬라이스 ④ 권위 스펙 기준으로 `POST /accounting/journals/{id}/post`에 결재라인 B-게이트를 적용했다.
- `ACCOUNTING_JOURNAL` + `JOURNAL_POST` action anchor를 auth-service 내부 인가 API로 조회한다.
- 결재자 미지정 상태는 auth-service의 기존 opt-in 판정(`configured=false`)을 유지한다.

## 변경

- accounting-service
  - `ApprovalLineAuthorizeClient`, `ApprovalLineAuthorizeResult` 추가
  - `JournalService.post` 진입 직후 UUID actor에 한해 결재라인 인가 강제
  - `configured=true && allowed=false`일 때 `FORBIDDEN`과 메시지 `결재라인 결재자만 회계전표를 게시할 수 있습니다.` 반환
  - 기존 Journal 관련 IT에는 `configured=false` 기본 mock을 추가해 opt-in 전 무회귀 보장
- auth-service
  - `V68__approval_line_accounting_journal_seed.sql` 추가
  - `approval_line_config`에 `ACCOUNTING_JOURNAL` 작성자/결재자 구조만 시드
  - `approval_line_approver`는 미시드

## 검증

- TDD red
  - `:services:accounting-service:test --tests ...ApprovalLineAuthorizeClientTest --tests ...JournalApprovalGateIT`
  - 최초 실패: `ApprovalLineAuthorizeClient`, `ApprovalLineAuthorizeResult` 미구현 컴파일 실패
- Targeted green
  - `.\gradlew :services:accounting-service:test --tests "com.samhanair.logis.accounting.client.ApprovalLineAuthorizeClientTest" --tests "com.samhanair.logis.accounting.client.ApprovalLineAuthorizeClientDiGuardTest" --tests "com.samhanair.logis.accounting.it.JournalApprovalGateIT" --tests "com.samhanair.logis.accounting.it.JournalControllerIT" --tests "com.samhanair.logis.accounting.it.LedgerControllerIT" --tests "com.samhanair.logis.accounting.it.TrialBalanceControllerIT" --tests "com.samhanair.logis.accounting.service.JournalServiceTest"`
  - 결과: `BUILD SUCCESSFUL`
- Fresh Postgres probe
  - Docker `postgres:16-alpine` 임시 컨테이너 사용
  - `DROP DATABASE IF EXISTS approval_probe;`와 `CREATE DATABASE approval_probe;` 분리 실행
  - 선행 `approval_line_config` 스키마 생성 후 `V68`을 UTF-8 stdin으로 `psql -v ON_ERROR_STOP=1` 적용
  - 1회 적용: `INSERT 0 2`
  - 2회 적용: `INSERT 0 0`
  - 결과 행:
    - `ACCOUNTING_JOURNAL:0:작성자:CREATOR:<NULL>`
    - `ACCOUNTING_JOURNAL:1:결재자:GROUP:JOURNAL_POST`
  - count: `2`
- Full requested verification
  - `.\gradlew :services:accounting-service:test :services:auth-service:test`
  - 결과: `BUILD SUCCESSFUL`

## 자기리뷰

- Opt-in 무회귀: approver 미시드 및 `configured=false` 통과 테스트로 기존 게시 흐름을 보존했다.
- Actor 해석: `X-User-Id`가 null/blank/system/비 UUID이면 auth 호출 없이 skip한다.
- 게이트 정확성: UUID actor에서만 `ACCOUNTING_JOURNAL` + `JOURNAL_POST`를 호출하고, `configured=true && allowed=false`만 403으로 차단한다.
- 자동 분개/역분개: `postAutoJournal`, `autoReverse`, `reverse`는 이번 슬라이스 대상이 아니므로 추가 게이트를 걸지 않았다.
