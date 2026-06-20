# 이카운트 네이티브 편입 에픽 — Phase 11 cutover 마무리 Runbook

> 2026-06-20. 본 문서는 [ECOUNT-CUTOVER-GUIDE.md](./ECOUNT-CUTOVER-GUIDE.md)(MIG-1~11 원 이관 절차)의 **속편** — 2026 "이카운트 이관 자료 네이티브 편입" 에픽([[project-ecount-native-fold]])이 cutover 시점에 실행해야 할 **주문 네이티브 이식 + silo 최종 폐기 + 중간테이블 물리 정리** 절차.
> 에픽 pre-cutover 슬라이스(전부 머지): 슬1 잔액스냅샷 silo 폐기(#518) · partner 시드 UUID 정합(#519) · 슬2 현금 silo 폐기(#520) · 슬4 회계 관리자 그룹 해체(#521) · 슬6 6a+6b 주문 이식 메커니즘(#522). **잔여(본 runbook)=6c·D3·D4.**

## 0. 전제 (cutover 시점 충족 필수)
- MIG-1~11 이관 완료(거래처/마스터/전표/cash/order transform 등 — 메인 가이드 §2).
- **품목 import(MIG-2) 선행 필수** — `staging.ecount_item_alias`(product_db) populated. **품목 import = CSV 경로**(`품목/품목관계/품목계층그룹-Excel다운로드.csv` → `POST /admin/products/imports/ecount` itemFile/relationFile/groupFile). eCount API키 불요. 미populate 시 주문 라인 product 룩업 miss → reject.
- **MIG-8 주문 변환의 product 해석 = product-service 경유**(#529 수정): `Mig8OrderTransformService` 가 product-service `POST /products/internal/resolve-ecount-aliases`(소유 테이블 `staging.ecount_item_alias` 배치 해석)로 alias→product UUID 해석. ⚠️ 과거 버그(accounting_db 직접 cross-DB 쿼리→상시 실패)는 #529 로 해소 — **cutover dry-run 이 단독 적발**(issue #528). product-service 가 떠 있고 X-Internal-Token 설정돼야 변환 성공.
- `accounting.orders`/`order_lines` populated(MIG-8 `Mig8OrderTransformService` transform-from-staging 실행, 메인 가이드 Step 8 — 품목 import + product-service 가동 후).
- partner-service 거래처 마스터 + 시드 UUID 정합(#519) — 주문 partner 룩업(partnerId→code/biz) 의존.
- X-Internal-Token 설정(서비스 간 internal API).

## Step A — 주문 네이티브 이식 (슬6 메커니즘 실행)
1. **사전 확인**: `SELECT count(*) FROM orders WHERE kind='ECOUNT_MIG8' AND is_deleted=false;`(accounting_db) > 0.
2. **이식 실행**: `POST /admin/partner-orders/mig8-import?batchSize=500`(partner-order-service, `sales.partner-order.convert` 권한). 페이지 순회로 전량까지 반복(결과 fetched/created/skipped/rejected 카운트 확인). 멱등(`idempotency_key=ecount-mig8:orderNo`, 재실행 안전).
3. **검증 게이트** (전부 통과해야 Step B 진입):
   - `count(partner_orders WHERE idempotency_key LIKE 'ecount-mig8:%')` == 이식 대상 주문 수.
   - **reject=0** 또는 reject 사유 전수 조사: partner 룩업 miss(거래처 미정합 — #519 정합 확인) / product 룩업 miss(품목 staging 미populate — 전제 0 확인) / categoryKey blank. reject 주문은 가짜 생성 금지([[no-fake-data]]) — 원천(staging/룩업) 교정 후 재실행.
   - 상태 매핑 표본: COMPLETED→CONFIRMED, IN_PROGRESS/PENDING→DRAFT, CANCELED→CANCELED.
   - 금액 표본 cross-check: partner_orders.total_amount == accounting total_supply+total_vat. 라인 subtotal=supply+vat, price_vat=(supply+vat)/qty.
   - 네이티브 `판매 ▸ 주문서`(/sales/partner-orders)에서 이식 주문 조회·표시(거래처명/사업자번호 실표시).
4. **롤백**: 이식분 soft-delete(`UPDATE partner_orders SET is_deleted=TRUE WHERE created_by='mig8-import' ...`) 또는 idempotency_key prefix 기준. 재실행으로 복구.

## Step B — 주문 silo 폐기 (6c, Step A 검증 후에만)
> ⚠️ Step A 전량 이식·검증 완료 전 절대 금지(미편입분 사용자 접근 손실 방지).

준비된 6c 변경(cutover 시점 PR 머지):
- **FE**: AppLayout "주문서 관리 (이관)"(판매 flat, `/accounting/admin/orders`, testid sidebar-accounting-admin-orders) 링크 + `showAccountingAdminOrder` 제거. routes/index.tsx `/accounting/admin/orders`(+/:orderNo) route 제거. accountingAdminApi `listAccountingOrders`/`getAccountingOrder` + OrderSummary/OrderDetail 타입 제거.
- **BE(accounting)**: AccountingAdminQueryController `GET /orders`·`/orders/{orderNo}` + ORDER_PAGE_CODE + `listOrders`/`getOrderDetail` + DTO(OrderSummaryResponse/OrderDetailResponse) 제거. (단 6a `/internal/accounting/mig8-orders` export 는 이식 재실행 가능성 위해 cutover 안정 후 D3 와 함께 제거.)
- **auth**: PageCode `ECOUNT_MIG14_ORDER_LIST` 제거 + V61(권한모델 5테이블 정리, V59/60 패턴: role_page_permissions hard + templates/account/group/override soft delete WHERE page_code='ecount.mig14.order-list').
- 가드: 전체 mock suite([[fe-guard-removal-contract-tests]]) + V61 fresh-DB probe + menu-ia-contract 갱신(주문서 (이관) 링크 제거 박제) + Docker 실QA(네이티브 /sales/partner-orders 에 이식 주문 표시).
- **검증**: 슬1/2/4 패턴 동일. page-code 4종 sweep([[defect-family-sweep-fix]]).

## Step C — 중간테이블 물리 DROP (D3, cutover 안정 후)
> cutover 후 N일(예 운영 안정 14일) + 백업 확인 후. lineage 더 불요 시점.

준비된 DROP 마이그(별도, cutover-gated):
- accounting: `cash_disbursements`·`cash_receipts`·`orders`·`order_lines` DROP(+ staging.ecount_*_raw 보존 여부 결정 — 재import 가능성). MV `partner_aging_snapshot` DROP(슬1 lineage 종료). `Mig9AgingSnapshotRefreshService`/EcountReimportService 의 MV 참조 제거 동반.
- 6a `/internal/accounting/mig8-orders` export 제거(이식 재실행 불요 확정 후).
- 가드: fresh-DB probe, 의존 서비스(EcountReimportService 등) 무손상, 백업 필수.

## Step D — 원장대조/운영대시보드 최종 (D4)
- 현 슬4: `매출/매입 원장 대조`(ecount.mig14.ledger)·`운영 대시보드`(ecount.mig.ops-dashboard)는 회계 메뉴 flat 으로 잔존(cutover 검증 도구).
- cutover 후 결정(개발책임자): (a) 완전 제거(page-code/route/화면/V62) vs (b) 운영 admin 영구 보존(감사용 — MASTER 전용 가드 이동). 원장 대조 staging(ecount_*_ledger_raw)·운영 대시보드(Prometheus) lineage 동반 처리.

## 검증·완료 기준
- Step A: 이식 주문 수 == 대상, reject=0, 네이티브 화면 표시.
- Step B: silo 메뉴/route/endpoint/page-code 잔여 0, 네이티브 단일화, mock suite green.
- Step C: 중간테이블 DROP, 의존 서비스 health green, 백업 보관.
- Step D: 개발책임자 결정 반영.
- 전 단계: 슬라이스별 commit/push, dual review, Docker/라이브 실QA, CI green.

## 부록 — cutover dry-run 결과 (2026-06-20) + dev 환경 제약
**dry-run 단독 적발·수정**: MIG-8 주문 변환이 `accounting_db.staging.ecount_item_alias`(미존재 — product-service V7 가 product_db 에만 생성)를 cross-DB 직접 쿼리 → 'relation does not exist' **상시 실패** = accounting.orders 상시 0 = silo 빈화면의 진짜 원인. **변환이 한 번도 작동한 적 없는 cutover 블로커**(issue #528). → **#529 로 product-service alias 해석 경유 수정**(전제 0). dry-run 이 없었으면 cutover 당일 주문 이관이 실패했을 사안.

**dev 라이브 end-to-end 제약**: 품목 CSV import 가 dev 에선 `duplicate key ux_products_model_name_active`(시드 product model_name 충돌)로 차단 — **dev 전용 artifact**(cutover 는 fresh DB라 무관). → 슬6/MIG-8 fix 는 dev 에서 IT(product-service resolve-ecount-aliases staging seed 배치 + accounting transform client mock)로 검증, 실 주문 end-to-end 는 fresh-DB cutover 환경(Step A)에서 검증. dev 에서 실 주문 QA 필요 시 시드 product 제거(스택 교란 주의) 후 품목 import.
