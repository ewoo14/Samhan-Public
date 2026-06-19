# spec: 이카운트 네이티브 편입 슬6 — 주문 네이티브 이식 (대형·cross-service)

> 2026-06-20. 에픽 [[project-ecount-native-fold]] 마지막 silo(주문, G1 최대 갭). 개발책임자 결정: **설계 spec 먼저 → 승인 후 구현**. 상태매핑=제안대로 승인.
> 정찰 근거: `docs/research/2026-06-19-ecount-native-fold-recon.md` §2 G1 + 본 spec 추가 정찰(2026-06-20).

## 0. 정찰 정정 (spec D1 대비 실측)
- **이식 대상 = partner-order-service** (`partner_order_db.partner_orders` + `partner_order_lines`). 에픽 spec D1 의 "slip-service partner_orders"는 **부정확** — 실 소유는 partner-order-service. (slip-service 의 `SlipSourceOrder`는 slip↔order 연결 엔티티로 별개.) → **D1 정정**.
- **dev 데이터 현황**: `staging.ecount_order_raw` **26,084행** 보유. 그러나 `Mig8OrderTransformService.transformFromStaging`(수동 batch)가 dev 미실행 → `accounting.orders`/`order_lines` **0행**. "주문서 관리 (이관)" silo 는 dev 에서 빈 화면. → 슬6 은 **cutover급**(실 이식 데이터는 MIG-8 변환 선행 필요).

## 1. 목표
eCount 이관 주문(`accounting.orders`/`order_lines`, MIG-8)을 네이티브 주문 도메인(`partner-order-service partner_orders`/`partner_order_lines`)으로 cross-service 이식. 이식 후 "주문서 관리 (이관)" silo(page-code `ecount.mig14.order-list`, /accounting/admin/orders) 폐기 → 네이티브 `판매 ▸ 주문서`(/sales/partner-orders) 단일화.

## 2. 메커니즘 (cross-DB·cross-service)
서로 다른 DB(accounting_db ↔ partner_order_db) → Flyway cross-DB 불가. **pull 모델**: partner-order-service 가 이식 주체.
- 신규 `Mig8OrderImportService`(partner-order-service): accounting-service internal API `GET /internal/accounting/mig8-orders`(신규, X-Internal-Token, 페이지네이션)로 이관 주문+라인 조회 → partner/product 룩업 보강 → `partner_orders`/`partner_order_lines` 멱등 INSERT(native jdbcTemplate, [[seed-product-uuid-catalog]] 패턴).
- 신규 `POST /admin/partner-orders/mig8-import`(partner-order-service, page-code 신규 또는 system.* 게이트) 또는 CommandLineRunner(cutover 1회). batchSize 파라미터.
- **멱등성**: `idempotency_key = "ecount-mig8:" + orderNo` (UNIQUE) → 재실행 skip. `ON CONFLICT (idempotency_key) DO NOTHING`.

## 3. 필드 매핑

### orders → partner_orders
| partner_orders (NOT NULL) | 소스 | 비고 |
|---|---|---|
| id | `deterministicId("partner-order:mig8", orderNo)` | 결정적 UUID(재이식 안정) |
| partner_code | partner 룩업(partner_id→code) | #519 시드정합으로 resolve. miss 시 reject(보고) |
| biz_code | partner 룩업(사업자번호) | 〃 |
| order_no | `orders.order_no` | 형식 [[slip-order-number-format]] YYYY/MM/DD-N 정합 확인(불일치 시 정규화) |
| status | `progress_status` 매핑(§4) | |
| slip_publish_status | `linked_slip_no` 유무 → PUBLISHED / 기본 NOT_PUBLISHED | 확인 필요 |
| total_amount | `total_supply_amount + total_vat_amount` | |
| idempotency_key | `"ecount-mig8:"+order_no` | UNIQUE |
| revision_count / lock_version | 0 | |
| due_date | `valid_until` | nullable |
| memo | `reference`/`payment_terms` 조합 | nullable |
| source_estimate_id | NULL | 이관 주문은 견적 출처 없음 |
| confirmed_at | status=CONFIRMED 시 modified_at/created_at | |

### order_lines → partner_order_lines
| partner_order_lines (NOT NULL) | 소스 | 비고 |
|---|---|---|
| id | `deterministicId("partner-order-line:mig8", orderNo+":"+line_no)` | |
| partner_order_id | 상위 매핑 id | |
| product_id | `order_lines.product_id` | null 가능 → product 룩업/reject 정책 |
| model_name / product_name / category_key | product-service 룩업(product_id) | 룩업 miss 시 item_name fallback + category_key 기본값 결정 필요 |
| quantity | `quantity` | |
| price_vat | `unit_price`(VAT 포함 여부 확인) | accounting unit_price 의미 정합 |
| subtotal | `supply_amount + vat_amount` 또는 line_total | |
| converted_quantity | `quantity`(기본=수량) | 의미 확인 |

