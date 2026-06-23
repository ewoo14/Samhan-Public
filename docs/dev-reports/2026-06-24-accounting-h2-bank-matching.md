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

## Opus 5-agent 라운드 fix (Opus 직접) + 라이브 QA

4-static 리뷰(BE BLOCKING+P1×2+P2×2+P3×2, FE P1+P3, Design P1, DevOps) → Opus 직접 fix:
- **🔴 BLOCKING 식별자 4-key**: 매칭/해제 식별을 V43 unique index 전체 키(bankAccountLabel+transactedAt+amount+externalRef)로 변경. 2-key(label+externalRef) 충돌 시 정당 행 INVALID_INPUT 거부 회귀 해소. DTO+repository 단건 Optional+service+FE 4-key 전송+IT.
- **P1**: requireUnreflected 한국어 가드("미반영 상태가 아니라…"), clearPartner REFLECTED 409 IT + 권한 실HTTP IT, 재지정(덮어쓰기) 정책 Javadoc+IT.
- **P3**: DELETE-body→`PATCH /match-partner/clear`, 영문 예외→CONFLICT 단언, partnerDisplay=bizNo숫자+명, ariaLabel externalRef 미노출.
- **Design P1**: FORCED 배지 미존재 토큰 `--color-primary`→`--state-info`(런타임 깨짐 해소).
- **FE P1**: AsyncAutocomplete onChange(null) 미발화→해제는 명시 버튼만(dead 분기 제거).
- 스코프-노트: `PartnerLookupClient.findByPartnerCode`(`/internal/partners/{code}`)는 bizNo 미반환이나, **GET list 는 findByPartnerIdsBatch 로 bizNo 정확 해석**(matchedBizNo 채움) → UI(매칭 후 list refetch)는 정상 표시. PATCH 즉시응답 bizNo 공백은 transient·UX 무영향.

### 🐳 라이브 QA (Docker, mock OFF, dev_master/MASTER, accounting-service 재빌드)
실 스택(게이트웨이:8080) 실연동:
- **CSV import → 200** (3건 적재, 입금 800,000·출금 120,000).
- **매칭 PATCH 4-key → 200**: LIVEQA-H2-001 → P0-6-C001 "(주)한국냉동물류", GET list 에서 matchedBizNo `1018100001`(하이픈 제거) 해석.
- **틀린 amount 4-key → 404**: 4-key 정밀 식별 실증(2-key 모호성 제거, BLOCKING fix 실증).
- 데스크톱 UI 실화면(`docs/qa/accounting-h2-bank-matching/01-bank-matching-list.png`): 미반영 거래 PartnerAutocomplete(거래처명/코드), 매칭 거래 "(주)한국냉동물류"+해제 버튼, 탭/금액/상태 정상.
