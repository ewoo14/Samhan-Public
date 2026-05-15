-- D-AX-21 — 공개 업무번호 범위 정합.
-- 전표번호는 사용자 메뉴/속성별 공개번호이므로 판매전표와 구매전표가 같은 날짜에
-- 같은 "YYYY/MM/DD-{순번}" 값을 가져도 된다. UUID PK + slip_type 으로 내부 유일성을 유지한다.

ALTER TABLE slip_number_sequences
    ADD COLUMN IF NOT EXISTS slip_type VARCHAR(20) NOT NULL DEFAULT 'OUTBOUND';

ALTER TABLE slip_number_sequences
    DROP CONSTRAINT IF EXISTS ux_slip_number_sequences_date;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ux_slip_number_sequences_date_type'
          AND conrelid = 'slip_number_sequences'::regclass
    ) THEN
        ALTER TABLE slip_number_sequences
            ADD CONSTRAINT ux_slip_number_sequences_date_type UNIQUE (slip_date, slip_type);
    END IF;
END $$;

DROP INDEX IF EXISTS ux_slips_slip_no_active;

CREATE UNIQUE INDEX IF NOT EXISTS ux_slips_slip_type_no_active
    ON slips (slip_type, slip_no)
    WHERE is_deleted = FALSE;
