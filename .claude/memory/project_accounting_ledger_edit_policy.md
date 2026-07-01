---
name: project_accounting_ledger_edit_policy
description: 회계 원장(Journal) 수정 금지·입금보고서 등 비-원장 문서가 편집/coedit 대상
metadata:
  type: project
---

2026-07-02 개발책임자 확정. 회계전표 **원장(Journal 계정과목/차변/대변)은 수정 금지** — 감사 무결성상 라이브 협업 편집·수정 대상 아님. 정정은 reverse(역분개) 후 신규 분개. 원장 coedit = 협업 메모(CollaborativeTextField)만.

**단, 입금보고서 등 비-원장 회계 문서는 편집/coedit 대상.** 회계 full-form coedit 대상이 Journal(원장)에서 입금보고서 등으로 이관.

**입금보고서 = `CashReceipt` 엔티티.** 업무 흐름(이카운트 방식): 계좌 입출금내역(`BankTransaction`) 선택 → 입금보고서(`CashReceipt`) 작성 → 입출금내역+거래처(사업자) 매칭 → 거래처 원장 반영(수금/미수금 회수).
- 정찰(2026-07-02): 이 라이브 흐름 **대부분 미구축** — 3섬 단절(BankTransaction 행별매칭까지·`markReflected` dead code / CashReceipt MIG적재전용·수기작성 전무·CashReceiptController 부재 / DepositMatch KFTC DRY_RUN mock·FE없음). 동작 유일구간=MIG 과거데이터→Mig9 admin 배치→원장(라이브 아님).
- **입금보고서 에픽**(대형 신규, 회사PC 재개 착수 대기): BE(BankTransaction→CashReceipt 생성·CashReceipt 수기 CRUD·markReflected 라이브 승격)+FE(BankTransactionPage 다중선택+입금보고서 작성 액션·작성폼/목록, 목업 `docs/design/mig-14-admin-ui/02_cash_receipt_list_mock.md`). brainstorming(요구·설계 탐색) → 슬라이스 → 캐논 8단계.

**슬1(#697 Journal PUT full-form) = 폐기** — 원장 수정 금지로 불필요. 감사·보완 완료(순차 듀얼 3사이클·5-agent·Codex 대칭·blocking 11 적발). main 무오염(미머지). PR close 대기(승인 후). [[feedback_integrity_domain_policy_preconfirm]]
