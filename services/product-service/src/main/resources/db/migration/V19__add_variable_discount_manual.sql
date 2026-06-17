-- V19: 변동DC 수동 override 플래그
-- 근거: useK2($L$2)=변동DC 초기값은 시트 sync 로 적재하되,
--   견적품목 관리의 수동 토글은 variableDiscountManual=true 로 보호하여 sync 가 덮어쓰지 않는다.
--   구성품/겹침 탭의 useK2=false 가 견적 탭 품목을 false 로 오염시키는 회귀도 함께 차단한다.
ALTER TABLE products ADD COLUMN IF NOT EXISTS variable_discount_manual BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN products.variable_discount_manual IS
    '변동DC 수동 override 여부. true 이면 ProductSheetSyncService 가 has_variable_discount 를 시트 기준으로 덮어쓰지 않음.';
