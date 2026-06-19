---
name: project-ecount-native-fold
description: 이카운트 이관 자료는 별도 메뉴/저장(silo) 금지, 시드로 네이티브 도메인 편입 + "회계 관리자" 메뉴 폐기 에픽
metadata:
  type: project
---

2026-06-19 개발책임자 지적: "이카운트 이관 자료를 따로 메뉴로 만들지 말고 **시드로 기존 시스템에 편입**시켜야지. 과거 자료를 따로 저장하면 어떻게해." → [[project-replaces-ecount-gas-was-exporter]] · [[project-sheets-to-db-full-migration]] 전략의 구체 적용.

**현황(정찰 `docs/research/2026-06-19-ecount-native-fold-recon.md`):**
- 현금(지출/입금)은 **이미** MIG-9가 네이티브 `journals`(POSTED 복식부기)로 편입 → 분개장/원장/시산표/재무보고서/입금매칭에 노출됨. 그러나 중간테이블 `cash_disbursements`/`cash_receipts` + FE "회계 관리자"(회계 메뉴 하위 중첩 토글, page-code `ecount.mig14.*`)가 그 자료를 **중복 silo 화면**으로 또 보여줌.
- 주문(accounting `orders`, MIG-8)은 네이티브 미편입 silo(최대 갭 G1). 잔액 스냅샷은 native MV 파생인데 silo 화면 중복. 원장 대조·운영 대시보드는 cutover 검증 1회성 도구.

**방침:** 과거 자료=네이티브 일반 화면에서 보이게, "회계 관리자(MIG-14)" 상설 메뉴 폐기. 중간테이블은 사용자 비노출 lineage로만(물리 DROP은 Phase11 cutover 후). 원장대조/운영대시보드는 운영 admin으로 격리(cutover 전 폐기 금지).

**슬라이스(pre-cutover 전부 머지 완료, 2026-06-20 주말 무중단 세션):** ✅슬1 잔액스냅샷 폐기→partner-aging(#518 V59 5테이블) · ✅partner 시드 UUID 정합(#519, native INSERT) · ✅슬2 현금 silo 폐기→분개장/입금매칭(#520 V60) · ~~슬3~~(D2 통합표시→폐기) · ✅슬4 회계 관리자 그룹 해체→네이티브 메뉴 평면 편입(#521, 슬5 흡수: 원장대조/운영/수정요청=회계 flat, 주문서=판매 "(이관)") · ✅슬6 6a+6b 주문 이식 메커니즘(#522: accounting `/internal/.../mig8-orders` export + partner-order-service Mig8OrderImportService 멱등 이식, 상태매핑·partner/product 룩업). **잔여=cutover 전용**: 6c silo 폐기(ecount.mig14.order-list)·D3 물리 DROP·D4 격리도구.

**결정(개발책임자 확정):** D1=주문→**partner-order-service** partner_orders 이식(spec "slip-service" 정정 — 실 소유=partner-order-service, slip-service는 SlipSourceOrder 별개) · D2=그대로 통합표시(배지/기간컷오프 없음, 슬3 폐기) · D3 cash_*/orders 물리 제거=Phase11 cutover 후 · D4 원장대조·운영대시보드 cutover 후 처리. · 메뉴 정정(슬4): 신규 silo 섹션 금지, 기존 회계 메뉴 평면 편입+그룹 삭제; 주문서 관리는 판매 도메인.

**🪤 슬1 교훈:** ①네이티브 대체화면 거래처 UUID 노출/미조회는 슬1 회귀 아닌 **선재 cross-service 시드 UUID 미정합**(silo 도 동일) — 라이브 실QA 단독 적발. ②**partner-service PartnerSeeder 도 제품 시더와 동일 @UuidGenerator 버그**(forceId 덮임)→native INSERT 로 deterministic UUID 박제(머지#519 `0501ac99`, PartnerSeederIT 회귀가드). 기존 dev 스택은 P-2026 삭제 후 partner-service 재기동 1회 필요. → [[seed-product-uuid-catalog]].

진행: **에픽 pre-cutover 스코프 완결**(슬1·시드정합·슬2·슬4·슬6 6a+6b 전부 머지, 2026-06-20). 잔여는 Phase11 cutover 시점(6c silo 폐기·D3 물리 DROP·D4). 슬6 dev 실데이터 QA는 cutover 시(품목 import cross-DB 갭으로 dev 변환 불가→IT 실 Postgres 검증). **다음=개발책임자 지정 다음 우선작업**(품목/견적 에픽 잔여·외부연동 등). 🪤 슬6 듀얼리뷰가 mocked-IT false-green 뒤 P1 보안 fail-open(위조 X-User-Id export)+categoryKey 계약+price_vat/converted 의미버그 단독 적발→라이브 검증으로 폐쇄.
