# Slip DB Backup Runbook (Slice C 추가)

> Slice C 도입 후 `slips.signature_png` (BYTEA, 평균 30KB / row) 가 추가됨에 따라
> backup 명령에 binary 안전 옵션이 필수입니다.

## 1. 사용 금지 — plain format

```
# 사용 금지
pg_dump -U samhan -d slip_db --format=plain > slip_db.sql
```

이유: `bytea` 가 `\x...` hex 문자열로 직렬화되어 row 당 60KB+ 텍스트로 팽창,
restore 시 파싱 비용도 비례 증가. plain text 검색 가능성 거의 없음.

## 2. 권장 — custom format (binary)

```
# 권장
pg_dump -U samhan -d slip_db --format=custom --file=slip_db_$(date +%F).dump
```

장점:
- bytea 컬럼을 binary 그대로 직렬화 (압축 효과 ↑)
- 병렬 복원 (`pg_restore -j N`) 가능
- 선택적 테이블 복원 가능 (`pg_restore -t slips`)

복원:
```
pg_restore -U samhan -d slip_db_restored --clean --if-exists slip_db_2026-05-04.dump
```

## 3. 사이즈 추정 (Slice C 기준)

| 시점 | 슬립 누적 | 서명 PNG 누적 사이즈 |
| --- | --- | --- |
| 1개월 | 1,000 | 30 MB |
| 6개월 | 6,000 | 180 MB |
| 12개월 | 12,000 | 360 MB |
| MinIO 마이그 트리거 (월 1만건) | — | Phase 6 deferred |

- TOAST 압축 자동 적용 (PostgreSQL `bytea` ≥ 2KB) → 실제 디스크 사이즈 ~70%
- pg_dump custom format 추가 압축 → backup 파일 사이즈 ~50% 수준

## 4. VACUUM / Autovacuum 영향

- `slips` 테이블 row 사이즈 증가 → autovacuum 빈도 영향 미미 (UPDATE 시에만
  bytea 컬럼 재기록)
- 서명 1회 INSERT 후 변경 거의 없음 (무효화 시 bytea NULL 처리) → bloat 위험 낮음
- 모니터링: `pg_stat_user_tables.n_dead_tup` 주기 점검 (Phase 5 prometheus
  pg_exporter 도입 후 자동화)

## 5. MinIO 마이그 트리거 (Phase 6 deferred)

월 1만건 초과 시 마이그 시나리오:
1. MinIO 버킷 신규 (`slip-signatures`)
2. `signature_png_url VARCHAR(500)` 컬럼 추가, 기존 `signature_png BYTEA` 유지
3. 백필 배치: `signature_png` 읽어 MinIO 업로드 후 URL 기록
4. read path: URL 우선, 없으면 BYTEA fallback
5. cutover 후 BYTEA 컬럼 NULL 처리 + V? 마이그로 컬럼 drop

본 슬라이스에서는 컬럼 명세만 확정 (`signature_channel` 컬럼이 추후
`MOBILE_CANVAS` / `S3_OBJECT` 등 확장 키로 사용).
