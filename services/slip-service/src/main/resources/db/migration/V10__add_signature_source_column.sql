-- V10__add_signature_source_column.sql
-- Slip Service — Phase 10 W10-4 (PR #99): 전자서명 source 컬럼 추가
-- 기존 LINK (SMS/Aligo 공개 모바일 서명) + 신규 APP (arologis 모바일 어플 직접 캡처) 통합
--
-- 컨텍스트:
--   * V5 / V6 단계에서 Slip 자체에 인수자 + 기사 서명 컬럼 인라인.
--   * W10-4: arologis-service 의 driver-app 직접 서명 (Signature.source=APP) 호출이 slip-service
--     로 전파되어 본 슬립 signature 가 갱신됨.
--   * 본 마이그레이션 = LINK / APP 구분 컬럼 1개 + audit 테이블 동일 컬럼 1개 추가.
--
-- 컬럼 컨벤션 (V5 계승):
--   * VARCHAR(20) NOT NULL DEFAULT 'LINK' — 기존 데이터 LINK 로 backfill, 신규 데이터는
--     서비스 레이어가 명시적으로 LINK / APP 지정 (Slip.recordSignature / recordDriverSignature 시그니처 유지).
--   * COMMENT 의무 (운영 자서: ALTER TABLE 단계에서 의미 식별 가능).
--
-- 회귀 영향 (Phase 6 IT 11 case 학습):
--   * V5/V6 의 signed_at, driver_signed_at 컬럼은 변경 X.
--   * 기존 IT 가 verifying signed_at IS NOT NULL → 영향 0.
--   * Hibernate 매핑 추가 시 nothing 매핑 + DEFAULT 'LINK' 로 ddl-validate 통과.

----------------------------------------------------------------------
-- 1) slips — 인수자 서명 source 컬럼 (V5 의 signed_at 외 7 필드 set 에 1개 추가)
----------------------------------------------------------------------
ALTER TABLE slips
    ADD COLUMN signature_source VARCHAR(20) NOT NULL DEFAULT 'LINK';

COMMENT ON COLUMN slips.signature_source IS
    'LINK = SMS/Aligo 링크 발급 (Public 모바일 인수자 서명) / APP = arologis 모바일 어플 직접 캡처 (W10-4)';

----------------------------------------------------------------------
-- 2) slips — 기사 서명 source 컬럼 (V6 의 driver_signed_at 외 4 필드 set 에 1개 추가)
--    인수자 컬럼과 별도 — 한 슬립에서 인수자 = LINK, 기사 = APP 같은 혼합 가능.
----------------------------------------------------------------------
ALTER TABLE slips
    ADD COLUMN driver_signature_source VARCHAR(20) NOT NULL DEFAULT 'LINK';

COMMENT ON COLUMN slips.driver_signature_source IS
    'LINK = SMS/Aligo 링크 발급 (Public 모바일 기사 서명) / APP = arologis 모바일 어플 직접 캡처 (W10-4)';

----------------------------------------------------------------------
-- 3) slip_signature_audit — audit 행에도 source 보존 (전자서명법 §17 무결성 입증).
--    audit 행은 signer_name 컬럼이 NULL 가능 (RECORD/RECORD_DRIVER 모두 사용),
--    source 도 NULL 허용으로 기존 행 호환 + 신규 행은 service 가 명시.
----------------------------------------------------------------------
ALTER TABLE slip_signature_audit
    ADD COLUMN signature_source VARCHAR(20);

COMMENT ON COLUMN slip_signature_audit.signature_source IS
    '서명 source — RECORD/RECORD_DRIVER action 시점 LINK 또는 APP. INVALIDATE 시 NULL 가능.';

----------------------------------------------------------------------
-- 4) APP source 슬립 lookup 가속 — arologis-service 호출 통계용 secondary index.
----------------------------------------------------------------------
CREATE INDEX ix_slips_signature_source_app
    ON slips (signed_at DESC)
    WHERE is_deleted = FALSE AND signature_source = 'APP' AND signed_at IS NOT NULL;

CREATE INDEX ix_slips_driver_signature_source_app
    ON slips (driver_signed_at DESC)
    WHERE is_deleted = FALSE AND driver_signature_source = 'APP' AND driver_signed_at IS NOT NULL;
