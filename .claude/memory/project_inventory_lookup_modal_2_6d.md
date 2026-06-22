---
name: project_inventory_lookup_modal_2_6d
description: "Phase 2.6d 후속 — 주문/판매/구매 상세 품목 재고조회 모달(창고별, 0수량 토글)"
metadata: 
  node_type: memory
  type: project
  originSessionId: 527690b1-44aa-46ea-8abd-38502bfd74d3
---

**Phase 2.6d** — 품목 재고조회 모달. ✅ **구현 완료(2026-06-23 검증)** — `clients/desktop/src/renderer/routes/components/InventoryLookupModal.tsx`(품목 행 × 창고 열 매트릭스, 셀 가용/실/예약, showZero 토글 기본 OFF, VIRTUAL 창고 제외, bundleOnlyLines 세트 가드, UUID 미노출). 아래는 원 요구 기록(stale 아님, 참고용).

개발책임자 요구(2026-05-31):
- **주문서 / 출고전표(판매전표) / 입고전표(구매전표) 상세**에서 원하는 품목 선택 → **재고조회 모달** 표시.
- 모달 = 창고별 재고. **기본값: 수량 0 창고 숨김**(재고 있는 창고만). **토글 버튼: "0 수량 창고도 표시"** 켜면 전체 창고.
- 표시 = 창고별 **가용/실/예약**([[project_partner_order_status_model]] 재고 모델, 2.6c 와 동일 개념).
- API = `GET /inventory/balances?productId=`(이미 창고별 availableQty/reservedQty/totalQty 반환, 2.6c 구축). 0수량 포함 옵션은 파라미터 또는 FE 필터.
- 읽기전용 FE 기능 → 2.6c(reserve 도메인)와 분리, 별도 슬라이스로 결정(개발책임자 마우스 선택 [[always-mouse-choices]]).

**Why:** 출고/입고/주문 작업 중 품목 재고를 즉시 확인하는 운영 편의. 2.6c 가 가용/실/예약 데이터를 확정하므로 그 위에 조회 UX.
**How to apply:** 2.6c 머지 직후 spec→plan→5팀. design-system Modal + 토글 재사용. UUID 비공개([[feedback_uuid_no_user_visibility]]).
