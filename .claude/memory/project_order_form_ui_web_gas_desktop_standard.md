---
name: project_order_form_ui_web_gas_desktop_standard
description: 주문서 UI — GAS 방식은 웹 주문서 전용, 데스크탑 주문서 메뉴는 타 메뉴와 동일 표준 UI 유지
metadata:
  node_type: memory
  type: project
---

2026-07-05 개발책임자 결정. **주문서(order form) 메뉴 UI 디자인 이원화**:

- **웹 주문서(web 주문서)만** = **GAS 방식 그대로** 프론트 구성 유지. (레거시 GAS 주문서 화면 디자인/레이아웃을 웹에서 그대로 재현. [[project_sp_08_legacy_gas_parity]] 파리티 대상.)
- **삼한 퍼블릭 데스크탑(clients/desktop) + 모바일(clients/mobile-staff)의 주문서 메뉴** = **다른 메뉴와 동일한 UI 디자인으로 통일**(design-system 표준 컴포넌트·토큰·레이아웃). GAS 방식 아님.

**Why**: "GAS 방식 그대로 프론트 구성"이라는 지침은 **오직 웹 주문서**를 지칭. 데스크탑·모바일 주문서 메뉴까지 GAS 스타일로 만들면 앱 내 타 메뉴와 이질적. 데스크탑·모바일은 일관된 ERP UI 유지가 원칙(2026-07-05 개발책임자 "데스크탑뿐 아니라 모바일도 타 메뉴와 통일").

**How to apply**: 주문서 관련 작업([[project_order_slip_conversion]]·[[project_partner_order_status_model]]·GAS 이관 주문 intake) 시 — 대상이 **웹**이면 GAS 레이아웃 재현, **데스크탑/모바일**이면 design-system 표준 UI(타 메뉴 동일 패턴)로 구성. 데스크탑·모바일 주문서에 GAS 화면을 그대로 이식하지 말 것. [[project_replaces_ecount_gas_was_exporter]]
