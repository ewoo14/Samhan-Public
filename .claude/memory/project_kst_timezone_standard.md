---
name: project-kst-timezone-standard
description: 시스템 전역(DB 포함) KST(Asia/Seoul) 기준 표준화 — 현재 전부 UTC. 배차 collab 머지 후 전담 PR. 합의된 접근(JVM -Duser.timezone + postgres TZ + Hibernate/Jackson)
metadata:
  type: project
---

**개발책임자 지시 (2026-06-14)**: "시스템 전체적으로 DB 포함 한국시간(KST, Asia/Seoul)이 기준이 되어야 한다." 배차 collab QA 스크린샷 시각이 UTC(예: 15:50 = KST 00:50)로 표시되어 발견됨.

**진단 (현 상태 — 전부 UTC)**:
- postgres 컨테이너 `SHOW timezone`=UTC, `now()`=+00. 서비스 컨테이너 `date`=UTC, `TZ` env 없음.
- `infrastructure/docker-compose*.yml` 에 `TZ`/timezone 설정 **전무**.
- 서비스 application.yml 에 `hibernate.jdbc.time_zone` / `spring.jackson.time-zone` KST 설정 **없음**.
- 베이스 이미지 = `eclipse-temurin:17-jre-alpine` (Alpine — OS tzdata 미포함이나 **JVM 자체 tzdb(tzdb.dat) 보유** → `-Duser.timezone=Asia/Seoul` 은 OS tzdata 없이 동작).

**합의된 접근 (전담 PR, 시스템 14서비스 + DB 인프라)**:
1. 전 서비스 컨테이너: `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Seoul` (compose env) → JVM 기본 TZ=KST → `LocalDateTime.now()` KST.
2. postgres 컨테이너: `TZ=Asia/Seoul` (compose env) → postgres `timezone` GUC=KST (postgres alpine 은 tzdata 포함).
3. Spring belt-and-suspenders: `spring.jackson.time-zone=Asia/Seoul` + `spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Seoul`.
4. `docker-compose`(로컬) + Phase 11 AWS 설정 동일 적용. redis/rabbitmq/ES 등 로그 일관성 위해 동일 TZ 권장.
5. 검증: 각 컨테이너 `date`·postgres `SHOW timezone`·실 타임스탬프 KST 렌더(실서버 QA 캡처).

**주의 (기존 데이터)**: TIMESTAMP(tz 없음) 컬럼은 UTC 벽시계값으로 저장돼 있어 적용 후 신규 쓰기는 KST·기존은 UTC 혼재 → dev 는 재시드로 정리. TIMESTAMPTZ(decided_at 등)는 tz-aware 라 표시만 KST 로 시프트(정상).

**시점**: §7 배차 collab 머지(#478) 후 → 본 KST 전담 PR → 이후 §7 그룹웨어 결재. (개발책임자 "배차 머지 후 전담 PR" 선택.) 워크플로우 = [[temp-multimodel-workflow]] (Opus 4.8↔Codex, Fable5 제외) + DevOps 주도.
