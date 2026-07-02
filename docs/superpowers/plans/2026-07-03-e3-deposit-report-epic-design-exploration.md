# E3 입금보고서(CashReceipt) 에픽 — 설계탐색 (morning brainstorming 준비)

> ⚠️ **본 문서는 구현 착수 전 설계탐색/brainstorming 준비물이다.** E3 는 ③ POSTED 분개 계정과목 매핑이 **무결성 도메인**([[feedback_integrity_domain_policy_preconfirm]])이라 **개발책임자 확인 후 착수**. 오버나이트 자율 세션에서 정찰+옵션+권장방향을 정리하되, **구현 PR 은 개발책임자 brainstorming/preconfirm 후 개설**한다(회계 무결성 epic 자율 전면구현 지양).

## Goal (개발책임자 확정 정책)
회계 full-form 이관: **원장(Journal) 수정 금지(역분개만)** · **CashReceipt(입금보고서)=편집 대상**. 입금보고서를 라이브(수기+통장연계) 생성·편집·확정→분개하는 4-다리 신규. E2 born-live 인프라 소비. (근거: [[project_accounting_ledger_edit_policy]], [[project_accounting_gh_decisions]])

## 정찰 요약 (2026-07-03, accounting-service head V47)
| 자산 | 상태 |
|---|---|
| **Journal(원장/분개)** | ✅ 완성형 — 라이브 역분개(`JournalService.reverse/autoReverse`)·**`postAutoJournal(sourceType,...)`**(타모듈 POSTED 분개 클린 API=브릿지③ 재사용점)·오버레이 편집(`applyOverlayPatchBatch`=적요/line.memo만·계정/차대/일자 400 거부=**원장불변 코드화**). `JournalSourceType.CASH_RECEIPT` 이미 존재. |
| **BankTransaction(통장)** | ✅ 행매칭까지 — CSV(UTF-8/MS949)+**CODEF 실 client**(bank/card/loan) 실피드·`matchPartner`·`markReflected/markForced`(엔티티만, **프로덕션 호출자 0=dead**). |
| **CashReceipt(입금보고서)** | ❌ **MIG 배치 적재 전용** — 라이브 팩토리·CRUD·컨트롤러·역방향 링크 전무. `MANUAL_RECEIPT` enum 선제공(미사용). `cash_receipts.slip_no` UNIQUE(수기=신규 채번 필요). `kind` CHECK 없음(enum 추가 마이그 불요). |
| **브릿지③ 레퍼런스** | 🟡 `Mig9CashJournalService.processReceipt`(배치, cash_receipts→**POSTED** 분개 차 보통예금/대 외상매출금·멱등) + `partner_aging_snapshot`(거래처원장 자동 반영). 라이브 미배선. |
| **born-live 인프라** | ✅ accounting 이미 TaxInvoice/Journal/AccountingPeriod **3엔티티 온보딩**(`AccountingRealtimeController` SSE·`AccountingLockPolicies`·`AccountingEditRequest`·`JournalCollabConfig`) → CashReceipt=4번째 동형 확장(반복 패턴). |
| **FE** | ❌ CashReceipt 페이지/폼/API client 전무. 앵커=`BankTransactionPage.tsx`(CODEF import·거래처매칭, **다중선택 없음**·`DataTable` selection 미지원). 목업 존재 `docs/design/mig-14-admin-ui/02_cash_receipt_list_mock.md`. |

## 4-다리 + 슬라이스 (의존순: ②→③→①→④)
- **S0 spec/brainstorming**(본 문서→개발책임자 확정): §결정 확정, 특히 ③ 계정매핑(무결성 선확인)·CashReceipt 상태 라이프사이클.
- **S1 CashReceipt 도메인 기반(②)**: 상태 enum + 채번서비스 + 수기 create/list/get 서비스·컨트롤러 + repo 검색 + PageCode(`accounting.cash-receipts` 신설) + V48 마이그. born-live 온보딩. 규모 中.
- **S2 라이브 POSTED 분개+역분개(③)**: `postAutoJournal(CASH_RECEIPT)` 배선 + 수정 시 `autoReverse` 재게시 + `AccountingLockPolicies` CashReceipt 정책 + aging refresh. 규모 中·**리스크 高(계정매핑·멱등)**.
- **S3 통장→입금보고서(①)**: `markReflected` 라이브 승격 + N건 선택 생성 + `matched_journal_id`/bank link. 규모 中.
- **S4 FE(④)**: `CashReceiptListPage`+작성폼+디테일+coedit(born-live) + BankTransactionPage 다중선택 벌크액션 + 금액포맷 규약 수렴 + `api/accounting.ts` client. 규모 中.

## 🔑 개발책임자 morning 결정 (권장방향 병기)
1. **[선결] CashReceipt 상태 라이프사이클** — 권장: `DRAFT→CONFIRMED→CANCELLED`(CONFIRMED 시 POSTED 분개 생성, coedit=DRAFT, CANCELLED=역분개). lock/coedit/분개 타이밍 모두 의존.
2. **[🔴 무결성 선확인] ③ POSTED 분개 계정과목 매핑** — 권장: 차 보통예금(103)/대 외상매출금(110) 회수 고정 + Mig9(명칭 lookup)·DepositMatch(코드 103/110) **단일화**. 선수금·기타 대변 허용 여부·자동 vs 수동 게시·수정 시 역분개 재게시 = **개발책임자 확정 필수**([[feedback_integrity_domain_policy_preconfirm]]).
3. **① 통장→입금보고서 카디널리티** — 권장: 통장 N건→입금보고서 1건(합산) 허용 + 1:1. `markReflected` 타이밍=분개 게시 시(journalId 확보 후). 생성 전 거래처 매칭 강제.
4. **② 수기 편집 정책** — 권장: 입금보고서=coedit 자유편집(원장 아님) + soft delete. 채번 포맷·`external_ref` 수기 규칙 확정.
5. **④ FE 다중선택 UX** — 권장: 신규 `CashReceiptListPage` + BankTransactionPage 체크박스열(DataTable selection 신설 or 인페이지)→"입금보고서 생성" 벌크. 금액 `-1,234` 빨강·0 `—`([[feedback_accounting_report_display_conventions]], 현 FE 포맷터 3종 불일치 수렴).

## Self-Review / 주의
- **구현 착수 전 개발책임자 brainstorming + ③ 무결성 preconfirm 필수** — 본 문서는 준비물, PR 미개설.
- 적용 마이그 불변(V48+ 신규만)·enum CHECK(kind 는 CHECK 없어 불요)·PageCode FE↔BE 일치·born-live 4번째 온보딩 반복패턴·역분개 정책 재사용.
