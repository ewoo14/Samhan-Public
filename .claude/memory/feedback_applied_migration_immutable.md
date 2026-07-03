---
name: feedback_applied_migration_immutable
description: 이미 적용된 Flyway 마이그 파일은 주석조차 수정 금지 — checksum mismatch 로 기존 DB(prod/dev) 기동 실패. CI fresh-DB 미검출, 라이브 재빌드 적발
metadata:
  type: feedback
---

이미 머지·적용된 Flyway 마이그레이션 파일(`V*.sql`)은 **주석 한 줄도 수정 금지**. Flyway 는 파일 내용 checksum 으로 검증하므로, 적용된 마이그를 변경하면 그 마이그가 이미 적용된 DB(prod·로컬 dev)에서 `Migration checksum mismatch for version N` → 서비스 기동 실패(crash loop).

**Why:** F4(PR #506)에서 docs 동기화로 V21 주석을 수정 → F1.5(#504) 머지로 V21 이 이미 적용된 dev DB 에서 product-service crash loop. **CI fresh-DB 는 V21 을 fresh 적용(빈 history)이라 mismatch 미발생 → green 위장**. 기존 DB 대상 라이브 재빌드(`docker compose build/up` 후 기동)가 단독 적발 — [[feedback_migration_fresh_postgres_probe]] 의 역방향(fresh probe 는 신규 마이그 syntax, 본 건은 적용된 마이그 변경).

**How to apply:** ①마이그 파일은 적용 후 불변 이력으로 취급 — 내용(주석 포함) 변경 금지. 문서/설명 갱신은 비-마이그 파일(Javadoc·dev-report·spec)에 기재. ②마이그 변경(신규/수정) 시 fresh probe + **기존 DB 대상 재빌드 기동 검증** 둘 다(fresh-CI 만으로 불충분). ③checksum 정정 불가피 시 Flyway repair 별도 검토. 머지 전 라이브 재빌드 기동 확인 권장.

**2026-07-03 재확장(E3 S2 V51/V52 — out-of-order):** 미머지 브랜치 마이그를 리뷰 라운드에서 부득이 수정(적용 환경=본인 로컬 dev DB 1곳뿐일 때 한정 허용)했다면, 로컬 재적용은 **수정된 버전만이 아니라 그 이후 전부** — `flyway_schema_history` 에서 V51 행만 삭제하면 V52 가 이미 최신이라 **out-of-order validate 실패로 부팅 crash**(실측). V51+V52 동시 삭제 후 순서 재적용(멱등 전제 확인)으로 해소. 재적용 전 데이터 전제(preflight 대상 등) 실측 필수.

**2026-07-03 확장(E3 S1 V79/V48 교훈):** "적용됨"은 머지/prod 뿐 아니라 **미머지 PR 내 마이그가 QA/dev 라이브 재빌드로 로컬 공유 DB 에 적용된 경우도 포함**. 미머지라도 로컬 적용 후 재수정(리뷰 라운드 fix 등) 시 그 로컬 DB 에서 checksum mismatch crash(#706 V79 를 라이브 QA 로 로컬 적용 후 Codex HIGH-fix 로 재수정 → 후속 세션 auth_db 부팅 crash, QA 가 flyway_schema_history UPDATE 로 우회). → **리뷰 라운드에서 마이그 로직 변경이 필요하면 동일 PR 내 신규 V 로 추가**(이미 로컬 적용된 V 수정 금지 — 예: @Version 은 V48 수정 아닌 **V49 신규**로 처리·E3 S1 준수). 로컬 crash 시 flyway repair or 해당 service DB fresh(회사PC 등 타 환경 공유 필요).
