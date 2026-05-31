---
name: project_serial_inventory_model
description: "시리얼 인스턴스 재고 모델 (품목코드 그룹→UUID 시리얼) — 신규 대형 Phase, spec 박제 (2026-05-31)"
metadata: 
  node_type: memory
  type: project
  originSessionId: 527690b1-44aa-46ea-8abd-38502bfd74d3
---

**재고 = 시리얼(UUID) 인스턴스 단위 추적 모델** (개발책임자 확정 2026-05-31). 현 수량모델(stock_balances available/reserved/total)을 넘어서는 신규 대형 Phase. spec 박제: `docs/superpowers/specs/2026-05-31-serial-instance-inventory-design.md`.

**도메인 규칙 (개발책임자 확정)**:
- **UUID = 품목 시리얼 키(PK)**, 개별 instance 식별자. **품목코드(productCode) = 같은 품목 그룹**. 품목코드(1)→UUID(N).
- **관리방식 = 품목 카테고리로 지정**: `에어컨`/`판넬` = 개별 시리얼(1대=1 UUID row), `부자재` = batch(수량 묶음). (products.inventory_qty_mgmt 또는 category_key 기반)
- **입고(구매전표=입고전표, 동의어)** 구분: `구매`/`차용` → 품목코드 그룹에 수량만큼 새 인스턴스 생성(창고 입고). `반품`/`회차` → 그 **거래처+품목코드 출고이력 역-FIFO(LIFO)** 회수(재고 복원).
- **출고(판매전표)**: 품목코드+수량 → 개별시리얼=가장 먼저 생성된 인스턴스부터 FIFO 소진(received_at ASC)+출고처(거래처/전표) 기록 / batch=수량 차감.

**현행 자산(재활용)**: inventory `stock_lots`(입고 batch=인스턴스 토대: received_at/unit_cost/lot_no/status) + `stock_movements`(이력/출고처 추적: reference_id/type/lot_id) + FIFO deduct 이미 구현(`findAvailableLotsForFifo` received_at ASC). slip slip_type(INBOUND/OUTBOUND). products product_code 기존재.

**슬라이스 분해**: S1 인스턴스 기반(신규 `stock_instances` 테이블 + 도메인 + 카테고리 판정 + seed) → S2 입고 연동(구매전표→생성) → S3 출고 연동(판매전표→FIFO 소진+출고처) → S4 회수(반품/회차→역-FIFO). 각 독립 PR·실 QA.

**관계**: 2.6c(수량 reserve)는 별개 트랙으로 먼저 머지됨(#327). 2.6c reserve → 시리얼 인스턴스 status RESERVED 통합은 시리얼 Phase 에서. [[project_seed_product_uuid_catalog]](UUID single source)/[[project_inventory_lookup_modal_2_6d]](재고조회) 수혜. 별도 미결: 품목코드 그룹 product_code 정식화(spec `2026-05-31-product-code-grouping-design.md`, slip_lines 엔 product_code 있으나 products 마스터엔 컬럼만 있고 1:1 — 1:N 그룹 미구현).

**Why**: 삼성전자 등 거래처 입고 시 시리얼 단위 추적 + FIFO 출고 + 거래처별 반품/회차 회수 = 실 재고 업무 정확 반영.
