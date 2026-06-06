---
name: feedback_fe_canaccess_pagecode_be_match
description: FE role→canAccess(pageCode) 이관 시 page-code 는 그 action 의 실제 BE @RequirePermission 과 정확히 일치 + seed≤BE 가드 (dual review 필수)
metadata:
  type: feedback
---

FE 의 role 기반 인가(`role.includes`·`*_ROLES`·session 헬퍼)를 `usePermissions().canAccess(pageCode, action)` 로 이관할 때(권한그룹 Phase C2/C5), **page-code 매핑이 가장 흔한 결함원**. CI(mock) green 이어도 dual review(특히 Codex)가 반복 적발(C2b dispatch-reconcile, C5-2b canCreateTransfer/canQuerySales, C5-2c 삭제버튼).

**규칙**:
1. **page-code = 그 버튼/action 이 호출하는 실제 BE 엔드포인트의 `@RequirePermission(page, action)`** 과 정확히 일치. 테마틱/유사 page-code 금지(예: 이동전표 create 는 `inventory.transfer` 이지 `inventory.stock-transfer` 아님; 주문 삭제는 `sales.partner-order.edit` DELETE).
2. **action 정확**: update/delete/create/print 구분(7-action). 삭제 버튼은 `canAccess(page,'delete')`(또는 cancel→update) 별도 — update 로 퉁치지 말 것.
3. **FE>BE 불일치 금지**: 이관 page-code 의 seed grant role-set 이 그 기능의 **BE 가드보다 넓으면**(예: sales.slip.list seed=ACCOUNTANT/INVENTORY 도 view 이나 BE SlipSalesAccessGuard=SALES/MANAGER/MASTER) FE 노출>BE 403(UX 버그) → 그 헬퍼는 **이관 말고 유지**(canQuerySales 선례).
4. **버튼이 canAccess 미확인이면 추가**: possibleActions/status 만 보고 권한 미확인 버튼 = FE노출/BE403. canAccess 가드 동반.
5. **mock 카탈로그 보강은 seed 정확**: 추가 page-code 역할 grant 를 auth seed(V*.sql) can_view/can_edit 그대로(과다 grant 0). create-only/restore-only 는 MOCK_ACTION_ONLY_PAGES.

→ FE 인가 이관 슬라이스는 **dual review 의무**(page-code↔BE 대조는 정적 grep 으로만 잡힘).

관련: [[feedback_fe_guard_removal_contract_tests]], [[feedback_pgc_c2_widening_option_a]], [[feedback_dual_5agent_review]].
