-- V2__add_unit_processing_flag.sql
-- PR-D Part 2-2 — Notion CSV `단위처리` (Yes/No) 컬럼을 native 보존하기 위한 boolean flag 추가.
--
-- 배경:
--   Notion DB 의 거래처 DC 정보 시트는 "단위처리" 라는 Yes/No 플래그 컬럼을 사용한다.
--   이는 V1 의 unit_round_to (INT, 반올림 단위 — 예: 1000 = 천원 단위 반올림) 와는 다른
--   의미 (단위처리 적용 여부 자체) 이므로 별도 컬럼으로 보존한다.
--
--   - unit_round_to (V1, INT) — 단가 반올림 단위 (legacy UNIT_ROUND_TO)
--   - unit_processing_enabled (V2, BOOLEAN) — Notion 시트 "단위처리" Yes/No 플래그
--
-- 기본값 FALSE — 기존 row 는 단위처리 미적용 으로 마이그레이션.

ALTER TABLE dc_configs
    ADD COLUMN unit_processing_enabled BOOLEAN NOT NULL DEFAULT FALSE;
