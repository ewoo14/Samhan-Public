---
name: feedback_applied_migration_immutable
description: 이미 적용된 Flyway 마이그 파일은 주석조차 수정 금지 — checksum mismatch 로 기존 DB(prod/dev) 기동 실패. CI fresh-DB 미검출, 라이브 재빌드 적발
metadata:
  type: feedback
---

이미 머지·적용된 Flyway 마이그레이션 파일(`V*.sql`)은 **주석 한 줄도 수정 금지**. Flyway 는 파일 내용 checksum 으로 검증하므로, 적용된 마이그를 변경하면 그 마이그가 이미 적용된 DB(prod·로컬 dev)에서 `Migration checksum mismatch for version N` → 서비스 기동 실패(crash loop).

**Why:** F4(PR #506)에서 docs 동기화로 V21 주석을 수정 → F1.5(#504) 머지로 V21 이 이미 적용된 dev DB 에서 product-service crash loop. **CI fresh-DB 는 V21 을 fresh 적용(빈 history)이라 mismatch 미발생 → green 위장**. 기존 DB 대상 라이브 재빌드(`docker compose build/up` 후 기동)가 단독 적발 — [[feedback_migration_fresh_postgres_probe]] 의 역방향(fresh probe 는 신규 마이그 syntax, 본 건은 적용된 마이그 변경).

**How to apply:** ①마이그 파일은 적용 후 불변 이력으로 취급 — 내용(주석 포함) 변경 금지. 문서/설명 갱신은 비-마이그 파일(Javadoc·dev-report·spec)에 기재. ②마이그 변경(신규/수정) 시 fresh probe + **기존 DB 대상 재빌드 기동 검증** 둘 다(fresh-CI 만으로 불충분). ③checksum 정정 불가피 시 Flyway repair 별도 검토. 머지 전 라이브 재빌드 기동 확인 권장.
