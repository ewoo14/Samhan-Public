-- Phase 10 W10-1 종합 TM 후속 (PR #97) — partial unique index 보강
-- BE+QA 합의 (사용자 가드 적용) — 본 PR 즉시 채택

-- Fix 4 (QA-2): drivers app_user_id partial unique
--  - 풀스캔 회피 + 1:1 매핑 의무 (어플 사용자 ↔ Driver)
CREATE UNIQUE INDEX ux_drivers_app_user_active
    ON drivers (app_user_id)
    WHERE is_deleted = FALSE AND app_user_id IS NOT NULL;

-- Fix 7 (BE 부수 2): dispatches (dispatch_date, dispatch_type) partial unique
--  - 같은 날짜 + 같은 타입 = 1 dispatch 의무 (중복 입력 차단)
CREATE UNIQUE INDEX ux_dispatches_date_type_active
    ON dispatches (dispatch_date, dispatch_type)
    WHERE is_deleted = FALSE;
