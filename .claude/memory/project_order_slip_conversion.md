---
name: project-order-slip-conversion
description: "주문→출고전표 전환 — 부분전환+다중병합 구현 완료(Phase 2.6a/2.6b D2), 2026-06-07 개발책임자 정책 4건 현행 확정"
metadata: 
  node_type: memory
  type: project
  originSessionId: 66bf5482-6c8e-4d40-8915-cbe33b1c607d
---

# 주문 → 출고전표 전환 — 구현 완결 + 정책 확정 (2026-06-07 갱신)

당초 2026-05-30 "차기 슬라이스" 박제였으나 **Phase 2.6a(부분전환)/2.6b D2(다중 병합)에서 구현 완결** — 2026-06-07 정찰로 확인 (BE/FE/IT 완비).

## 구현 상태 (grounding 2026-06-07)
- **품목별 부분전환**: `PartnerOrderConvertService` — 라인 선택+수량, `convertedQuantity` 누적, 전량 시 CONVERTED 자동 전이(`markConvertedIfComplete`), 멱등 3중(idempotencyKey 스냅샷 + convertKeyUuid + slip 기존 반환), 재고 예약(reserve)+발행 실패 보상(release).
- **다중주문 병합**: `PartnerOrderMergeConvertService` — 같은 거래처만(409), `slip_source_orders` 역참조 N행, 헤더 충돌은 FE 선택 or '/'병기(shippingInfo).
- 권한: `sales.partner-order.convert` CREATE (단일/병합 공유). FE: SalesPartnerOrderDetailPage 전환 모달.

## 2026-06-07 개발책임자 정책 확정 — 4건 전부 현행 유지
1. 부분 병합 후 주문 상태 = **각 주문 독립 추적** (참여 주문별 전량 시점에 개별 CONVERTED).
2. 상이 거래처 병합 = **불허 유지** (업무 케이스 없음, 409).
3. 헤더 충돌 = **사용자 선택 or '/'병기** 저장 유지.
4. 재고 모델 = **전환 시 예약**(가용 감소), 실차감은 출고 프로세스 — 예약 모델 유지.

## 잔여
- `requireConvertible()` 이 slipNo!=null 만 검사 — CONVERTED status 명시 검사 보강 (2026-06-07 정비 슬라이스 처리 대상, FE 화이트리스트 방어 의존 해소).
