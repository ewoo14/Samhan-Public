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

**슬라이스:** ✅**슬1 잔액스냅샷 폐기→partner-aging 머지#518**(`0d09e936`, V59 권한모델 5테이블 정리) · **슬2 현금 silo 폐기→분개장/입금매칭(다음)** · ~~슬3 분개장 source_type 가시성~~(D2=통합표시 확정→**폐기**) · 슬4 원장대조/운영대시보드 격리 · 슬5 토글그룹 해체 · 슬6 주문 네이티브 이식(D1=slip partner_orders 이식 확정, 대형).

**결정(개발책임자 확정):** D1=주문→slip-service partner_orders 이식 · D2=그대로 통합표시(배지/기간컷오프 없음, 슬3 폐기) · D3 cash_*/orders 물리 제거=Phase11 cutover 후 · D4 원장대조·운영대시보드 cutover 후 처리.

**🪤 슬1 교훈:** ①네이티브 대체화면 거래처 UUID 노출/미조회는 슬1 회귀 아닌 **선재 cross-service 시드 UUID 미정합**(silo 도 동일) — 라이브 실QA 단독 적발. ②**partner-service PartnerSeeder 도 제품 시더와 동일 @UuidGenerator 버그**(forceId 덮임)→native INSERT 로 deterministic UUID 박제(머지#519 `0501ac99`, PartnerSeederIT 회귀가드). 기존 dev 스택은 P-2026 삭제 후 partner-service 재기동 1회 필요. → [[seed-product-uuid-catalog]].

진행: 슬1+시드정합 머지 완료(2026-06-19 주말 무중단 세션). presence 에픽 완료. **다음=슬2 연속 진행**.
