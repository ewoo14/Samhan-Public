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

## How to apply
마이그 파일 건드린 PR 은 push 전 fresh probe 적용 1회. 관련: [[testcontainers-windows-docker]] [[enum-expansion-check-constraint]] [[changed-module-full-test-before-push]] [[standalone-boot-real-qa]].
