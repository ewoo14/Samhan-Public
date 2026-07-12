# #12 회계 full-form coedit — 설계 스펙 / 실태 규명 (PM 자율 판단 + 개발책임자 최종확인 1점)

- **일자**: 2026-07-12 · **작성**: PM(Opus) 심화 정찰 기반 · **상태**: ✅ **종결 확정**(2026-07-13 개발책임자 "입금보고서로 종결 close" 결정) — 회계 full-form coedit = 입금보고서(CashReceipt·E3-S4d #755)로 충족·완료. Journal 원장은 수정금지 정책(2026-07-02)상 현행 유지(적요/라인메모 coedit #683 + 정정=역분개). #697(Journal PUT) 폐기. **추가 작업 없음.**
- **연관**: §7/#16 협업 에픽 · #697(폐기) · E3-S4d(#755 완료) · `project_accounting_ledger_edit_policy`(2026-07-02)

---

## 0. 🚨 최우선 발견 — #12는 이미 정책으로 해소·완료된 항목

정찰 결과 "#12 회계 full-form = Journal 원장 전체폼 동시편집"은 **2026-07-02 개발책임자 확정 정책과 충돌하며, 실체는 이미 완료**됨:

1. **원장 수정금지 정책**(`project_accounting_ledger_edit_policy.md:8-16`): "회계전표 원장(Journal 계정/차변/대변)은 감사 무결성상 수정 금지·라이브 협업 편집 대상 아님. 정정=역분개 후 신규 분개. 원장 coedit=협업 메모만. **회계 full-form coedit 대상이 Journal에서 입금보고서로 이관.**"
2. **#697(Journal PUT full-form) = 폐기 이력**(동 파일 L16): "원장 수정 금지로 불필요. main 무오염(미머지). PR close 대기." → Journal 전체폼 PUT은 이미 개발됐다 정책상 폐기.
3. **회계 full-form coedit 실체 = 입금보고서(CashReceipt)로 재정의·완료**: E3-S4d(#755, `2026-07-07-e3-s4d-cash-receipt-coedit.md`) born-live 방식 머지 완료.

## 1. 사실관계 (파일:라인)
| 항목 | 사실 | 근거 |
|---|---|---|
| Journal 수정 PUT/PATCH | **부재**. create·post·reverse·overlay만 | `JournalController.java`(PUT 없음)·`JournalService.java` |
| FE 회계 "edit" | 실제로 **신규 생성**(POST create) | `JournalFormPage.tsx:8-10,326` 주석 명시 |
| 원장 라인 add/remove | DRAFT만·POSTED 차단 | `Journal.java:183-206,222-244` |
| 회계 coedit 현황 | 적요(description)+라인메모만(원장키 400 거부) | `JournalDocumentCollaborationPort.java:43-47,269-311` |
| CashReceipt 구조 | 단일 amount+고정 2계정(**다중라인·균형 배열 없음**) → S4d full-form 성립 용이 | `CashReceipt.java:49-67` |
| slip full-form 저장 | **기존 도메인 PUT 재사용**(updateSalesSlip). 롤아웃 스펙=BE변경0 | `SlipDetailPage.tsx:660,690`·롤아웃 스펙 |
| **핵심 갭** | slip/주문/입금보고서는 "기존 전체폼 저장 PUT" 보유 → Journal만 부재(원장 불변 정책상 의도적 부재) | 항목1 |

## 2. PM 자율 판단 (D1)
**#12의 "회계 full-form"은 (b) 입금보고서(CashReceipt) full-form coedit로 이미 충족·완료**로 판단.
- 근거: ① 개발책임자 2026-07-02 정책이 "회계 full-form 대상을 Journal→입금보고서로 이관" 명문화. ② #697(Journal PUT)은 그 정책으로 폐기. ③ E3-S4d로 입금보고서 full-form coedit 완료. ④ Journal 원장 동시편집을 신설하려면 원장 수정금지 정책 역전 필요(무결성 도메인·PM 자율 대상 아님).
- **Journal은 현행 유지**: 적요/라인메모 라이브 coedit(#683) + 정정=역분개+신규게시(원장 불변). = 정책 준수 상태가 이미 올바름.

## 3. 🟡 개발책임자 최종확인 1점 (무결성 정책이라 PM 자율 밖)
**#12를 "입금보고서 full-form(E3-S4d)로 완료"로 종결하고 close할지**, 아니면 **Journal 원장 수정금지 정책을 예외 승인해 Journal 원장 동시편집(#697 부활·S0~S5 대규모)을 진행할지.**
- **PM 권고: 전자(종결·close)** — 무결성 정책 준수·이미 완료·#697 폐기 이력 일관. 후자는 감사 무결성 근본 변경이라 별도 정책 결정.

## 4. (후자 선택 시) 슬라이스 — 참고
S0(원장 편집 정책 예외 승인+D2/D3) → S1(updateDraft PUT+균형 재검증) → S2(POSTED=역분개+재작성) → S3(FE createDocCoeditProvider 배선) → S4(라인 CRDT+계정 Select 협업) → S5(낙관락↔라이브 정합·에픽 공통 후속). FE provider/셀/relay는 재사용 가능하나 **BE 저장 PUT 신설이 원장 불변 정책 역전 전제**.
