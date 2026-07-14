# #809 — 전표/견적 품목 추가 시 (거래처+품목) 최근 수동단가 자동채움

- **일자**: 2026-07-15
- **PR**: #809 (feat/809-partner-product-price-memory)
- **연관**: 이슈 #809·spec `docs/specs/809-slip-estimate-recent-manual-price-spec.md`
- **상태**: 구현 진행 중 (조기 PR 시드)
- **민감도**: 🔴 가격 도메인 — 리뷰서 단가 로직 correctness 엄격 검증.

## 목표
전표(출고/입고)·견적 품목 추가 시 **(거래처+품목) 최근 사용 단가를 기억**해 자동채움(현재 catalog 정가·거래처 무관).

## 개발책임자 결정 (2026-07-15·"권고안대로 A"·슬라이스 선택 자율)
- ① 대상=**전표+견적**(주문 제외·주문은 DcConfig 규칙 유지)
- ② 저장소=**slip-service 신규 테이블**(정찰 해소: 견적도 slip-service 영속 → cross-service 불필요)
- ③ 확정=라인 저장 시 upsert · ④ **effective(마지막 사용) 단가 기억**(source 태그·override 갱신)
- ⑤ VAT=라인 관례 · ⑥ 키=**partnerId**(UUID·FE는 partnerCode→partnerId)

## 구현 (작성 예정)

_(구현 완료 시 채움)_

## 검증 (작성 예정)

_(테스트·라이브 QA — 리뷰 라운드에서)_

## 리뷰 이력 (작성 예정)

_(캐논 듀얼 — 진행 예정)_
