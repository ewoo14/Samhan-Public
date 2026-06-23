# 회계 H-2 입출금 매칭 — 거래처 수동지정

## 범위

- `PATCH /accounting/bank-transactions/match-partner`: `bankAccountLabel + externalRef + partnerCode` 로 미반영 통장 거래에 거래처를 지정한다.
- `DELETE /accounting/bank-transactions/match-partner`: `bankAccountLabel + externalRef` 로 미반영 통장 거래의 거래처 지정을 해제한다.
- 데스크톱 `입출금 매칭` 화면의 미반영 행에 기존 `PartnerAutocomplete`를 재사용해 거래처 지정/해제를 배선했다.

## 식별자 결정

- 화면/API에는 UUID를 노출하지 않는다. 요청 식별자는 `bankAccountLabel + externalRef`, 거래처 식별자는 `partnerCode`만 사용한다.
- H-1 Flyway V43의 unique index는 `(bank_account_label, transacted_at, amount, external_ref)`이다. 따라서 `(bankAccountLabel, externalRef)`는 import 생성 externalRef 기준으로 실사용 단건이지만 DB 제약으로는 완전 보장되지 않는다.
- 신규 조회는 `(bankAccountLabel, externalRef)` 결과가 0건이면 `NOT_FOUND`, 2건 이상이면 `INVALID_INPUT`으로 막아 모호한 매칭 변경을 거부한다. 신규 Flyway는 추가하지 않았다.

## 상태전이

- `BankTransaction.matchPartner`와 `clearPartner`는 `UNREFLECTED`에서만 허용한다.
- `REFLECTED`/`FORCED` 거래 변경 시 도메인 `IllegalStateException`을 서비스에서 `BusinessException(CONFLICT)`로 변환한다.

## 검증 포인트

- BE IT: 매칭 성공, 해제, 미등록 거래처 404, 반영 거래 재지정 409, 응답 UUID 미노출.
- FE mock test: 매칭/해제, 탭 필터, UUID 미노출, 반영/강제 행 변경 409.