## 4. 상태 매핑 (개발책임자 승인)
- `COMPLETED` → `CONFIRMED` (완료)
- `IN_PROGRESS`/`PENDING` → `DRAFT` (진행중)
- `CANCELED` → `CANCELED`
([[project-partner-order-status-model]]: 진행중=DRAFT / 완료=CONFIRMED / 보류=ON_HOLD(미사용)). CANCELED 복원(RESTORE) 정책은 이식분에 별도 영향 없음(상태만 보존).

## 5. 룩업 의존
- partner_id(UUID) → partner_code/biz_no: partner-service internal(`/internal/partners/{id}/summary`). #519 시드정합으로 dev resolve 가능. **miss row = reject + 보고**(가짜 생성 금지 [[no-fake-data]]).
- product_id → model_name/product_name/category_key: product-service internal. miss 시 정책(item_name fallback + category 기본) 결정 필요.

## 6. dev QA 방안 (결정 필요)
dev `accounting.orders` 0행 → 이식 실데이터 없음. 옵션:
- **(A 권장)** MIG-8 변환(`Mig8OrderTransformService` 26k raw→accounting.orders, batch) 선행 실행 → 실 이관 주문으로 이식·Docker 실QA([[no-fake-data]]·[[overnight-live-capture]] 정합). 26k 중 일부 batch 로 QA 후 전량.
- (B) IT(Testcontainers 합성 픽스처)만 — cutover 시 실데이터 검증. 라이브 캡처 불가(silo 빈 화면).
→ A 권장(실데이터 원칙). 단 26k 변환은 별도 운영급이라 batch 규모 확인.

## 7. silo 폐기 (이식 검증 후, 슬1/2 패턴)
- FE: "주문서 관리 (이관)"(판매 flat, /accounting/admin/orders, testid sidebar-accounting-admin-orders) 링크 제거 + showAccountingAdminOrder 제거.
- BE(accounting): AccountingAdminQueryController GET /orders·/orders/{orderNo} + ORDER_PAGE_CODE + listOrders/getOrderDetail + DTO(OrderSummaryResponse/OrderDetailResponse) 제거.
- auth: PageCode.ECOUNT_MIG14_ORDER_LIST 제거 + V61(권한모델 5테이블 정리, V59/60 패턴).
- routes/index.tsx /accounting/admin/orders(+/:orderNo) route 제거.
- **단, 이식 완료(전량 partner_orders 적재) 확인 후에만** silo 폐기(미편입분 손실 방지). accounting.orders/order_lines lineage 는 cutover 후 D3.

## 8. 가드
- [[migration-fresh-postgres-probe]]: V61 fresh-DB probe. 이식 job 멱등성(2회 실행 동일).
- cross-service IT: partner-order-service Mig8OrderImportService + accounting internal API + partner/product @MockBean([[it-mockbean-external-clients]]) 격리. Linux CI Testcontainers([[testcontainers-windows-docker]]).
- [[changed-module-full-test-before-push]], [[enforcement-real-http-test]](internal API 계약).
- 이식 reject(룩업 miss) 정직 보고(가짜 생성 금지).

## 9. 개발책임자 결정 대기 (구현 착수 전)
1. **이식 메커니즘**: pull 모델(partner-order-service Mig8OrderImportService + accounting internal API) 채택? vs accounting push?
2. **dev QA**: §6 A(MIG-8 변환 선행, 실데이터) vs B(IT만)?
3. **룩업 miss 정책**: partner/product 룩업 miss row = reject 보고(권장) vs 부분 적재?
4. **slip_publish_status / price_vat / converted_quantity 의미** 확정(§3 비고).
5. **이식 job 실행 주체**: 운영 endpoint(권한 게이트) vs cutover CommandLineRunner 1회?
6. silo 폐기 시점: 이식 전량 검증 후(권장).

## 10. 슬라이스 분해 (대형 → 서브)
- 슬6a: accounting internal API(`/internal/accounting/mig8-orders`) + DTO + IT.
- 슬6b: partner-order-service Mig8OrderImportService(룩업·매핑·멱등 INSERT) + cross-service IT + (A 시) dev MIG-8 변환·실QA.
- 슬6c: silo 폐기(FE/BE/auth V61) + 네이티브 단일화 실QA.
