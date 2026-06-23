# 회계 H-2 입출금 매칭 — 거래처 수동지정 설계서

> 작성 2026-06-24 (Opus 기획). 연관: 회계 H 입출금매칭. 선행 **H-1 #583**(BankTransaction CSV import + matchStatus 탭 조회). 후속 H-3(입출금보고서+거래처원장 POSTED 전기)·H-4(KFTC).
> 상위 spec `docs/superpowers/specs/2026-06-23-accounting-gh-receivables-bank-matching-design.md`, 메모리 [[project_accounting_gh_decisions]].

## 1. 배경 / 정찰

- **H-1 기구현**: `BankTransaction`(external_ref non-null, matchStatus=UNREFLECTED/REFLECTED/FORCED), 도메인 `matchPartner(UUID)`/`markReflected`/`markForced`(미배선·호출처 0), `BankTransactionController` = `POST /import` + `GET /`(matchStatus·기간·통장 필터). `BankTransactionResponse` 이미 `matchedPartnerCode`/`matchedBizNo`/`matchedPartnerName` 보유(매칭 표시 인프라 존재). FE `BankTransactionPage.tsx` = import + 탭(전체/미반영/회계반영/강제) + DataTable.
- **H-2 갭**: 거래처 **수동지정 매칭**(matchPartner) 엔드포인트 + FE AsyncAutocomplete 미구현.

## 2. 스코프 (H-2 = 거래처 수동지정 매칭까지)

- **포함**: 미반영(UNREFLECTED) 거래에 거래처 수동지정(matchPartner) + 지정 해제 + FE PartnerAutocomplete 매칭 UI + 매칭 거래처 표시.
- **제외(H-3 별도 슬라이스)**: markReflected/markForced(journalId 필요=분개 생성), 입출금보고서, 거래처원장 POSTED 전기. **본 슬라이스는 거래처 지정까지**(matchStatus 는 UNREFLECTED 유지).

## 3. BE (accounting-service)

### 3.1 거래처 매칭 엔드포인트 (신규)
`BankTransactionController` 에 추가. 권한 `@RequirePermission(accounting.bank-matching, UPDATE)`.

> ⚠️ **식별자 4-key 확정 (Opus 라운드 BLOCKING fix)**: 초안의 2-key(bankAccountLabel+externalRef)는 V43 unique index(bank_account_label+transacted_at+amount+external_ref) 와 불일치 — 같은 (label,externalRef)가 다른 일시/금액으로 공존하면 정당 행 거부. 매칭/해제 모두 **4-key 자연키** 사용으로 단건 보장. 해제는 DELETE-body 비표준 회피 위해 **PATCH .../clear**.

- **매칭**: `PATCH /accounting/bank-transactions/match-partner`
  - body: `{ bankAccountLabel, transactedAt, amount, externalRef, partnerCode }` (UUID 미노출 — FE 가 가진 표시 식별자만).
  - 처리: (1) **4-key 자연키**로 미삭제 BankTransaction 단건 조회(`findByBankAccountLabelAndTransactedAtAndAmountAndExternalRefAndIsDeletedFalse`, 없으면 404). (2) partnerCode → partnerId 해석(`PartnerLookupClient.findByPartnerCode`, 미등록 NOT_FOUND). (3) `transaction.matchPartner(partnerId)`(requireUnreflected — UNREFLECTED 아니면 IllegalState→CONFLICT 409). (4) 갱신 `BankTransactionResponse` 반환.
- **해제**: `PATCH /accounting/bank-transactions/match-partner/clear` (body 4-key) → `clearPartner()` matchedPartnerId=null (UNREFLECTED 한정·409 가드).
- **식별자 규약**: V43 unique 4-key 와 동일 자연키로 단건 식별(UUID 경로/응답 노출 금지). 모호성(2-key 다건) 제거.

### 3.2 partnerCode→partnerId 해석
`PartnerLookupClient` 재사용(accounting-service 기존, searchDirectory/findByCode). 미등록 partnerCode → BusinessException(NOT_FOUND). UUID 미노출 유지(matchedPartnerId 는 내부 보관, 응답은 code/bizNo/name).

### 3.3 검증(BE)
- 도메인 상태전이 가드(matchPartner UNREFLECTED 한정·이미 매칭된 거래 재지정 허용 여부=허용[덮어쓰기], REFLECTED/FORCED 매칭 변경 409). clearPartner UNREFLECTED 가드.
- 신규 엔드포인트 IT(MockRestServiceServer PartnerLookupClient + 실 HTTP 매칭/해제/404/409). 권한 V67 기존(accounting.bank-matching) 재사용·신규 Flyway 0.

## 4. FE (clients/desktop)

### 4.1 BankTransactionPage 매칭 UI
- 기존 DataTable 에 **거래처 매칭 열/액션** 추가:
  - **미반영(UNREFLECTED) 행**: `PartnerAutocomplete`(AC-3, `/admin/partners/search` 백킹) inline 또는 행 클릭 모달 → 거래처 선택 → `matchBankTransactionPartner({bankAccountLabel, transactedAt, amount, externalRef, partnerCode})` (4-key) mutation → 성공 시 react-query invalidate(목록 갱신, [[project_local_stack_qa_gotchas]] stale 주의).
  - **매칭됨**: `matchedPartnerCode`(bizNo, 하이픈 제거 [[그룹4 규약]]) + `matchedPartnerName` 표시 + 해제 버튼.
- 탭/필터/import 무변경(H-1 유지). UUID 미노출(응답에 없음).
- api/accounting.ts 에 `matchBankTransactionPartner`/`clearBankTransactionMatch` 추가. mock.ts 핸들러 동반([[feedback_inprocess_mock_principles]]: parseMockBody·non-null envelope·재seed).

### 4.2 검증(FE)
- `npm run typecheck`. mock 단위테스트(매칭/해제/탭 필터). PartnerAutocomplete 재사용(신규 컴포넌트 금지).

## 5. 비목표 (YAGNI)
- 자동 제안 매칭(거래처 추천) = H 후속(KFTC DepositMatchPage 별개). markReflected/markForced/분개 생성/입출금보고서/거래처원장 전기 = H-3.
- 신규 권한/Flyway 0(accounting.bank-matching·external_ref 기존).

## 6. 라이브 QA (라운드마다 인라인)
- accounting-service:8087 + 게이트웨이:8080 + 데스크톱 standalone 렌더러(:5175, mock off, dev_master). 실 CSV import(또는 기존 seed BankTransaction)된 미반영 거래에 PartnerAutocomplete 로 거래처 지정 → 매칭 거래처(bizNo/name) 표시 실화면 스크린샷 `docs/qa/accounting-h2-bank-matching/`. 미반영→매칭됨 상태변화 실증.
- 실 BankTransaction 시드 없으면 CSV import 실행 후 매칭(가짜 캡처 금지·실연동).

## 7. 산출물
- BE: BankTransactionController(매칭/해제) + BankTransaction.clearPartner + service + repository(자연키 조회) + IT
- FE: BankTransactionPage 매칭 UI + api/accounting.ts + mock + 단위테스트
- dev-report `docs/dev-reports/2026-06-24-accounting-h2-bank-matching.md`, 라이브 QA `docs/qa/accounting-h2-bank-matching/`
