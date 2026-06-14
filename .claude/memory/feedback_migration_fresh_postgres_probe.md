---
name: 마이그레이션 변경은 fresh Postgres probe 검증
description: Windows 로컬 Testcontainers IT skip 이 마이그 syntax/제약 오류를 가림 → 마이그 추가/수정 시 fresh Postgres probe 에 직접 적용해 검증
metadata:
  type: feedback
---
2026-06-12 PR #470(#1 V41) 회고. V41 matrix CHECK 닫는 괄호 1개 초과(`));`)가 fresh Postgres 적용 syntax error → 전 slip IT 컨텍스트 부팅 실패(CI 적색). **로컬 `gradlew :slip-service:test` 는 BUILD SUCCESSFUL** — Testcontainers IT 가 Windows npipe 로 skip([[testcontainers-windows-docker]]) 되어 마이그가 실제 fresh DB 에 적용된 적 없어 미검출. 또 로컬 slip_db 는 구 마이그가 이미 적용돼 재적용 안 함.

## 규칙
- **DB 마이그레이션(Flyway) 추가/수정 시 fresh Postgres probe 로 직접 적용 검증** (CI 전):
  ```
  docker exec samhan-postgres psql -U samhan -d postgres -tAc "DROP DATABASE IF EXISTS vXXprobe"
  docker exec samhan-postgres psql -U samhan -d postgres -tAc "CREATE DATABASE vXXprobe"
  # 대상 테이블 최소 스키마 + 대표 seed row 생성 후
  cat services/.../db/migration/VXX__*.sql | docker exec -i samhan-postgres psql -U samhan -d vXXprobe -v ON_ERROR_STOP=1
  ```
  → syntax/제약/backfill 오류를 CI 전에 잡는다. (DROP/CREATE DATABASE 는 트랜잭션 밖 — `-c "DROP;CREATE"` 한 줄 금지, 분리 실행.)
- backfill UPDATE 는 대표 legacy 값 seed 후 결과 행을 SELECT 로 실측(매핑·CHECK 위반 0 확인).
- 로컬 `gradlew test` BUILD SUCCESSFUL ≠ 마이그 검증(IT skip). [[standalone-boot-real-qa]] 와 동일 취지.

## 정규화(backfill) 마이그 3대 추가 교훈 (2026-06-15 PR #482 Phase2 전표번호 0제거 회고)
- **동기화 복사 컬럼 동반 필수**: 한 컬럼이 다른 컬럼의 복사본(예 V6 `ref_doc_no = ref_slip_no` 동기화, 또는 JSONB snapshot 사본)이면 원본만 정규화 시 복사본에 구값 잔류 → 노출 불일치. 정규화 마이그는 **복사/동기 컬럼 전수 동반**([[defect-family-sweep-fix]] 적용). slip_revisions snapshot JSONB(V48)·approval_attachments ref_doc_no(V7)가 사례.
- **bulk regexp 는 형식 앵커**: `regexp_replace(col,'-0+([0-9])','-\1')` 처럼 broad 하면 비대상 식별자(`SEED-001`, batch ID)까지 오변형. **정확 형식 앵커** `^([0-9]{4}/[0-9]{2}/[0-9]{2})-0+([1-9][0-9]*)$` (날짜부 0 보존 + all-zero→`-0` 방지 + 비날짜 미변형). TEXT 목록 컬럼(여러 참조)은 `'g'` 플래그.
- **운영 DB 선적용 금지(리뷰 확정 전)**: 마이그를 로컬 운영 DB 에 Flyway 로 적용한 뒤 리뷰로 파일이 바뀌면 **체크섬 불일치 → 다음 재기동 startup 실패**. 해법: 리뷰 확정 후 적용, 또는 적용했으면 `flyway_schema_history` 해당 version row 삭제(멱등 마이그면 다음 재기동 재적용=no-op) — 단 데이터는 이미 보정됨.

## How to apply
마이그 파일 건드린 PR 은 push 전 fresh probe 적용 1회 + (backfill 이면) 위 3대 교훈 점검. 관련: [[testcontainers-windows-docker]] [[enum-expansion-check-constraint]] [[changed-module-full-test-before-push]] [[standalone-boot-real-qa]] [[defect-family-sweep-fix]].
