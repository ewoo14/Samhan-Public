# #809 — 전표/견적 품목 추가 시 (거래처+품목) 최근 수동단가 자동채움

- **상태**: 기획(정찰 완비[이슈 본문]·PM 권고 결정 제시) · **구현 대기(개발책임자 결정 확정)** — 2026-07-15 야간 자율(질문 불가) 준비
- **연관**: 이슈 #809(개발책임자 요청·현재코드 분석 포함)·`slip-service`·`dc-config-service`·`clients/desktop`
- **민감도**: 🔴 **가격(단가) 도메인** — 실 전표/견적 단가에 영향. 자율 구현보다 개발책임자 결정 확정 후 착수 권장([[feedback_integrity_domain_policy_preconfirm]] 인접·#816 ③-A 교훈).

## 목표 (이슈 확인)
전표(출고/입고)·견적에 품목 추가 시 **(거래처+품목) 이전 수동단가를 기억**해 자동채움. 현재는 catalog 정가(`Product.sellingPrice`·거래처 무관)로만 채움.

## 정찰 요약 (이슈 본문 상세)
- `(partnerId, productId) → 최근단가` 저장/조회 엔티티·endpoint **전무**. 수동 단가는 라인 row 에만 잔존.
- 유사하나 축 불일치: `PriceHistory`(품목+시행일·거래처축X)·`PartnerPriceDiscount`(거래처 1행 기본할인·품목축X)·`Product.fixedDiscountRate`(품목·거래처X).
- 주문(partner-order)만 서버가 DcConfig **할인율 규칙**(정가×(1−율)−옵션) 적용 — "과거 수동단가 재현" 아님.

## 🔑 스코프 결정 (개발책임자 확정 필요 — PM 권고 병기)

| # | 결정 | PM 권고 | 근거 |
|---|---|---|---|
| ① | **대상 범위** | **전표(출고/입고) + 견적** (주문 제외) | 이슈 의도="전표·견적". 주문은 이미 DcConfig 규칙가라 "최근 수동단가" 개념과 상충(규칙 vs 기억) → 주문은 현행 유지 |
| ② | **저장소 위치** | **slip-service 신규 테이블 `partner_product_price_memory`** + endpoint | ✅ **정찰 해소(2026-07-15)**: 견적도 slip-service 영속(`slip/estimate/domain/EstimateLine`·`Estimate`). 전표+견적 **둘 다 slip-service** → **cross-service 불필요**·양쪽 라인저장서 upsert/read. 단일 store |
| ③ | **"확정"(upsert) 시점** | **라인 저장(POST/PUT slip/estimate line)** 시 upsert | 마감/결재는 늦음. 저장=이 거래처+품목의 단가 확정 신호 |
| ④ | **source/우선순위** | **저장된 라인 단가(effective) 기억**(source 태그). 조회 시 최근단가>정가 폴백·**사용자 override 항상 우선**(override 시 다음 저장서 갱신) | "마지막 사용 단가" 재현이 실용적. 순수 "수동 편집만" 구분은 FE 플래그 필요(복잡) → effective 단가로 단순화(단, 개발책임자 "수동만" 원하면 FE isManuallyEdited 플래그 추가) |
| ⑤ | **VAT 기준** | 라인 `unitPrice` 기존 관례 그대로(공급가 기준·SlipLine 규약) | 라인과 동일 basis 유지(포함/제외 혼선 방지) |
| ⑥ | **거래처 식별키** | 내부 **partnerId(UUID)** 저장·FE는 partnerCode→partnerId resolve | UUID 안정키(거래처코드 변경 내성). UUID 비공개 유지 |

## 구현안 (결정 ①~⑥ 가정·확정 후 조정)
1. **BE(slip-service)**: `PartnerProductPriceMemory`(BaseEntity·partnerId·productId·unitPrice·source·updatedAt·유니크(partnerId,productId)) + repo + Flyway V+ (slip-service). 라인 저장 서비스(`SlipService.addSlipLinesExpanded`)에서 upsert. `GET /internal/price-memory?partnerId&productId`(단건)·bulk 조회.
2. **FE(clients/desktop)**: `SlipFormPage`/`EstimateFormPage` 품목 선택 핸들러 — 최근단가 조회 → 있으면 자동채움(정가 대신)·없으면 정가 폴백. override 가능.
3. **견적 BE 배선**: 견적 라인 저장 경로 확인 후 동일 upsert(또는 slip-service client 경유).
4. **테스트+라이브 QA**: (partner,item) 저장→재추가 시 최근단가 자동채움 실증(#815/#816 패턴).

## 캐논 워크플로우
Opus 기획(본 spec) → **개발책임자 ①~⑥ 확정** → 조기 PR → Codex 개발 → Opus 5-agent+fix+라이브QA ↔ Codex 적대 → 0수렴 → PM 종합 → CI → 머지.

## ⚠️ 착수 전 확인 (야간 자율 미착수 사유)
가격 도메인 민감 + 결정 ①②④가 동작/아키텍처 분기(범위·저장소·source semantics)라, **개발책임자 확정 후 구현**. 특히 ④(수동만 vs effective)·②(견적 BE 저장소)는 정찰/결정 필요.
