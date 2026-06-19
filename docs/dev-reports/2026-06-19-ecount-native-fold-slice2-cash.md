# dev-report: 이카운트 네이티브 편입 슬2 — 현금 지출/입금 silo 폐기

> 2026-06-19 · PR #520 · 브랜치 `feat/ecount-native-fold-slice2` · 슬1(#518)과 동형
> 에픽: 이카운트 이관 자료 네이티브 편입 + "회계 관리자(MIG-14)" silo 폐기

## 1. 목표
이관 silo **"회계 관리자 ▸ 지출 트랜잭션 / 입금 트랜잭션"**(page-code `ecount.mig14.cash-list`)을 완전 폐기. 현금 지출/입금은 MIG-9 가 이미 네이티브 `journals`(POSTED 복식부기)로 편입해 **분개장 `/accounting/journals` + 입금매칭 `/accounting/deposit-match` + 원장**에 노출 — silo 는 `cash_disbursements`/`cash_receipts` 중간테이블 파생 **중복 화면**. (D2=통합표시 확정 → 분개장 변경 없음.)

## 2. 변경 manifest
- **FE**: AppLayout 지출/입금 메뉴 + `showAccountingAdminCash`, route 2개, `CashDisbursementListPage`/`CashReceiptListPage`/`CashTransactionList` 삭제, `accountingAdminApi` cash 함수·타입, PermissionMatrixPage/permissionsApi/mock/playwright(`mig-14-cash-admin` 삭제)/pagecodes/menu-5category/capture-all 참조 제거. (슬1 QA spec 의 cash 형제 단언도 갱신 — 산재 회귀 [[fe-guard-removal-contract-tests]])
- **BE(accounting)**: `GET /accounting/cash-disbursements`·`/cash-receipts` + `CASH_PAGE_CODE` + `listCashDisbursements`/`listCashReceipts` + DTO 2종 + 테스트 제거.
- **BE(auth)**: `PageCode.ECOUNT_MIG14_CASH_LIST` 제거 + `isValid` 박제(mig14 2종=order/ledger) + **V60**(권한모델 5테이블 정리, V59 패턴: role_page_permissions hard + templates/account/group/override soft).
- **유지(LINEAGE/MIG-7·9)**: `CashDisbursement`/`CashReceipt` 엔티티·repository·cash_* 테이블(물리 DROP=Phase11 cutover 후 D3), `Mig7Cash*TransformController`(`/transform-from-staging`, `ecount.mig7.*`), `Mig9CashJournalController` cash-journal 생성(`ecount.mig9.*`).

## 3. 듀얼 리뷰
- Opus 4-agent(제거완전성·lineage보존·auth/V60·QA계약 + 적대적 검증) + Codex 4-agent 교차.
- Codex P2 2건: ①docs drift(ecount-migration/cutover/README/qa README stale cash-list) → 본 doc-sync 갱신. ②슬2 negative QA spec 부재 → `ecount-fold-slice2-real-qa.spec.ts` 추가(cash 메뉴 absent + journals/deposit-match reachable + 구 route 미렌더). P1 없음 — lineage/MIG-7·9 무손상, 제거심볼 잔여 0 확인.

## 4. 검증
- `auth`+`accounting` `compileTestJava` + `PageCodeTest`(mig14 2종) + `AccountingAdminQueryServiceTest` **BUILD SUCCESSFUL**
- desktop `npm run typecheck` + `vitest` **97 green**
- **V60 실 auth_db 트랜잭션 probe**: cash-list rpp 7/app 12/gpp 5 → 전부 **0**, 타 mig14(order/ledger) **36 active 무손상**, ROLLBACK
- 제거 심볼(listCash*/Cash*Response/CashTransactionList) + `ecount.mig14.cash-list` 코드 잔여 **0**(V60+isFalse 박제뿐)
- **Docker 실QA**(실 게이트웨이 :8080 + dev_master): 회계 관리자 그룹 cash 메뉴 미노출(주문서/원장대조/운영대시보드/회계수정요청만 잔존), 분개장·입금매칭 도달·렌더, 구 silo route 미렌더 — `docs/qa/ecount-fold-slice2/T1~T5.png`

## 5. 후속
- 슬4(원장대조·운영대시보드 운영admin 격리, cutover 전 폐기금지) → 슬5(회계 관리자 토글그룹 최종 해체) → 슬6(주문 partner_orders cross-service 이식, 대형). 슬3=D2 통합표시 확정으로 폐기.
- cutover(Phase11) 후: cash_*/MV 물리 DROP(D3), 원장대조/운영대시보드 최종 처리(D4).
