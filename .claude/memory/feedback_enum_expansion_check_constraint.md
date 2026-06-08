---
name: enum-expansion-check-constraint
description: 영속 enum 값 확장 시 DB CHECK 제약(IN 목록) 마이그레이션 동반 필수 — enum/시드만으론 INSERT 거부
metadata:
  type: feedback
---

영속되는 enum(예 arologis `AdminUserRole`)에 값을 추가할 때 Java enum + 시드 변경만으로는 부족하다. 해당 enum 이 저장되는 컬럼에 **CHECK 제약(`role IN ('A','B')`)**이 걸려 있으면 신규 값 INSERT 가 제약 위반으로 거부된다(런타임 HTTP 500, `*_role_check` violation).

**Why**: PR #432(arologis 2→6롤) 회고. `auth_admin_user.role`(V7) + `arologis_role_change_history.new_role/previous_role`(V14)에 구 2롤 CHECK 가 있어, enum 확장 + 권한 시드(auth V53)가 다 통과했는데도 신규 롤 직원 생성이 `auth_admin_user_role_check` 위반 500. **정적 dual review(클로드 2트랙 + 크로스체크) 전부 통과** — 실 Postgres INSERT(풀스택 실 QA)만 적발. [[standalone-boot-real-qa]] · [[no-fake-data-ever]] 가치 재실증.

**How to apply**: enum 값 추가 슬라이스에서
1. 영속 컬럼 전수 grep — `grep -rn "IN ('OLD_A','OLD_B')" **/db/migration/*.sql` 로 해당 enum 의 모든 CHECK 제약(테이블·컬럼 nullable 여부 포함) 식별.
2. 신규 마이그레이션으로 `DROP CONSTRAINT IF EXISTS <name>; ADD CONSTRAINT <name> CHECK (... IN (전체값))` (nullable 컬럼은 `IS NULL OR ...` 보존). pg 제약명 = `<table>_<col>_check`.
3. 가드 IT 추가 — 신규 값 INSERT/UPDATE(롤변경 이력 등)가 제약 통과함을 Testcontainers 로 단언([[changed-module-full-test-before-push]]).
4. **머지 전 실 Postgres standalone 부팅 QA 로 신규 값 실 INSERT 검증**(Windows Testcontainers skip 한계 → jar 부팅). enum/시드 green ≠ DB 제약 OK.
